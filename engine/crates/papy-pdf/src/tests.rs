//! PDFium 이 있어야 도는 테스트. 먼저 `node scripts/fetch-pdfium.mjs` 를 실행한다
//! (또는 `PAPY_PDFIUM_LIBRARY` 에 라이브러리 경로를 지정).
//! 테스트용 PDF 는 코드로 만든다 — 저작권 문제가 없고 내용이 정확히 알려져 있다.

use super::*;
use papy_render::CancelToken;
use std::sync::Arc;

fn library() -> PathBuf {
    if let Some(path) = std::env::var_os("PAPY_PDFIUM_LIBRARY") {
        return PathBuf::from(path);
    }
    let target = match (std::env::consts::ARCH, std::env::consts::OS) {
        ("aarch64", "macos") => "aarch64-apple-darwin",
        ("x86_64", "macos") => "x86_64-apple-darwin",
        ("x86_64", "windows") => "x86_64-pc-windows-msvc",
        ("aarch64", "windows") => "aarch64-pc-windows-msvc",
        ("x86_64", "linux") => "x86_64-unknown-linux-gnu",
        ("aarch64", "linux") => "aarch64-unknown-linux-gnu",
        other => panic!("no PDFium build for {other:?}"),
    };
    let path = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../vendor/pdfium")
        .join(target)
        .join(library_file_name());
    assert!(
        path.exists(),
        "PDFium not found at {}. Run `node scripts/fetch-pdfium.mjs` first.",
        path.display()
    );
    path
}

/// 텍스트 페이지 `pages` 개, 제목, 목차(장마다 1개 + 하위 1개)를 가진 최소 PDF 를 만든다.
pub(crate) fn sample_pdf(pages: usize) -> Vec<u8> {
    let mut objects: Vec<String> = Vec::new();
    // 1: Catalog, 2: Pages, 3: Font, 4: Info, 5: Outlines, 이후 페이지·내용·목차 항목
    let first_page = 6;
    let page_ids: Vec<usize> = (0..pages).map(|i| first_page + i * 2).collect();
    let first_outline = first_page + pages * 2;
    objects.push("<< /Type /Catalog /Pages 2 0 R /Outlines 5 0 R >>".into());
    let kids: Vec<String> = page_ids.iter().map(|id| format!("{id} 0 R")).collect();
    objects.push(format!(
        "<< /Type /Pages /Kids [{}] /Count {pages} >>",
        kids.join(" ")
    ));
    objects.push("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".into());
    objects.push("<< /Title (Papy Sample) /Producer (papy tests) >>".into());
    objects.push(format!(
        "<< /Type /Outlines /First {first_outline} 0 R /Last {} 0 R /Count {pages} >>",
        first_outline + (pages - 1) * 2
    ));
    for (i, id) in page_ids.iter().enumerate() {
        objects.push(format!(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 400] /Resources << /Font << /F1 3 0 R >> >> /Contents {} 0 R >>",
            id + 1
        ));
        let stream = format!(
            "BT /F1 24 Tf 40 300 Td (Hello Papy {}) Tj ET 0 0 1 rg 40 40 100 50 re f",
            i + 1
        );
        objects.push(format!(
            "<< /Length {} >>\nstream\n{stream}\nendstream",
            stream.len()
        ));
    }
    for (i, page_id) in page_ids.iter().enumerate() {
        let id = first_outline + i * 2;
        let prev = if i > 0 {
            format!("/Prev {} 0 R ", id - 2)
        } else {
            String::new()
        };
        let next = if i + 1 < pages {
            format!("/Next {} 0 R ", id + 2)
        } else {
            String::new()
        };
        objects.push(format!(
            "<< /Title (Chapter {}) /Parent 5 0 R {prev}{next}/First {child} 0 R /Last {child} 0 R /Count 1 /Dest [{page} 0 R /Fit] >>",
            i + 1,
            child = id + 1,
            page = page_id
        ));
        objects.push(format!(
            "<< /Title (Section {}.1) /Parent {id} 0 R /Dest [{} 0 R /Fit] >>",
            i + 1,
            page_id
        ));
    }

    let mut pdf = b"%PDF-1.7\n".to_vec();
    let mut offsets = Vec::new();
    for (i, body) in objects.iter().enumerate() {
        offsets.push(pdf.len());
        pdf.extend_from_slice(format!("{} 0 obj\n{body}\nendobj\n", i + 1).as_bytes());
    }
    let xref = pdf.len();
    pdf.extend_from_slice(
        format!("xref\n0 {}\n0000000000 65535 f \n", objects.len() + 1).as_bytes(),
    );
    for offset in offsets {
        pdf.extend_from_slice(format!("{offset:010} 00000 n \n").as_bytes());
    }
    pdf.extend_from_slice(
        format!(
            "trailer\n<< /Size {} /Root 1 0 R /Info 4 0 R >>\nstartxref\n{xref}\n%%EOF\n",
            objects.len() + 1
        )
        .as_bytes(),
    );
    pdf
}

fn open_bytes(bytes: Vec<u8>) -> Result<Box<dyn Document>> {
    let bytes: Arc<[u8]> = Arc::from(bytes);
    let options = BackendOpenOptions {
        max_pages: 1000,
        ..BackendOpenOptions::default()
    };
    PdfBackend::new(library()).open(Source::Bytes(&bytes), &options)
}

#[test]
fn opens_and_reads_sample() {
    let mut doc = open_bytes(sample_pdf(3)).unwrap();
    let info = doc.info();
    assert_eq!(info.page_count, 3);
    assert_eq!(info.title.as_deref(), Some("Papy Sample"));
    assert!(
        info.capabilities.render && info.capabilities.extract_text && info.capabilities.outline
    );
    assert_eq!(
        doc.page_size(0).unwrap(),
        PageSize {
            width: 300.0,
            height: 400.0
        }
    );
    assert!(doc.extract_text(1).unwrap().contains("Hello Papy 2"));
}

#[test]
fn renders_pixels() {
    let mut doc = open_bytes(sample_pdf(1)).unwrap();
    let bitmap = doc.render_page(0, &RenderRequest::with_scale(1.0)).unwrap();
    assert_eq!((bitmap.width, bitmap.height), (300, 400));
    assert_eq!(bitmap.pixels.len(), 300 * 400 * 4);
    let pixel = |x: usize, y: usize| &bitmap.pixels[(y * 300 + x) * 4..][..4];
    assert_eq!(pixel(5, 5), &[255, 255, 255, 255], "white background");
    // 파란 사각형: PDF 좌표 (40,40)-(140,90) → 이미지 y = 400 - 65
    let blue = pixel(90, 335);
    assert!(
        blue[2] > 200 && blue[0] < 50 && blue[1] < 50,
        "blue box, got {blue:?}"
    );

    let transparent = RenderRequest {
        white_background: false,
        ..RenderRequest::with_scale(0.5)
    };
    let bitmap = doc.render_page(0, &transparent).unwrap();
    assert_eq!((bitmap.width, bitmap.height), (150, 200));
    assert_eq!(bitmap.pixels[3], 0, "transparent corner");
}

#[test]
fn reads_outline() {
    let mut doc = open_bytes(sample_pdf(2)).unwrap();
    let outline = doc.outline().unwrap();
    assert_eq!(outline.len(), 2);
    assert_eq!(outline[1].title, "Chapter 2");
    assert_eq!(outline[1].page, Some(1));
    assert_eq!(outline[1].children[0].title, "Section 2.1");
}

#[test]
fn opens_from_path() {
    let dir = std::env::temp_dir().join(format!("papy-pdf-test-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let path = dir.join("sample.pdf");
    std::fs::write(&path, sample_pdf(2)).unwrap();
    let options = BackendOpenOptions {
        max_pages: 1000,
        ..BackendOpenOptions::default()
    };
    let doc = PdfBackend::new(library())
        .open(Source::Path(&path), &options)
        .unwrap();
    assert_eq!(doc.info().page_count, 2);
    drop(doc);
    std::fs::remove_dir_all(&dir).unwrap();
}

#[test]
fn damaged_files_fail_cleanly() {
    let truncated = sample_pdf(2)[..200].to_vec();
    for (name, bytes) in [
        ("truncated", truncated),
        ("garbage", b"%PDF-1.7\n\x00\x01\x02 not a pdf".to_vec()),
        ("empty-ish", b"%PDF-".to_vec()),
    ] {
        match open_bytes(bytes) {
            Err(error) => assert_eq!(error.code, ErrorCode::InvalidDocument, "{name}: {error}"),
            // PDFium 은 일부 손상 파일을 복구해서 연다 — 그때도 패닉 없이 0쪽 이상이면 된다.
            Ok(doc) => assert!(doc.info().page_count <= 2, "{name}"),
        }
    }
}

#[test]
fn page_limit_and_cancel() {
    let bytes: Arc<[u8]> = Arc::from(sample_pdf(3));
    let backend = PdfBackend::new(library());
    let limited = BackendOpenOptions {
        max_pages: 2,
        ..BackendOpenOptions::default()
    };
    let error = backend.open(Source::Bytes(&bytes), &limited).err().unwrap();
    assert_eq!(error.code, ErrorCode::LimitExceeded);

    let cancel = CancelToken::new();
    cancel.cancel();
    let cancelled = BackendOpenOptions {
        max_pages: 10,
        cancel,
        ..BackendOpenOptions::default()
    };
    let error = backend
        .open(Source::Bytes(&bytes), &cancelled)
        .err()
        .unwrap();
    assert_eq!(error.code, ErrorCode::Cancelled);
}

#[test]
fn documents_are_usable_from_many_threads() {
    let handles: Vec<_> = (0..6)
        .map(|_| {
            std::thread::spawn(|| {
                let mut doc = open_bytes(sample_pdf(2)).unwrap();
                for page in 0..2 {
                    doc.render_page(page, &RenderRequest::with_scale(0.5))
                        .unwrap();
                }
            })
        })
        .collect();
    for handle in handles {
        handle.join().unwrap();
    }
}
