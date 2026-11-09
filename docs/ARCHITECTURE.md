# Architecture

```text
   Client laptop A (manual HTTP(S) proxy configuration) ----\
                                                         -> Java explicit FORWARD proxy ----> allowed website
   Client laptop B (manual HTTP(S) proxy configuration) ----/              |
                                                                        | FlowEvent (metadata)
                                                                        v
                                                                 Thread-safe EventLog
                                                                        |
                                                  append-only data/proxy-events.jsonl
                                                                        |
                                                          localhost Admin Dashboard
                                                                /api/events, /api/summary
```

**Request processing:** client -> parse HTTP absolute URL or CONNECT authority -> validate client authorization and destination safety -> establish upstream socket -> relay traffic -> write metadata outcome -> dashboard can query recent events.

**HTTP traffic:** The starter accepts GET/HEAD absolute-form HTTP URLs and relays response bytes. **HTTPS:** `CONNECT host:443` creates a blind TCP tunnel. The tunnel typically carries TLS, but the proxy does not decrypt it.

**What the log means:** `CONNECTED` means the proxy established a TCP socket to destination, not that a page loaded, an HTTP response succeeded or a security event occurred.

**Java modules:**
- `ForwardProxyServer`: connection accepting, safe target parsing, forwarding and CONNECT tunnel, client allowlist.
- `FlowEvent`: agreed metadata record.
- `EventLog`: concurrent JSONL writing and recent-session retrieval.
- `DashboardServer`: local read-only monitoring.
- `LocalDemoSite`: owned mock web server for local testing.
- `NetGuardApp`: application composition, CLI and lifecycle.

**Scope caution:** Browser proxy exceptions and other applications can bypass this explicit proxy. It is not a network tap or a packet IDS.
