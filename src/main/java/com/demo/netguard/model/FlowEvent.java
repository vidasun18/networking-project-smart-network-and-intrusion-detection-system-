package com.demo.netguard.model;

import java.time.Instant;

/** Metadata for one proxied connection attempt; never page content or private request details.
 *
 * <p>The destination is a hostname only. Do not put a full URL, path, query, credentials,
 * headers, cookies, request/response bodies, or TLS data in this record. The source IP is
 * the address observed on the client-to-proxy socket; it is not a verified user or device
 * identity.
 *
 * <p>Supported methods are {@code GET}, {@code HEAD}, and {@code CONNECT}. Supported
 * protocols are {@code HTTP} and {@code HTTPS_TUNNEL}. Outcomes are {@code CONNECTED},
 * {@code FAILED}, and {@code DENIED}; {@code CONNECTED} means the upstream TCP connection
 * succeeded, not that an HTTP response was successful. A {@code DENIED} outcome represents
 * an operational safety-policy decision, not an intrusion verdict.
 *
 * @param at UTC event timestamp
 * @param sourceIp IP address observed from the client socket connected to the proxy
 * @param method supported HTTP proxy method: {@code GET}, {@code HEAD}, or {@code CONNECT}
 * @param destination destination hostname only, without URL path or query
 * @param port destination TCP port
 * @param protocol {@code HTTP} or {@code HTTPS_TUNNEL}
 * @param outcome {@code CONNECTED}, {@code FAILED}, or {@code DENIED}
 * @param reason operational reason for the outcome
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
