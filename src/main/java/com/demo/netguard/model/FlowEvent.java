package com.demo.netguard.model;

import java.time.Instant;

/** Metadata of ONE proxied connection attempt, never the page content or secret request details.
 *  "CONNECTED" means the upstream TCP connection succeeded, not an HTTP 2xx response.
 */
public record FlowEvent(Instant at, String sourceIp, String method,
                        String destination, int port, String protocol,
                        String outcome, String reason) {
    private static String esc(String s) {
        return (s == null ? "" : s).replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", " ").replace("\r", " ");
    }
    public String json() {
        return "{\"at\":\"" + at + "\",\"sourceIp\":\"" + esc(sourceIp)
            + "\",\"method\":\"" + esc(method) + "\",\"destination\":\""
            + esc(destination) + "\",\"port\":" + port + ",\"protocol\":\""
            + esc(protocol) + "\",\"outcome\":\"" + esc(outcome)
            + "\",\"reason\":\"" + esc(reason) + "\"}";
    }
}
