# Acceptance and definition of done

## Product acceptance (all REQUIRED for Phase 1)
- [ ] Proxy receives requests from 2 explicit configured clients (real authorized lab clients OR separately simulated sockets).
- [ ] HTTP GET test reaches local mock website through proxy.
- [ ] HTTPS CONNECT test establishes a blind TCP tunnel; no TLS content extraction.
- [ ] Every accepted connection attempt yields correct client/destination/protocol/outcome metadata where observable.
- [ ] JSONL stays valid with concurrent events; on-disk log survives restart; history can be inspected later.
- [ ] Search/filter by client and domain works and event list is time ordered.
- [ ] Local admin dashboard summarizes connected, failed and denied (safety-policy) activity.
- [ ] Proxy refuses to start as an unallowlisted LAN relay; disallows private/metadata destination abuse.
- [ ] No full URL path/query, request body, cookies, auth, passwords or HTTPS data appear in logs.
- [ ] Fresh checkout compiles on Java 17 and automated tests pass; test command documented.
- [ ] Two-client demonstration instructions include informed permission and known observation limits.
- [ ] Every developer contributes tested feature-branch changes, receives review and records evidence.

## Individual ownership completion
- Dev 1: stable concurrent HTTP + CONNECT proxy, allowlist/policy tests, error handling.
- Dev 2: durable reload + JSONL rotation strategy + concurrency/failure tests + filters.
- Dev 3: local dashboard + integration/CI + live demo and documentation.

## NOT a Phase 1 success criterion
Intrusion-detection accuracy, malware blocking, alert response times, packet sniffing, all-network traffic completeness, or TLS content visibility. These cannot be claimed without future capabilities.
