# Developer 2 — Java Logging, Storage and Filtering

**Own:** `src/main/java/com/demo/netguard/model/FlowEvent.java`, `src/main/java/com/demo/netguard/monitor/EventLog.java` plus logging tests.
**Branch:** `feature/dev2-traffic-logging`. **Reviewer:** Developer 3.

## Mission
Turn the starter JSONL logger into a dependable, privacy-aware activity record that the monitoring UI can query.

## Tasks in order
1. Freeze and document `FlowEvent` field names, values and semantics with Developers 1/3.
2. Ensure concurrent writes remain atomic at the event/line level and each event is valid JSON.
3. Improve error reporting: log write failure must be observable and described, not silently treated as success.
4. Add persisted-history loading (with bounded reading), so users can view prior run events, not just current-session memory.
5. Add safe retention/rotation for an example cap (e.g., size-based), avoiding loss of active writes and accidental ingestion of corrupt lines.
6. Implement/test efficient recent lookup and `client` / `domain` / `limit` filters through the agreed EventLog methods.
7. Confirm the log never contains paths, query strings, cookie headers, credentials or TLS contents. Clarify that client IP is not a verified device identity.
8. Supply 10 representative synthetic JSONL examples (at least 2 clients, HTTP and HTTPS, success/failure/denial) for Dev 3's initial dashboard.

## Acceptance
- 20 concurrent events produce 20 parseable lines; reopening the logger retains historical records.
- Search by client and domain returns only matching records; timestamps are UTC ISO-8601.
- Long-running demo does not grow the active log file without a documented retention plan.
- Tests show failure behavior when disk is unavailable/read-only.
- No IDS patterns, threat scores or attack conclusions.

**Coordinate** changes to `FlowEvent` and `summaryJson()` with both other developers before merging.
