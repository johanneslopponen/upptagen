use crate::models::TimeEntry;
use crate::utils::format_duration;
use std::path::Path;

pub fn export_csv<P: AsRef<Path>>(entries: &[TimeEntry], output_path: P) -> Result<(), String> {
    let mut wtr = csv::Writer::from_path(output_path).map_err(|e| e.to_string())?;

    wtr.write_record([
        "ID",
        "Project",
        "Description",
        "Start Time",
        "End Time",
        "Duration (Seconds)",
        "Formatted Duration",
        "Tags",
    ])
    .map_err(|e| e.to_string())?;

    for entry in entries {
        let tags_str = entry.tags.join(",");
        wtr.write_record([
            entry.id.to_string(),
            entry.project.clone().unwrap_or_default(),
            entry.description.clone().unwrap_or_default(),
            entry.start_time.to_rfc3339(),
            entry.end_time.to_rfc3339(),
            entry.duration_seconds.to_string(),
            format_duration(entry.duration_seconds),
            tags_str,
        ])
        .map_err(|e| e.to_string())?;
    }

    wtr.flush().map_err(|e| e.to_string())?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::TimeEntry;
    use chrono::Utc;
    use tempfile::NamedTempFile;

    #[test]
    fn test_export_csv() {
        let temp_file = NamedTempFile::new().unwrap();
        let path = temp_file.path();

        let entries = vec![TimeEntry {
            id: 1,
            project: Some("Demo".into()),
            description: Some("Test description".into()),
            start_time: Utc::now(),
            end_time: Utc::now(),
            duration_seconds: 3600,
            tags: vec!["tag1".into(), "tag2".into()],
        }];

        export_csv(&entries, path).unwrap();

        let content = std::fs::read_to_string(path).unwrap();
        assert!(content.contains("Demo"));
        assert!(content.contains("Test description"));
        assert!(content.contains("01:00:00"));
        assert!(content.contains("tag1,tag2"));
    }
}
