# Message to the three developers

Team, our Smart Network Proxy project will start with **Phase 1 only**: Java HTTP/HTTPS forward proxy plus metadata traffic logging and a localhost dashboard. Intrusion detection, historical attack comparison and blocking are reserved for Phase 2.

**Dev 1:** `feature/dev1-forward-proxy` — reliable networking, HTTP/CONNECT, concurrency, safety and tests.

**Dev 2:** `feature/dev2-traffic-logging` — JSONL storage, history replay, client/domain filters, retention and tests.

**Dev 3:** `feature/dev3-admin-dashboard` — local activity dashboard, integration tests, scripts and demo documentation.

Please first read `START_HERE.md`, `AGENTS.md`, `docs/INTERFACE_CONTRACT.md`, and your own `docs/roles/DEV*.md`. Run the starter tests. Share your branch, one working change and tests at daily check-in. Any schema change must be agreed by all three before merging.

Do not capture real employees' browsing or deploy on a company network without authorization. The final demonstration should use a local mock site and approved test clients.
