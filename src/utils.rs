use chrono::{DateTime, Local, NaiveDateTime, TimeZone, Utc};

fn naive_to_utc(naive: NaiveDateTime) -> DateTime<Utc> {
    match Local.from_local_datetime(&naive) {
        chrono::LocalResult::Single(dt) => dt.with_timezone(&Utc),
        chrono::LocalResult::Ambiguous(dt, _) => dt.with_timezone(&Utc),
        chrono::LocalResult::None => DateTime::from_naive_utc_and_offset(naive, Utc),
    }
}

pub fn parse_datetime(input: &str) -> Result<DateTime<Utc>, String> {
    let trimmed = input.trim();

    if let Ok(dt) = DateTime::parse_from_rfc3339(trimmed) {
        return Ok(dt.with_timezone(&Utc));
    }

    if let Ok(dt) = DateTime::parse_from_str(trimmed, "%Y-%m-%d %H:%M:%S %z") {
        return Ok(dt.with_timezone(&Utc));
    }

    if let Ok(dt) = DateTime::parse_from_str(trimmed, "%Y-%m-%d %H:%M:%S%:z") {
        return Ok(dt.with_timezone(&Utc));
    }

    if let Ok(dt) = DateTime::parse_from_str(trimmed, "%Y-%m-%d %H:%M %z") {
        return Ok(dt.with_timezone(&Utc));
    }

    if let Ok(dt) = DateTime::parse_from_str(trimmed, "%Y-%m-%d %H:%M%:z") {
        return Ok(dt.with_timezone(&Utc));
    }

    if let Ok(naive) = chrono::NaiveDateTime::parse_from_str(trimmed, "%Y-%m-%d %H:%M:%S") {
        return Ok(naive_to_utc(naive));
    }

    if let Ok(naive) = chrono::NaiveDateTime::parse_from_str(trimmed, "%Y-%m-%dT%H:%M:%S") {
        return Ok(naive_to_utc(naive));
    }

    if let Ok(naive) = chrono::NaiveDateTime::parse_from_str(trimmed, "%Y-%m-%d %H:%M") {
        return Ok(naive_to_utc(naive));
    }

    if let Ok(naive) = chrono::NaiveDateTime::parse_from_str(trimmed, "%Y-%m-%dT%H:%M") {
        return Ok(naive_to_utc(naive));
    }

    if let Ok(naive_date) = chrono::NaiveDate::parse_from_str(trimmed, "%Y-%m-%d") {
        let naive = naive_date.and_hms_opt(0, 0, 0).unwrap();
        return Ok(naive_to_utc(naive));
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

pub fn format_local_datetime(dt: &DateTime<Utc>) -> String {
    dt.with_timezone(&Local).format("%Y-%m-%d %H:%M").to_string()
}

pub fn format_local_datetime_seconds(dt: &DateTime<Utc>) -> String {
    dt.with_timezone(&Local).format("%Y-%m-%d %H:%M:%S").to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_datetime() {
        assert!(parse_datetime("2025-05-10T12:00:00Z").is_ok());
        assert!(parse_datetime("2025-05-10 12:00:00").is_ok());
        assert!(parse_datetime("2025-05-10T12:00:00").is_ok());
        assert!(parse_datetime("2025-05-10 12:00").is_ok());
        assert!(parse_datetime("2025-05-10T12:00").is_ok());
        assert!(parse_datetime("2025-05-10").is_ok());
        assert!(parse_datetime("invalid-date").is_err());
    }

    #[test]
    fn test_format_duration() {
        assert_eq!(format_duration(3665), "01:01:05");
        assert_eq!(format_duration(0), "00:00:00");
    }

    #[test]
    fn test_parse_and_format_local() {
        let input = "2025-05-10 12:34";
        let parsed = parse_datetime(input).unwrap();
        let formatted = format_local_datetime(&parsed);
        assert_eq!(formatted, "2025-05-10 12:34");

        let input_sec = "2025-05-10 12:34:56";
        let parsed_sec = parse_datetime(input_sec).unwrap();
        let formatted_sec = format_local_datetime_seconds(&parsed_sec);
        assert_eq!(formatted_sec, "2025-05-10 12:34:56");
    }
}
