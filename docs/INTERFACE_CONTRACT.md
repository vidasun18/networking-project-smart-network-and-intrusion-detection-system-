# Frozen integration interfaces — read before coding

This document is **the shared agreement**; role agents must not independently invent incompatible data models. Propose any schema changes in a single team-reviewed PR.

## Java data contract (owner: Developer 2; inputs supplied by Developer 1)

```java
package com.demo.netguard.model;
public record FlowEvent(
    java.time.Instant at,
    String sourceIp,        // IP address of the client CONNECTED TO PROXY, never inferred real identity
    String method,          // GET | HEAD | CONNECT
    String destination,     // hostname, not full URL/path/query
    int port,               // destination TCP port
    String protocol,        // HTTP | HTTPS_TUNNEL
    String outcome,         // CONNECTED | FAILED | DENIED
    String reason           // UPSTREAM_TCP_CONNECTED | UPSTREAM_CONNECTION_FAILURE | DESTINATION_POLICY | ...
) {}
```

### Interpretation
- One entry for each accepted, fully parsed connection attempt and outcome. Invalid request lines may have no event; Dev 2+1 should agree on malformed-request counters separately.
- `CONNECTED`: upstream TCP connection accepted; do not call this `HTTP_SUCCESS`.
- `FAILED`: upstream connect failed or was unavailable; not a threat or alert.
- `DENIED`: prevented by demo safety guard (such as an internal-only destination), not an intrusion detection verdict.
- IP is observed from client network socket; a NAT gateway can make multiple end-user devices share one IP. Do not invent device IDs or usernames.
- Never log paths, query strings, request/response bodies, authentication, cookies, tokens or raw TLS payload.

## EventStore operations (owner: Developer 2)
- `EventLog(Path file)` creates a log writer.
- `synchronized void append(FlowEvent event)` writes one JSONL line and updates recent bounded in-memory history (existing prototype behavior). Dev 2 will propose explicit failure reporting and replay without breaking callsites.
- `recentJson(int limit, String client, String destination)` returns JSON array with optional exact-client and partial-domain filters. `limit` clamped to 0..500.
- `summaryJson()` returns `{"recentTotal":n,"connected":n,"failed":n,"denied":n}` for bounded in-memory history. Document that it is not a lifetime counter.

## JSONL on disk (owner: Developer 2)
Example with synthetic addresses:
```json
{"at":"2026-11-09T12:00:00Z","sourceIp":"192.168.1.101","method":"CONNECT","destination":"example.com","port":443,"protocol":"HTTPS_TUNNEL","outcome":"CONNECTED","reason":"UPSTREAM_TCP_CONNECTED"}
```
Each log event is one newline-delimited JSON object. File location `data/proxy-events.jsonl` (ignored by Git). Implement replay, retention and rotation carefully and document behavior; no requirement to bring in a database for the initial demo.

## API contract (owner: Developer 3)
- `GET /health` -> plaintext `ok`
- `GET /api/events?client=<IP>&domain=<hostname-fragment>&limit=<1..500>` -> JSON array of FlowEvent objects
- `GET /api/summary` -> JSON object with keys above
- `GET /` -> local dashboard
All listen on `127.0.0.1:8181` only. If dashboard needs LAN access in future, authentication and security design review are mandatory (out of Phase 1).

## Proxy contract (owner: Developer 1)
Constructor: `ForwardProxyServer(int port, String bindIp, Set<String> clientAllowlist, EventLog eventLog, int demoPort)`.
- `start()`, `port()`, `close()`.
- Local default: `127.0.0.1:8888`. LAN bind requires explicit private IPv4 plus allowlist (see safety doc).
- Upstream port policy: public HTTP :80 and CONNECT :443; local mock site is the only loopback exception for loopback-connected tests.
- No automatic request handling for POST/PUT or other methods in baseline; describe extensions before implementing them.

## Merge and change control
If an interface must change: open a short contract change note; get consent from all three developers; update model + tests + docs in one coordinated integration branch. No agent may silently rewrite the entire repository.
