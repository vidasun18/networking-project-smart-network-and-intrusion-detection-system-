package com.demo.netguard;

import com.demo.netguard.model.FlowEvent;
import com.demo.netguard.monitor.EventLog;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** No external network needed; owned loopback mock only. */
public final class SmokeTest {
    private static int checks;
    private static void check(boolean result,String label) {
        if(!result)throw new AssertionError(label);
        checks++;System.out.println("  OK " + label);
    }
    private static String request(int port,String request) throws Exception {
        try(Socket socket=new Socket("127.0.0.1",port)){
            socket.setSoTimeout(12000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private static String get(int proxy,int target,String path) throws Exception {
        return request(proxy,"GET http://127.0.0.1:"+target+path+" HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
    }
    public static void main(String[] args) throws Exception {
        Path tmp=Files.createTempDirectory("netproxy-phase1-");
        Path events=tmp.resolve("events.jsonl");
        try(var app=new NetGuardApp(0,0,0,events,"127.0.0.1",Set.of())){
            app.start();int proxy=app.proxyPort(),demo=app.demoPort(),admin=app.dashboardPort();
            String good=get(proxy,demo,"/secret-path?token=must-not-log");
            check(good.startsWith("HTTP/1.1 200"),"HTTP proxy returns upstream status");
            check(good.contains("LOCAL DEMO WEBSITE"),"GET reaches owned test web site");
            String tunnel=request(proxy,"CONNECT 127.0.0.1:"+demo+" HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
            check(tunnel.startsWith("HTTP/1.1 200 Connection Established"),"CONNECT tunnel opens");
            check(tunnel.contains("LOCAL DEMO WEBSITE"),"CONNECT relays bytes without TLS inspection");
            String denied=request(proxy,"GET http://169.254.169.254/latest HTTP/1.1\r\nHost: 169.254.169.254\r\n\r\n");
            check(denied.startsWith("HTTP/1.1 403"),"metadata/internal target denied");
            check(request(proxy,"POST http://example.com/ HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 400"),"unsupported POST rejected");
            check(request(proxy,"GET https://example.com/ HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 400"),"HTTPS absolute-form rejected (must tunnel)");
            String api=request(admin,"GET /api/events HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            check(api.contains("127.0.0.1"),"events API returns logged client");
            check(api.contains("\"protocol\":\"HTTPS_TUNNEL\""),"events distinguish HTTPS tunnel");
            check(api.contains("\"outcome\":\"CONNECTED\""),"events record connected outcome");
            check(api.contains("\"outcome\":\"DENIED\""),"safe-demo denial recorded without IDS");
            String filter=request(admin,"GET /api/events?domain=not-a-match HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            check(filter.endsWith("[]"),"domain filter excludes mismatches");
            String summary=request(admin,"GET /api/summary HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            check(summary.contains("\"connected\":2"),"summary counts two successful upstream connections");
            check(summary.contains("\"denied\":1"),"summary includes policy-denied request");
            check(Files.exists(events),"persistent JSONL log exists");
            String text=Files.readString(events);
            check(text.contains("\"destination\""),"JSONL includes destination host");
            check(!text.contains("secret-path")&&!text.contains("must-not-log"),"paths and query tokens NOT logged");
            check(!text.contains("HISTORICAL_DOMAIN")&&!text.contains("INTRUSION"),"no IDS logic in Phase 1 log");
            check(request(admin,"GET /health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").contains("ok"),"dashboard health endpoint");
            ExecutorService jobs=Executors.newFixedThreadPool(6);
            try{
                List<Future<String>> results=new ArrayList<>();
                for(int i=0;i<10;i++)results.add(jobs.submit(()->get(proxy,demo,"/bulk")));
                for(Future<String> r:results)check(r.get(15,TimeUnit.SECONDS).contains("LOCAL DEMO WEBSITE"),"concurrent proxy request");
            }finally{jobs.shutdownNow();}
        }
        // Verify direct concurrent logger behaviour, independent of web server.
        Path separate=tmp.resolve("parallel.jsonl");
        EventLog store=new EventLog(separate);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try{
            List<Future<?>> fs=new ArrayList<>();
            for(int i=0;i<20;i++){
                final int n=i;
                fs.add(pool.submit(()->store.append(new FlowEvent(Instant.now(),"10.1.1."+n,"GET","example.com",80,"HTTP","CONNECTED","UPSTREAM_TCP_CONNECTED"))));
            }
            for(Future<?> f:fs)f.get(5,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        check(Files.readAllLines(separate).size()==20,"20 concurrent log writes kept as complete JSONL records");
        try{
            new NetGuardApp(0,0,0,tmp.resolve("unused.jsonl"),"192.168.15.2",Set.of());
            throw new AssertionError("unsafe LAN startup unexpectedly accepted");
        }catch(IllegalArgumentException expected){checks++;System.out.println("  OK unsafe LAN startup requires allowlist");}
        System.out.println("PASS: " + checks + " Phase 1 proxy-and-logging checks");
    }
}
