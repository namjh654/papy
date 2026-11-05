//! 문서 한 쪽을 BMP 로 그린다(눈으로 확인하는 개발 도구).
//!
//!   cargo run --release -p papy-core --example render_page -- <문서> <쪽(0부터)> <출력.bmp> [배율]
//!
//! PDF 는 `PAPY_PDFIUM_LIBRARY` 에 PDFium 라이브러리 경로를 준다. `PAPY_TEXT=1` 이면 그 쪽 글도 찍는다.

use std::path::PathBuf;

use papy_core::{DocumentSource, Engine, EngineResources, Limits, OpenOptions, RenderRequest};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 4 {
        eprintln!("사용법: render_page <문서> <쪽> <출력.bmp> [배율]");
        std::process::exit(2);
    }
    let page: u32 = args[2].parse().expect("쪽 번호");
    let scale: f32 = args.get(4).and_then(|s| s.parse().ok()).unwrap_or(1.0);
    let engine = Engine::with_resources(
        Limits::default(),
        EngineResources {
            pdfium_library: std::env::var_os("PAPY_PDFIUM_LIBRARY")
                .map(PathBuf::from)
                .unwrap_or_default(),
            korean_font: None,
        },
    );
    let id = engine
        .open(
            DocumentSource::Path(PathBuf::from(&args[1])),
            OpenOptions::default(),
        )
        .expect("열기");
    let info = engine.info(id).expect("정보");
    eprintln!(
        "{} · {}쪽 · 못 옮긴 개체 {}",
        info.format.name(),
        info.page_count,
        info.dropped_objects
    );
    if std::env::var_os("PAPY_TEXT").is_some() {
        println!("{}", engine.extract_text(id, page).expect("글 꺼내기"));
    }
    let bitmap = engine
        .render_page(id, page, &RenderRequest::with_scale(scale))
        .expect("그리기");
    let (w, h) = (bitmap.width, bitmap.height);
    let mut bmp = Vec::with_capacity(54 + (w * h * 4) as usize);
    let size = 54 + w * h * 4;
    bmp.extend_from_slice(b"BM");
    bmp.extend_from_slice(&size.to_le_bytes());
    bmp.extend_from_slice(&[0; 4]);
    bmp.extend_from_slice(&54u32.to_le_bytes());
    bmp.extend_from_slice(&40u32.to_le_bytes());
    bmp.extend_from_slice(&(w as i32).to_le_bytes());
    bmp.extend_from_slice(&(-(h as i32)).to_le_bytes());
    bmp.extend_from_slice(&1u16.to_le_bytes());
    bmp.extend_from_slice(&32u16.to_le_bytes());
    bmp.extend_from_slice(&[0; 24]);
    for p in bitmap.pixels.chunks_exact(4) {
        // RGBA → BGRA, 투명은 흰 바탕에
        let a = u32::from(p[3]);
        let mix = |c: u8| ((u32::from(c) * a + 255 * (255 - a)) / 255) as u8;
        bmp.extend_from_slice(&[mix(p[2]), mix(p[1]), mix(p[0]), 255]);
    }
    std::fs::write(&args[3], bmp).expect("쓰기");
}
