use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct TimeEntry {
    pub id: i64,
    pub project: Option<String>,
    pub description: Option<String>,
    pub start_time: DateTime<Utc>,
    pub end_time: DateTime<Utc>,
    pub duration_seconds: i64,
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ActiveTimer {
    pub id: i64,
    pub project: Option<String>,
    pub description: Option<String>,
    pub start_time: DateTime<Utc>,
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct TagStat {
    pub name: String,
    pub usage_count: i64,
    pub total_duration_seconds: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct EntryFilter {
    pub project: Option<String>,
    pub tag: Option<String>,
    pub from_date: Option<DateTime<Utc>>,
    pub to_date: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum ExportFormat {
    Csv,
    Pdf,
}
