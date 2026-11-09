package com.demo.netguard.demo;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** Test destination. Binds only to loopback. */
public final class LocalDemoSite implements AutoCloseable {
    private final HttpServer server;
    public LocalDemoSite(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.setExecutor(Executors.newCachedThreadPool(r -> { Thread t=new Thread(r,"demo-site"); t.setDaemon(true); return t; }));
        server.createContext("/", e -> {
            byte[] msg = "LOCAL DEMO WEBSITE: reached through the forward proxy".getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            e.sendResponseHeaders(200, msg.length);
            try(var o=e.getResponseBody()) { o.write(msg); }
        });
    }
    public void start() {server.start();}
    public int port() {return server.getAddress().getPort();}
    public void close() {server.stop(0);}
}
