# Developer 1 — Java Proxy and Networking

**Own:** `src/main/java/com/demo/netguard/proxy/ForwardProxyServer.java` and related tests.
**Branch:** `feature/dev1-forward-proxy`. **Reviewer:** Developer 2.

## Mission
Make the explicit forward proxy reliable enough for a controlled two-client LAN demonstration without exposing an open relay.

## Tasks in order
1. Understand `CONNECT` versus ordinary HTTP proxy requests. Draw the actual request path and explain encrypted payload visibility.
2. Audit request parsing, HTTP GET/HEAD forwarding, absolute URI validation, `CONNECT host:port` and `Host` consistency.
3. Improve client disconnection, upstream timeouts, error status codes and clean shutdown. Avoid double HTTP responses after bytes have already been sent.
4. Add bounded concurrency behavior, connection accounting, and tests for at least 2 simulated simultaneous clients.
5. Validate the explicit LAN bind + approved client IP allowlist, loopback-only mock exception and prevention of internal/private/metadata proxying.
6. Emit exactly one final outcome `FlowEvent` per accepted connection attempt where feasible; collaborate with Dev 2 before changing the schema.
7. Document known limitations (HTTP/1.1 partial support; no request bodies, keepalive, response-status parsing, MITM or production hardening).

## Acceptance
- Local GET to bundled mock succeeds; HTTPS CONNECT transport test passes without decryption.
- No untrusted access when the allowlist is empty in LAN mode.
- Concurrent smoke tests pass; errors do not hang clients or corrupt logs.
- No full URLs, cookies, request bodies or payload data are sent into EventLog.
- New tests and short network diagram included in PR.

**Do not implement IDS, destination reputation checks or admin UI.** Safety checks for private targets are allowed, explicitly NOT classified as intrusion detection.
