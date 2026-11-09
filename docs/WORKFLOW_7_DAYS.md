# Seven-day sample workflow (not an externally imposed deadline)

| Day | Developer 1 — Proxy | Developer 2 — Logger | Developer 3 — Viewer/CI | Shared gate |
|---|---|---|---|---|
| 1 | Run baseline, confirm request handling | Freeze FlowEvent JSONL schema | Build UI against mock data | Agree contract in writing |
| 2 | Improve GET/HEAD and error handling | Atomic JSONL and validation | Dashboard UI / filters | Unit tests in branches |
| 3 | CONNECT and client-allowlist tests | History replay and filters | CI and APIs | Integration checkpoint |
| 4 | Concurrency / stop handling | Retention and failure tests | End-to-end smoke tests | Review interfaces |
| 5 | Submit reviewed proxy PR | Submit reviewed logger PR | Combine branches into dev | Baseline tests pass |
| 6 | 2-client LAN rehearsal (approved lab) | Verify real metadata fields | Finish docs and screenshots | Privacy and safe binding audit |
| 7 | Demo network explanation | Explain stored log entries | Present live dashboard | Demo and code freeze |

**Daily checkpoint:** each person reports branch, commits, passing/failing tests, blocker and one working change. No fabricated progress screenshots.

**Minimum gate for live two-client demonstration:** supervisor/network-owner approval, isolated lab subnet, explicit per-client proxy settings, client allowlist, specific private bind IP, host firewall restrictions and never an open Internet proxy. If unavailable, demonstrate two simulated clients locally and document why.
