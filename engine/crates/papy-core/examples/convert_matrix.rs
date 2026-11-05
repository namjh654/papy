//! 형식 변환 점검: 문서마다 DOCX·HWP·HWPX·PDF 로 저장하고 다시 열어 쪽 수와 글(빈칸 뺀 글자 빈도)이 얼마나 남는지 본다.
//!
//!   cargo run --release -p papy-core --example convert_matrix -- <문서 경로 목록 파일> [대상,…] > 결과.tsv
//!
//! 대상 기본값은 `docx,hwp,hwpx,pdf`. PDF 는 `PAPY_PDFIUM_LIBRARY` 가 있어야 다시 열린다.
//! 출력 열: 파일 · 원본 쪽 수 · 대상 · 결과(ok 또는 오류) · 다시 연 쪽 수 · 글 보존율(0~1) · 저장 ms.

use std::collections::HashMap;
use std::path::PathBuf;
use std::time::Instant;

use papy_core::{
    DocumentSource, Engine, EngineResources, Limits, OpenOptions, SaveFormat, SessionId,
};

fn text_of(engine: &Engine, id: SessionId) -> String {
    let pages = engine.page_count(id).unwrap_or(0);
    let mut text = String::new();
    for page in 0..pages {
        text.push_str(&engine.extract_text(id, page).unwrap_or_default());
    }
    text.chars().filter(|c| !c.is_whitespace()).collect()
}

/// 원문 글자(빈도 포함) 가운데 결과에 남은 비율.
fn keep_ratio(source: &str, result: &str) -> f64 {
    if source.is_empty() {
        return 1.0;
    }
    let mut left: HashMap<char, i64> = HashMap::new();
    for c in result.chars() {
        *left.entry(c).or_default() += 1;
    }
    let mut hit = 0usize;
    for c in source.chars() {
        if let Some(n) = left.get_mut(&c).filter(|n| **n > 0) {
            *n -= 1;
            hit += 1;
        }
    }
    hit as f64 / source.chars().count() as f64
}

fn main() {
    let mut args = std::env::args().skip(1);
    let list = std::fs::read_to_string(args.next().expect("문서 경로 목록 파일")).expect("목록");
    let wanted = args.next().unwrap_or_else(|| "docx,hwp,hwpx,pdf".into());
    let targets: Vec<(&str, SaveFormat)> = [
        ("docx", SaveFormat::Docx),
        ("hwp", SaveFormat::Hwp),
        ("hwpx", SaveFormat::Hwpx),
        ("pdf", SaveFormat::Pdf),
    ]
    .into_iter()
    .filter(|(name, _)| wanted.split(',').any(|w| w == *name))
    .collect();
    let engine = Engine::with_resources(
        Limits::default(),
        EngineResources {
            pdfium_library: std::env::var_os("PAPY_PDFIUM_LIBRARY")
                .map(PathBuf::from)
                .unwrap_or_default(),
            korean_font: None,
        },
    );
    println!("file\tsrc_pages\ttarget\tresult\tpages\tkeep\tms");
    for path in list.lines().map(str::trim).filter(|l| !l.is_empty()) {
        let name = path.rsplit('/').next().unwrap_or(path);
        let Ok(id) = engine.open(
            DocumentSource::Path(PathBuf::from(path)),
            OpenOptions::default(),
        ) else {
            println!("{name}\t-\t-\topen_fail\t-\t-\t-");
            continue;
        };
        let pages = engine.page_count(id).unwrap_or(0);
        let source = text_of(&engine, id);
        for &(target, format) in &targets {
            let started = Instant::now();
            let saved = engine.save(id, format);
            let ms = started.elapsed().as_millis();
            let bytes = match saved {
                Ok(bytes) => bytes,
                Err(e) => {
                    println!("{name}\t{pages}\t{target}\tsave:{:?}\t-\t-\t{ms}", e.code);
                    continue;
                }
            };
            match engine.open(DocumentSource::Bytes(bytes.into()), OpenOptions::default()) {
                Ok(reopened) => {
                    let result_pages = engine.page_count(reopened).unwrap_or(0);
                    let keep = keep_ratio(&source, &text_of(&engine, reopened));
                    println!("{name}\t{pages}\t{target}\tok\t{result_pages}\t{keep:.3}\t{ms}");
                    let _ = engine.close(reopened);
                }
                Err(e) => println!("{name}\t{pages}\t{target}\treopen:{:?}\t-\t-\t{ms}", e.code),
            }
        }
        let _ = engine.close(id);
    }
}
