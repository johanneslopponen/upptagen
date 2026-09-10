use chrono::{DateTime, Utc};

pub fn parse_datetime(input: &str) -> Result<DateTime<Utc>, String> {
    if let Ok(dt) = DateTime::parse_from_rfc3339(input) {
        return Ok(dt.with_timezone(&Utc));
    }

    if let Ok(naive) = chrono::NaiveDateTime::parse_from_str(input, "%Y-%m-%d %H:%M:%S") {
        return Ok(DateTime::from_naive_utc_and_offset(naive, Utc));
    }

    if let Ok(naive) = chrono::NaiveDateTime::parse_from_str(input, "%Y-%m-%d %H:%M") {
        return Ok(DateTime::from_naive_utc_and_offset(naive, Utc));
    }

    if let Ok(naive_date) = chrono::NaiveDate::parse_from_str(input, "%Y-%m-%d") {
        let naive = naive_date.and_hms_opt(0, 0, 0).unwrap();
        return Ok(DateTime::from_naive_utc_and_offset(naive, Utc));
    }

    Err(format!(
        "Unable to parse datetime '{}'. Expected ISO8601 or YYYY-MM-DD HH:MM:SS format.",
        input
    ))
}

pub fn format_duration(seconds: i64) -> String {
    let hours = seconds / 3600;
    let mins = (seconds % 3600) / 60;
    let secs = seconds % 60;
    format!("{:02}:{:02}:{:02}", hours, mins, secs)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_datetime() {
        assert!(parse_datetime("2025-05-10T12:00:00Z").is_ok());
        assert!(parse_datetime("2025-05-10 12:00:00").is_ok());
        assert!(parse_datetime("2025-05-10 12:00").is_ok());
        assert!(parse_datetime("2025-05-10").is_ok());
        assert!(parse_datetime("invalid-date").is_err());
    }

    #[test]
    fn test_format_duration() {
        assert_eq!(format_duration(3665), "01:01:05");
        assert_eq!(format_duration(0), "00:00:00");
    }
}
