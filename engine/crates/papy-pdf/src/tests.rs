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

// ---- 편집 ----

use papy_model::{Annotation, MarkupKind, PageOp, PageRect, SaveFormat};

fn korean_font() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../vendor/rhwp/ttfs/opensource/NotoSansKR-Regular.ttf")
}

fn open_editable(pages: usize) -> Box<dyn Document> {
    let bytes: Arc<[u8]> = Arc::from(sample_pdf(pages));
    let options = BackendOpenOptions {
        max_pages: 1000,
        ..BackendOpenOptions::default()
    };
    PdfBackend::new(library())
        .with_text_font(korean_font())
        .open(Source::Bytes(&bytes), &options)
        .unwrap()
}

/// 영역 안에서 조건에 맞는 픽셀 수.
fn count_pixels(
    bitmap: &Bitmap,
    area: (u32, u32, u32, u32),
    test: impl Fn(&[u8]) -> bool,
) -> usize {
    let (x0, y0, x1, y1) = area;
    (y0..y1)
        .flat_map(|y| (x0..x1).map(move |x| (x, y)))
        .filter(|&(x, y)| test(&bitmap.pixels[((y * bitmap.width + x) * 4) as usize..][..4]))
        .count()
}

fn render(doc: &mut Box<dyn Document>, page: u32) -> Bitmap {
    doc.render_page(page, &RenderRequest::with_scale(1.0))
        .unwrap()
}

/// 샘플의 "Hello Papy N" 글줄: 기준선이 화면 y=100, 24pt.
const TEXT_AREA: PageRect = PageRect {
    page: 0,
    x: 30.0,
    y: 70.0,
    width: 240.0,
    height: 40.0,
};

#[test]
fn capabilities_and_text_rects() {
    let mut doc = open_editable(1);
    let caps = doc.info().capabilities;
    assert!(caps.annotate && caps.page_edit && !caps.edit);
    assert_eq!(caps.save_formats, vec![SaveFormat::Pdf, SaveFormat::Text]);
    let rects = doc.text_rects(0, TEXT_AREA).unwrap();
    assert_eq!(rects.len(), 1, "{rects:?}");
    assert!(
        rects[0].y > 70.0 && rects[0].y + rects[0].height < 110.0,
        "{rects:?}"
    );
    assert!(
        doc.text_rects(
            0,
            PageRect {
                page: 0,
                x: 10.0,
                y: 360.0,
                width: 20.0,
                height: 20.0
            }
        )
        .unwrap()
        .is_empty()
    );
}

#[test]
fn markup_note_ink_and_text_box_render() {
    let mut doc = open_editable(1);
    let before = render(&mut doc, 0);
    doc.annotate(&Annotation::Markup {
        page: 0,
        kind: MarkupKind::Highlight,
        area: TEXT_AREA,
        color: "#ffeb3b".into(),
    })
    .unwrap();
    let yellow = |p: &[u8]| p[0] > 200 && p[1] > 200 && p[2] < 120;
    let after = render(&mut doc, 0);
    assert!(
        count_pixels(&after, (40, 75, 200, 105), yellow)
            > count_pixels(&before, (40, 75, 200, 105), yellow) + 200
    );

    doc.annotate(&Annotation::Ink {
        page: 0,
        points: vec![(10.0, 150.0), (120.0, 150.0)],
        color: "#ff0000".into(),
        width: 3.0,
    })
    .unwrap();
    doc.annotate(&Annotation::TextBox {
        page: 0,
        x: 20.0,
        y: 200.0,
        text: "ABC".into(),
        font_size: 20.0,
        color: "#000000".into(),
    })
    .unwrap();
    doc.annotate(&Annotation::TextBox {
        page: 0,
        x: 20.0,
        y: 240.0,
        text: "한글 상자".into(),
        font_size: 20.0,
        color: "#0000ff".into(),
    })
    .unwrap();
    doc.annotate(&Annotation::Note {
        page: 0,
        x: 250.0,
        y: 10.0,
        text: "메모".into(),
    })
    .unwrap();
    let after = render(&mut doc, 0);
    let red = |p: &[u8]| p[0] > 200 && p[1] < 80 && p[2] < 80;
    let dark = |p: &[u8]| p[0] < 100 && p[1] < 100 && p[2] < 100;
    let blue = |p: &[u8]| p[2] > 180 && p[0] < 80 && p[1] < 80;
    assert!(count_pixels(&after, (15, 146, 115, 154), red) > 150, "ink");
    assert!(
        count_pixels(&after, (18, 200, 80, 225), dark) > 30,
        "latin text box"
    );
    assert!(
        count_pixels(&after, (18, 240, 120, 265), blue) > 30,
        "korean text box"
    );
    assert!(
        count_pixels(&after, (248, 8, 272, 32), |p| p != [255, 255, 255, 255]) > 30,
        "note icon"
    );

    // 저장 후 다시 열어도 남아 있다.
    let saved = doc.save(SaveFormat::Pdf).unwrap();
    let mut reopened = open_bytes(saved).unwrap();
    let again = render(&mut reopened, 0);
    assert!(count_pixels(&again, (15, 146, 115, 154), red) > 150);
    assert!(count_pixels(&again, (18, 240, 120, 265), blue) > 30);
    // CID 글꼴의 공백은 줄바꿈 없는 공백(U+00A0)으로 읽힌다.
    assert!(
        reopened
            .extract_text(0)
            .unwrap()
            .replace('\u{a0}', " ")
            .contains("한글 상자")
    );
}

#[test]
fn invalid_annotations_are_rejected_without_undo_step() {
    let mut doc = open_editable(1);
    let bad_color = Annotation::Ink {
        page: 0,
        points: vec![(0.0, 0.0), (1.0, 1.0)],
        color: "red".into(),
        width: 1.0,
    };
    assert_eq!(
        doc.annotate(&bad_color).unwrap_err().code,
        ErrorCode::InvalidArgument
    );
    let empty_area = Annotation::Markup {
        page: 0,
        kind: MarkupKind::Underline,
        area: PageRect {
            page: 0,
            x: 0.0,
            y: 380.0,
            width: 10.0,
            height: 10.0,
        },
        color: "#000000".into(),
    };
    assert_eq!(
        doc.annotate(&empty_area).unwrap_err().code,
        ErrorCode::InvalidArgument
    );
    let out_of_range = Annotation::Note {
        page: 5,
        x: 0.0,
        y: 0.0,
        text: "x".into(),
    };
    assert_eq!(
        doc.annotate(&out_of_range).unwrap_err().code,
        ErrorCode::PageOutOfRange
    );
    assert!(doc.undo().unwrap().is_none());
}

#[test]
fn page_operations_and_undo() {
    let mut doc = open_editable(3);
    let first_line = |doc: &mut Box<dyn Document>, page: u32| doc.extract_text(page).unwrap();

    doc.edit_pages(PageOp::Rotate {
        page: 0,
        quarter_turns: 1,
    })
    .unwrap();
    assert_eq!(
        doc.page_size(0).unwrap(),
        PageSize {
            width: 400.0,
            height: 300.0
        }
    );
    // 회전한 쪽에서도 화면 좌표로 주석이 맞는 자리에 간다: 글줄은 이제 화면 오른쪽 세로줄.
    let rects = doc
        .text_rects(
            0,
            PageRect {
                page: 0,
                x: 280.0,
                y: 20.0,
                width: 50.0,
                height: 260.0,
            },
        )
        .unwrap();
    assert_eq!(rects.len(), 1, "{rects:?}");

    doc.edit_pages(PageOp::Move { from: 2, to: 0 }).unwrap();
    assert!(first_line(&mut doc, 0).contains("Hello Papy 3"));
    assert!(first_line(&mut doc, 1).contains("Hello Papy 1"));

    doc.edit_pages(PageOp::Delete { page: 1 }).unwrap();
    assert_eq!(doc.info().page_count, 2);
    assert!(first_line(&mut doc, 1).contains("Hello Papy 2"));

    doc.undo().unwrap();
    assert_eq!(doc.info().page_count, 3);
    doc.undo().unwrap();
    assert!(first_line(&mut doc, 0).contains("Hello Papy 1"));
    doc.redo().unwrap();
    assert!(first_line(&mut doc, 0).contains("Hello Papy 3"));

    let mut single = open_editable(1);
    assert_eq!(
        single
            .edit_pages(PageOp::Delete { page: 0 })
            .unwrap_err()
            .code,
        ErrorCode::InvalidArgument
    );
}
