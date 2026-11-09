# Git workflow

## Branches
```text
main  (stable demonstration)
└── dev  (integration)
    ├── feature/dev1-forward-proxy
    ├── feature/dev2-traffic-logging
    └── feature/dev3-admin-dashboard
```

Project owner creates `dev` when the repository exists. Each developer then:
```bash
git clone <YOUR-REPOSITORY-URL>
cd <YOUR-REPOSITORY-FOLDER>
git fetch origin
git switch dev
git pull --ff-only origin dev
git switch -c feature/dev1-forward-proxy  # replace with your assigned branch
```
Commit small independently verified features. Examples:
- `feat(proxy): make CONNECT tunnel close cleanly`
- `feat(logging): reload JSONL events on startup`
- `feat(dashboard): filter events by client and domain`
- `test(proxy): cover concurrent test clients`
- `docs: explain local and authorized LAN setup`

Before sharing:
```bash
bash scripts/test.sh  # or Windows PowerShell equivalent
git status
git add <specific files>
git commit -m "test(proxy): add concurrent request coverage"
git push -u origin <YOUR-FEATURE-BRANCH>
```
Submit PR **to `dev`**, obtain cross-developer review, pass CI, then merge. Project manager releases verified `dev` to `main` for demonstration. Never commit `data/*.jsonl`, actual company logs, credentials, screenshots with private browsing history, or unapproved machine/client addresses.
