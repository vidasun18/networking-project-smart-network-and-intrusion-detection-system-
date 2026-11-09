# Project scope — Phase 1

## Vision
A potential future company network security gateway. The **present prototype is intentionally much smaller**: an explicit Java forward proxy with privacy-limited logs and an administrator activity viewer.

## Problem statement
A network administrator needs basic visibility of which configured lab computers use an HTTP(S) proxy to connect to which destination domains. The system must record meaningful metadata for later analysis without attempting to read private communications.

## In scope
- Forward HTTP GET/HEAD and HTTPS CONNECT from explicitly configured test clients.
- Observe connected client source IP and requested destination hostname, destination port and protocol type.
- Record timestamp, attempt/outcome, and operational failure reason.
- Persist JSONL metadata and retrieve/filter recent events.
- Show localhost-only activity dashboard and counters.
- Support multiple test clients in an approved isolated lab, with explicit IP allowlist.
- Build repeatable automated tests and demonstrate live local and approved LAN use.

## Explicitly OUT of scope
- IDS/IPS, threat scores, historical attack signatures, real-time intrusion alerts or blocking rules.
- All-network monitoring, packet capture, DNS logs, Wi-Fi or ARP sniffing, unsupported application protocols.
- HTTPS payload inspection, TLS interception, stored page content, full URL paths or query strings.
- Production deployment, identity-based authentication, multi-site scaling, silent installation on devices.

## Success definition
Two approved client devices route configured web activity through a Java proxy, a test destination is reachable, and the admin can see accurate timestamped connection metadata for each client. The team explains observation boundaries and privacy limits.

## Demo assumption
One development laptop may host proxy, localhost dashboard and mock website. LAN demonstration with two computers is desirable but requires authorized networking and explicit configuration; no assumption of company network access is made.
