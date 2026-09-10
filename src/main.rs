mod cli;
mod db;
mod export;
mod models;
mod utils;
mod web;

use clap::Parser;
use cli::{Cli, Commands};
use db::Database;
use export::csv::export_csv;
use export::pdf::export_pdf;
use models::EntryFilter;
use std::process;
use std::sync::{Arc, Mutex};
use utils::{format_duration, parse_datetime};

#[tokio::main]
async fn main() {
    let cli = Cli::parse();

    let db_path = match cli.db_path {
        Some(p) => std::path::PathBuf::from(p),
        None => Database::default_db_path(),
    };

    let db = match Database::open_file(&db_path) {
        Ok(db) => db,
        Err(e) => {
            eprintln!("Error opening database at {}: {}", db_path.display(), e);
            process::exit(1);
        }
    };

    match cli.command {
        Commands::Start {
            description,
            project,
            tags,
        } => match db.start_timer(project.clone(), description.clone(), tags) {
            Ok(timer) => {
                println!("🚀 Timer started!");
                if let Some(p) = timer.project {
                    println!("   Project:     {}", p);
                }
                if let Some(d) = timer.description {
                    println!("   Description: {}", d);
                }
                if !timer.tags.is_empty() {
                    println!("   Tags:        {}", timer.tags.join(", "));
                }
                println!("   Start time:  {}", timer.start_time.format("%Y-%m-%d %H:%M:%S UTC"));
            }
            Err(e) => {
                eprintln!("Error starting timer: {}", e);
                process::exit(1);
            }
        },

        Commands::Stop { description, tags } => match db.stop_timer(description, tags) {
            Ok(entry) => {
                println!("⏹️ Timer stopped and logged!");
                println!("   ID:       {}", entry.id);
                if let Some(p) = entry.project {
                    println!("   Project:  {}", p);
                }
                if let Some(d) = entry.description {
                    println!("   Desc:     {}", d);
                }
                println!("   Duration: {}", format_duration(entry.duration_seconds));
                if !entry.tags.is_empty() {
                    println!("   Tags:     {}", entry.tags.join(", "));
                }
            }
            Err(e) => {
                eprintln!("Error stopping timer: {}", e);
                process::exit(1);
            }
        },

        Commands::Status => match db.get_active_timer() {
            Ok(Some(timer)) => {
                let elapsed = (chrono::Utc::now() - timer.start_time).num_seconds();
                println!("⏱️ Active Timer Running");
                println!("   Project:     {}", timer.project.as_deref().unwrap_or("Unassigned"));
                println!("   Description: {}", timer.description.as_deref().unwrap_or("No description"));
                println!("   Elapsed:     {}", format_duration(elapsed));
                println!("   Start time:  {}", timer.start_time.format("%Y-%m-%d %H:%M:%S UTC"));
                if !timer.tags.is_empty() {
                    println!("   Tags:        {}", timer.tags.join(", "));
                }
            }
            Ok(None) => {
                println!("No timer currently running.");
            }
            Err(e) => {
                eprintln!("Error fetching timer status: {}", e);
                process::exit(1);
            }
        },

        Commands::Add {
            start,
            end,
            project,
            description,
            tags,
        } => {
            let start_dt = match parse_datetime(&start) {
                Ok(dt) => dt,
                Err(e) => {
                    eprintln!("Invalid start time: {}", e);
                    process::exit(1);
                }
            };

            let end_dt = match parse_datetime(&end) {
                Ok(dt) => dt,
                Err(e) => {
                    eprintln!("Invalid end time: {}", e);
                    process::exit(1);
                }
            };

            match db.add_entry(project, description, start_dt, end_dt, tags) {
                Ok(entry) => {
                    println!("✅ Entry added successfully!");
                    println!("   ID:       {}", entry.id);
                    println!("   Duration: {}", format_duration(entry.duration_seconds));
                }
                Err(e) => {
                    eprintln!("Error adding entry: {}", e);
                    process::exit(1);
                }
            }
        }

        Commands::List {
            project,
            tag,
            from,
            to,
        } => {
            let from_date = match from {
                Some(ref f) => match parse_datetime(f) {
                    Ok(dt) => Some(dt),
                    Err(e) => {
                        eprintln!("Invalid --from date: {}", e);
                        process::exit(1);
                    }
                },
                None => None,
            };

            let to_date = match to {
                Some(ref t) => match parse_datetime(t) {
                    Ok(dt) => Some(dt),
                    Err(e) => {
                        eprintln!("Invalid --to date: {}", e);
                        process::exit(1);
                    }
                },
                None => None,
            };

            let filter = EntryFilter {
                project,
                tag,
                from_date,
                to_date,
            };

            match db.list_entries(&filter) {
                Ok(entries) => {
                    if entries.is_empty() {
                        println!("No matching time entries found.");
                        return;
                    }

                    println!(
                        "{:<5} {:<15} {:<25} {:<20} {:<10} {:<15}",
                        "ID", "Project", "Description", "Start Time", "Duration", "Tags"
                    );
                    println!("{}", "-".repeat(95));

                    let mut total_secs = 0;
                    for e in entries {
                        total_secs += e.duration_seconds;
                        let proj = e.project.as_deref().unwrap_or("-");
                        let desc = e.description.as_deref().unwrap_or("-");
                        let start_str = e.start_time.format("%Y-%m-%d %H:%M").to_string();
                        let dur_str = format_duration(e.duration_seconds);
                        let tags_str = e.tags.join(",");

                        println!(
                            "{:<5} {:<15} {:<25} {:<20} {:<10} {:<15}",
                            e.id, proj, desc, start_str, dur_str, tags_str
                        );
                    }

                    println!("{}", "-".repeat(95));
                    println!("Total Duration: {}", format_duration(total_secs));
                }
                Err(e) => {
                    eprintln!("Error listing entries: {}", e);
                    process::exit(1);
                }
            }
        }

        Commands::Delete { id } => match db.delete_entry(id) {
            Ok(true) => println!("🗑️ Entry #{} deleted.", id),
            Ok(false) => {
                eprintln!("Entry #{} not found.", id);
                process::exit(1);
            }
            Err(e) => {
                eprintln!("Error deleting entry #{}: {}", id, e);
                process::exit(1);
            }
        },

        Commands::Tags => match db.list_tags() {
            Ok(tags) => {
                if tags.is_empty() {
                    println!("No tags found.");
                    return;
                }
                println!("{:<20} {:<12} {:<15}", "Tag", "Entries", "Total Duration");
                println!("{}", "-".repeat(50));
                for t in tags {
                    println!(
                        "{:<20} {:<12} {:<15}",
                        t.name,
                        t.usage_count,
                        format_duration(t.total_duration_seconds)
                    );
                }
            }
            Err(e) => {
                eprintln!("Error fetching tags: {}", e);
                process::exit(1);
            }
        },

        Commands::Export {
            format,
            output,
            project,
            tag,
            from,
            to,
        } => {
            let from_date = match from {
                Some(ref f) => parse_datetime(f).ok(),
                None => None,
            };
            let to_date = match to {
                Some(ref t) => parse_datetime(t).ok(),
                None => None,
            };

            let filter = EntryFilter {
                project,
                tag,
                from_date,
                to_date,
            };

            let entries = match db.list_entries(&filter) {
                Ok(e) => e,
                Err(err) => {
                    eprintln!("Error reading entries for export: {}", err);
                    process::exit(1);
                }
            };

            match format.to_lowercase().as_str() {
                "csv" => {
                    if let Err(e) = export_csv(&entries, &output) {
                        eprintln!("CSV Export failed: {}", e);
                        process::exit(1);
                    }
                    println!("📄 Exported {} entries to CSV file: {}", entries.len(), output);
                }
                "pdf" => {
                    if let Err(e) = export_pdf(&entries, &output) {
                        eprintln!("PDF Export failed: {}", e);
                        process::exit(1);
                    }
                    println!("📄 Exported {} entries to PDF file: {}", entries.len(), output);
                }
                other => {
                    eprintln!("Unsupported export format: {}. Use 'csv' or 'pdf'.", other);
                    process::exit(1);
                }
            }
        }

        Commands::Serve { port } => {
            let shared_db = Arc::new(Mutex::new(db));
            if let Err(e) = web::run_server(shared_db, port).await {
                eprintln!("Web server error: {}", e);
                process::exit(1);
            }
        }
    }
}
