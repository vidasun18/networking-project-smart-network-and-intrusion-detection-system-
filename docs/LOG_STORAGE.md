# JSONL log storage and retention

`EventLog` writes metadata-only records to the configured JSONL file (the default is
`data/proxy-events.jsonl`). Appends are serialized between proxy workers. Each successful
append adds one JSON object and a newline; an I/O error is reported to the caller as an
`UncheckedIOException`, and the event is not added to in-memory history.

## Size limit and rotation

- The active log is capped at 1 MiB.
- When the next event would exceed that cap, the active file moves to `.1`; older archives
  shift to `.2` and `.3`.
- At most three archives are kept, so the retained JSONL files use at most 4 MiB total.
- Files that are already larger than 1 MiB are reduced on startup to their newest complete
  newline-delimited records that fit within the cap. The partial first line at the cut point
  is discarded.
- The active file and rotated archives are ignored by Git (`data/*.jsonl*`). Keep real lab
  logs private and remove them when the approved retention period ends.

## Restart and damaged records

On startup, the logger reads archives from oldest to newest, then the active file. It skips
malformed or incomplete JSONL records and restores the latest 500 valid events into bounded
dashboard history. The on-disk files retain more history than the dashboard displays, until
size-based retention removes the oldest archive.

## Synthetic dashboard sample

[`examples/synthetic-proxy-events.jsonl`](examples/synthetic-proxy-events.jsonl) contains ten
synthetic entries for two documentation-only client IPs. It covers HTTP, HTTPS CONNECT, and
`CONNECTED`, `FAILED`, and `DENIED` outcomes. To preview these records in the local dashboard,
copy the fixture to the default runtime log path, then start the app:

```bash
mkdir -p data
cp docs/examples/synthetic-proxy-events.jsonl data/proxy-events.jsonl
bash scripts/run.sh
```

On PowerShell, use `Copy-Item docs\examples\synthetic-proxy-events.jsonl data\proxy-events.jsonl`
before running `scripts\run.ps1`. The runtime copy is ignored by Git. Remove it before a live
demo if you want the dashboard to show only newly proxied connections.

Run focused storage, replay, filter, and dashboard integration checks after building:

```bash
bash scripts/build.sh
java -cp build/main:build/test com.demo.netguard.monitor.EventLogTest
```

The full proxy smoke test remains `bash scripts/test.sh`.
