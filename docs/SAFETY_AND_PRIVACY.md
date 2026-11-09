# Safety, privacy and realistic visibility

This is an **authorized educational explicit proxy**. Do not monitor employee activity or real traffic without organizational permission, a documented purpose and appropriate disclosure.

- Only configured traffic appears; an explicit forward proxy **cannot** see all packets, DNS requests, devices or protocols across the LAN.
- For HTTPS `CONNECT`, log only the requested host/port, client source IP, timestamp, transport result. Do NOT decrypt TLS or collect pages, search terms or credentials.
- HTTP URLs may contain path and query secrets; never store them. Bodies, cookies, headers, access tokens and passwords must never be logged.
- Client IP can be affected by NAT; it is not a verified person or computer identity.
- Bind proxy to loopback by default. For isolated lab LAN testing, choose an explicit IP on a private interface; populate exact approved client IPs; apply firewall rules; never bind to all interfaces or public IPs.
- Keep localhost demo website as the **only** local destination exception; public web destinations restricted to ports 80 (HTTP) and 443 (CONNECT) in the baseline. No route to cloud-metadata or internal-control IPs.
- Dashboard binds to `127.0.0.1` with no authentication. Do not publish to the LAN. Authentication and access reviews are required before any future remote monitoring.
- Logs contain personal network activity metadata. Minimize, retain briefly for lab purposes, secure storage, don't commit, don't share externally, delete after demo under the agreed data policy.
- The source is a teaching starter with incomplete HTTP feature coverage and no formal security audit. Do not use as a corporate gateway or general open proxy.

**Incident detection is not Phase 1.** Operational `DENIED` and `FAILED` outcomes are NOT evidence of attack.

References: RFC 9110, HTTP Semantics/CONNECT: https://www.rfc-editor.org/rfc/rfc9110.html; RFC 9112, HTTP/1.1 request targets: https://www.rfc-editor.org/rfc/rfc9112.html.
