package com.demo.netguard.monitor;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/** Read-only, LOCALHOST-ONLY dashboard; not an authenticated network admin console. */
public final class DashboardServer implements AutoCloseable {
    private final HttpServer server;
    private final EventLog log;
    public DashboardServer(int port,EventLog log) throws IOException {
        this.log=log;
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"dashboard");t.setDaemon(true);return t;}));
        server.createContext("/api/events", e -> {
            Map<String,String> q=query(e.getRequestURI().getRawQuery());
            int limit=100;
            try{limit=Integer.parseInt(q.getOrDefault("limit","100"));}catch(NumberFormatException ignored){}
            reply(e,200,"application/json",log.recentJson(limit,q.getOrDefault("client",""),q.getOrDefault("domain","")));
        });
        server.createContext("/api/summary",e->reply(e,200,"application/json",log.summaryJson()));
        server.createContext("/health",e->reply(e,200,"text/plain","ok"));
        server.createContext("/",e->reply(e,200,"text/html",PAGE));
    }
    private static Map<String,String> query(String raw) {
        Map<String,String> m=new HashMap<>();
        if(raw==null || raw.length()>1024)return m;
        for(String part:raw.split("&",6)) {
            String[] kv=part.split("=",2);
            if(kv.length==2)try{m.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),
                URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}catch(IllegalArgumentException ignored){}
        }
        return m;
    }
    private static void reply(HttpExchange e,int status,String type,String response) throws IOException {
        e.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        e.getResponseHeaders().set("Cache-Control","no-store");
        e.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'");
        byte[] data=response.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,data.length);
        try(var out=e.getResponseBody()){out.write(data);}
    }
    private static final String PAGE="""
        <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
        <title>NetProxy Phase 1 — Activity Viewer</title>
        <style>body{font:15px system-ui;background:#0f172a;color:#f8fafc;max-width:1050px;margin:30px auto;padding:0 16px}
        h1{font-size:25px}section{background:#1e293b;padding:17px;border-radius:10px;margin:16px 0}
        label{margin-right:12px}input{background:#0f172a;color:#fff;border:1px solid #64748b;border-radius:5px;padding:7px}
        table{border-collapse:collapse;width:100%;font-size:13px}td,th{text-align:left;border-bottom:1px solid #475569;padding:8px}
        .small{color:#cbd5e1;font-size:13px}</style>
        <h1>Java Network Proxy — Phase 1</h1><p>Only proxy metadata is recorded. No IDS or threat classification exists yet.</p>
        <section><h2>Connection summary</h2><div id="totals">Loading...</div></section>
        <section><h2>Recent proxied connections</h2>
        <label>Client IP <input id="client" placeholder="192.168.1.10"></label>
        <label>Domain <input id="domain" placeholder="example.com"></label>
        <p class="small">HTTPS CONNECT destination only; no URL paths, credentials or encrypted content.</p>
        <table><thead><tr><th>Time</th><th>Client</th><th>Protocol</th><th>Destination</th><th>Outcome</th></tr></thead><tbody id="entries"></tbody></table>
        </section><script>
        function td(row,value){const cell=document.createElement('td');cell.textContent=String(value);row.appendChild(cell)}
        async function refresh(){try{
          const params=new URLSearchParams({client:document.getElementById('client').value,domain:document.getElementById('domain').value,limit:'100'});
          const [summary,events]=await Promise.all([fetch('/api/summary').then(r=>r.json()),fetch('/api/events?'+params).then(r=>r.json())]);
          document.getElementById('totals').textContent=`Recent: ${summary.recentTotal} | Connected: ${summary.connected} | Failed: ${summary.failed} | Denied by safe-demo rules: ${summary.denied}`;
          const body=document.getElementById('entries');body.replaceChildren();
          for(const e of events){const tr=document.createElement('tr');[e.at,e.sourceIp,e.protocol,e.destination+':'+e.port,e.outcome].forEach(x=>td(tr,x));body.appendChild(tr)}
        }catch(e){document.getElementById('totals').textContent='Dashboard unavailable'}}
        refresh();setInterval(refresh,2500);</script></html>
        """;
    public void start(){server.start();}
    public int port(){return server.getAddress().getPort();}
    @Override public void close(){server.stop(0);}
}
