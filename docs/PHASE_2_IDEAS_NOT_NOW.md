# Future Phase 2 backlog — DO NOT IMPLEMENT IN PHASE 1

1. Import **confirmed** historical intrusion indicators with provenance/expiration.
2. Rule-based matches against new proxy events: suspect destinations, rates, unusual patterns (with false-positive handling).
3. Admin alerts and review status; distinguish *suspicion* from *confirmed incidents*.
4. Approved per-domain block policies with explainable rule IDs and audit trail.
5. Additional network telemetry collectors (DNS/gateway logs, flow records) with explicit authorization and meaningful scope boundaries.
6. Optional storage and authentication upgrades, retention policy, secure production deployment planning.

**Important:** A proxy cannot automatically observe packets that bypass it. A full network IDS is a separate architecture that must be designed and tested when Phase 2 is approved.
