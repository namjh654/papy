//! 손상 파일 회귀 점검(기본 `cargo test` 에서 돈다).
//!
//! 엔진으로 HWP·HWPX·DOCX·PDF 문서를 만든 뒤 여러 방식으로 망가뜨려(앞부분만 남기기, 바이트 뒤집기, 0 으로 덮기)
//! 연다. 어떤 입력이든 패닉 없이 정해진 오류 코드로 끝나거나, 열리면 첫 쪽을 그리고 글을 꺼낼 수 있어야 한다.
//! 문서 하나는 시간 상한 안에 끝나야 한다(무한 반복 방지). PDF 는 PDFium 이 있을 때만 본다.

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use papy_core::{
    DocumentSource, Engine, EngineResources, Limits, OpenOptions, SaveFormat, TextPos,
};
use papy_render::RenderRequest;

/// 망가뜨린 문서 하나에 허용하는 시간. 디버그 빌드 기준으로 넉넉하게 잡는다.
const PER_CASE: Duration = Duration::from_secs(20);

fn blank_hwp() -> Vec<u8> {
    let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../vendor/rhwp/saved/blank2010.hwp");
    std::fs::read(&path).unwrap_or_else(|_| {
        panic!(
            "{} 가 없다. 먼저 `node scripts/fetch-rhwp.mjs`",
            path.display()
        )
    })
}

fn pdfium() -> Option<PathBuf> {
    if let Some(path) = std::env::var_os("PAPY_PDFIUM_LIBRARY") {
        return Some(PathBuf::from(path));
    }
    let target = match (std::env::consts::ARCH, std::env::consts::OS) {
        ("aarch64", "macos") => "aarch64-apple-darwin",
        ("x86_64", "macos") => "x86_64-apple-darwin",
        ("x86_64", "windows") => "x86_64-pc-windows-msvc",
        ("aarch64", "windows") => "aarch64-pc-windows-msvc",
        ("x86_64", "linux") => "x86_64-unknown-linux-gnu",
        ("aarch64", "linux") => "aarch64-unknown-linux-gnu",
        _ => return None,
    };
    let path = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../vendor/pdfium")
        .join(target)
        .join(papy_core::pdfium_library_file_name());
    path.exists().then_some(path)
}

/// 글 두 문단과 표 하나가 든 문서를 각 형식으로 저장한다.
fn samples(engine: &Engine, with_pdf: bool) -> Vec<(&'static str, Vec<u8>)> {
    let id = engine
        .open(
            DocumentSource::Bytes(blank_hwp().into()),
            OpenOptions::default(),
        )
        .unwrap();
    engine
        .edit(id, |doc| {
            let start = TextPos::default();
            doc.insert_text(start, "Papy 손상 파일 점검\n둘째 문단 — 표 앞")?;
            doc.insert_table(
                TextPos {
                    paragraph: 1,
                    ..start
                },
                2,
                3,
            )
        })
        .unwrap();
    let mut out = vec![
        ("hwp", engine.save(id, SaveFormat::Hwp).unwrap()),
        ("hwpx", engine.save(id, SaveFormat::Hwpx).unwrap()),
        ("docx", engine.save(id, SaveFormat::Docx).unwrap()),
    ];
    if with_pdf {
        out.push(("pdf", engine.save(id, SaveFormat::Pdf).unwrap()));
    }
    engine.close(id).unwrap();
    out
}

/// 바이트열을 망가뜨리는 방법들. 같은 입력이면 늘 같은 결과(재현 가능).
fn mutations(bytes: &[u8]) -> Vec<(String, Vec<u8>)> {
    let len = bytes.len();
    let mut out = Vec::new();
    // 앞부분만 남기기: 헤더 근처는 촘촘히, 나머지는 고르게.
    let mut cuts: Vec<usize> = vec![0, 1, 4, 8, 16, 31, 64, 100, 512, 513];
    cuts.extend((1..16).map(|i| len * i / 16));
    cuts.push(len.saturating_sub(1));
    cuts.sort_unstable();
    cuts.dedup();
    for cut in cuts.into_iter().filter(|&c| c < len) {
        out.push((format!("앞 {cut}바이트만"), bytes[..cut].to_vec()));
    }
    // 바이트 뒤집기: 의사 난수(xorshift) 자리 여러 군데.
    let mut seed = 0x9e37_79b9_7f4a_7c15_u64 ^ len as u64;
    for round in 0..12 {
        let mut data = bytes.to_vec();
        for _ in 0..(1 + round * 4) {
            seed ^= seed << 13;
            seed ^= seed >> 7;
            seed ^= seed << 17;
            let at = (seed % len as u64) as usize;
            data[at] ^= (seed >> 32) as u8 | 1;
        }
        out.push((format!("바이트 뒤집기 {round}"), data));
    }
    // 가운데 한 덩어리를 0 으로 덮기(압축 스트림·레코드 길이 손상).
    for i in 1..4 {
        let mut data = bytes.to_vec();
        let start = len * i / 4;
        let end = (start + 256).min(len);
        data[start..end].fill(0);
        out.push((format!("{}/4 지점 0 덮기", i), data));
    }
    // 뒤에 쓰레기 붙이기.
    let mut data = bytes.to_vec();
    data.extend(std::iter::repeat_n(0xAB, 4096));
    out.push(("뒤에 쓰레기".into(), data));
    out
}

#[test]
fn damaged_documents_fail_cleanly() {
    let pdfium = pdfium();
    let engine = Engine::with_resources(
        Limits::default(),
        EngineResources {
            pdfium_library: pdfium.clone().unwrap_or_default(),
            korean_font: None,
        },
    );
    let mut failures = Vec::new();
    let mut opened = 0;
    let mut cases = 0;
    for (format, bytes) in samples(&engine, pdfium.is_some()) {
        for (how, data) in mutations(&bytes) {
            cases += 1;
            let started = Instant::now();
            let result = catch_unwind(AssertUnwindSafe(|| -> Result<bool, String> {
                let Ok(id) =
                    engine.open(DocumentSource::Bytes(data.into()), OpenOptions::default())
                else {
                    return Ok(false);
                };
                let info = engine.info(id).map_err(|e| format!("정보 {:?}", e.code))?;
                if info.page_count > 0 {
                    // 열렸으면 그리기·글 꺼내기도 패닉 없이 끝나야 한다(오류는 괜찮다).
                    let _ = engine.render_page(id, 0, &RenderRequest::with_scale(0.5));
                    let _ = engine.extract_text(id, 0);
                }
                engine.close(id).map_err(|e| format!("닫기 {:?}", e.code))?;
                Ok(true)
            }));
            match result {
                Ok(Ok(was_opened)) => opened += usize::from(was_opened),
                Ok(Err(message)) => failures.push(format!("{format} {how}: {message}")),
                Err(_) => failures.push(format!("{format} {how}: 패닉")),
            }
            let took = started.elapsed();
            if took > PER_CASE {
                failures.push(format!("{format} {how}: {} ms 걸림", took.as_millis()));
            }
        }
    }
    eprintln!("손상 문서 {cases}개 중 {opened}개는 복구해서 열림");
    assert_eq!(engine.session_count(), 0, "닫히지 않은 문서");
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}
