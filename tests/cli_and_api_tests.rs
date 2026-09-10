use std::process::Command;
use tempfile::NamedTempFile;

fn run_cli(db_path: &str, args: &[&str]) -> (bool, String, String) {
    let mut command = Command::new(env!("CARGO_BIN_EXE_timetrack"));
    command.arg("--db-path").arg(db_path);
    command.args(args);

    let output = command.output().expect("Failed to execute CLI binary");
    let stdout = String::from_utf8_lossy(&output.stdout).to_string();
    let stderr = String::from_utf8_lossy(&output.stderr).to_string();

    (output.status.success(), stdout, stderr)
}

#[test]
fn test_cli_full_workflow() {
    let db_file = NamedTempFile::new().unwrap();
    let db_path = db_file.path().to_str().unwrap().to_string();

    // 1. Status with no timer
    let (ok, stdout, stderr) = run_cli(&db_path, &["status"]);
    assert!(ok, "Status failed: {}", stderr);
    assert!(stdout.contains("No timer currently running"));

    // 2. Start timer
    let (ok, stdout, stderr) = run_cli(
        &db_path,
        &[
            "start",
            "Initial setup",
            "--project",
            "ProjectX",
            "--tags",
            "dev,rust",
        ],
    );
    assert!(ok, "Start failed: {}", stderr);
    assert!(stdout.contains("Timer started!"));
    assert!(stdout.contains("ProjectX"));

    // 3. Status with active timer
    let (ok, stdout, stderr) = run_cli(&db_path, &["status"]);
    assert!(ok, "Status active failed: {}", stderr);
    assert!(stdout.contains("Active Timer Running"));
    assert!(stdout.contains("ProjectX"));

    // 4. Stop timer
    let (ok, stdout, stderr) = run_cli(&db_path, &["stop", "Finished setup", "--tags", "v1"]);
    assert!(ok, "Stop failed: {}", stderr);
    assert!(stdout.contains("Timer stopped and logged!"));

    // 5. Add manual retroactive entry
    let (ok, stdout, stderr) = run_cli(
        &db_path,
        &[
            "add",
            "--start",
            "2025-01-01 09:00:00",
            "--end",
            "2025-01-01 11:30:00",
            "--project",
            "ProjectY",
            "--description",
            "Manual review",
            "--tags",
            "review,retro",
        ],
    );
    assert!(ok, "Add failed: {}", stderr);
    assert!(stdout.contains("Entry added successfully!"));

    // 6. List entries
    let (ok, stdout, stderr) = run_cli(&db_path, &["list"]);
    assert!(ok, "List failed: {}", stderr);
    assert!(stdout.contains("ProjectX"));
    assert!(stdout.contains("ProjectY"));

    // 7. Filter by project
    let (ok, stdout, stderr) = run_cli(&db_path, &["list", "--project", "ProjectY"]);
    assert!(ok, "List filter failed: {}", stderr);
    assert!(stdout.contains("ProjectY"));
    assert!(!stdout.contains("ProjectX"));

    // 8. List tags
    let (ok, stdout, stderr) = run_cli(&db_path, &["tags"]);
    assert!(ok, "Tags failed: {}", stderr);
    assert!(stdout.contains("dev"));
    assert!(stdout.contains("rust"));
    assert!(stdout.contains("retro"));

    // 9. Export to CSV
    let csv_file = NamedTempFile::new().unwrap();
    let csv_path = csv_file.path().to_str().unwrap().to_string();
    let (ok, stdout, stderr) = run_cli(&db_path, &["export", "--format", "csv", "--output", &csv_path]);
    assert!(ok, "Export CSV failed: {}", stderr);
    assert!(stdout.contains("Exported 2 entries to CSV"));
    let csv_content = std::fs::read_to_string(&csv_path).unwrap();
    assert!(csv_content.contains("ProjectX"));
    assert!(csv_content.contains("ProjectY"));

    // 10. Export to PDF (LaTeX)
    let temp_pdf_dir = tempfile::tempdir().unwrap();
    let pdf_path = temp_pdf_dir.path().join("out.pdf").to_str().unwrap().to_string();
    let (ok, stdout, stderr) = run_cli(&db_path, &["export", "--format", "pdf", "--output", &pdf_path]);
    assert!(ok, "Export PDF failed: {}\nstdout: {}", stderr, stdout);
    assert!(stdout.contains("Exported 2 entries to PDF"));

    // 11. Delete entry
    let (ok, stdout, stderr) = run_cli(&db_path, &["delete", "1"]);
    assert!(ok, "Delete failed: {}", stderr);
    assert!(stdout.contains("deleted"));

    let (ok, stdout, stderr) = run_cli(&db_path, &["list"]);
    assert!(ok, "List after delete failed: {}", stderr);
    assert!(!stdout.contains("ProjectX"));
    assert!(stdout.contains("ProjectY"));
}
