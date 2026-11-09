# Developer 3 — Monitoring Dashboard, Integration and Demo

**Own:** `src/main/java/com/demo/netguard/monitor/DashboardServer.java`, shared tests, CI, scripts, documentation and coordinated `NetGuardApp.java` changes.
**Branch:** `feature/dev3-admin-dashboard`. **Reviewer:** Developer 1.

## Mission
Make logged proxy activity easy to see, filter and explain. Assemble a repeatable live demonstration that uses two authorized test clients.

## Tasks in order
1. Read API contract and build a local read-only dashboard that can work with the sample JSONL schema (mock events first).
2. Display connection count by outcome, client source IP, destination, protocol and timestamp.
3. Provide working filter controls for client IP, destination domain and recent time window where practical (coordinate time-window API updates).
4. Handle missing/empty/corrupt log gracefully and show a clear "not yet connected" or storage failure status.
5. Do NOT make the dashboard network-exposed without an authentication/security review. This phase keeps it on localhost.
6. Improve and maintain cross-module smoke/integration tests plus GitHub Actions CI on Java 17.
7. Build safe one-command startup/teardown scripts for Windows PowerShell and Linux Bash.
8. Prepare 5-minute intern demonstration, architecture drawing, README setup, troubleshooting and evidence screenshots (only synthetic data).

## Acceptance
- Dashboard displays and filters Dev 2's entries after Dev 1 connections.
- Two configured test clients are distinguishable by observed IP where the network presents unique IPs.
- The test suite runs on fresh extraction without remote services.
- No claim of monitoring encrypted page contents or unproxied network traffic.
- All three developers have reviewed the final demo and integration branch.

**Do not build IDS alerts, a threat dashboard, or extra security heuristics in Phase 1.** A failure/outcome count is operational monitoring, not intrusion detection.
