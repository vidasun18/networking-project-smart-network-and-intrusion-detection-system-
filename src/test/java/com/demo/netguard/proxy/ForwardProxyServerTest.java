package com.demo.netguard.proxy;

import com.demo.netguard.monitor.EventLog;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Focused Dev 1 regression checks; uses only owned loopback test servers. */
public final class ForwardProxyServerTest {
    private static int checks;

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
        System.out.println("  OK " + description);
    }

    private static String request(int port, String message) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(12000);
            socket.getOutputStream().write(message.getBytes(StandardCharsets.ISO_8859_1));
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readHeaderBlock(Socket socket) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        InputStream in = socket.getInputStream();
        int value;
        while (bytes.size() < 8192 && (value = in.read()) != -1) {
            bytes.write(value);
            if (bytes.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n"))
                return bytes.toString(StandardCharsets.ISO_8859_1);
        }
        throw new java.io.IOException("Incomplete CONNECT response headers");
    }

    private static void testTunnelHalfClose(Path directory) throws Exception {
        String clientPayload = "opaque client tunnel bytes";
        String upstreamPayload = "opaque upstream tunnel bytes";
        String[] receivedPayload = new String[1];
        CountDownLatch upstreamSawEof = new CountDownLatch(1);
        try (ServerSocket tunnelTarget = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread tunnelPeer = new Thread(() -> {
                try (Socket peer = tunnelTarget.accept()) {
                    peer.setSoTimeout(3000);
                    receivedPayload[0] = new String(peer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    upstreamSawEof.countDown();
                    peer.getOutputStream().write(upstreamPayload.getBytes(StandardCharsets.UTF_8));
                    peer.shutdownOutput();
                } catch (Exception ignored) { }
            }, "tunnel-half-close-test-peer");
            tunnelPeer.setDaemon(true);
            tunnelPeer.start();

            EventLog tunnelEvents = new EventLog(directory.resolve("tunnel-events.jsonl"));
            try (ForwardProxyServer tunnelProxy = new ForwardProxyServer(0, "127.0.0.1",
                    Set.of(), tunnelEvents, tunnelTarget.getLocalPort())) {
                tunnelProxy.start();
                try (Socket client = new Socket("127.0.0.1", tunnelProxy.port())) {
                    client.setSoTimeout(5000);
                    String connect = "CONNECT 127.0.0.1:" + tunnelTarget.getLocalPort()
                        + " HTTP/1.1\r\nHost: 127.0.0.1:" + tunnelTarget.getLocalPort() + "\r\n\r\n";
                    client.getOutputStream().write(connect.getBytes(StandardCharsets.ISO_8859_1));
                    client.getOutputStream().flush();
                    check(readHeaderBlock(client).startsWith("HTTP/1.1 200 Connection Established"),
                        "CONNECT establishes the synthetic tunnel");

                    client.getOutputStream().write(clientPayload.getBytes(StandardCharsets.UTF_8));
                    client.shutdownOutput();
                    String response = new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    check(upstreamSawEof.await(1, TimeUnit.SECONDS),
                        "client half-close propagates promptly to the upstream socket");
                    check(clientPayload.equals(receivedPayload[0]), "CONNECT preserves client tunnel bytes");
                    check(upstreamPayload.equals(response), "upstream half-close relays its final bytes and EOF");
                }
            } finally {
                tunnelTarget.close();
                tunnelPeer.join(1000);
            }
        }
    }

    private static void testTunnelClientDisconnect(Path directory) throws Exception {
        String clientPayload = "bytes before client disconnect";
        String[] receivedPayload = new String[1];
        CountDownLatch upstreamSawDisconnect = new CountDownLatch(1);
        try (ServerSocket tunnelTarget = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread tunnelPeer = new Thread(() -> {
                try (Socket peer = tunnelTarget.accept()) {
                    peer.setSoTimeout(3000);
                    receivedPayload[0] = new String(peer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    upstreamSawDisconnect.countDown();
                } catch (Exception ignored) { }
            }, "tunnel-disconnect-test-peer");
            tunnelPeer.setDaemon(true);
            tunnelPeer.start();

            EventLog tunnelEvents = new EventLog(directory.resolve("disconnect-tunnel-events.jsonl"));
            try (ForwardProxyServer tunnelProxy = new ForwardProxyServer(0, "127.0.0.1",
                    Set.of(), tunnelEvents, tunnelTarget.getLocalPort())) {
                tunnelProxy.start();
                try (Socket client = new Socket("127.0.0.1", tunnelProxy.port())) {
                    client.setSoTimeout(3000);
                    String connect = "CONNECT 127.0.0.1:" + tunnelTarget.getLocalPort()
                        + " HTTP/1.1\r\nHost: 127.0.0.1:" + tunnelTarget.getLocalPort() + "\r\n\r\n";
                    client.getOutputStream().write(connect.getBytes(StandardCharsets.ISO_8859_1));
                    client.getOutputStream().flush();
                    check(readHeaderBlock(client).startsWith("HTTP/1.1 200 Connection Established"),
                        "CONNECT opens before client disconnect test");
                    client.getOutputStream().write(clientPayload.getBytes(StandardCharsets.UTF_8));
                    client.getOutputStream().flush();
                }
                check(upstreamSawDisconnect.await(2, TimeUnit.SECONDS),
                    "full client disconnect propagates promptly to upstream");
                check(clientPayload.equals(receivedPayload[0]),
                    "upstream receives buffered tunnel bytes before disconnect");
            } finally {
                tunnelTarget.close();
                tunnelPeer.join(1000);
            }
        }
    }

    private static void testTunnelShutdown(Path directory) throws Exception {
        CountDownLatch upstreamClosed = new CountDownLatch(1);
        try (ServerSocket tunnelTarget = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread tunnelPeer = new Thread(() -> {
                try (Socket peer = tunnelTarget.accept()) {
                    peer.setSoTimeout(3000);
                    if (peer.getInputStream().read() == -1) upstreamClosed.countDown();
                } catch (Exception ignored) { }
            }, "tunnel-shutdown-test-peer");
            tunnelPeer.setDaemon(true);
            tunnelPeer.start();

            EventLog tunnelEvents = new EventLog(directory.resolve("shutdown-tunnel-events.jsonl"));
            ForwardProxyServer tunnelProxy = new ForwardProxyServer(0, "127.0.0.1",
                Set.of(), tunnelEvents, tunnelTarget.getLocalPort());
            Socket client = new Socket("127.0.0.1", tunnelProxy.port());
            try {
                client.setSoTimeout(3000);
                tunnelProxy.start();
                String connect = "CONNECT 127.0.0.1:" + tunnelTarget.getLocalPort()
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + tunnelTarget.getLocalPort() + "\r\n\r\n";
                client.getOutputStream().write(connect.getBytes(StandardCharsets.ISO_8859_1));
                client.getOutputStream().flush();
                check(readHeaderBlock(client).startsWith("HTTP/1.1 200 Connection Established"),
                    "CONNECT opens before proxy shutdown test");

                long started = System.nanoTime();
                tunnelProxy.close();
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                check(elapsedMs < 2000, "proxy shutdown releases an idle tunnel promptly");
                check(upstreamClosed.await(1, TimeUnit.SECONDS),
                    "proxy shutdown closes the upstream tunnel socket");
            } finally {
                client.close();
                tunnelProxy.close();
                tunnelTarget.close();
                tunnelPeer.join(1000);
            }
        }
    }

    private static boolean connectionRefused(int port) {
        try (Socket ignored = new Socket("127.0.0.1", port)) {
            return false;
        } catch (java.io.IOException expected) {
            return true;
        }
    }

    private static void rejectsConfiguration(Path events, int port, String bindIp,
                                             Set<String> allowlist, int demoPort,
                                             String description) throws Exception {
        try {
            new ForwardProxyServer(port, bindIp, allowlist,
                new EventLog(events), demoPort).close();
            throw new AssertionError(description);
        } catch (IllegalArgumentException expected) {
            check(true, description);
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("dev1-proxy-test-");
        check(ForwardProxyServer.isPublicDestinationAddress(InetAddress.getByName("8.8.8.8")),
            "globally routable IPv4 destination is allowed by address policy");
        check(ForwardProxyServer.isPublicDestinationAddress(InetAddress.getByName("2606:4700:4700::1111")),
            "globally routable IPv6 destination is allowed by address policy");
        for (String blocked : new String[] {"10.1.2.3", "100.64.0.1", "127.0.0.2",
                "169.254.169.254", "172.16.0.1", "192.0.2.1", "192.168.1.1",
                "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1",
                "::", "::1", "fc00::1", "fdff::1", "fe80::1", "fec0::1", "ff02::1",
                "2001:db8::1", "2002::1", "3fff::1"}) {
            check(!ForwardProxyServer.isPublicDestinationAddress(InetAddress.getByName(blocked)),
                "nonpublic destination range is rejected: " + blocked);
        }
        rejectsConfiguration(directory.resolve("invalid-bind.jsonl"), 0, "localhost",
            Set.of(), 9090, "proxy bind must be an explicit IPv4 literal");
        rejectsConfiguration(directory.resolve("public-bind.jsonl"), 0, "203.0.113.10",
            Set.of("192.168.1.20"), 9090, "LAN proxy refuses non-private bind addresses");
        rejectsConfiguration(directory.resolve("invalid-client.jsonl"), 0, "192.168.1.10",
            Set.of("localhost"), 9090, "LAN allowlist entries must be private IPv4 literals");
        rejectsConfiguration(directory.resolve("empty-allowlist.jsonl"), 0, "192.168.1.10",
            Set.of(), 9090, "LAN proxy requires at least one approved client");

        HttpServer mock = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mock.setExecutor(java.util.concurrent.ForkJoinPool.commonPool());
        CountDownLatch simultaneous = new CountDownLatch(2);
        mock.createContext("/", exchange -> {
            simultaneous.countDown();
            byte[] body = "synthetic response".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        mock.start();

        EventLog events = new EventLog(directory.resolve("events.jsonl"));
        ForwardProxyServer proxy = new ForwardProxyServer(0, "127.0.0.1", Set.of(), events, mock.getAddress().getPort());
        proxy.start();
        int proxyPort = proxy.port();
        check(proxyPort > 0, "proxy starts on an ephemeral port");

        String authorizedRequest = "GET http://127.0.0.1:" + mock.getAddress().getPort()
            + "/allowlist-check HTTP/1.1\r\nHost: 127.0.0.1:" + mock.getAddress().getPort() + "\r\n\r\n";
        try (ForwardProxyServer rejectingProxy = new ForwardProxyServer(0, "127.0.0.1",
                Set.of("192.168.1.99"), events, mock.getAddress().getPort(), true)) {
            rejectingProxy.start();
            check(request(rejectingProxy.port(), authorizedRequest).startsWith("HTTP/1.1 403"),
                "runtime rejects a client IP missing from the allowlist");
        }

        String validHost = "GET http://127.0.0.1:" + mock.getAddress().getPort() + "/ok HTTP/1.1\r\n"
            + "Host: 127.0.0.1:" + mock.getAddress().getPort() + "\r\nConnection: close\r\n\r\n";
        var callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(() -> request(proxyPort, validHost));
            var second = callers.submit(() -> request(proxyPort, validHost));
            check(first.get(8, TimeUnit.SECONDS).contains("synthetic response"), "first concurrent HTTP request is forwarded");
            check(second.get(8, TimeUnit.SECONDS).contains("synthetic response"), "second concurrent HTTP request is forwarded");
            check(simultaneous.await(2, TimeUnit.SECONDS), "mock server observes overlapping client requests");
        } finally {
            callers.shutdownNow();
        }
        try (ForwardProxyServer acceptingProxy = new ForwardProxyServer(0, "127.0.0.1",
                Set.of("127.0.0.1"), events, mock.getAddress().getPort(), true)) {
            acceptingProxy.start();
            check(request(acceptingProxy.port(), authorizedRequest).contains("synthetic response"),
                "runtime accepts a client IP present in the allowlist");
        }

        String mismatch = "GET http://127.0.0.1:" + mock.getAddress().getPort() + "/ HTTP/1.1\r\n"
            + "Host: example.org\r\n\r\n";
        check(request(proxyPort, mismatch).startsWith("HTTP/1.1 400"), "mismatched Host is rejected");
        String duplicate = "GET http://127.0.0.1:" + mock.getAddress().getPort() + "/ HTTP/1.1\r\n"
            + "Host: 127.0.0.1\r\nHost: example.org\r\n\r\n";
        check(request(proxyPort, duplicate).startsWith("HTTP/1.1 400"), "duplicate Host header is rejected");
        check(request(proxyPort, "GET http://127.0.0.1/ HTTP/1.9\r\nHost: 127.0.0.1\r\n\r\n")
            .startsWith("HTTP/1.1 400"), "unsupported HTTP version is rejected");
        for (String blocked : new String[] {"100.64.0.1", "169.254.169.254", "192.0.2.1", "198.18.0.1"}) {
            String request = "GET http://" + blocked + "/synthetic HTTP/1.1\r\nHost: " + blocked + "\r\n\r\n";
            check(request(proxyPort, request).startsWith("HTTP/1.1 403"),
                "proxy denies nonpublic IPv4 destination: " + blocked);
        }
        testTunnelHalfClose(directory);
        testTunnelClientDisconnect(directory);
        testTunnelShutdown(directory);

        proxy.close();
        mock.stop(0);
        check(connectionRefused(proxyPort), "proxy shuts down cleanly");

        int unavailablePort;
        try (ServerSocket reserved = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            unavailablePort = reserved.getLocalPort();
        }
        EventLog failureEvents = new EventLog(directory.resolve("failure-events.jsonl"));
        try (ForwardProxyServer failureProxy = new ForwardProxyServer(0, "127.0.0.1", Set.of(), failureEvents, unavailablePort)) {
            failureProxy.start();
            String unavailable = "GET http://127.0.0.1:" + unavailablePort + "/ HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + unavailablePort + "\r\n\r\n";
            check(request(failureProxy.port(), unavailable).startsWith("HTTP/1.1 502"), "unavailable upstream returns 502");
        }
        String failureLog = Files.readString(directory.resolve("failure-events.jsonl"));
        check(failureLog.contains("\"outcome\":\"FAILED\"") && failureLog.lines().count() == 1,
            "upstream failure produces exactly one FAILED event");

        ServerSocket slowServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        Thread slowResponder = new Thread(() -> {
            try (Socket ignored = slowServer.accept()) { Thread.sleep(11000); }
            catch (Exception ignored) { }
        }, "slow-upstream-test");
        slowResponder.setDaemon(true);
        slowResponder.start();
        try (ForwardProxyServer timeoutProxy = new ForwardProxyServer(0, "127.0.0.1", Set.of(), events, slowServer.getLocalPort())) {
            timeoutProxy.start();
            String slow = "GET http://127.0.0.1:" + slowServer.getLocalPort() + "/ HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + slowServer.getLocalPort() + "\r\n\r\n";
            long started = System.nanoTime();
            check(request(timeoutProxy.port(), slow).isEmpty(), "upstream read timeout closes the client without a second response");
            check(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 11,
                "upstream timeout returns control instead of hanging");
        } finally {
            slowServer.close();
            slowResponder.interrupt();
        }
        System.out.println("PASS: " + checks + " Developer 1 proxy checks");
    }
}
