# AGENTS.md — Project-wide AI coding-agent rules

Project: **Smart Network Proxy & Traffic Logging — Phase 1**. Language: **Java 17+**. Three developers. Intended audience: third-year Computer Engineering interns. A small controlled demonstration for a future company product.

## Scope: STRICT
- Build an **explicit outbound forward proxy**, not a reverse proxy.
- Log only configured clients' proxied web connections; expose connection data in a local dashboard.
- **No IDS, DPI, anomaly scoring, ML, historical intrusion log matching, automatic threat blocks, alerts, packet sniffer, DNS sensor, or transparent interception in Phase 1.** Those are future backlog items.
- Rejecting unsafe destinations or unsupported proxy operations is a safety guard, NOT intrusion detection. Do not advertise it as such.

## Read in this order
1. `docs/PROJECT_SCOPE.md` and `docs/ARCHITECTURE.md`
2. `docs/INTERFACE_CONTRACT.md` and `docs/SAFETY_AND_PRIVACY.md`
3. Your role document under `docs/roles/`
4. `docs/TEST_PLAN.md` and `docs/ACCEPTANCE.md`

## Shared contracts (do not break without team agreement)
- `com.demo.netguard.model.FlowEvent` is a Java record containing only metadata. Never add paths, query strings, headers, cookies, bodies, URL credentials or TLS content.
- `com.demo.netguard.monitor.EventLog.append(FlowEvent)` is the ingest interface. It must remain thread-safe, resilient and unambiguously report write failures after Dev 2 improvements.
- `ForwardProxyServer` owns outbound connection/relay; `EventLog` owns storage and retrieval; `DashboardServer` owns admin API/UI. Dashboard is read-only and loopback-only.
- JSONL contract: `at`, `sourceIp`, `method`, `destination`, `port`, `protocol`, `outcome`, `reason`. Supported outcomes: `CONNECTED`, `FAILED`, `DENIED`; `CONNECTED` means upstream socket opened, not HTTP success.
- `/api/events?client=&domain=&limit=`, `/api/summary`, `/health` stay compatible. Do not expose an unprotected admin interface on the LAN.

## Mandatory safety rules
- Default is loopback binding. LAN mode: explicitly selected private IPv4 bind address + nonempty authorized client allowlist + network owner approval.
- Proxy must not be an open relay or allow access to internal/metadata endpoints (the exact loopback mock destination is a test exception).
- Never disable TLS certificate validation, forge CA certificates, decrypt HTTPS, capture passwords, or covertly intercept network traffic.
- Log only pre-approved lab traffic. Respect retention/privacy; keep actual JSONL logs outside git.
- Use controlled synthetic demo site and test inputs. Never claim to observe all devices or all traffic on a LAN.

## Development workflow
- Java 17; prefer standard JDK and simple understandable code; no unnecessary frameworks.
- Run the existing test scripts before and after changes. Add tests covering new feature, failure modes and concurrency where applicable.
- Never edit another developer's owned files without asking; cross-cutting changes need contract review.
- Do not delete or weaken tests to obtain green CI.
- Work on a feature branch from `dev`; send a reviewable PR targeting `dev`. Never push directly to `main`.
- Each PR must include what changed, precise testing evidence, privacy/safety implications, and remaining limits. No fabricated pass reports.

## Developer roles
- **Dev 1:** Networking/HTTP proxy (`proxy/`); forward requests, HTTPS CONNECT reliability and client handling.
- **Dev 2:** Structured traffic logging (`model/`, `monitor/EventLog.java`); persistence, filtering, rotation and thread safety.
- **Dev 3:** Local admin viewer, integration tests and demonstration (`monitor/DashboardServer.java`, `.github/`, `scripts/`, docs).
- `NetGuardApp.java` and `docs/INTERFACE_CONTRACT.md` may change only after all roles agree.

## Definition of Done
Compiles on Java 17; new tests and existing tests pass; reproducible local demo; documentation current; no private data in logs; code reviewed and integrated to `dev`.
