use crate::models::TimeEntry;
use crate::utils::{format_duration, format_local_datetime};
use std::collections::HashMap;
use std::fs;
use std::path::Path;
use std::process::Command;

pub fn escape_latex(text: &str) -> String {
    text.chars()
        .map(|c| match c {
            '\\' => "\\textbackslash{}".to_string(),
            '&' => "\\&".to_string(),
            '%' => "\\%".to_string(),
            '$' => "\\$".to_string(),
            '#' => "\\#".to_string(),
            '_' => "\\_".to_string(),
            '{' => "\\{".to_string(),
            '}' => "\\}".to_string(),
            '~' => "\\textasciitilde{}".to_string(),
            '^' => "\\textasciicircum{}".to_string(),
            _ => c.to_string(),
        })
        .collect()
}

pub fn generate_latex_source(entries: &[TimeEntry]) -> String {
    let mut total_duration: i64 = 0;
    let mut project_durations: HashMap<String, i64> = HashMap::new();
    let mut tag_durations: HashMap<String, i64> = HashMap::new();

    for entry in entries {
        total_duration += entry.duration_seconds;
        let proj = entry
            .project
            .clone()
            .unwrap_or_else(|| "Unassigned".to_string());
        *project_durations.entry(proj).or_insert(0) += entry.duration_seconds;

        for tag in &entry.tags {
            *tag_durations.entry(tag.clone()).or_insert(0) += entry.duration_seconds;
        }
    }

    let mut tex = String::new();
    tex.push_str("\\documentclass[11pt,a4paper]{article}\n");
    tex.push_str("\\usepackage[utf8]{inputenc}\n");
    tex.push_str("\\usepackage[margin=1in]{geometry}\n");
    tex.push_str("\\usepackage{booktabs}\n");
    tex.push_str("\\usepackage{longtable}\n");
    tex.push_str("\\usepackage{hyperref}\n");
    tex.push_str("\\title{Time Tracking Report}\n");
    tex.push_str("\\author{upptagen}\n");
    tex.push_str("\\date{\\today}\n");
    tex.push_str("\\begin{document}\n");
    tex.push_str("\\maketitle\n\n");

    // Overview section
    tex.push_str("\\section*{Overview}\n");
    tex.push_str("\\begin{itemize}\n");
    tex.push_str(&format!(
        "  \\item \\textbf{{Total Logged Entries:}} {}\n",
        entries.len()
    ));
    tex.push_str(&format!(
        "  \\item \\textbf{{Total Duration:}} {}\n",
        escape_latex(&format_duration(total_duration))
    ));
    tex.push_str("\\end{itemize}\n\n");

    // Project summary table
    if !project_durations.is_empty() {
        tex.push_str("\\subsection*{Summary by Project}\n");
        tex.push_str("\\begin{tabular}{lr}\n");
        tex.push_str("\\toprule\n");
        tex.push_str("\\textbf{Project} & \\textbf{Total Duration} \\\\\n");
        tex.push_str("\\midrule\n");

        let mut proj_list: Vec<_> = project_durations.into_iter().collect();
        proj_list.sort_by_key(|item| std::cmp::Reverse(item.1));

        for (proj, dur) in proj_list {
            tex.push_str(&format!(
                "{} & {} \\\\\n",
                escape_latex(&proj),
                escape_latex(&format_duration(dur))
            ));
        }
        tex.push_str("\\bottomrule\n");
        tex.push_str("\\end{tabular}\n\n");
    }

    // Tag summary table
    if !tag_durations.is_empty() {
        tex.push_str("\\subsection*{Summary by Tag}\n");
        tex.push_str("\\begin{tabular}{lr}\n");
        tex.push_str("\\toprule\n");
        tex.push_str("\\textbf{Tag} & \\textbf{Total Duration} \\\\\n");
        tex.push_str("\\midrule\n");

        let mut tag_list: Vec<_> = tag_durations.into_iter().collect();
        tag_list.sort_by_key(|item| std::cmp::Reverse(item.1));

        for (tag, dur) in tag_list {
            tex.push_str(&format!(
                "{} & {} \\\\\n",
                escape_latex(&tag),
                escape_latex(&format_duration(dur))
            ));
        }
        tex.push_str("\\bottomrule\n");
        tex.push_str("\\end{tabular}\n\n");
    }

    // Detailed Log Table
    tex.push_str("\\section*{Detailed Time Log}\n");
    tex.push_str("\\begin{longtable}{r l l l r l}\n");
    tex.push_str("\\toprule\n");
    tex.push_str(
        "\\textbf{ID} & \\textbf{Project} & \\textbf{Description} & \\textbf{Start} & \\textbf{Duration} & \\textbf{Tags} \\\\\n",
    );
    tex.push_str("\\midrule\n");
    tex.push_str("\\endhead\n");

    for entry in entries {
        let proj = entry.project.as_deref().unwrap_or("-");
        let desc = entry.description.as_deref().unwrap_or("-");
        let start_fmt = format_local_datetime(&entry.start_time);
        let dur_fmt = format_duration(entry.duration_seconds);
        let tags_fmt = if entry.tags.is_empty() {
            "-".to_string()
        } else {
            entry.tags.join(", ")
        };

        tex.push_str(&format!(
            "{} & {} & {} & {} & {} & {} \\\\\n",
            entry.id,
            escape_latex(proj),
            escape_latex(desc),
            escape_latex(&start_fmt),
            escape_latex(&dur_fmt),
            escape_latex(&tags_fmt)
        ));
    }

    tex.push_str("\\bottomrule\n");
    tex.push_str("\\end{longtable}\n");
    tex.push_str("\\end{document}\n");

    tex
}

pub fn export_pdf<P: AsRef<Path>>(entries: &[TimeEntry], output_path: P) -> Result<(), String> {
    let output_path = output_path.as_ref();

    let temp_dir = tempfile::tempdir().map_err(|e| format!("Failed to create temp dir: {}", e))?;
    let tex_file_path = temp_dir.path().join("report.tex");

    let tex_source = generate_latex_source(entries);
    fs::write(&tex_file_path, tex_source)
        .map_err(|e| format!("Failed to write LaTeX source: {}", e))?;

    let output_dir = temp_dir.path();

    let status = Command::new("pdflatex")
        .arg("-interaction=nonstopmode")
        .arg(format!("-output-directory={}", output_dir.display()))
        .arg(&tex_file_path)
        .status()
        .map_err(|e| {
            format!(
                "Failed to execute pdflatex: {}. Ensure LaTeX is installed.",
                e
            )
        })?;

    if !status.success() {
        let log_file = output_dir.join("report.log");
        let log_content = fs::read_to_string(log_file).unwrap_or_default();
        return Err(format!(
            "LaTeX compilation failed with exit status {}.\nLogs:\n{}",
            status, log_content
        ));
    }

    let generated_pdf = output_dir.join("report.pdf");
    if !generated_pdf.exists() {
        return Err("pdflatex completed but PDF file was not generated.".into());
    }

    if let Some(parent) = output_path.parent() {
        if !parent.as_os_str().is_empty() {
            let _ = fs::create_dir_all(parent);
        }
    }

    fs::copy(&generated_pdf, output_path)
        .map_err(|e| format!("Failed to copy generated PDF to destination: {}", e))?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::TimeEntry;
    use chrono::Utc;
    use tempfile::NamedTempFile;

    #[test]
    fn test_escape_latex() {
        assert_eq!(escape_latex("100% & $5 #_ {} ~ ^ \\"), "100\\% \\& \\$5 \\#\\_ \\{\\} \\textasciitilde{} \\textasciicircum{} \\textbackslash{}");
    }

    #[test]
    fn test_export_pdf_compilation() {
        if Command::new("pdflatex").arg("--version").output().is_err() {
            println!("pdflatex not found; skipping test_export_pdf_compilation");
            return;
        }

        let temp_pdf = NamedTempFile::new().unwrap();
        let path = temp_pdf.path();

        let entries = vec![TimeEntry {
            id: 1,
            project: Some("Special & Project".into()),
            description: Some("100% completed task #1".into()),
            start_time: Utc::now(),
            end_time: Utc::now(),
            duration_seconds: 1800,
            tags: vec!["rust_lang".into(), "pdf".into()],
        }];

        let result = export_pdf(&entries, path);
        assert!(result.is_ok(), "PDF generation failed: {:?}", result.err());
        assert!(path.metadata().unwrap().len() > 0);
    }
}
