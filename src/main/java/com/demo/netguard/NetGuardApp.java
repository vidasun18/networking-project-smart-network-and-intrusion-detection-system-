package com.demo.netguard;

import com.demo.netguard.demo.LocalDemoSite;
import com.demo.netguard.monitor.*;
import com.demo.netguard.proxy.ForwardProxyServer;
import java.nio.file.*;
import java.util.*;

/** NetProxy Phase 1: Java forward proxy + connection-metadata logging + admin viewer. */
public final class NetGuardApp implements AutoCloseable {
    private final LocalDemoSite mock;
    private final EventLog log;
    private final ForwardProxyServer proxy;
    private final DashboardServer dashboard;
    public NetGuardApp(int proxyPort,int dashboardPort,int mockPort,Path events,
                       String bindIp,Set<String> allowlist) throws Exception {
        log=new EventLog(events);
        mock=new LocalDemoSite(mockPort);
        proxy=new ForwardProxyServer(proxyPort,bindIp,allowlist,log,mock.port());
        dashboard=new DashboardServer(dashboardPort,log);
    }
    public void start(){mock.start();proxy.start();dashboard.start();}
    public int proxyPort(){return proxy.port();}
    public int dashboardPort(){return dashboard.port();}
    public int demoPort(){return mock.port();}
    public EventLog events(){return log;}
    @Override public void close(){dashboard.close();proxy.close();mock.close();}
    public static void main(String[] args) throws Exception {
        String bindIp="127.0.0.1";
        for(int i=0;i<args.length;i++) {
            if(args[i].equals("--bind") && i+1<args.length)bindIp=args[++i];
            else throw new IllegalArgumentException("Usage: --bind <explicit-private-IPv4-address> (optional)");
        }
        Set<String> allowed=new HashSet<>();
        Path list=Path.of("config/allowed-lan-clients.txt");
        if(Files.exists(list))for(String item:Files.readAllLines(list)){
            item=item.strip();if(!item.isBlank()&&!item.startsWith("#"))allowed.add(item);
        }
        try(var app=new NetGuardApp(8888,8181,9090,Path.of("data/proxy-events.jsonl"),bindIp,allowed)){
            app.start();
            System.out.println("Proxy listening: "+bindIp+":"+app.proxyPort());
            System.out.println("Local-only dashboard: http://127.0.0.1:"+app.dashboardPort());
            System.out.println("Local-only demo destination: http://127.0.0.1:"+app.demoPort());
            System.out.println("Configure a browser/curl HTTP(S) explicit proxy. Ctrl+C to stop.");
            Thread.currentThread().join();
        }
    }
}
