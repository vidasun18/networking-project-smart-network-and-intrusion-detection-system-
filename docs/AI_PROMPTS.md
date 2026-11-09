# Copy-paste AI agent prompts

Open this project folder in your coding agent. Each person uses ONE role prompt and works in their feature branch.

## Manager kickoff prompt
"You are the Phase 1 Java proxy project coordinator. Read AGENTS.md, START_HERE.md, docs/PROJECT_SCOPE.md, docs/INTERFACE_CONTRACT.md and docs/DEVELOPER_OWNERSHIP.md. Inspect the current repository without changing it. Summarize what already works versus what remains. Identify integration risks and list an actionable 7-day plan. Do not implement IDS or network-wide capture. Do not run monitoring on a real company network."

## Developer 1 — Proxy
"You are Developer 1. Read AGENTS.md, docs/roles/DEV1_PROXY_AND_NETWORKING.md, docs/INTERFACE_CONTRACT.md and docs/SAFETY_AND_PRIVACY.md. Inspect current Java source and run the baseline test script. Work ONLY on feature/dev1-forward-proxy and the proxy/ module plus role tests. Improve HTTP GET/HEAD and CONNECT reliability, concurrency, input validation, safe destinations and approved-client handling. Do not edit EventLog/FlowEvent or dashboard contracts without team agreement. Add tests, run all tests, explain limitations, and propose concise commits. Do not implement IDS, DPI or TLS interception."

## Developer 2 — Logging
"You are Developer 2. Read AGENTS.md, docs/roles/DEV2_TRAFFIC_LOGGING.md, docs/INTERFACE_CONTRACT.md, docs/SAFETY_AND_PRIVACY.md. Inspect and test the starter. Work ONLY on feature/dev2-traffic-logging and model/ + EventLog.java + role tests. Strengthen append-only JSONL persistence, thread safety, startup replay, retention, failures and client/domain filtering. Preserve agreed API/schema and never log paths, query strings, headers, credentials, or TLS contents. Add synthetic fixtures and tests; document behavior and scope. Do not implement IDS."

## Developer 3 — Monitoring
"You are Developer 3. Read AGENTS.md, docs/roles/DEV3_DASHBOARD_AND_INTEGRATION.md, docs/INTERFACE_CONTRACT.md and docs/SAFETY_AND_PRIVACY.md. Inspect project and run tests. Work ONLY on feature/dev3-admin-dashboard: local dashboard/API, scripts, CI and integration/e2e tests. Build filters and statistics against the frozen FlowEvent contract; start with synthetic events and integrate after other branches merge. Keep dashboard localhost-only. Prepare demo and troubleshooting instructions. Do not add IDS alerts or inspect HTTPS payloads."

## Reviewer prompt
"Read AGENTS.md and docs/ACCEPTANCE.md. Review this branch against dev for security/scope regressions, interface compatibility, privacy leaks, bad file ownership and missing tests. Run relevant scripts. Report findings with file paths and actionable fixes. Do not claim tests passed unless executed."
