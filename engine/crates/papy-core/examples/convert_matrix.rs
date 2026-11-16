//! 형식 변환 점검: 문서마다 DOCX·HWP·HWPX·PDF 로 저장하고 다시 열어 쪽 수와 글(빈칸 뺀 글자 빈도)이 얼마나 남는지 본다.
//!
//!   cargo run --release -p papy-core --example convert_matrix -- <문서 경로 목록 파일> [대상,…] > 결과.tsv
//!
//! 대상 기본값은 `docx,hwp,hwpx,pdf`. PDF 는 `PAPY_PDFIUM_LIBRARY` 가 있어야 다시 열린다.
//! `PAPY_SOFFICE=<LibreOffice soffice 경로>` 면 저장한 DOCX 를 LibreOffice 로 PDF 로 바꿔(Word 와 비슷한 조판)
//! 쪽 수·글 보존율을 `docx@lo` 줄로 더 적는다(Papy 로 다시 연 값과 별도. `PAPY_PDFIUM_LIBRARY` 필요).
//! `PAPY_SOFFICE_FONTS=<글꼴 폴더>` 는 LibreOffice 가 쓸 글꼴(따로 만든 프로필에 넣는다, 한글 글꼴이 없는 환경용).
//! `PAPY_SAVE_DIR=<폴더>` 면 저장한 파일을 `<문서 이름>.<대상>` 으로 남긴다(Word 등 다른 프로그램으로 따로 잴 때).
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
            korean_font: std::env::var_os("PAPY_KOREAN_FONT").map(PathBuf::from),
        },
    );
    println!("file\tsrc_pages\ttarget\tresult\tpages\tkeep\tms");
    // LibreOffice 로 바꿀 DOCX: (파일 이름, 원본 쪽 수, 원본 글, 임시 파일)
    let soffice = std::env::var_os("PAPY_SOFFICE").map(PathBuf::from);
    let lo_dir = std::env::temp_dir().join(format!("papy-lo-{}", std::process::id()));
    let mut lo_jobs: Vec<(String, u32, String, PathBuf)> = Vec::new();
    if soffice.is_some() {
        let _ = std::fs::create_dir_all(&lo_dir);
    }
    let save_dir = std::env::var_os("PAPY_SAVE_DIR").map(PathBuf::from);
    if let Some(dir) = &save_dir {
        let _ = std::fs::create_dir_all(dir);
    }
    for path in list.lines().map(str::trim).filter(|l| !l.is_empty()) {
        let name = path.rsplit(['/', '\\']).next().unwrap_or(path);
        let Ok(id) = engine.open(
            DocumentSource::Path(PathBuf::from(path)),
            OpenOptions {
                rewrite_same_format: true,
                ..OpenOptions::default()
            },
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
                Ok(bytes) => {
                    if let Some(dir) = &save_dir {
                        let _ = std::fs::write(dir.join(format!("{name}.{target}")), &bytes);
                    }
                    if target == "docx" && soffice.is_some() {
                        let file = lo_dir.join(format!("{}.docx", lo_jobs.len()));
                        if std::fs::write(&file, &bytes).is_ok() {
                            lo_jobs.push((name.to_string(), pages, source.clone(), file));
                        }
                    }
                    bytes
                }
                Err(e) => {
                    println!("{name}\t{pages}\t{target}\tsave:{:?}\t-\t-\t{ms}", e.code);
                    continue;
                }
            };
            match engine.open(
                DocumentSource::Bytes(bytes.into()),
                OpenOptions {
                    rewrite_same_format: true,
                    ..OpenOptions::default()
                },
            ) {
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
    if let (Some(soffice), false) = (soffice, lo_jobs.is_empty()) {
        // 한 번에 바꾼다(문서마다 LibreOffice 를 띄우면 느리다).
        // 한글 글꼴을 찾지 못하는 LibreOffice 가 있어(macOS 등) 따로 쓰는 프로필 글꼴 폴더에 글꼴을 넣는다.
        let profile = lo_dir.join("profile");
        if let Some(fonts) = std::env::var_os("PAPY_SOFFICE_FONTS") {
            let dir = profile.join("user").join("fonts");
            let _ = std::fs::create_dir_all(&dir);
            for entry in std::fs::read_dir(fonts).into_iter().flatten().flatten() {
                let _ = std::fs::copy(entry.path(), dir.join(entry.file_name()));
            }
        }
        // 문서마다 따로 바꾸고 60초가 넘으면 멈춘다(LibreOffice 가 멈추는 문서가 있다).
        let mut times = Vec::new();
        for job in &lo_jobs {
            let started = Instant::now();
            let child = std::process::Command::new(&soffice)
                .arg(format!(
                    "-env:UserInstallation=file://{}",
                    profile.display()
                ))
                .args(["--headless", "--convert-to", "pdf", "--outdir"])
                .arg(&lo_dir)
                .arg(&job.3)
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .spawn();
            if let Ok(mut child) = child {
                while child.try_wait().ok().flatten().is_none() {
                    if started.elapsed().as_secs() > 60 {
                        let _ = child.kill();
                        let _ = child.wait();
                        break;
                    }
                    std::thread::sleep(std::time::Duration::from_millis(100));
                }
            }
            times.push(started.elapsed().as_millis());
        }
        for ((name, pages, source, file), ms) in lo_jobs.iter().zip(times) {
            let pdf = file.with_extension("pdf");
            let result = match std::fs::read(&pdf) {
                Ok(bytes) => {
                    match engine.open(
                        DocumentSource::Bytes(bytes.into()),
                        OpenOptions {
                            rewrite_same_format: true,
                            ..OpenOptions::default()
                        },
                    ) {
                        Ok(id) => {
                            let lo_pages = engine.page_count(id).unwrap_or(0);
                            let keep = keep_ratio(source, &text_of(&engine, id));
                            let _ = engine.close(id);
                            format!("ok\t{lo_pages}\t{keep:.3}")
                        }
                        Err(e) => format!("reopen:{:?}\t-\t-", e.code),
                    }
                }
                _ => "convert_fail\t-\t-".to_string(),
            };
            println!("{name}\t{pages}\tdocx@lo\t{result}\t{ms}");
        }
        let _ = std::fs::remove_dir_all(&lo_dir);
    }
}
