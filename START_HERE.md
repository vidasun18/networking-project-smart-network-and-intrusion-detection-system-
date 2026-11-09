# START HERE — Phase 1 Java proxy + traffic logging

**Goal:** Three developers deliver a SMALL demonstrable explicit forward proxy and an activity log viewer. Java 17+, standard JDK, no paid services. **No intrusion detection, historical threat matching, alerting or auto-blocking in this phase.**

1. Unzip this package to a project folder or a NEW repository. Do not overwrite an existing repository without checking its files.
2. Read `AGENTS.md`, `docs/PROJECT_SCOPE.md`, `docs/INTERFACE_CONTRACT.md` and your assigned role file.
3. Install JDK 17+, verify `java -version` and `javac -version`.
4. Windows: `powershell -ExecutionPolicy Bypass -File scripts/test.ps1`; Linux/macOS: `bash scripts/test.sh`.
5. Start: `powershell -ExecutionPolicy Bypass -File scripts/run.ps1` or `bash scripts/run.sh`.
6. Browse the local-only admin viewer: `http://127.0.0.1:8181`.
7. In another terminal, run `curl.exe --noproxy "" --proxy http://127.0.0.1:8888 http://127.0.0.1:9090/hello` (Windows) or `curl --noproxy "" --proxy http://127.0.0.1:8888 http://127.0.0.1:9090/hello` (Linux).
8. Check `data/proxy-events.jsonl`; explain why it contains destinations but not page paths, query strings or passwords.
9. Create individual branches from `dev` and follow `docs/WORKFLOW_7_DAYS.md`.
10. **Before any LAN test**, read `docs/SAFETY_AND_PRIVACY.md`; configure an allowed client IP list and a specific private interface with approval.

The starter is a baseline, not an internship completion. Every developer must add tests and deliver improvements specified in their role document.

Notes: Browser proxy-bypass settings can skip local addresses; the curl command above forces the proxy for the safe demonstration. The visible dashboard shows only the latest 500 events since the current process started; the JSONL file is retained across restarts. Loading persisted history is an assigned task, not an existing feature.
