//! 통합 테스트가 같이 쓰는 도우미: 시험 문서 만들기, PDFium 찾기, 망가뜨린 문서 열어 보기.
#![allow(dead_code)] // 테스트 파일마다 쓰는 도우미가 다르다.

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::{Path, PathBuf};

use papy_core::{
    DocumentSource, Engine, EngineResources, Limits, OpenOptions, SaveFormat, TextPos,
};
use papy_render::RenderRequest;

/// PDFium 이 있으면 PDF 도 보는 엔진.
pub fn engine() -> (Engine, bool) {
    let pdfium = pdfium();
    let has_pdf = pdfium.is_some();
    let engine = Engine::with_resources(
        Limits::default(),
        EngineResources {
            pdfium_library: pdfium.unwrap_or_default(),
            korean_font: None,
        },
    );
    (engine, has_pdf)
}

/// 망가졌을 수 있는 문서를 열고, 열리면 첫 쪽 그리기·글 꺼내기·목차·찾기까지 해 본다.
/// 패닉이면 `Err("패닉")`, 엔진 상태가 이상하면(정보·닫기 실패) 그 내용. 열리지 않은 것은 `Ok(false)`.
pub fn exercise(engine: &Engine, data: Vec<u8>) -> Result<bool, String> {
    catch_unwind(AssertUnwindSafe(|| -> Result<bool, String> {
        let Ok(id) = engine.open(
            DocumentSource::Bytes(data.into()),
            OpenOptions {
                rewrite_same_format: true,
                ..OpenOptions::default()
            },
        ) else {
            return Ok(false);
        };
        let info = engine.info(id).map_err(|e| format!("정보 {:?}", e.code))?;
        if info.page_count > 0 {
            // 열렸으면 그리기·글 꺼내기도 패닉 없이 끝나야 한다(오류는 괜찮다).
            let _ = engine.render_page(id, 0, &RenderRequest::with_scale(0.5));
            let _ = engine.extract_text(id, 0);
            let _ = engine.unsupported_objects(id, 0);
        }
        let _ = engine.outline(id);
        engine.close(id).map_err(|e| format!("닫기 {:?}", e.code))?;
        Ok(true)
    }))
    .unwrap_or_else(|_| Err("패닉".into()))
}

pub fn blank_hwp() -> Vec<u8> {
    let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../vendor/rhwp/saved/blank2010.hwp");
    std::fs::read(&path).unwrap_or_else(|_| {
        panic!(
            "{} 가 없다. 먼저 `node scripts/fetch-rhwp.mjs`",
            path.display()
        )
    })
}

pub fn pdfium() -> Option<PathBuf> {
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
pub fn samples(engine: &Engine, with_pdf: bool) -> Vec<(&'static str, Vec<u8>)> {
    let id = engine
        .open(
            DocumentSource::Bytes(blank_hwp().into()),
            OpenOptions {
                rewrite_same_format: true,
                ..OpenOptions::default()
            },
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
