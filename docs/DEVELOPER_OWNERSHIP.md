# Developer ownership / review matrix

| Area | Owner | Reviewer | Files |
|---|---|---|---|
| Proxy/Networking | Dev 1 | Dev 2 | `src/main/java/com/demo/netguard/proxy/` |
| Metadata schema & logging | Dev 2 | Dev 3 | `src/main/java/com/demo/netguard/model/`, `monitor/EventLog.java` |
| Dashboard/CI/Integration | Dev 3 | Dev 1 | `monitor/DashboardServer.java`, `src/test/`, `.github/`, `scripts/` |
| Application composition | Dev 3 leads | Dev 1 + Dev 2 approve | `NetGuardApp.java` |
| API & data contract | All 3 | All 3 | `docs/INTERFACE_CONTRACT.md` |

Recommended feature branches:
- `feature/dev1-forward-proxy`
- `feature/dev2-traffic-logging`
- `feature/dev3-admin-dashboard`

There is no developer 4 in this project. A project manager may review all streams and require test evidence. Every developer writes their own role tests; Dev 3 coordinates integration and shared smoke tests, not doing everyone else's features.
