# Smart Network Proxy & Traffic Logging — Phase 1

**Small Java demonstration for third-year Computer Engineering interns.** A future security-product foundation, not an operational security gateway.

## What it does now
- Explicit **forward** proxy for configured clients; forwards HTTP GET/HEAD and supports HTTPS `CONNECT` blind tunnels.
- Logs date/time, source client IP, requested host, port, protocol, and upstream connection outcome.
- Writes append-only JSONL and shows current-session recent events in a localhost dashboard.
- Supports domain/client filters on the current-session dashboard and a built-in local example website.

## What it does NOT do
- No intrusion detection, signature comparison, machine learning, automatic threat blocking, notification system or network-wide packet capture.
- No monitoring of applications that bypass the proxy; no packet sniffing or Wi-Fi traffic capture.
- No reading decrypted HTTPS content or user browsing pages. Not a commercial proxy.

## Start

Windows (PowerShell):
```powershell
java -version
javac -version
powershell -ExecutionPolicy Bypass -File scripts\test.ps1
powershell -ExecutionPolicy Bypass -File scripts\run.ps1
```
Linux/macOS:
```bash
bash scripts/test.sh
bash scripts/run.sh
```

Open local dashboard: <http://127.0.0.1:8181>. Proxy address: `127.0.0.1:8888`. Safe demonstration destination: `127.0.0.1:9090`.

In another terminal (Windows):
```powershell
curl.exe --noproxy "" --proxy http://127.0.0.1:8888 http://127.0.0.1:9090/hello
Get-Content data\proxy-events.jsonl -Tail 5
```

In Bash:
```bash
curl --noproxy "" --proxy http://127.0.0.1:8888 http://127.0.0.1:9090/hello
tail -5 data/proxy-events.jsonl
```

To run in a pre-approved isolated LAN lab, edit `config/allowed-lan-clients.txt` and pass `--bind <private IPv4 address of proxy PC>` to `scripts/run.*`, e.g. on PowerShell:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\run.ps1 --bind 192.168.1.20
```
The IP must belong to a local interface. Allow only explicitly approved lab clients; clients manually configure their browser proxy to `192.168.1.20:8888`. Dashboard remains localhost-only. Configure firewall restrictions to ensure no unauthorised access. **Do not expose to a public network.**

## Limitations of starter
- HTTP GET/HEAD only; HTTPS CONNECT tunnels, not decrypts.
- Restricts public destinations to HTTP port 80 and HTTPS CONNECT port 443; a loopback demo site is a special local exception.
- Connection records report **upstream TCP connection success**, not web-page response success.
- The admin UI only lists the latest 500 events in this execution, though disk JSONL survives restart.
- Log write failure prints an error; robust failure policy, rotation and replay are Phase 1 development tasks.
- No authentication on dashboard; safe only because it is loopback-bound.

## Explore
`docs/ARCHITECTURE.md` • `docs/INTERFACE_CONTRACT.md` • `docs/DEVELOPER_OWNERSHIP.md` • `docs/roles/` • `docs/AI_PROMPTS.md` • `docs/ACCEPTANCE.md`
