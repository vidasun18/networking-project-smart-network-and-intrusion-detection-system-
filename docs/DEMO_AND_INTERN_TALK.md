# Five-minute demo and intern pitch

**0:00–0:45 — Problem:** "We want to understand basic outbound web connections from devices that voluntarily use a company-managed proxy. Later the data may support security analysis, but this demo focuses on reliable collection and visibility."

**0:45–1:30 — Architecture:** two configured lab clients -> Java forward proxy -> website, with metadata-only JSONL -> local dashboard. Explain proxy is NOT a network-wide packet sensor.

**1:30–2:30 — Live HTTP:** start Java program; send GET to local mock through proxy; show response; inspect `data/proxy-events.jsonl` and dashboard.

**2:30–3:15 — HTTPS:** explain CONNECT requests by host/port, demonstrate a test CONNECT tunnel; emphasize no decryption of user content.

**3:15–4:00 — Ownership:** Dev 1 networking reliability, Dev 2 privacy-safe storage/history, Dev 3 monitoring/integration/tests. Ask each student for a one-minute module explanation.

**4:00–5:00 — Future:** after a verified logging foundation, Phase 2 could add approved security indicator ingestion, rule matching and alerts. Those functions do not exist in this Phase 1 deliverable.

## Team lead selection rubric
- Understands protocol and observation limits, not just AI-generated code.
- Can explain one event from client request to file and dashboard.
- Writes tests and can reproduce a bug.
- Keeps changes within role ownership while coordinating data contracts.
- Respects authorization, safe deployment and privacy.

**Ask candidates:** "Can the proxy detect what a user types into an HTTPS website? Why not?"; "Why might two computers share one source IP?"; "What happens to logs when disk writes fail?"; "How do we prevent a public open proxy?".
