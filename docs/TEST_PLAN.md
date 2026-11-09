# Test plan

## Baseline (works without external Internet)
`bash scripts/test.sh` or `powershell -ExecutionPolicy Bypass -File scripts/test.ps1` compiles Java 17 and runs loopback smoke tests.

Covered baseline: HTTP forwarding, mock GET content, CONNECT tunnel byte transfer, refusal of internal/metadata target, method validation, event JSONL, no path/query leak, dashboard API, filters, basic concurrency and LAN startup guard.

## Add tests by owner
**Dev 1:** multiple connections; slow upstream timeouts; malformed headers; invalid `CONNECT`; hard stop; no broken concurrent records; LAN allowlist in isolated test environment.

**Dev 2:** 20+ simultaneous JSONL writes; malformed/corrupt file restart behavior; history replay; log rotation; disk failure detection; case-insensitive domain search; privacy redaction.

**Dev 3:** dashboard empty/invalid log state; API and browser filtering; safe output encoding; run scripts on Windows/Linux; full end-to-end scenario with two synthetic clients.

## Manual demo checklist
1. Start on localhost; run mock site through proxy using curl `--noproxy ""`.
2. Check JSONL includes observed client IP, destination, timestamp, protocol, outcome.
3. Repeat with a second authorized client or simulated client; verify clear separation if IPs differ.
4. Filter viewer by client, domain and confirm summary counts.
5. Confirm request path/query and header tokens never appear in event file.
6. Stop and restart; inspect disk history and demonstrate Dev 2's reload implementation.
7. Explain why a browser configured to bypass the proxy will not appear in this log.
8. State honestly that this is *network visibility foundation*, not IDS.

## CI gate
Every push/PR must compile and run `scripts/test.sh`. The baseline tests are a starting point; each developer adds tests for assigned features. Do not reduce test coverage to pass CI.
