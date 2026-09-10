use crate::models::{ActiveTimer, EntryFilter, TagStat, TimeEntry};
use chrono::{DateTime, Utc};
use rusqlite::{params, Connection, Result as SqliteResult};
use std::path::Path;

pub struct Database {
    conn: Connection,
}

impl Database {
    pub fn new(conn: Connection) -> SqliteResult<Self> {
        let db = Database { conn };
        db.init_schema()?;
        Ok(db)
    }

    pub fn open_file<P: AsRef<Path>>(path: P) -> SqliteResult<Self> {
        if let Some(parent) = path.as_ref().parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let conn = Connection::open(path)?;
        Self::new(conn)
    }

    pub fn open_in_memory() -> SqliteResult<Self> {
        let conn = Connection::open_in_memory()?;
        Self::new(conn)
    }

    pub fn default_db_path() -> std::path::PathBuf {
        let mut path = dirs::data_dir().unwrap_or_else(|| std::path::PathBuf::from("."));
        path.push("timetrack");
        path.push("timetrack.db");
        path
    }

    fn init_schema(&self) -> SqliteResult<()> {
        self.conn.execute_batch(
            "
            CREATE TABLE IF NOT EXISTS entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project TEXT,
                description TEXT,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                duration_seconds INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
            );

            CREATE TABLE IF NOT EXISTS entry_tags (
                entry_id INTEGER NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY (entry_id, tag_id),
                FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE,
                FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS active_timer (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                project TEXT,
                description TEXT,
                start_time TEXT NOT NULL,
                tags_json TEXT NOT NULL
            );
            ",
        )?;
        Ok(())
    }

    pub fn start_timer(
        &self,
        project: Option<String>,
        description: Option<String>,
        tags: Vec<String>,
    ) -> Result<ActiveTimer, String> {
        if self.get_active_timer().map_err(|e| e.to_string())?.is_some() {
            return Err("A timer is already running. Please stop it before starting a new one.".into());
        }

        let start_time = Utc::now();
        let tags_json = serde_json::to_string(&tags).map_err(|e| e.to_string())?;

        self.conn
            .execute(
                "INSERT INTO active_timer (id, project, description, start_time, tags_json) VALUES (1, ?1, ?2, ?3, ?4)",
                params![project, description, start_time.to_rfc3339(), tags_json],
            )
            .map_err(|e| e.to_string())?;

        Ok(ActiveTimer {
            id: 1,
            project,
            description,
            start_time,
            tags,
        })
    }

    pub fn get_active_timer(&self) -> SqliteResult<Option<ActiveTimer>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, project, description, start_time, tags_json FROM active_timer WHERE id = 1",
        )?;

        let mut rows = stmt.query([])?;
        if let Some(row) = rows.next()? {
            let id: i64 = row.get(0)?;
            let project: Option<String> = row.get(1)?;
            let description: Option<String> = row.get(2)?;
            let start_time_str: String = row.get(3)?;
            let tags_json: String = row.get(4)?;

            let start_time = DateTime::parse_from_rfc3339(&start_time_str)
                .map(|dt| dt.with_timezone(&Utc))
                .map_err(|e| rusqlite::Error::FromSqlConversionFailure(0, rusqlite::types::Type::Text, Box::new(e)))?;

            let tags: Vec<String> = serde_json::from_str(&tags_json).unwrap_or_default();

            Ok(Some(ActiveTimer {
                id,
                project,
                description,
                start_time,
                tags,
            }))
        } else {
            Ok(None)
        }
    }

    pub fn stop_timer(
        &self,
        override_description: Option<String>,
        additional_tags: Vec<String>,
    ) -> Result<TimeEntry, String> {
        let timer = self
            .get_active_timer()
            .map_err(|e| e.to_string())?
            .ok_or_else(|| "No active timer running.".to_string())?;

        let end_time = Utc::now();
        let description = override_description.or(timer.description);
        let mut tags = timer.tags;
        for t in additional_tags {
            if !tags.contains(&t) {
                tags.push(t);
            }
        }

        let entry = self.add_entry(timer.project, description, timer.start_time, end_time, tags)?;

        self.conn
            .execute("DELETE FROM active_timer WHERE id = 1", [])
            .map_err(|e| e.to_string())?;

        Ok(entry)
    }

    pub fn add_entry(
        &self,
        project: Option<String>,
        description: Option<String>,
        start_time: DateTime<Utc>,
        end_time: DateTime<Utc>,
        tags: Vec<String>,
    ) -> Result<TimeEntry, String> {
        if end_time < start_time {
            return Err("End time cannot be earlier than start time.".into());
        }

        let duration_seconds = (end_time - start_time).num_seconds();

        self.conn
            .execute(
                "INSERT INTO entries (project, description, start_time, end_time, duration_seconds) VALUES (?1, ?2, ?3, ?4, ?5)",
                params![project, description, start_time.to_rfc3339(), end_time.to_rfc3339(), duration_seconds],
            )
            .map_err(|e| e.to_string())?;

        let entry_id = self.conn.last_insert_rowid();

        self.associate_tags(entry_id, &tags)?;

        Ok(TimeEntry {
            id: entry_id,
            project,
            description,
            start_time,
            end_time,
            duration_seconds,
            tags,
        })
    }

    fn associate_tags(&self, entry_id: i64, tags: &[String]) -> Result<(), String> {
        for tag_name in tags {
            let clean_tag = tag_name.trim();
            if clean_tag.is_empty() {
                continue;
            }

            self.conn
                .execute(
                    "INSERT OR IGNORE INTO tags (name) VALUES (?1)",
                    params![clean_tag],
                )
                .map_err(|e| e.to_string())?;

            let tag_id: i64 = self
                .conn
                .query_row(
                    "SELECT id FROM tags WHERE name = ?1",
                    params![clean_tag],
                    |row| row.get(0),
                )
                .map_err(|e| e.to_string())?;

            self.conn
                .execute(
                    "INSERT OR IGNORE INTO entry_tags (entry_id, tag_id) VALUES (?1, ?2)",
                    params![entry_id, tag_id],
                )
                .map_err(|e| e.to_string())?;
        }
        Ok(())
    }

    pub fn list_entries(&self, filter: &EntryFilter) -> Result<Vec<TimeEntry>, String> {
        let mut query = String::from(
            "SELECT DISTINCT e.id, e.project, e.description, e.start_time, e.end_time, e.duration_seconds
             FROM entries e
             LEFT JOIN entry_tags et ON e.id = et.entry_id
             LEFT JOIN tags t ON et.tag_id = t.id
             WHERE 1=1",
        );

        let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

        if let Some(ref proj) = filter.project {
            query.push_str(" AND e.project = ?");
            params_vec.push(Box::new(proj.clone()));
        }

        if let Some(ref tag) = filter.tag {
            query.push_str(" AND t.name = ?");
            params_vec.push(Box::new(tag.clone()));
        }

        if let Some(ref from_dt) = filter.from_date {
            query.push_str(" AND e.start_time >= ?");
            params_vec.push(Box::new(from_dt.to_rfc3339()));
        }

        if let Some(ref to_dt) = filter.to_date {
            query.push_str(" AND e.end_time <= ?");
            params_vec.push(Box::new(to_dt.to_rfc3339()));
        }

        query.push_str(" ORDER BY e.start_time DESC");

        let mut stmt = self.conn.prepare(&query).map_err(|e| e.to_string())?;

        let rusqlite_params: Vec<&dyn rusqlite::ToSql> =
            params_vec.iter().map(|p| p.as_ref()).collect();

        let entry_rows = stmt
            .query_map(rusqlite_params.as_slice(), |row| {
                let id: i64 = row.get(0)?;
                let project: Option<String> = row.get(1)?;
                let description: Option<String> = row.get(2)?;
                let start_time_str: String = row.get(3)?;
                let end_time_str: String = row.get(4)?;
                let duration_seconds: i64 = row.get(5)?;

                Ok((id, project, description, start_time_str, end_time_str, duration_seconds))
            })
            .map_err(|e| e.to_string())?;

        let mut entries = Vec::new();
        for row in entry_rows {
            let (id, project, description, start_time_str, end_time_str, duration_seconds) =
                row.map_err(|e| e.to_string())?;

            let start_time = DateTime::parse_from_rfc3339(&start_time_str)
                .map(|dt| dt.with_timezone(&Utc))
                .map_err(|e| e.to_string())?;
            let end_time = DateTime::parse_from_rfc3339(&end_time_str)
                .map(|dt| dt.with_timezone(&Utc))
                .map_err(|e| e.to_string())?;

            let tags = self.get_tags_for_entry(id)?;

            entries.push(TimeEntry {
                id,
                project,
                description,
                start_time,
                end_time,
                duration_seconds,
                tags,
            });
        }

        Ok(entries)
    }

    fn get_tags_for_entry(&self, entry_id: i64) -> Result<Vec<String>, String> {
        let mut stmt = self
            .conn
            .prepare(
                "SELECT t.name FROM tags t
                 JOIN entry_tags et ON t.id = et.tag_id
                 WHERE et.entry_id = ?1
                 ORDER BY t.name ASC",
            )
            .map_err(|e| e.to_string())?;

        let tag_rows = stmt
            .query_map(params![entry_id], |row| row.get(0))
            .map_err(|e| e.to_string())?;

        let mut tags = Vec::new();
        for t in tag_rows {
            tags.push(t.map_err(|e| e.to_string())?);
        }
        Ok(tags)
    }

    pub fn delete_entry(&self, id: i64) -> Result<bool, String> {
        self.conn
            .execute("DELETE FROM entry_tags WHERE entry_id = ?1", params![id])
            .map_err(|e| e.to_string())?;

        let count = self
            .conn
            .execute("DELETE FROM entries WHERE id = ?1", params![id])
            .map_err(|e| e.to_string())?;

        Ok(count > 0)
    }

    pub fn list_tags(&self) -> Result<Vec<TagStat>, String> {
        let mut stmt = self
            .conn
            .prepare(
                "SELECT t.name, COUNT(et.entry_id), COALESCE(SUM(e.duration_seconds), 0)
                 FROM tags t
                 LEFT JOIN entry_tags et ON t.id = et.tag_id
                 LEFT JOIN entries e ON et.entry_id = e.id
                 GROUP BY t.id, t.name
                 ORDER BY t.name ASC",
            )
            .map_err(|e| e.to_string())?;

        let rows = stmt
            .query_map([], |row| {
                Ok(TagStat {
                    name: row.get(0)?,
                    usage_count: row.get(1)?,
                    total_duration_seconds: row.get(2)?,
                })
            })
            .map_err(|e| e.to_string())?;

        let mut stats = Vec::new();
        for r in rows {
            stats.push(r.map_err(|e| e.to_string())?);
        }
        Ok(stats)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_database_operations() {
        let db = Database::open_in_memory().unwrap();

        // 1. Start timer
        let timer = db
            .start_timer(
                Some("Project A".into()),
                Some("Initial work".into()),
                vec!["rust".into(), "cli".into()],
            )
            .unwrap();
        assert_eq!(timer.id, 1);
        assert_eq!(timer.project.as_deref(), Some("Project A"));

        // 2. Active timer check
        let active = db.get_active_timer().unwrap().unwrap();
        assert_eq!(active.project.as_deref(), Some("Project A"));

        // Starting another timer should fail
        assert!(db
            .start_timer(Some("Project B".into()), None, vec![])
            .is_err());

        // 3. Stop timer
        let entry = db
            .stop_timer(Some("Completed work".into()), vec!["backend".into()])
            .unwrap();
        assert_eq!(entry.project.as_deref(), Some("Project A"));
        assert_eq!(entry.description.as_deref(), Some("Completed work"));
        assert_eq!(entry.tags, vec!["rust", "cli", "backend"]);

        // Active timer should now be None
        assert!(db.get_active_timer().unwrap().is_none());

        // 4. Retroactive entry addition
        let start = Utc::now() - chrono::Duration::hours(2);
        let end = Utc::now();
        let retro = db
            .add_entry(
                Some("Project B".into()),
                Some("Retroactive task".into()),
                start,
                end,
                vec!["latex".into(), "rust".into()],
            )
            .unwrap();
        assert_eq!(retro.duration_seconds, 7200);

        // 5. Listing and Filtering
        let all_entries = db.list_entries(&EntryFilter::default()).unwrap();
        assert_eq!(all_entries.len(), 2);

        let filtered_tag = db
            .list_entries(&EntryFilter {
                tag: Some("latex".into()),
                ..Default::default()
            })
            .unwrap();
        assert_eq!(filtered_tag.len(), 1);
        assert_eq!(filtered_tag[0].project.as_deref(), Some("Project B"));

        let filtered_proj = db
            .list_entries(&EntryFilter {
                project: Some("Project A".into()),
                ..Default::default()
            })
            .unwrap();
        assert_eq!(filtered_proj.len(), 1);

        // 6. Tags stats
        let tag_stats = db.list_tags().unwrap();
        assert!(tag_stats.iter().any(|t| t.name == "rust"));

        // 7. Deletion
        let deleted = db.delete_entry(retro.id).unwrap();
        assert!(deleted);
        let after_delete = db.list_entries(&EntryFilter::default()).unwrap();
        assert_eq!(after_delete.len(), 1);
    }
}
