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
