use crate::db::Database;
use chrono::{DateTime, Utc};
use serde::Deserialize;
use std::path::Path;

#[derive(Debug, Deserialize)]
struct CsvEntry {
    #[serde(rename = "ID")]
    _id: Option<String>,
    #[serde(rename = "Project")]
    project: Option<String>,
    #[serde(rename = "Description")]
    description: Option<String>,
    #[serde(rename = "Start Time")]
    start_time: String,
    #[serde(rename = "End Time")]
    end_time: String,
    #[serde(rename = "Duration (Seconds)")]
    _duration_seconds: Option<String>,
    #[serde(rename = "Formatted Duration")]
    _formatted_duration: Option<String>,
    #[serde(rename = "Tags")]
    tags: Option<String>,
}

pub fn import_csv<P: AsRef<Path>>(db: &Database, path: P) -> Result<usize, String> {
    let mut rdr = csv::Reader::from_path(path).map_err(|e| format!("Failed to open CSV file: {}", e))?;
    let mut count = 0;

    let existing_entries = db.list_entries(&crate::models::EntryFilter::default()).unwrap_or_default();
    let mut seen = std::collections::HashSet::new();

    for e in existing_entries {
        let mut sorted_tags = e.tags.clone();
        sorted_tags.sort();
        seen.insert((e.project, e.description, e.start_time.timestamp(), e.end_time.timestamp(), sorted_tags));
    }

    for result in rdr.deserialize() {
        let record: CsvEntry = result.map_err(|e| format!("Failed to parse CSV row: {}", e))?;

        let start_time = crate::utils::parse_datetime(&record.start_time)
            .map_err(|e| format!("Invalid start time '{}': {}", record.start_time, e))?;
        let end_time = crate::utils::parse_datetime(&record.end_time)
            .map_err(|e| format!("Invalid end time '{}': {}", record.end_time, e))?;

        let tags_vec: Vec<String> = record.tags.unwrap_or_default()
            .split(',')
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .collect();

        let project = match record.project {
            Some(p) if p.is_empty() => None,
            other => other,
        };

        let description = match record.description {
            Some(d) if d.is_empty() => None,
            other => other,
        };

        let mut sorted_tags = tags_vec.clone();
        sorted_tags.sort();

        let key = (project.clone(), description.clone(), start_time.timestamp(), end_time.timestamp(), sorted_tags);

        if seen.contains(&key) {
            continue;
        }

        db.add_entry(project, description, start_time, end_time, tags_vec)?;
        seen.insert(key);
        count += 1;
    }

    Ok(count)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::Database;
    use tempfile::NamedTempFile;
    use std::io::Write;

    #[test]
    fn test_import_csv() {
        let db = Database::open_in_memory().unwrap();

        let mut temp_file = NamedTempFile::new().unwrap();
        let csv_data = "ID,Project,Description,Start Time,End Time,Duration (Seconds),Formatted Duration,Tags\n\
                        1,Project Alpha,Task 1,2025-01-01T10:00:00Z,2025-01-01T12:00:00Z,7200,02:00:00,\"rust,dev\"\n\
                        2,,Task 2,2025-01-02T10:00:00Z,2025-01-02T11:00:00Z,3600,01:00:00,\"\"\n";

        write!(temp_file, "{}", csv_data).unwrap();

        let path = temp_file.path();

        let imported = import_csv(&db, path).unwrap();
        assert_eq!(imported, 2);

        let mut entries = db.list_entries(&crate::models::EntryFilter {
            project: None, tag: None, from_date: None, to_date: None
        }).unwrap();

        assert_eq!(entries.len(), 2);

        // Sort to ensure predictable order based on ID (or start time)
        entries.sort_by_key(|e| e.id);

        let e1 = &entries[0];
        assert_eq!(e1.project.as_deref(), Some("Project Alpha"));
        assert_eq!(e1.description.as_deref(), Some("Task 1"));

        // Tags might be returned in any order from DB
        let mut e1_tags = e1.tags.clone();
        e1_tags.sort();
        assert_eq!(e1_tags, vec!["dev", "rust"]);
        assert_eq!(e1.duration_seconds, 7200);

        let e2 = &entries[1];
        assert_eq!(e2.project.as_deref(), None);
        assert_eq!(e2.description.as_deref(), Some("Task 2"));
        assert!(e2.tags.is_empty());
        assert_eq!(e2.duration_seconds, 3600);

        // Test duplicates are skipped
        let mut temp_file2 = NamedTempFile::new().unwrap();
        // Has a duplicate of "Task 1", and a new entry "Task 3"
        let csv_data_with_dup = "ID,Project,Description,Start Time,End Time,Duration (Seconds),Formatted Duration,Tags\n\
                                 1,Project Alpha,Task 1,2025-01-01T10:00:00Z,2025-01-01T12:00:00Z,7200,02:00:00,\"rust,dev\"\n\
                                 3,Project Beta,Task 3,2025-01-03T10:00:00Z,2025-01-03T11:00:00Z,3600,01:00:00,\"go\"\n";
        write!(temp_file2, "{}", csv_data_with_dup).unwrap();

        let imported_again = import_csv(&db, temp_file2.path()).unwrap();
        // Only Task 3 should be imported, Task 1 should be skipped
        assert_eq!(imported_again, 1);

        let total_entries = db.list_entries(&crate::models::EntryFilter::default()).unwrap();
        assert_eq!(total_entries.len(), 3);
    }
}
