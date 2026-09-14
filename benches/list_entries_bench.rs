use criterion::{criterion_group, criterion_main, Criterion};
use std::hint::black_box;
use upptagen::db::Database;
use upptagen::models::EntryFilter;
use chrono::Utc;

fn benchmark_list_entries(c: &mut Criterion) {
    let db = Database::open_in_memory().unwrap();

    // Seed 1,000 entries with tags
    let start_time = Utc::now();
    for i in 0..1000 {
        let tags = vec![
            format!("tag_{}", i % 10),
            format!("common_tag"),
            format!("tag_{}", i % 50),
        ];
        db.add_entry(
            Some(format!("Project {}", i % 5)),
            Some(format!("Description for entry {}", i)),
            start_time,
            start_time + chrono::Duration::minutes(30),
            tags,
        ).unwrap();
    }

    let filter_all = EntryFilter::default();
    let filter_project = EntryFilter {
        project: Some("Project 2".to_string()),
        ..Default::default()
    };

    c.bench_function("list_entries_1000_all", |b| {
        b.iter(|| {
            let entries = db.list_entries(black_box(&filter_all)).unwrap();
            black_box(entries);
        });
    });

    c.bench_function("list_entries_1000_filtered_project", |b| {
        b.iter(|| {
            let entries = db.list_entries(black_box(&filter_project)).unwrap();
            black_box(entries);
        });
    });
}

criterion_group!(benches, benchmark_list_entries);
criterion_main!(benches);
