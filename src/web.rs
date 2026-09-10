use axum::{
    extract::{Path as AxumPath, Query, State},
    http::{header, HeaderValue, StatusCode},
    response::{Html, IntoResponse, Response},
    routing::{delete, get, post},
    Json, Router,
};
use serde::Deserialize;
use std::net::SocketAddr;
use std::sync::{Arc, Mutex};
use tower_http::cors::CorsLayer;

use crate::db::Database;
use crate::export::csv::export_csv;
use crate::export::pdf::export_pdf;
use crate::models::EntryFilter;
use crate::utils::parse_datetime;

#[derive(Clone)]
pub struct AppState {
    pub db: Arc<Mutex<Database>>,
}

#[derive(Deserialize)]
pub struct ApiEntryFilter {
    pub project: Option<String>,
    pub tag: Option<String>,
    pub from: Option<String>,
    pub to: Option<String>,
}

#[derive(Deserialize)]
pub struct StartTimerReq {
    pub project: Option<String>,
    pub description: Option<String>,
    pub tags: Option<Vec<String>>,
}

#[derive(Deserialize)]
pub struct EditEntryReq {
    pub project: Option<String>,
    pub description: Option<String>,
    pub start: Option<String>,
    pub end: Option<String>,
    pub tags: Option<Vec<String>>,
}

#[derive(Deserialize)]
pub struct StopTimerReq {
    pub description: Option<String>,
    pub tags: Option<Vec<String>>,
}

#[derive(Deserialize)]
pub struct AddEntryReq {
    pub start: String,
    pub end: String,
    pub project: Option<String>,
    pub description: Option<String>,
    pub tags: Option<Vec<String>>,
}

#[derive(Deserialize)]
pub struct ExportReq {
    pub format: Option<String>,
    pub project: Option<String>,
    pub tag: Option<String>,
    pub from: Option<String>,
    pub to: Option<String>,
}

pub fn create_router(db: Arc<Mutex<Database>>) -> Router {
    Router::new()
        .route("/", get(dashboard_html))
        .route("/api/entries", get(get_entries).post(add_entry))
        .route("/api/entries/:id", delete(delete_entry).put(edit_entry))
        .route("/api/timer", get(get_timer))
        .route("/api/timer/start", post(start_timer))
        .route("/api/timer/stop", post(stop_timer))
        .route("/api/tags", get(get_tags))
        .route("/api/export", get(export_data))
        .layer(CorsLayer::permissive())
        .with_state(AppState { db })
}

pub async fn run_server(db: Arc<Mutex<Database>>, port: u16) -> Result<(), String> {
    let app = create_router(db);
    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    println!("Starting web dashboard server on http://localhost:{}", port);
    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .map_err(|e| e.to_string())?;
    axum::serve(listener, app).await.map_err(|e| e.to_string())
}

async fn dashboard_html() -> Html<&'static str> {
    Html(INDEX_HTML)
}

fn build_filter(query: ApiEntryFilter) -> Result<EntryFilter, String> {
    let from_date = match query.from {
        Some(s) if !s.trim().is_empty() => Some(parse_datetime(&s)?),
        _ => None,
    };
    let to_date = match query.to {
        Some(s) if !s.trim().is_empty() => Some(parse_datetime(&s)?),
        _ => None,
    };
    Ok(EntryFilter {
        project: query.project.filter(|s| !s.trim().is_empty()),
        tag: query.tag.filter(|s| !s.trim().is_empty()),
        from_date,
        to_date,
    })
}

async fn get_entries(
    State(state): State<AppState>,
    Query(query): Query<ApiEntryFilter>,
) -> Response {
    let filter = match build_filter(query) {
        Ok(f) => f,
        Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
    };

    let db = state.db.lock().unwrap();
    match db.list_entries(&filter) {
        Ok(entries) => Json(entries).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e).into_response(),
    }
}

async fn add_entry(
    State(state): State<AppState>,
    Json(payload): Json<AddEntryReq>,
) -> Response {
    let start_time = match parse_datetime(&payload.start) {
        Ok(dt) => dt,
        Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
    };
    let end_time = match parse_datetime(&payload.end) {
        Ok(dt) => dt,
        Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
    };

    let tags = payload.tags.unwrap_or_default();
    let db = state.db.lock().unwrap();
    match db.add_entry(payload.project, payload.description, start_time, end_time, tags) {
        Ok(entry) => (StatusCode::CREATED, Json(entry)).into_response(),
        Err(e) => (StatusCode::BAD_REQUEST, e).into_response(),
    }
}

async fn edit_entry(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<i64>,
    Json(payload): Json<EditEntryReq>,
) -> Response {
    let start_time = match payload.start {
        Some(ref s) if !s.trim().is_empty() => match parse_datetime(s) {
            Ok(dt) => Some(dt),
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        },
        _ => None,
    };

    let end_time = match payload.end {
        Some(ref e_str) if !e_str.trim().is_empty() => match parse_datetime(e_str) {
            Ok(dt) => Some(dt),
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        },
        _ => None,
    };

    let db = state.db.lock().unwrap();
    match db.edit_entry(
        id,
        payload.project.filter(|s| !s.trim().is_empty()),
        payload.description.filter(|s| !s.trim().is_empty()),
        start_time,
        end_time,
        payload.tags,
    ) {
        Ok(entry) => Json(entry).into_response(),
        Err(e) => (StatusCode::BAD_REQUEST, e).into_response(),
    }
}

async fn delete_entry(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<i64>,
) -> Response {
    let db = state.db.lock().unwrap();
    match db.delete_entry(id) {
        Ok(true) => StatusCode::NO_CONTENT.into_response(),
        Ok(false) => (StatusCode::NOT_FOUND, "Entry not found").into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e).into_response(),
    }
}

async fn get_timer(State(state): State<AppState>) -> Response {
    let db = state.db.lock().unwrap();
    match db.get_active_timer() {
        Ok(timer) => Json(timer).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn start_timer(
    State(state): State<AppState>,
    Json(payload): Json<StartTimerReq>,
) -> Response {
    let tags = payload.tags.unwrap_or_default();
    let db = state.db.lock().unwrap();
    match db.start_timer(payload.project, payload.description, tags) {
        Ok(timer) => (StatusCode::CREATED, Json(timer)).into_response(),
        Err(e) => (StatusCode::BAD_REQUEST, e).into_response(),
    }
}

async fn stop_timer(
    State(state): State<AppState>,
    Json(payload): Json<StopTimerReq>,
) -> Response {
    let tags = payload.tags.unwrap_or_default();
    let db = state.db.lock().unwrap();
    match db.stop_timer(payload.description, tags) {
        Ok(entry) => Json(entry).into_response(),
        Err(e) => (StatusCode::BAD_REQUEST, e).into_response(),
    }
}

async fn get_tags(State(state): State<AppState>) -> Response {
    let db = state.db.lock().unwrap();
    match db.list_tags() {
        Ok(tags) => Json(tags).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e).into_response(),
    }
}

async fn export_data(
    State(state): State<AppState>,
    Query(query): Query<ExportReq>,
) -> Response {
    let filter = match build_filter(ApiEntryFilter {
        project: query.project,
        tag: query.tag,
        from: query.from,
        to: query.to,
    }) {
        Ok(f) => f,
        Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
    };

    let entries = {
        let db = state.db.lock().unwrap();
        match db.list_entries(&filter) {
            Ok(e) => e,
            Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, e).into_response(),
        }
    };

    let fmt = query.format.as_deref().unwrap_or("csv").to_lowercase();
    let temp_file = match tempfile::NamedTempFile::new() {
        Ok(f) => f,
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    };

    if fmt == "pdf" {
        if let Err(e) = export_pdf(&entries, temp_file.path()) {
            return (StatusCode::INTERNAL_SERVER_ERROR, format!("PDF export error: {}", e)).into_response();
        }
        let data = std::fs::read(temp_file.path()).unwrap_or_default();
        let mut response = data.into_response();
        response.headers_mut().insert(
            header::CONTENT_TYPE,
            HeaderValue::from_static("application/pdf"),
        );
        response.headers_mut().insert(
            header::CONTENT_DISPOSITION,
            HeaderValue::from_static("attachment; filename=\"report.pdf\""),
        );
        response
    } else {
        if let Err(e) = export_csv(&entries, temp_file.path()) {
            return (StatusCode::INTERNAL_SERVER_ERROR, format!("CSV export error: {}", e)).into_response();
        }
        let data = std::fs::read(temp_file.path()).unwrap_or_default();
        let mut response = data.into_response();
        response.headers_mut().insert(
            header::CONTENT_TYPE,
            HeaderValue::from_static("text/csv"),
        );
        response.headers_mut().insert(
            header::CONTENT_DISPOSITION,
            HeaderValue::from_static("attachment; filename=\"report.csv\""),
        );
        response
    }
}

const INDEX_HTML: &str = r#"<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Upptagen Dashboard</title>
    <style>
        :root {
            --bg: #0f172a;
            --card-bg: #1e293b;
            --border: #334155;
            --text: #f8fafc;
            --text-muted: #94a3b8;
            --primary: #3b82f6;
            --primary-hover: #2563eb;
            --danger: #ef4444;
            --danger-hover: #dc2626;
            --success: #10b981;
            --accent: #8b5cf6;
        }

        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
        body { background-color: var(--bg); color: var(--text); padding: 2rem; }
        .container { max-width: 1100px; margin: 0 auto; display: flex; flex-direction: column; gap: 2rem; }
        
        header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 1rem; }
        h1 { font-size: 1.8rem; font-weight: 700; color: #fff; }

        .card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 8px; padding: 1.5rem; }
        .card-title { font-size: 1.2rem; font-weight: 600; margin-bottom: 1rem; color: #cbd5e1; }

        .timer-grid { display: grid; grid-template-columns: 1fr auto; gap: 1rem; align-items: center; }
        .input-group { display: flex; gap: 0.5rem; flex-wrap: wrap; }
        input, select { background: #0f172a; border: 1px solid var(--border); color: var(--text); padding: 0.6rem 0.9rem; border-radius: 6px; font-size: 0.95rem; }
        input:focus { outline: none; border-color: var(--primary); }
        
        button { cursor: pointer; border: none; padding: 0.6rem 1.2rem; border-radius: 6px; font-weight: 600; font-size: 0.95rem; transition: background 0.2s; }
        .btn-primary { background: var(--primary); color: white; }
        .btn-primary:hover { background: var(--primary-hover); }
        .btn-danger { background: var(--danger); color: white; }
        .btn-danger:hover { background: var(--danger-hover); }
        .btn-secondary { background: #475569; color: white; }
        .btn-secondary:hover { background: #334155; }

        .timer-display { font-size: 2.2rem; font-family: monospace; font-weight: bold; color: var(--success); text-align: right; }

        .filter-bar { display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center; }

        table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
        th, td { text-align: left; padding: 0.75rem; border-bottom: 1px solid var(--border); font-size: 0.95rem; }
        th { color: var(--text-muted); font-weight: 600; }
        tr:hover { background: rgba(255, 255, 255, 0.02); }

        .tag-badge { background: #334155; color: #38bdf8; padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.8rem; margin-right: 0.3rem; }

        .modal { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.6); align-items: center; justify-content: center; }
        .modal.active { display: flex; }
        .modal-content { background: var(--card-bg); border: 1px solid var(--border); padding: 1.5rem; border-radius: 8px; width: 400px; display: flex; flex-direction: column; gap: 1rem; }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>⏱️ Upptagen Dashboard</h1>
            <div>
                <button class="btn-secondary" onclick="openAddModal()">+ Add Manual Entry</button>
            </div>
        </header>

        <!-- Active Timer Card -->
        <div class="card">
            <div class="card-title">Active Timer</div>
            <div id="timer-active-sec" style="display: none;">
                <div class="timer-grid">
                    <div>
                        <div id="timer-info" style="font-size: 1.1rem; font-weight: 600;">-</div>
                        <div id="timer-tags" style="margin-top: 0.4rem;"></div>
                    </div>
                    <div>
                        <div id="timer-clock" class="timer-display">00:00:00</div>
                        <div style="text-align: right; margin-top: 0.5rem;">
                            <button class="btn-danger" onclick="stopTimer()">Stop Timer</button>
                        </div>
                    </div>
                </div>
            </div>

            <div id="timer-start-sec">
                <form onsubmit="startTimer(event)" class="timer-grid">
                    <div class="input-group">
                        <input type="text" id="start-project" placeholder="Project name" style="flex: 1; min-width: 140px;">
                        <input type="text" id="start-desc" placeholder="Task description" style="flex: 2; min-width: 200px;">
                        <input type="text" id="start-tags" placeholder="Tags (comma-separated)" style="flex: 1; min-width: 160px;">
                    </div>
                    <button type="submit" class="btn-primary">Start Timer</button>
                </form>
            </div>
        </div>

        <!-- Filter & Export Bar -->
        <div class="card">
            <div class="card-title">Filter & Export</div>
            <div class="filter-bar">
                <input type="text" id="filter-project" placeholder="Filter by Project" oninput="loadEntries()">
                <input type="text" id="filter-tag" placeholder="Filter by Tag" oninput="loadEntries()">
                <input type="date" id="filter-from" onchange="loadEntries()">
                <input type="date" id="filter-to" onchange="loadEntries()">
                <button class="btn-secondary" onclick="clearFilters()">Clear</button>
                <div style="margin-left: auto; display: flex; gap: 0.5rem;">
                    <button class="btn-primary" onclick="triggerExport('csv')">Export CSV</button>
                    <button class="btn-primary" onclick="triggerExport('pdf')">Export PDF</button>
                </div>
            </div>
        </div>

        <!-- Entries List -->
        <div class="card">
            <div class="card-title">Logged Entries</div>
            <table>
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Project</th>
                        <th>Description</th>
                        <th>Start Time</th>
                        <th>Duration</th>
                        <th>Tags</th>
                        <th>Action</th>
                    </tr>
                </thead>
                <tbody id="entries-tbody">
                    <!-- Populated dynamically -->
                </tbody>
            </table>
        </div>
    </div>

    <!-- Add Entry Modal -->
    <div id="add-modal" class="modal">
        <div class="modal-content">
            <div class="card-title">Add Retroactive Time Entry</div>
            <input type="text" id="add-proj" placeholder="Project">
            <input type="text" id="add-desc" placeholder="Description">
            <div>
                <label style="font-size:0.8rem; color:var(--text-muted)">Start Time</label>
                <input type="datetime-local" id="add-start" style="width:100%">
            </div>
            <div>
                <label style="font-size:0.8rem; color:var(--text-muted)">End Time</label>
                <input type="datetime-local" id="add-end" style="width:100%">
            </div>
            <input type="text" id="add-tags" placeholder="Tags (comma separated)">
            <div style="display:flex; justify-content:flex-end; gap:0.5rem; margin-top:0.5rem;">
                <button class="btn-secondary" onclick="closeAddModal()">Cancel</button>
                <button class="btn-primary" onclick="submitAddEntry()">Save Entry</button>
            </div>
        </div>
    </div>

    <!-- Edit Entry Modal -->
    <div id="edit-modal" class="modal">
        <div class="modal-content">
            <div class="card-title">Edit Time Entry</div>
            <input type="hidden" id="edit-id">
            <div>
                <label style="font-size:0.8rem; color:var(--text-muted)">Project</label>
                <input type="text" id="edit-proj" placeholder="Project" style="width:100%">
            </div>
            <div>
                <label style="font-size:0.8rem; color:var(--text-muted)">Description</label>
                <input type="text" id="edit-desc" placeholder="Description" style="width:100%">
            </div>
            <div>
                <label style="font-size:0.8rem; color:var(--text-muted)">Start Time</label>
                <input type="datetime-local" id="edit-start" style="width:100%">
            </div>
            <div>
                <label style="font-size:0.8rem; color:var(--text-muted)">End Time</label>
                <input type="datetime-local" id="edit-end" style="width:100%">
            </div>
            <div>
                <label style="font-size:0.8rem; color:var(--text-muted)">Tags (comma separated)</label>
                <input type="text" id="edit-tags" placeholder="Tags (comma separated)" style="width:100%">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:0.5rem; margin-top:0.5rem;">
                <button class="btn-secondary" onclick="closeEditModal()">Cancel</button>
                <button class="btn-primary" onclick="submitEditEntry()">Update Entry</button>
            </div>
        </div>
    </div>

    <script>
        let timerInterval = null;
        let timerStartTime = null;
        let loadedEntries = [];

        function formatDuration(secs) {
            const h = Math.floor(secs / 3600);
            const m = Math.floor((secs % 3600) / 60);
            const s = secs % 60;
            return [h, m, s].map(v => String(v).padStart(2, '0')).join(':');
        }

        async function checkTimer() {
            const res = await fetch('/api/timer');
            const timer = await res.json();
            if (timer) {
                document.getElementById('timer-active-sec').style.display = 'block';
                document.getElementById('timer-start-sec').style.display = 'none';
                document.getElementById('timer-info').innerText = `${timer.project || 'No Project'} - ${timer.description || 'No Description'}`;
                document.getElementById('timer-tags').innerHTML = (timer.tags || []).map(t => `<span class="tag-badge">${t}</span>`).join('');
                
                timerStartTime = new Date(timer.start_time).getTime();
                if (!timerInterval) {
                    timerInterval = setInterval(updateTimerClock, 1000);
                }
                updateTimerClock();
            } else {
                document.getElementById('timer-active-sec').style.display = 'none';
                document.getElementById('timer-start-sec').style.display = 'block';
                if (timerInterval) {
                    clearInterval(timerInterval);
                    timerInterval = null;
                }
            }
        }

        function updateTimerClock() {
            if (!timerStartTime) return;
            const now = new Date().getTime();
            const elapsedSecs = Math.max(0, Math.floor((now - timerStartTime) / 1000));
            document.getElementById('timer-clock').innerText = formatDuration(elapsedSecs);
        }

        async function startTimer(e) {
            e.preventDefault();
            const project = document.getElementById('start-project').value.trim();
            const description = document.getElementById('start-desc').value.trim();
            const tagsStr = document.getElementById('start-tags').value.trim();
            const tags = tagsStr ? tagsStr.split(',').map(s => s.trim()).filter(Boolean) : [];

            await fetch('/api/timer/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ project, description, tags })
            });

            document.getElementById('start-project').value = '';
            document.getElementById('start-desc').value = '';
            document.getElementById('start-tags').value = '';
            checkTimer();
        }

        async function stopTimer() {
            await fetch('/api/timer/stop', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({})
            });
            checkTimer();
            loadEntries();
        }

        async function loadEntries() {
            const proj = document.getElementById('filter-project').value;
            const tag = document.getElementById('filter-tag').value;
            const from = document.getElementById('filter-from').value;
            const to = document.getElementById('filter-to').value;

            const params = new URLSearchParams();
            if (proj) params.append('project', proj);
            if (tag) params.append('tag', tag);
            if (from) params.append('from', from);
            if (to) params.append('to', to);

            const res = await fetch(`/api/entries?${params.toString()}`);
            loadedEntries = await res.json();

            const tbody = document.getElementById('entries-tbody');
            tbody.innerHTML = '';

            loadedEntries.forEach(e => {
                const tr = document.createElement('tr');
                const startFormatted = new Date(e.start_time).toLocaleString();
                const tagsHtml = (e.tags || []).map(t => `<span class="tag-badge">${t}</span>`).join('');
                tr.innerHTML = `
                    <td>${e.id}</td>
                    <td><strong>${e.project || '-'}</strong></td>
                    <td>${e.description || '-'}</td>
                    <td>${startFormatted}</td>
                    <td>${formatDuration(e.duration_seconds)}</td>
                    <td>${tagsHtml}</td>
                    <td>
                        <button class="btn-primary" style="padding:0.3rem 0.6rem; font-size:0.8rem; margin-right: 0.3rem;" onclick="openEditModal(${e.id})">Edit</button>
                        <button class="btn-danger" style="padding:0.3rem 0.6rem; font-size:0.8rem" onclick="deleteEntry(${e.id})">Delete</button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        }

        async function deleteEntry(id) {
            if (!confirm(`Delete entry #${id}?`)) return;
            await fetch(`/api/entries/${id}`, { method: 'DELETE' });
            loadEntries();
        }

        function clearFilters() {
            document.getElementById('filter-project').value = '';
            document.getElementById('filter-tag').value = '';
            document.getElementById('filter-from').value = '';
            document.getElementById('filter-to').value = '';
            loadEntries();
        }

        function triggerExport(fmt) {
            const proj = document.getElementById('filter-project').value;
            const tag = document.getElementById('filter-tag').value;
            const from = document.getElementById('filter-from').value;
            const to = document.getElementById('filter-to').value;

            const params = new URLSearchParams({ format: fmt });
            if (proj) params.append('project', proj);
            if (tag) params.append('tag', tag);
            if (from) params.append('from', from);
            if (to) params.append('to', to);

            window.location.href = `/api/export?${params.toString()}`;
        }

        function openAddModal() {
            document.getElementById('add-modal').classList.add('active');
        }
        function closeAddModal() {
            document.getElementById('add-modal').classList.remove('active');
        }

        function formatForDatetimeLocal(isoStr) {
            if (!isoStr) return '';
            const d = new Date(isoStr);
            const pad = n => String(n).padStart(2, '0');
            return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
        }

        function openEditModal(id) {
            const entry = loadedEntries.find(e => e.id === id);
            if (!entry) return;
            document.getElementById('edit-id').value = entry.id;
            document.getElementById('edit-proj').value = entry.project || '';
            document.getElementById('edit-desc').value = entry.description || '';
            document.getElementById('edit-start').value = formatForDatetimeLocal(entry.start_time);
            document.getElementById('edit-end').value = formatForDatetimeLocal(entry.end_time);
            document.getElementById('edit-tags').value = (entry.tags || []).join(', ');
            document.getElementById('edit-modal').classList.add('active');
        }

        function closeEditModal() {
            document.getElementById('edit-modal').classList.remove('active');
        }

        async function submitEditEntry() {
            const id = document.getElementById('edit-id').value;
            const project = document.getElementById('edit-proj').value.trim();
            const description = document.getElementById('edit-desc').value.trim();
            const start = document.getElementById('edit-start').value;
            const end = document.getElementById('edit-end').value;
            const tagsStr = document.getElementById('edit-tags').value.trim();
            const tags = tagsStr ? tagsStr.split(',').map(s => s.trim()).filter(Boolean) : [];

            const res = await fetch(`/api/entries/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ project, description, start, end, tags })
            });

            if (res.ok) {
                closeEditModal();
                loadEntries();
            } else {
                const err = await res.text();
                alert('Error updating entry: ' + err);
            }
        }

        async function submitAddEntry() {
            const project = document.getElementById('add-proj').value.trim();
            const description = document.getElementById('add-desc').value.trim();
            const start = document.getElementById('add-start').value;
            const end = document.getElementById('add-end').value;
            const tagsStr = document.getElementById('add-tags').value.trim();
            const tags = tagsStr ? tagsStr.split(',').map(s => s.trim()).filter(Boolean) : [];

            if (!start || !end) {
                alert('Start and end times are required.');
                return;
            }

            const res = await fetch('/api/entries', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ start, end, project, description, tags })
            });

            if (res.ok) {
                closeAddModal();
                loadEntries();
            } else {
                const err = await res.text();
                alert('Error adding entry: ' + err);
            }
        }

        // Init
        checkTimer();
        loadEntries();
        setInterval(checkTimer, 5000);
    </script>
</body>
</html>
"#;

#[cfg(test)]
mod tests {
    use super::*;
    use axum::http::StatusCode;
    use reqwest::Client;
    use std::sync::{Arc, Mutex};

    #[tokio::test]
    async fn test_web_routes() {
        let db = Arc::new(Mutex::new(Database::open_in_memory().unwrap()));
        let app = create_router(db.clone());

        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();

        tokio::spawn(async move {
            axum::serve(listener, app).await.unwrap();
        });

        let client = Client::new();
        let base_url = format!("http://{}", addr);

        // 1. GET Dashboard HTML
        let res = client.get(&base_url).send().await.unwrap();
        assert_eq!(res.status(), StatusCode::OK);

        // 2. GET /api/timer
        let res = client.get(format!("{}/api/timer", base_url)).send().await.unwrap();
        assert_eq!(res.status(), StatusCode::OK);

        // 3. POST /api/timer/start
        let res = client
            .post(format!("{}/api/timer/start", base_url))
            .json(&serde_json::json!({
                "project": "Test Web",
                "description": "Running via API",
                "tags": ["web", "api"]
            }))
            .send()
            .await
            .unwrap();
        assert_eq!(res.status(), StatusCode::CREATED);

        // 4. POST /api/timer/stop
        let res = client
            .post(format!("{}/api/timer/stop", base_url))
            .json(&serde_json::json!({}))
            .send()
            .await
            .unwrap();
        assert_eq!(res.status(), StatusCode::OK);

        // 5. GET /api/entries
        let res = client.get(format!("{}/api/entries", base_url)).send().await.unwrap();
        assert_eq!(res.status(), StatusCode::OK);
        let entries: Vec<crate::models::TimeEntry> = res.json().await.unwrap();
        assert_eq!(entries.len(), 1);
        let entry_id = entries[0].id;

        // 5b. PUT /api/entries/:id
        let res = client
            .put(format!("{}/api/entries/{}", base_url, entry_id))
            .json(&serde_json::json!({
                "project": "Updated Web",
                "description": "Edited via PUT API",
                "start": "2025-01-01T10:00",
                "end": "2025-01-01T11:00",
                "tags": ["updated"]
            }))
            .send()
            .await
            .unwrap();
        assert_eq!(res.status(), StatusCode::OK);
        let updated: crate::models::TimeEntry = res.json().await.unwrap();
        assert_eq!(updated.project.as_deref(), Some("Updated Web"));
        assert_eq!(updated.duration_seconds, 3600);

        // 6. GET /api/export
        let res = client
            .get(format!("{}/api/export?format=csv", base_url))
            .send()
            .await
            .unwrap();
        assert_eq!(res.status(), StatusCode::OK);
        let csv_text = res.text().await.unwrap();
        assert!(csv_text.contains("Updated Web"));
    }
}
