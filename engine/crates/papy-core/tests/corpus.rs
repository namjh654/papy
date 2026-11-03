//! 실제 문서 모음 점검(기본 실행에서 빠진다):
//!
//!   PAPY_CORPUS=/문서/폴더 cargo test -p papy-core --test corpus -- --ignored --nocapture
//!
//! 폴더 안의 PDF·HWP·HWPX·DOCX 를 모두 열어 모든 쪽을 그리고(1배) 글을 꺼낸다. 문서마다 시간·쪽 수를 출력하고,
//! 열기 실패(암호·손상 제외)나 그리기·글 꺼내기 실패, 패닉이 하나라도 있으면 실패한다. 문서 내용은 출력하지 않는다.
//! PDF 는 `PAPY_PDFIUM_LIBRARY`(PDFium 라이브러리 경로)이 있을 때만 연다.

use std::path::PathBuf;
use std::time::Instant;

use papy_core::{DocumentSource, Engine, EngineResources, ErrorCode, Limits, OpenOptions};
use papy_render::RenderRequest;

#[test]
#[ignore = "PAPY_CORPUS 폴더가 있을 때만"]
fn corpus_opens_renders_and_extracts() {
    let Some(dir) = std::env::var_os("PAPY_CORPUS").map(PathBuf::from) else {
        eprintln!("PAPY_CORPUS 가 없어 건너뜀");
        return;
    };
    let engine = Engine::with_resources(
        Limits::default(),
        EngineResources {
            pdfium_library: std::env::var_os("PAPY_PDFIUM_LIBRARY")
                .map(PathBuf::from)
                .unwrap_or_default(),
            korean_font: None,
        },
    );
    let mut files: Vec<PathBuf> = walk(&dir)
        .into_iter()
        .filter(|path| {
            path.extension()
                .and_then(|ext| ext.to_str())
                .is_some_and(|ext| {
                    matches!(
                        ext.to_ascii_lowercase().as_str(),
                        "pdf" | "hwp" | "hwpx" | "docx"
                    )
                })
        })
        .collect();
    files.sort();
    let mut failures = Vec::new();
    let mut total_pages = 0;
    let started = Instant::now();
    for path in &files {
        let name = path
            .strip_prefix(&dir)
            .unwrap_or(path)
            .display()
            .to_string();
        let t0 = Instant::now();
        let id = match engine.open(DocumentSource::Path(path.clone()), OpenOptions::default()) {
            Ok(id) => id,
            Err(error) if matches!(error.code, ErrorCode::PasswordRequired) => {
                eprintln!("{name}: 암호 문서 — 건너뜀");
                continue;
            }
            Err(error) if error.code == ErrorCode::Internal && error.message.contains("PDFium") => {
                eprintln!("{name}: PDFium 없음 — 건너뜀");
                continue;
            }
            Err(error) => {
                failures.push(format!("{name}: 열기 실패 {:?}", error.code));
                continue;
            }
        };
        let opened = t0.elapsed();
        let info = engine.info(id).unwrap();
        let t1 = Instant::now();
        let mut slowest = std::time::Duration::ZERO;
        for page in 0..info.page_count {
            let t = Instant::now();
            if let Err(error) = engine.render_page(id, page, &RenderRequest::with_scale(1.0)) {
                failures.push(format!("{name}: {page}쪽 그리기 실패 {:?}", error.code));
            }
            slowest = slowest.max(t.elapsed());
            if let Err(error) = engine.extract_text(id, page) {
                failures.push(format!("{name}: {page}쪽 글 꺼내기 실패 {:?}", error.code));
            }
        }
        total_pages += info.page_count;
        eprintln!(
            "{name}: {} {}쪽 · 열기 {} ms · 전체 그리기 {} ms (가장 느린 쪽 {} ms){}",
            info.format.name(),
            info.page_count,
            opened.as_millis(),
            t1.elapsed().as_millis(),
            slowest.as_millis(),
            if info.dropped_objects > 0 {
                format!(" · 못 옮긴 요소 {}", info.dropped_objects)
            } else {
                String::new()
            },
        );
        let _ = engine.close(id);
    }
    eprintln!(
        "문서 {}개 · {total_pages}쪽 · {} ms",
        files.len(),
        started.elapsed().as_millis()
    );
    assert!(
        failures.is_empty(),
        "실패 {}건:\n{}",
        failures.len(),
        failures.join("\n")
    );
}

fn walk(dir: &std::path::Path) -> Vec<PathBuf> {
    let mut out = Vec::new();
    let Ok(entries) = std::fs::read_dir(dir) else {
        return out;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            out.extend(walk(&path));
        } else {
            out.push(path);
        }
    }
    out
}
