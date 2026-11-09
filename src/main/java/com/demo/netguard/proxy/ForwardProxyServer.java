package com.demo.netguard.proxy;

import com.demo.netguard.model.FlowEvent;
import com.demo.netguard.monitor.EventLog;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Explicit outbound forward proxy for an authorized training lab, NOT production-grade.
 *  Supports HTTP GET/HEAD and HTTPS CONNECT with blind tunneling, NOT TLS interception.
 *  By default listens on 127.0.0.1; non-loopback binding requires a specified private LAN IP
 *  and allowlisted client IPs. Internal network destinations are denied except mock website.
 */
public final class ForwardProxyServer implements AutoCloseable {
    private static final int MAX_LINE = 8192, MAX_HEADERS = 48;
    private static final int SOCKET_TIMEOUT_MS = 9000;
    private final ServerSocket listener;
    private final Thread acceptor;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(16, 16, 0L,
        TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), r -> {
        Thread t = new Thread(r,"proxy-worker");t.setDaemon(true);return t;
    });
    private final boolean lan;
    private final Set<String> clientAllowlist;
    private final EventLog log;
    private final int demoPort;
    private volatile boolean running;
    private volatile boolean closed;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final Set<Socket> upstreams = ConcurrentHashMap.newKeySet();

    public ForwardProxyServer(int port, String bindIp, Set<String> clients,
                              EventLog log, int demoPort) throws IOException {
        InetAddress bind = InetAddress.getByName(bindIp);
        if (!(bind instanceof Inet4Address) || bind.isAnyLocalAddress() ||
            !(bind.isLoopbackAddress() || bind.isSiteLocalAddress())) {
            throw new IllegalArgumentException("Bind must be explicit loopback or private IPv4 address");
        }
        this.lan = !bind.isLoopbackAddress();
        this.clientAllowlist = Set.copyOf(clients);
        if (lan && this.clientAllowlist.isEmpty())
            throw new IllegalArgumentException("LAN proxy requires approved client IPs");
        this.log = log;this.demoPort = demoPort;
        listener = new ServerSocket();listener.bind(new InetSocketAddress(bind,port),50);
        acceptor = new Thread(this::accept,"proxy-accept");acceptor.setDaemon(true);
    }
    public synchronized void start(){
        if(closed)throw new IllegalStateException("Proxy is already closed");
        if(running)return;
        running=true;
        acceptor.start();
    }
    public int port(){return listener.getLocalPort();}
    private void accept() {
        while(running) {
            try {
                Socket client = listener.accept();
                client.setSoTimeout(SOCKET_TIMEOUT_MS);
                clients.add(client);
                try { workers.execute(() -> serve(client)); }
                catch (RejectedExecutionException ex) {
                    clients.remove(client);
                    try { send(client.getOutputStream(),503,"Service Unavailable","Proxy is at capacity"); }
                    catch(IOException ignored){}
                    try { client.close(); } catch(IOException ignored){}
                }
            } catch(IOException ex) { if(running) System.err.println("Proxy accept: " + ex.getMessage()); }
        }
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream buf=new ByteArrayOutputStream();int b;
        while((b=in.read())!=-1) {
            if(b=='\n')break;
            if(b!='\r')buf.write(b);
            if(buf.size()>MAX_LINE)throw new IOException("Header line too long");
        }
        if(b==-1 && buf.size()==0)throw new EOFException();
        return buf.toString(StandardCharsets.ISO_8859_1);
    }
    private static void send(OutputStream out,int code,String phrase,String message) throws IOException {
        byte[] body=message.getBytes(StandardCharsets.UTF_8);
        String headers="HTTP/1.1 "+code+" "+phrase+"\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));out.write(body);out.flush();
    }
    private static String normalizedHost(String host) {
        String h=host==null?"":host.toLowerCase(Locale.ROOT);
        if(h.length()>253 || !h.matches("[a-z0-9][a-z0-9.-]*[a-z0-9]|[a-z0-9]") || h.contains(".."))return null;
        return h;
    }
    private record Destination(String host,int port,String path,String method) {
        String protocol(){return method.equals("CONNECT")?"HTTPS_TUNNEL":"HTTP";}
    }
    private static Destination parse(String[] first) {
        if(first.length!=3 || !(first[2].equals("HTTP/1.0") || first[2].equals("HTTP/1.1")))
            throw new IllegalArgumentException("Invalid request line");
        String method=first[0],target=first[1];
        if(method.equals("CONNECT")) {
            if(!target.matches("[A-Za-z0-9.-]+:[0-9]{1,5}"))throw new IllegalArgumentException("Invalid CONNECT authority");
            int ix=target.lastIndexOf(':');
            String host=normalizedHost(target.substring(0,ix));int port=Integer.parseInt(target.substring(ix+1));
            if(host==null || port<1 || port>65535)throw new IllegalArgumentException("Invalid CONNECT target");
            return new Destination(host,port,"",method);
        }
        if(!method.equals("GET") && !method.equals("HEAD"))
            throw new IllegalArgumentException("Only GET, HEAD, CONNECT supported in starter");
        URI uri=URI.create(target);
        if(!"http".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null || uri.getRawFragment()!=null)
            throw new IllegalArgumentException("Expected absolute http:// URL");
        String host=normalizedHost(uri.getHost());int port=uri.getPort()<0?80:uri.getPort();
        if(host==null || port<1 || port>65535)throw new IllegalArgumentException("Invalid HTTP host/port");
        String path=uri.getRawPath();if(path==null || path.isEmpty())path="/";
        if(uri.getRawQuery()!=null)path+="?"+uri.getRawQuery();
        return new Destination(host,port,path,method);
    }
    private void record(String source,Destination d,String outcome,String reason) {
        log.append(new FlowEvent(Instant.now(),source,d.method(),d.host(),d.port(),d.protocol(),outcome,reason));
    }
    private InetAddress safeResolve(Destination d,boolean loopbackClient) throws IOException {
        if(d.host().equals("127.0.0.1") && d.port()==demoPort && loopbackClient)
            return InetAddress.getByName("127.0.0.1");
        if(d.method().equals("CONNECT") && d.port()!=443)throw new IOException("Only CONNECT 443 allowed");
        if(!d.method().equals("CONNECT") && d.port()!=80)throw new IOException("Only HTTP 80 allowed");
        for(InetAddress ip : InetAddress.getAllByName(d.host())) {
            if(!ip.isAnyLocalAddress() && !ip.isLoopbackAddress() && !ip.isLinkLocalAddress()
                && !ip.isSiteLocalAddress() && !ip.isMulticastAddress()) return ip;
        }
        throw new IOException("Restricted destination");
    }
    private void serve(Socket client) {
        try(client) {
            InputStream in=new BufferedInputStream(client.getInputStream());
            OutputStream out=client.getOutputStream();
            String source=client.getInetAddress().getHostAddress();
            if(lan && !clientAllowlist.contains(source)) {
                send(out,403,"Forbidden","Client IP not on authorized lab allowlist");return;
            }
            String requestLine;
            try {requestLine=line(in);}
            catch(SocketTimeoutException e){send(out,408,"Request Timeout","Timed out waiting for request");return;}
            catch(EOFException e){return;}
            catch(IOException e){send(out,400,"Bad Request","Invalid request line");return;}
            String[] parts=requestLine.split(" ",3);
            if(parts.length!=3){send(out,400,"Bad Request","Invalid request line");return;}
            Map<String,String> headers=new LinkedHashMap<>();int bytes=0;
            try {
            for(int i=0;i<MAX_HEADERS;i++){
                String h=line(in);bytes+=h.length();
                if(bytes>32*1024)throw new IOException("Headers too large");
                if(h.isBlank())break;
                int colon=h.indexOf(':');if(colon<=0)throw new IOException("Malformed header");
                String name=h.substring(0,colon).toLowerCase(Locale.ROOT).trim();
                if(!name.matches("[!#$%&'*+.^_`|~0-9a-z-]+") || headers.containsKey(name))
                    throw new IOException("Malformed or duplicate header");
                headers.put(name,h.substring(colon+1).trim());
                if(i==MAX_HEADERS-1)throw new IOException("Too many headers");
            }
            } catch(SocketTimeoutException e){send(out,408,"Request Timeout","Timed out waiting for headers");return;}
            catch(EOFException e){send(out,400,"Bad Request","Incomplete request headers");return;}
            catch(IOException e){send(out,400,"Bad Request","Malformed request headers");return;}
            Destination d;
            try {d=parse(parts);} catch(RuntimeException e){send(out,400,"Bad Request","Invalid or unsupported method/target");return;}
            if(!hostHeaderMatches(headers.get("host"),d,demoPort)) {
                send(out,400,"Bad Request","Host header does not match request target");return;
            }
            if(headers.containsKey("transfer-encoding") || (headers.containsKey("content-length") && !headers.get("content-length").equals("0"))) {
                record(source,d,"DENIED","REQUEST_BODY_UNSUPPORTED");
                send(out,400,"Bad Request","Request body not supported by phase 1 starter");return;
            }
            InetAddress ip;
            try {ip=safeResolve(d,client.getInetAddress().isLoopbackAddress());}
            catch(IOException ex){record(source,d,"DENIED","DESTINATION_POLICY");
                send(out,403,"Forbidden","Destination not permitted in this lab proxy");return;}
            if(closed){send(out,503,"Service Unavailable","Proxy is shutting down");return;}
            Socket upstream = new Socket();
            upstreams.add(upstream);
            if(closed){upstreams.remove(upstream);upstream.close();send(out,503,"Service Unavailable","Proxy is shutting down");return;}
            try {
                upstream.connect(new InetSocketAddress(ip,d.port()),3500);
            } catch(IOException e) {
                upstreams.remove(upstream);
                try { upstream.close(); } catch(IOException ignored){}
                record(source,d,"FAILED","UPSTREAM_CONNECTION_FAILURE");
                try{send(out,502,"Bad Gateway","Upstream connection failed");}catch(IOException ignored){}
                return;
            }
            try(upstream) {
                upstream.setSoTimeout(SOCKET_TIMEOUT_MS);
                // CONNECTED means the upstream TCP socket opened; relay results do not change it.
                record(source,d,"CONNECTED","UPSTREAM_TCP_CONNECTED");
                if(d.method().equals("CONNECT")) {
                    out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));out.flush();
                    InputStream remoteIn=upstream.getInputStream();OutputStream remoteOut=upstream.getOutputStream();
                    Thread upload=new Thread(() -> copy(in,remoteOut),"tunnel-upload");upload.setDaemon(true);upload.start();
                    copy(remoteIn,out);
                    try{upload.join(250);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                } else {
                    OutputStream remoteOut=upstream.getOutputStream();
                    String opening=d.method()+" "+d.path()+" HTTP/1.1\r\nHost: "+d.host()+(d.port()==80?"":":"+d.port())+"\r\nConnection: close\r\n";
                    remoteOut.write(opening.getBytes(StandardCharsets.ISO_8859_1));
                    for(String name:List.of("accept","accept-language","user-agent")) {
                        String val=headers.get(name);
                        if(val!=null && !val.contains("\r") && !val.contains("\n"))
                            remoteOut.write((name+": "+val+"\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    }
                    remoteOut.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));remoteOut.flush();
                    copy(upstream.getInputStream(),out);
                }
            } catch(IOException ignored) { /* Upstream was connected; never send a second response. */ }
            finally {upstreams.remove(upstream);}
        } catch(Exception ex) {
            // Malformed/closed client connections are not logged with secrets.
        } finally {
            clients.remove(client);
        }
    }
    private static boolean hostHeaderMatches(String header,Destination destination,int demoPort) {
        if(header==null || header.isBlank())return false;
        String value=header.trim();
        int colon=value.lastIndexOf(':');
        String host=value;int port=-1;
        if(colon>=0) {
            if(value.indexOf(':')!=colon)return false;
            host=value.substring(0,colon);
            String portText=value.substring(colon+1);
            if(portText.isEmpty() || !portText.matches("[0-9]{1,5}"))return false;
            try {port=Integer.parseInt(portText);} catch(NumberFormatException e){return false;}
            if(port<1 || port>65535)return false;
        }
        String normalized=normalizedHost(host);
        if(normalized==null || !normalized.equals(destination.host()))return false;
        if(port>=0)return port==destination.port();
        boolean loopbackDemo=destination.host().equals("127.0.0.1") && destination.port()==demoPort;
        return destination.port()==80 || loopbackDemo
            || (destination.method().equals("CONNECT") && destination.port()==443);
    }
    private static void copy(InputStream from,OutputStream to) {
        try {byte[] buf=new byte[8192];int n;while((n=from.read(buf))!=-1){to.write(buf,0,n);to.flush();}}
        catch(IOException ignored){}
    }
    @Override public void close() {
        closed=true;running=false;
        try{listener.close();}catch(IOException ignored){}
        workers.shutdownNow();
        for(Socket client:clients)try{client.close();}catch(IOException ignored){}
        for(Socket upstream:upstreams)try{upstream.close();}catch(IOException ignored){}
        try{workers.awaitTermination(1,TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
