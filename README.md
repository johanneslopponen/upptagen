# timetrack

A fast, feature-rich CLI and Web time tracking application written in Rust, using SQLite for local datastore storage.

## Features

- **Live Timer**: Start and stop real-time task timers from the CLI or Web UI.
- **Retroactive Manual Entry**: Insert time entries retroactively if you forgot to turn on the timer.
- **Tagging Support**: Tag entries and view aggregated statistics by tag and project.
- **Multiple Export Formats**: Export time logs to **CSV** or **PDF** (rendered cleanly via LaTeX `pdflatex`).
- **Web UI Dashboard**: Built-in REST API and modern single-page web server dashboard (`axum`).
- **SQLite Storage**: Reliable local storage with filtering by project, tag, and date range.

## Installation & Build

```bash
cargo build --release
```

## CLI Usage

### Start a Live Timer
```bash
timetrack start "Implementing new feature" --project "CoreApp" --tags "rust,backend"
```

### Check Active Timer Status
```bash
timetrack status
```

### Stop Active Timer
```bash
timetrack stop "Finished implementing feature" --tags "done"
```

### Add Manual Entry Retroactively
```bash
timetrack add --start "2025-01-01 09:00:00" --end "2025-01-01 11:30:00" --project "Docs" --description "Writing documentation" --tags "docs"
```

### List Entries
```bash
timetrack list
timetrack list --project "CoreApp" --tag "rust"
```

### View Tag Statistics
```bash
timetrack tags
```

### Delete Entry
```bash
timetrack delete <ENTRY_ID>
```

### Export Logs (CSV or PDF)
```bash
timetrack export --format csv --output report.csv
timetrack export --format pdf --output report.pdf
```

### Launch Web Server Dashboard
```bash
timetrack serve --port 3000
```
Then navigate to `http://localhost:3000` in your web browser.

## Testing

Run all unit and integration test suites:
```bash
cargo test
```
