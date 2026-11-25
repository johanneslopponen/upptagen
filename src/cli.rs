use clap::{Parser, Subcommand};

#[derive(Parser, Debug)]
#[command(name = "upptagen")]
#[command(about = "A CLI time tracking application in Rust", long_about = None)]
pub struct Cli {
    #[arg(long, global = true, help = "Path to SQLite database file")]
    pub db_path: Option<String>,

    #[command(subcommand)]
    pub command: Commands,
}

#[derive(Subcommand, Debug)]
pub enum Commands {
    #[command(about = "Start a new live timer")]
    Start {
        #[arg(help = "Optional description for the task")]
        description: Option<String>,

        #[arg(short, long, help = "Project name")]
        project: Option<String>,

        #[arg(
            short,
            long,
            value_delimiter = ',',
            help = "Comma-separated list of tags"
        )]
        tags: Vec<String>,
    },

    #[command(about = "Edit an existing time entry", alias = "update")]
    Edit {
        #[arg(help = "ID of entry to edit")]
        id: i64,

        #[arg(short, long, help = "New project name")]
        project: Option<String>,

        #[arg(short, long, help = "New task description")]
        description: Option<String>,

        #[arg(
            short,
            long,
            help = "New start time (e.g. '2025-01-01T10:00:00Z' or '2025-01-01 10:00:00')"
        )]
        start: Option<String>,

        #[arg(
            short,
            long,
            help = "New end time (e.g. '2025-01-01T10:00:00Z' or '2025-01-01 10:00:00')"
        )]
        end: Option<String>,

        #[arg(
            short,
            long,
            value_delimiter = ',',
            help = "New comma-separated list of tags"
        )]
        tags: Option<Vec<String>>,
    },

    #[command(about = "Stop the currently running timer")]
    Stop {
        #[arg(help = "Optional updated description for the task")]
        description: Option<String>,

        #[arg(short, long, value_delimiter = ',', help = "Additional tags to append")]
        tags: Vec<String>,
    },

    #[command(about = "Check status of currently active timer")]
    Status,

    #[command(about = "Manually insert a time entry retroactively", alias = "log")]
    Add {
        #[arg(
            short,
            long,
            help = "Start time (e.g. '2025-01-01T10:00:00Z' or '2025-01-01 10:00:00')"
        )]
        start: String,

        #[arg(
            short,
            long,
            help = "End time (e.g. '2025-01-01T10:00:00Z' or '2025-01-01 10:00:00')"
        )]
        end: String,

        #[arg(short, long, help = "Project name")]
        project: Option<String>,

        #[arg(short, long, help = "Task description")]
        description: Option<String>,

        #[arg(
            short,
            long,
            value_delimiter = ',',
            help = "Comma-separated list of tags"
        )]
        tags: Vec<String>,
    },

    #[command(about = "List recorded time entries")]
    List {
        #[arg(short, long, help = "Filter by project name")]
        project: Option<String>,

        #[arg(short, long, help = "Filter by tag")]
        tag: Option<String>,

        #[arg(long, help = "Filter start time from (ISO 8601 string)")]
        from: Option<String>,

        #[arg(long, help = "Filter end time to (ISO 8601 string)")]
        to: Option<String>,
    },

    #[command(about = "Delete an entry by ID")]
    Delete {
        #[arg(help = "ID of entry to delete")]
        id: i64,
    },

    #[command(about = "List tags and usage statistics")]
    Tags,

    #[command(about = "Export time entries to CSV or PDF (rendered via LaTeX)")]
    Export {
        #[arg(short, long, default_value = "csv", help = "Export format: csv or pdf")]
        format: String,

        #[arg(
            short,
            long,
            help = "Output file path (e.g. summary.csv or report.pdf)"
        )]
        output: String,

        #[arg(short, long, help = "Filter by project name")]
        project: Option<String>,

        #[arg(short, long, help = "Filter by tag")]
        tag: Option<String>,

        #[arg(long, help = "Filter start time from (ISO 8601 string)")]
        from: Option<String>,

        #[arg(long, help = "Filter end time to (ISO 8601 string)")]
        to: Option<String>,
    },

    #[command(about = "Import time entries from a CSV file")]
    Import {
        #[arg(short, long, help = "Path to the CSV file to import from")]
        path: String,
    },
}
