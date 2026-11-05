//! 테스트 문서는 rhwp 로 직접 만든다 — 저작권 문제가 없고 내용이 정확히 알려져 있다.

use super::*;
use papy_model::{Alignment, CharFormat, PageSize, ParaFormat, TextPos};
use papy_render::{CancelToken, RenderRequest};
use rhwp::document_core::DocumentCore;

/// 쪽마다 "Papy 쪽 N" 한 줄을 가진 문서. `hwpx` 면 HWPX, 아니면 HWP 5.x 바이트.
pub(crate) fn sample(pages: usize, hwpx: bool, password: Option<&str>) -> Vec<u8> {
    let mut core = DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    for page in 0..pages {
        let text = format!("Papy 쪽 {}", page + 1);
        core.insert_text_native(0, page, 0, &text).unwrap();
        if page + 1 < pages {
            core.insert_page_break_native(0, page, text.chars().count())
                .unwrap();
        }
    }
    match (hwpx, password) {
        (true, Some(password)) => core
            .export_hwpx_native_with_password(password.as_bytes())
            .unwrap(),
        (true, None) => core.export_hwpx_native().unwrap(),
        (false, _) => core.export_hwp_native().unwrap(),
    }
}

fn options() -> BackendOpenOptions {
    BackendOpenOptions {
        max_pages: 1000,
        ..BackendOpenOptions::default()
    }
}

fn open(
    backend: HwpBackend,
    bytes: Vec<u8>,
    options: &BackendOpenOptions,
) -> Result<Box<dyn Document>> {
    let bytes: Arc<[u8]> = Arc::from(bytes);
    backend.open(Source::Bytes(&bytes), options)
}

#[test]
fn samples_are_detected() {
    assert!(has_hwpx_mimetype(&sample(1, true, None)));
    assert!(has_cfb_signature(&sample(1, false, None)));
}

#[test]
fn opens_hwpx_and_hwp() {
    for (backend, hwpx) in [(HwpBackend::hwpx(), true), (HwpBackend::hwp(), false)] {
        let mut doc = open(backend, sample(2, hwpx, None), &options()).unwrap();
        let info = doc.info();
        assert_eq!(info.format, backend.format());
        assert_eq!(info.page_count, 2, "hwpx={hwpx}");
        assert!(info.capabilities.render && info.capabilities.extract_text);
        // 빈 문서 템플릿은 A4(210×297mm = 595.3×841.9pt)
        let size = doc.page_size(0).unwrap();
        assert!(
            (size.width - 595.3).abs() < 1.0 && (size.height - 841.9).abs() < 1.0,
            "{size:?}"
        );
        assert!(doc.extract_text(0).unwrap().contains("Papy 쪽 1"));
        assert!(doc.extract_text(1).unwrap().contains("Papy 쪽 2"));
    }
}

#[test]
fn renders_page_bitmap() {
    let mut doc = open(HwpBackend::hwpx(), sample(1, true, None), &options()).unwrap();
    let request = RenderRequest::with_scale(1.0);
    let bitmap = doc.render_page(0, &request).unwrap();
    assert_eq!(
        (bitmap.width, bitmap.height),
        request.pixel_size(doc.page_size(0).unwrap())
    );
    assert_eq!(
        bitmap.pixels.len(),
        bitmap.stride() * bitmap.height as usize
    );
    let pixel = |x: u32, y: u32| &bitmap.pixels[(y * bitmap.width + x) as usize * 4..][..4];
    assert_eq!(pixel(5, 5), &[255, 255, 255, 255], "white margin");
    // 본문 첫 줄(왼쪽 여백 30mm, 위 20+15mm 부근)에 글자가 그려졌는지 본다.
    let inked = (85..200)
        .flat_map(|x| (95..125).map(move |y| (x, y)))
        .filter(|&(x, y)| pixel(x, y)[0] < 128)
        .count();
    assert!(inked > 20, "text should be drawn, dark pixels: {inked}");

    let half = doc.render_page(0, &RenderRequest::with_scale(0.5)).unwrap();
    assert_eq!(half.width, bitmap.width.div_ceil(2));
}

#[test]
fn passwords() {
    let bytes = sample(1, true, Some("papy-pw"));
    let error = open(HwpBackend::hwpx(), bytes.clone(), &options())
        .err()
        .unwrap();
    assert_eq!(error.code, ErrorCode::PasswordRequired);
    let wrong = BackendOpenOptions {
        password: Some("nope".into()),
        ..options()
    };
    let error = open(HwpBackend::hwpx(), bytes.clone(), &wrong)
        .err()
        .unwrap();
    assert_eq!(error.code, ErrorCode::PasswordRequired);
    let right = BackendOpenOptions {
        password: Some("papy-pw".into()),
        ..options()
    };
    let mut doc = open(HwpBackend::hwpx(), bytes, &right).unwrap();
    assert!(doc.extract_text(0).unwrap().contains("Papy 쪽 1"));
}

#[test]
fn damaged_files_fail_cleanly() {
    let hwpx = sample(2, true, None);
    let hwp = sample(2, false, None);
    let cases = [
        (
            "truncated hwpx",
            HwpBackend::hwpx(),
            hwpx[..hwpx.len() / 2].to_vec(),
        ),
        (
            "truncated hwp",
            HwpBackend::hwp(),
            hwp[..hwp.len() / 2].to_vec(),
        ),
        (
            "cfb header only",
            HwpBackend::hwp(),
            [CFB_SIGNATURE, &[0; 504]].concat(),
        ),
        (
            "zip garbage",
            HwpBackend::hwpx(),
            b"PK\x03\x04 not really a zip".to_vec(),
        ),
    ];
    for (name, backend, bytes) in cases {
        match open(backend, bytes, &options()) {
            Err(error) => assert!(
                matches!(
                    error.code,
                    ErrorCode::InvalidDocument | ErrorCode::UnsupportedFormat
                ),
                "{name}: {error}"
            ),
            // 일부가 남은 문서를 복구해 여는 것도 괜찮다 — 패닉 없이 다룰 수 있으면 된다.
            Ok(mut doc) => {
                let _ = doc.render_page(0, &RenderRequest::with_scale(0.2));
            }
        }
    }
}

#[test]
fn page_limit_and_cancel() {
    let limited = BackendOpenOptions {
        max_pages: 1,
        ..options()
    };
    let error = open(HwpBackend::hwpx(), sample(2, true, None), &limited)
        .err()
        .unwrap();
    assert_eq!(error.code, ErrorCode::LimitExceeded);

    let cancel = CancelToken::new();
    cancel.cancel();
    let cancelled = BackendOpenOptions {
        cancel: cancel.clone(),
        ..options()
    };
    let error = open(HwpBackend::hwpx(), sample(1, true, None), &cancelled)
        .err()
        .unwrap();
    assert_eq!(error.code, ErrorCode::Cancelled);

    let mut doc = open(HwpBackend::hwpx(), sample(1, true, None), &options()).unwrap();
    let request = RenderRequest {
        cancel,
        ..RenderRequest::with_scale(1.0)
    };
    assert_eq!(
        doc.render_page(0, &request).unwrap_err().code,
        ErrorCode::Cancelled
    );
}

#[test]
fn opens_from_path() {
    let dir = std::env::temp_dir().join(format!("papy-hwp-test-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let path = dir.join("sample.hwpx");
    std::fs::write(&path, sample(1, true, None)).unwrap();
    let doc = HwpBackend::hwpx()
        .open(Source::Path(&path), &options())
        .unwrap();
    assert_eq!(doc.info().page_count, 1);
    std::fs::remove_dir_all(&dir).unwrap();
}

#[test]
fn page_info_parsing() {
    assert_eq!(
        parse_page_size(r#"{"width":96.0,"height":192.0}"#),
        Some(PageSize {
            width: 72.0,
            height: 144.0
        })
    );
    assert_eq!(parse_page_size(r#"{"width":0,"height":10}"#), None);
    assert_eq!(parse_page_size("not json"), None);
}

// ---- 편집 ----

fn editable(text_pages: usize) -> Box<dyn Document> {
    open(
        HwpBackend::hwpx(),
        sample(text_pages, true, None),
        &options(),
    )
    .unwrap()
}

fn pos(paragraph: u32, offset: u32) -> TextPos {
    TextPos {
        section: 0,
        paragraph,
        offset,
    }
}

fn all_text(doc: &mut Box<dyn Document>) -> String {
    let pages = doc.info().page_count;
    (0..pages)
        .map(|page| doc.extract_text(page).unwrap())
        .collect::<Vec<_>>()
        .join("\n")
}

#[test]
fn edit_insert_delete_undo_redo() {
    let mut doc = editable(1);
    assert!(doc.info().capabilities.edit);
    // "Papy 쪽 1" → 앞에 "새 " 를 넣는다.
    let caret = doc.insert_text(pos(0, 0), "새 ").unwrap().caret;
    assert_eq!(caret, pos(0, 2));
    assert!(all_text(&mut doc).starts_with("새 Papy"));

    // 줄바꿈은 문단 나누기
    let caret = doc.insert_text(caret, "첫줄\n둘째").unwrap().caret;
    assert_eq!(caret, pos(1, 2));
    assert_eq!(
        doc.text_in_range(pos(0, 0), pos(1, 2)).unwrap(),
        "새 첫줄\n둘째"
    );

    // 문단 처음에서 지우면 앞 문단과 합친다.
    let caret = doc.delete_backward(pos(1, 0)).unwrap().caret;
    assert_eq!(caret, pos(0, 4)); // "새 첫줄" 끝
    assert!(all_text(&mut doc).starts_with("새 첫줄둘째"));
    let caret = doc.delete_forward(pos(0, 0)).unwrap().caret;
    assert_eq!(caret, pos(0, 0));
    assert!(all_text(&mut doc).starts_with(" 첫줄둘째"));

    // 되돌리기 3번: 지우기, 합치기, 입력 두 번(공백·줄바꿈이 있어 각각 한 단계)
    assert!(doc.undo().unwrap().is_some());
    assert!(all_text(&mut doc).starts_with("새 첫줄둘째"));
    doc.undo().unwrap();
    doc.undo().unwrap();
    doc.undo().unwrap();
    assert!(all_text(&mut doc).starts_with("Papy 쪽 1"));
    assert!(doc.undo().unwrap().is_none());
    doc.redo().unwrap();
    assert!(all_text(&mut doc).starts_with("새 Papy"));
}

#[test]
fn typing_is_one_undo_step() {
    let mut doc = editable(1);
    let mut caret = pos(0, 0);
    for ch in ["가", "나", "다"] {
        caret = doc.insert_text(caret, ch).unwrap().caret;
    }
    assert!(all_text(&mut doc).starts_with("가나다Papy"));
    doc.undo().unwrap();
    assert!(all_text(&mut doc).starts_with("Papy"));
    assert!(doc.undo().unwrap().is_none());
}

#[test]
fn caret_and_hit_test_agree() {
    let mut doc = editable(1);
    let rect = doc.caret_rect(pos(0, 3)).unwrap();
    assert_eq!(rect.page, 0);
    assert!(rect.height > 5.0 && rect.x > 50.0, "{rect:?}");
    let hit = doc
        .hit_test(0, rect.x + 0.5, rect.y + rect.height / 2.0)
        .unwrap();
    assert_eq!(hit, pos(0, 3));
    let rects = doc.selection_rects(pos(0, 0), pos(0, 4)).unwrap();
    assert_eq!(rects.len(), 1);
    assert!(rects[0].width > 10.0);
}

#[test]
fn formatting() {
    let mut doc = editable(1);
    let format = CharFormat {
        bold: Some(true),
        font_size: Some(20.0),
        color: Some("#ff0000".into()),
        ..CharFormat::default()
    };
    doc.apply_char_format(pos(0, 0), pos(0, 4), &format)
        .unwrap();
    let read = doc.char_format(pos(0, 2)).unwrap();
    assert_eq!(read.bold, Some(true));
    assert_eq!(read.font_size, Some(20.0));
    assert_eq!(read.color.as_deref(), Some("#ff0000"));
    assert_eq!(doc.char_format(pos(0, 6)).unwrap().bold, Some(false));

    doc.apply_para_format(
        pos(0, 0),
        pos(0, 0),
        &ParaFormat {
            alignment: Some(Alignment::Center),
            ..ParaFormat::default()
        },
    )
    .unwrap();
    assert_eq!(
        doc.para_format(pos(0, 0)).unwrap().alignment,
        Some(Alignment::Center)
    );
    doc.undo().unwrap();
    assert_ne!(
        doc.para_format(pos(0, 0)).unwrap().alignment,
        Some(Alignment::Center)
    );
}

#[test]
fn page_count_follows_edits() {
    let mut doc = editable(1);
    let mut caret = pos(0, 0);
    for _ in 0..80 {
        caret = doc.insert_text(caret, "긴 문단 내용\n").unwrap().caret;
    }
    assert!(doc.info().page_count >= 2, "{}", doc.info().page_count);
    let last = doc.info().page_count - 1;
    assert!(doc.page_size(last).is_ok());
    doc.render_page(last, &RenderRequest::with_scale(0.2))
        .unwrap();
}

#[test]
fn save_in_every_format() {
    let mut doc = editable(1);
    doc.insert_text(pos(0, 0), "저장됨 ").unwrap();
    for format in [SaveFormat::Hwpx, SaveFormat::Hwp] {
        let bytes = doc.save(format).unwrap();
        let backend = if format == SaveFormat::Hwpx {
            HwpBackend::hwpx()
        } else {
            HwpBackend::hwp()
        };
        let mut reopened = open(backend, bytes, &options()).unwrap();
        assert!(
            reopened.extract_text(0).unwrap().starts_with("저장됨 Papy"),
            "{format:?}"
        );
    }
    assert!(doc.save(SaveFormat::Pdf).unwrap().starts_with(b"%PDF-"));
    let text = String::from_utf8(doc.save(SaveFormat::Text).unwrap()).unwrap();
    assert!(text.starts_with("저장됨 Papy 쪽 1"));
}

#[test]
fn encrypted_documents_stay_encrypted() {
    let with_password = BackendOpenOptions {
        password: Some("papy-pw".into()),
        ..options()
    };
    let mut doc = open(
        HwpBackend::hwpx(),
        sample(1, true, Some("papy-pw")),
        &with_password,
    )
    .unwrap();
    doc.insert_text(pos(0, 0), "비밀 ").unwrap();
    let saved = doc.save(SaveFormat::Hwpx).unwrap();
    let error = open(HwpBackend::hwpx(), saved.clone(), &options())
        .err()
        .unwrap();
    assert_eq!(error.code, ErrorCode::PasswordRequired);
    let mut reopened = open(HwpBackend::hwpx(), saved, &with_password).unwrap();
    assert!(reopened.extract_text(0).unwrap().starts_with("비밀 Papy"));
}

#[test]
fn unprotected_export_and_password_transfer() {
    let with_password = BackendOpenOptions {
        password: Some("papy-pw".into()),
        ..options()
    };
    let mut doc = open(
        HwpBackend::hwpx(),
        sample(1, true, Some("papy-pw")),
        &with_password,
    )
    .unwrap();
    assert_eq!(doc.save_password().as_deref(), Some("papy-pw"));
    // 다른 편집기로 넘길 바이트는 암호 없이 열린다. 원래 문서의 암호는 그대로다.
    let plain = doc.save_unprotected(SaveFormat::Hwpx).unwrap();
    let mut copy = open(HwpBackend::hwpx(), plain, &options()).unwrap();
    assert_eq!(copy.save_password(), None);
    assert!(doc.save_password().is_some());
    // 암호를 되돌리면 저장 결과가 다시 암호를 요구한다.
    copy.set_save_password(doc.save_password()).unwrap();
    let saved = copy.save(SaveFormat::Hwpx).unwrap();
    assert_eq!(
        open(HwpBackend::hwpx(), saved.clone(), &options())
            .err()
            .unwrap()
            .code,
        ErrorCode::PasswordRequired
    );
    assert!(open(HwpBackend::hwpx(), saved, &with_password).is_ok());
    // HWP 도 같다.
    let mut hwp = open(
        HwpBackend::hwp(),
        sample(1, false, Some("papy-pw")),
        &with_password,
    )
    .unwrap();
    let plain = hwp.save_unprotected(SaveFormat::Hwp).unwrap();
    assert!(open(HwpBackend::hwp(), plain, &options()).is_ok());
}

#[test]
fn caret_motion() {
    use papy_model::CaretMotion::*;
    let mut doc = editable(1);
    // 범위 밖 위치는 거절한다.
    assert_eq!(
        doc.insert_text(pos(0, 9), "x").unwrap_err().code,
        ErrorCode::InvalidArgument
    );
    doc.insert_text(pos(0, 8), "\n둘째").unwrap();
    assert_eq!(doc.move_caret(pos(1, 0), Left).unwrap(), pos(0, 8));
    assert_eq!(doc.move_caret(pos(0, 8), Right).unwrap(), pos(1, 0));
    assert_eq!(doc.move_caret(pos(0, 3), LineStart).unwrap(), pos(0, 0));
    assert_eq!(doc.move_caret(pos(0, 3), LineEnd).unwrap(), pos(0, 8));
    assert_eq!(doc.move_caret(pos(1, 1), DocumentStart).unwrap(), pos(0, 0));
    assert_eq!(doc.move_caret(pos(0, 1), DocumentEnd).unwrap(), pos(1, 2));
}

// ---- DOCX ----

#[test]
fn docx_round_trip_keeps_text_format_and_tables() {
    let mut doc = editable(1);
    doc.apply_char_format(
        pos(0, 0),
        pos(0, 4),
        &CharFormat {
            bold: Some(true),
            ..CharFormat::default()
        },
    )
    .unwrap();
    // 표가 있는 문단을 붙인다(rhwp HTML 붙여넣기).
    let mut core = DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "표 위 문단").unwrap();
    core.paste_html_native(
        0,
        0,
        5,
        "<table border=\"1\"><tr><td>칸1</td><td>칸2</td></tr></table>",
    )
    .unwrap();
    let with_table = core.export_hwpx_native().unwrap();

    for (source, expected) in [
        (doc.save(SaveFormat::Docx).unwrap(), "Papy 쪽 1"),
        (
            open(HwpBackend::hwpx(), with_table, &options())
                .unwrap()
                .save(SaveFormat::Docx)
                .unwrap(),
            "칸2",
        ),
    ] {
        assert!(papy_office::is_docx(&source));
        let mut reopened = open(HwpBackend::docx(), source, &options()).unwrap();
        let info = reopened.info();
        assert_eq!(info.format, DocumentFormat::Docx);
        assert!(
            info.capabilities.edit && info.capabilities.save_formats.contains(&SaveFormat::Docx)
        );
        assert!(
            reopened.extract_text(0).unwrap().contains(expected),
            "{expected}"
        );
    }

    let mut reopened = open(
        HwpBackend::docx(),
        doc.save(SaveFormat::Docx).unwrap(),
        &options(),
    )
    .unwrap();
    assert_eq!(reopened.char_format(pos(0, 2)).unwrap().bold, Some(true));
    assert_eq!(reopened.char_format(pos(0, 7)).unwrap().bold, Some(false));
    // DOCX 로 연 문서도 편집하고 다른 형식으로 저장할 수 있다.
    reopened.insert_text(pos(0, 0), "편집 ").unwrap();
    let hwpx = reopened.save(SaveFormat::Hwpx).unwrap();
    assert!(
        open(HwpBackend::hwpx(), hwpx, &options())
            .unwrap()
            .extract_text(0)
            .unwrap()
            .starts_with("편집 Papy")
    );
}

#[test]
fn non_docx_zip_is_rejected() {
    let error = open(
        HwpBackend::docx(),
        b"PK\x03\x04 not really a zip".to_vec(),
        &options(),
    )
    .err()
    .unwrap();
    assert_eq!(error.code, ErrorCode::UnsupportedFormat);
}

/// 40×20 빨간 PNG
const RED_PNG: &[u8] = &[
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x28, 0x00, 0x00, 0x00, 0x14, 0x08, 0x02, 0x00, 0x00, 0x00, 0x70, 0x24, 0xE8,
    0xEC, 0x00, 0x00, 0x00, 0x23, 0x49, 0x44, 0x41, 0x54, 0x78, 0xDA, 0xED, 0xCD, 0xB1, 0x0D, 0x00,
    0x00, 0x08, 0xC0, 0xA0, 0xFE, 0xFF, 0xB4, 0x9E, 0xE1, 0x20, 0x09, 0x3B, 0x4D, 0x9D, 0x48, 0x2C,
    0x16, 0x8B, 0xC5, 0x62, 0xB1, 0xF8, 0x4F, 0xBC, 0x7D, 0xFA, 0x1D, 0x0E, 0xA2, 0x1E, 0x87, 0x3D,
    0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82,
];

fn docx_with_image() -> Vec<u8> {
    use std::io::Write;
    let document = r#"<w:document xmlns:w="w" xmlns:wp="wp" xmlns:a="a" xmlns:pic="pic" xmlns:r="r"><w:body><w:p><w:r><w:t>그림 위</w:t></w:r></w:p><w:p><w:r><w:drawing><wp:inline><wp:extent cx="1905000" cy="952500"/><a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed="rId9"/></pic:blipFill></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p><w:p><w:r><w:t>그림 아래</w:t></w:r></w:p></w:body></w:document>"#;
    let rels = r#"<Relationships xmlns="r"><Relationship Id="rId9" Type="image" Target="media/red.png"/></Relationships>"#;
    let mut buffer = std::io::Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    let options = zip::write::SimpleFileOptions::default();
    for (name, bytes) in [
        ("[Content_Types].xml", &b"<Types/>"[..]),
        ("word/document.xml", document.as_bytes()),
        ("word/_rels/document.xml.rels", rels.as_bytes()),
        ("word/media/red.png", RED_PNG),
    ] {
        zip.start_file(name, options).unwrap();
        zip.write_all(bytes).unwrap();
    }
    zip.finish().unwrap();
    buffer.into_inner()
}

#[test]
fn docx_images_are_kept() {
    let mut doc = open(HwpBackend::docx(), docx_with_image(), &options()).unwrap();
    assert_eq!(doc.info().dropped_objects, 0);
    let text = doc.extract_text(0).unwrap();
    assert!(
        text.contains("그림 위") && text.contains("그림 아래"),
        "{text}"
    );
    // 그림(200×100px)이 빨갛게 그려진다.
    let bitmap = doc.render_page(0, &RenderRequest::with_scale(1.0)).unwrap();
    let red = bitmap
        .pixels
        .chunks_exact(4)
        .filter(|p| p[0] > 200 && p[1] < 80 && p[2] < 80)
        .count();
    assert!(red > 5_000, "red pixels: {red}");
    // HWP 로 저장해도 그림이 남고, PDF 에도 들어간다.
    let hwp = doc.save(SaveFormat::Hwp).unwrap();
    let mut reopened = open(HwpBackend::hwp(), hwp, &options()).unwrap();
    let again = reopened
        .render_page(0, &RenderRequest::with_scale(1.0))
        .unwrap();
    let red_again = again
        .pixels
        .chunks_exact(4)
        .filter(|p| p[0] > 200 && p[1] < 80 && p[2] < 80)
        .count();
    assert!(red_again > 5_000, "red pixels after HWP: {red_again}");
    let pdf = doc.save(SaveFormat::Pdf).unwrap();
    assert!(
        pdf.starts_with(b"%PDF-") && pdf.windows(6).any(|w| w == b"/Image"),
        "PDF should embed the image"
    );
    // DOCX 로 다시 써도 그림이 남는다(word/media + 그림 관계).
    let docx = doc.save(SaveFormat::Docx).unwrap();
    {
        let zip = zip::ZipArchive::new(std::io::Cursor::new(&docx[..])).unwrap();
        assert!(zip.index_for_name("word/media/image1.png").is_some());
    }
    let mut again_docx = open(HwpBackend::docx(), docx, &options()).unwrap();
    assert_eq!(again_docx.info().dropped_objects, 0);
    let shown = again_docx
        .render_page(0, &RenderRequest::with_scale(1.0))
        .unwrap();
    let red = shown
        .pixels
        .chunks_exact(4)
        .filter(|p| p[0] > 200 && p[1] < 80 && p[2] < 80)
        .count();
    assert!(red > 5_000, "DOCX→DOCX: red pixels {red}");
    // HWPX 로 저장해도 남는다. rhwp 0.8.7 은 그림 문단의 줄 정보를 쓰지 않아 열 때 채운다(repair.rs).
    for (label, bytes) in [
        ("DOCX→HWPX", doc.save(SaveFormat::Hwpx).unwrap()),
        ("DOCX→HWP→HWPX", reopened.save(SaveFormat::Hwpx).unwrap()),
    ] {
        let mut hwpx = open(HwpBackend::hwpx(), bytes, &options()).unwrap();
        let shown = hwpx
            .render_page(0, &RenderRequest::with_scale(1.0))
            .unwrap();
        let red = shown
            .pixels
            .chunks_exact(4)
            .filter(|p| p[0] > 200 && p[1] < 80 && p[2] < 80)
            .count();
        assert!(red > 5_000, "{label}: red pixels {red}");
        let text = hwpx.extract_text(0).unwrap();
        assert!(text.contains("그림 아래"), "{label}: {text}");
    }
}

#[test]
fn docx_headers_footers_and_footnotes() {
    use std::io::Write;
    let document = r#"<w:document xmlns:w="w" xmlns:r="r"><w:body><w:p><w:r><w:t>본문</w:t></w:r><w:r><w:footnoteReference w:id="2"/></w:r><w:r><w:t xml:space="preserve"> 계속</w:t></w:r><w:r><w:footnoteReference w:id="3"/></w:r></w:p><w:p><w:r><w:t>다음 문단</w:t></w:r></w:p><w:sectPr><w:headerReference w:type="default" r:id="rIdH"/><w:footerReference w:type="default" r:id="rIdF"/></w:sectPr></w:body></w:document>"#;
    let parts = [
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", document),
        (
            "word/_rels/document.xml.rels",
            r#"<Relationships xmlns="r"><Relationship Id="rIdH" Type="header" Target="header1.xml"/><Relationship Id="rIdF" Type="footer" Target="footer1.xml"/></Relationships>"#,
        ),
        (
            "word/header1.xml",
            r#"<w:hdr xmlns:w="w"><w:p><w:r><w:t>머리말 글</w:t></w:r></w:p></w:hdr>"#,
        ),
        (
            "word/footer1.xml",
            r#"<w:ftr xmlns:w="w"><w:p><w:r><w:t>꼬리말 글</w:t></w:r></w:p><w:p><w:r><w:t>둘째 줄</w:t></w:r></w:p></w:ftr>"#,
        ),
        (
            "word/footnotes.xml",
            r#"<w:footnotes xmlns:w="w"><w:footnote w:id="2"><w:p><w:r><w:t>첫 각주</w:t></w:r></w:p></w:footnote><w:footnote w:id="3"><w:p><w:r><w:t>둘째 각주</w:t></w:r></w:p><w:p><w:r><w:t>각주 둘째 문단</w:t></w:r></w:p></w:footnote></w:footnotes>"#,
        ),
    ];
    let mut buffer = std::io::Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    for (name, xml) in parts {
        zip.start_file(name, zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(xml.as_bytes()).unwrap();
    }
    zip.finish().unwrap();
    let mut doc = open(HwpBackend::docx(), buffer.into_inner(), &options()).unwrap();
    assert_eq!(doc.info().dropped_objects, 0);
    let text = doc.extract_text(0).unwrap();
    // 쪽 글자: 각주 번호가 본문 자리에, 머리말·꼬리말·각주 글자가 쪽에 나온다.
    assert!(text.contains("본문1) 계속2)"), "{text:?}");
    assert!(!text.contains(papy_office::FOOTNOTE_MARK), "{text:?}");
    for part in [
        "머리말 글",
        "꼬리말 글",
        "둘째 줄",
        "첫 각주",
        "각주 둘째 문단",
    ] {
        assert!(text.contains(part), "{part}: {text:?}");
    }
    let docx = doc.save(SaveFormat::Docx).unwrap();
    let read = |bytes: &[u8], name: &str| -> Option<String> {
        let mut zip = zip::ZipArchive::new(std::io::Cursor::new(bytes)).ok()?;
        let mut text = String::new();
        std::io::Read::read_to_string(&mut zip.by_name(name).ok()?, &mut text).ok()?;
        Some(text)
    };
    let body = read(&docx, "word/document.xml").unwrap();
    assert_eq!(body.matches("<w:footnoteReference").count(), 2, "{body}");
    assert!(
        body.contains(r#"<w:headerReference w:type="default""#),
        "{body}"
    );
    assert!(
        body.contains(r#"<w:footerReference w:type="default""#),
        "{body}"
    );
    let notes = read(&docx, "word/footnotes.xml").unwrap();
    assert!(
        notes.contains("첫 각주") && notes.contains("각주 둘째 문단"),
        "{notes}"
    );
    let header = (1..=2)
        .filter_map(|i| read(&docx, &format!("word/header{i}.xml")))
        .collect::<String>();
    assert!(header.contains("머리말 글"), "{header}");
    let footer = (1..=2)
        .filter_map(|i| read(&docx, &format!("word/footer{i}.xml")))
        .collect::<String>();
    assert!(
        footer.contains("꼬리말 글") && footer.contains("둘째 줄"),
        "{footer}"
    );
    let types = read(&docx, "[Content_Types].xml").unwrap();
    assert!(
        types.contains("/word/footnotes.xml") && types.contains("header+xml"),
        "{types}"
    );

    // 다시 열어도(DOCX→DOCX) 남고, HWPX·HWP 로 저장해도 머리말·각주가 남는다.
    let mut reopened = open(HwpBackend::docx(), docx, &options()).unwrap();
    assert_eq!(reopened.info().dropped_objects, 0);
    for (backend, format) in [
        (HwpBackend::hwpx(), SaveFormat::Hwpx),
        (HwpBackend::hwp(), SaveFormat::Hwp),
    ] {
        let bytes = reopened.save(format).unwrap();
        let mut again = open(backend, bytes, &options()).unwrap();
        let docx = again.save(SaveFormat::Docx).unwrap();
        let notes = read(&docx, "word/footnotes.xml").unwrap_or_default();
        assert!(
            notes.contains("첫 각주") && notes.contains("둘째 각주"),
            "{format:?}: {notes}"
        );
        let header = read(&docx, "word/header1.xml").unwrap_or_default();
        assert!(header.contains("머리말 글"), "{format:?}: {header}");
    }
}

#[test]
fn docx_endnotes_are_kept() {
    let document = r#"<w:document xmlns:w="w"><w:body><w:p><w:r><w:t>본문</w:t></w:r><w:r><w:endnoteReference w:id="1"/></w:r><w:r><w:t xml:space="preserve"> 계속</w:t></w:r><w:r><w:footnoteReference w:id="1"/></w:r></w:p></w:body></w:document>"#;
    let bytes = zip_parts(&[
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", document),
        (
            "word/endnotes.xml",
            r#"<w:endnotes xmlns:w="w"><w:endnote w:id="1"><w:p><w:r><w:t>미주 첫 줄</w:t></w:r></w:p><w:p><w:r><w:t>미주 둘째 줄</w:t></w:r></w:p></w:endnote></w:endnotes>"#,
        ),
        (
            "word/footnotes.xml",
            r#"<w:footnotes xmlns:w="w"><w:footnote w:id="1"><w:p><w:r><w:t>각주 글</w:t></w:r></w:p></w:footnote></w:footnotes>"#,
        ),
    ]);
    let mut doc = open(HwpBackend::docx(), bytes, &options()).unwrap();
    assert_eq!(doc.info().dropped_objects, 0);
    let text: String = (0..doc.info().page_count)
        .map(|page| doc.extract_text(page).unwrap())
        .collect();
    assert!(!text.contains(papy_office::ENDNOTE_MARK), "{text:?}");
    for part in ["본문", "계속", "미주 첫 줄", "미주 둘째 줄", "각주 글"] {
        assert!(text.contains(part), "{part}: {text:?}");
    }
    // DOCX 로 저장하면 미주 부분과 참조가 생기고, 다시 열어도 그대로다.
    let docx = doc.save(SaveFormat::Docx).unwrap();
    let body = zip_text(&docx, "word/document.xml");
    assert_eq!(body.matches("<w:endnoteReference").count(), 1, "{body}");
    assert_eq!(body.matches("<w:footnoteReference").count(), 1, "{body}");
    let endnotes = zip_text(&docx, "word/endnotes.xml");
    assert!(
        endnotes.contains("미주 첫 줄") && endnotes.contains("미주 둘째 줄"),
        "{endnotes}"
    );
    assert!(zip_text(&docx, "[Content_Types].xml").contains("endnotes+xml"));
    let mut again = open(HwpBackend::docx(), docx, &options()).unwrap();
    assert_eq!(again.info().dropped_objects, 0);
    // HWP 로 저장해도 미주가 남는다.
    let hwp = again.save(SaveFormat::Hwp).unwrap();
    let mut hwp_doc = open(HwpBackend::hwp(), hwp, &options()).unwrap();
    let text: String = (0..hwp_doc.info().page_count)
        .map(|page| hwp_doc.extract_text(page).unwrap())
        .collect();
    assert!(text.contains("미주 둘째 줄"), "{text:?}");
}

fn zip_parts(parts: &[(&str, &str)]) -> Vec<u8> {
    use std::io::Write;
    let mut buffer = std::io::Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    for (name, xml) in parts {
        zip.start_file(*name, zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(xml.as_bytes()).unwrap();
    }
    zip.finish().unwrap();
    buffer.into_inner()
}

fn zip_text(bytes: &[u8], name: &str) -> String {
    let mut zip = zip::ZipArchive::new(std::io::Cursor::new(bytes)).unwrap();
    let mut text = String::new();
    std::io::Read::read_to_string(&mut zip.by_name(name).unwrap(), &mut text).unwrap();
    text
}

/// 도형 4개: 빨간 사각형(쪽 기준 위치), 글상자(+ 같은 내용의 VML 대체본), 글자처럼 놓은 타원, 선.
fn docx_with_shapes() -> Vec<u8> {
    let wsp = |geometry: &str, fill: &str, extra: &str| {
        format!(
            r#"<a:graphic><a:graphicData><wps:wsp><wps:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1270000" cy="635000"/></a:xfrm><a:prstGeom prst="{geometry}"/>{fill}<a:ln w="12700"><a:solidFill><a:srgbClr val="000000"/></a:solidFill></a:ln></wps:spPr>{extra}<wps:bodyPr/></wps:wsp></a:graphicData></a:graphic>"#
        )
    };
    let red = wsp(
        "rect",
        r#"<a:solidFill><a:srgbClr val="FF0000"/></a:solidFill>"#,
        "",
    );
    let textbox = wsp(
        "rect",
        r#"<a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>"#,
        r#"<wps:txbx><w:txbxContent><w:p><w:r><w:t>상자 첫 줄</w:t></w:r></w:p><w:p><w:r><w:t>상자 &amp; 둘째</w:t></w:r></w:p></w:txbxContent></wps:txbx>"#,
    );
    let ellipse = wsp("ellipse", "<a:noFill/>", "");
    let line = wsp("line", "", "");
    let anchor = |pos: &str, body: &str| {
        format!(
            r#"<w:r><w:drawing><wp:anchor behindDoc="0"><wp:positionH relativeFrom="page"><wp:posOffset>{pos}</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV><wp:extent cx="1270000" cy="635000"/><wp:wrapSquare/><wp:docPr id="1"/>{body}</wp:anchor></w:drawing></w:r>"#
        )
    };
    let document = format!(
        r#"<w:document xmlns:w="w" xmlns:wp="wp" xmlns:a="a" xmlns:wps="wps" xmlns:mc="mc" xmlns:v="v"><w:body><w:p><w:r><w:t>본문 앞</w:t></w:r>{red}<w:r><mc:AlternateContent><mc:Choice Requires="wps"><w:drawing><wp:anchor><wp:positionH relativeFrom="margin"><wp:posOffset>2540000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV><wp:extent cx="1905000" cy="952500"/><wp:wrapNone/><wp:docPr id="2"/>{textbox}</wp:anchor></w:drawing></mc:Choice><mc:Fallback><w:pict><v:rect><v:textbox><w:txbxContent><w:p><w:r><w:t>상자 첫 줄</w:t></w:r></w:p></w:txbxContent></v:textbox></v:rect></w:pict></mc:Fallback></mc:AlternateContent></w:r><w:r><w:t xml:space="preserve"> 본문 뒤</w:t></w:r></w:p><w:p><w:r><w:drawing><wp:inline><wp:extent cx="635000" cy="635000"/><wp:docPr id="3"/>{ellipse}</wp:inline></w:drawing></w:r>{line}</w:p><w:p><w:r><w:t>끝</w:t></w:r></w:p></w:body></w:document>"#,
        red = anchor("381000", &red),
        line = anchor("3810000", &line),
    );
    zip_parts(&[
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", &document),
    ])
}

#[test]
fn docx_shapes_are_kept_and_saved() {
    let mut doc = open(HwpBackend::docx(), docx_with_shapes(), &options()).unwrap();
    assert_eq!(doc.info().dropped_objects, 0);
    let text = doc.extract_text(0).unwrap();
    // 도형 앞뒤 글자가 한 문단에 그대로 있고, VML 대체본의 글자가 두 번 나오지 않는다.
    assert!(text.contains("본문 앞 본문 뒤"), "{text:?}");
    assert!(!text.contains(papy_office::SHAPE_MARK), "{text:?}");
    assert!(text.matches("상자 첫 줄").count() <= 1, "{text:?}");
    // 빨간 사각형이 그려진다.
    let page = doc.render_page(0, &RenderRequest::with_scale(1.0)).unwrap();
    let red = page
        .pixels
        .chunks_exact(4)
        .filter(|p| p[0] > 200 && p[1] < 60 && p[2] < 60 && p[3] > 200)
        .count();
    assert!(red > 2_000, "red pixels {red}");

    // DOCX 로 저장하면 도형 4개와 글상자 글자가 남는다.
    let docx = doc.save(SaveFormat::Docx).unwrap();
    let body = zip_text(&docx, "word/document.xml");
    assert_eq!(body.matches("<wps:wsp>").count(), 4, "{body}");
    assert!(body.contains(r#"prst="ellipse""#) && body.contains(r#"prst="line""#));
    assert!(body.contains(r#"<a:srgbClr val="FF0000"/>"#), "{body}");
    assert!(
        body.contains("상자 첫 줄") && body.contains("상자 &amp; 둘째"),
        "{body}"
    );
    assert!(body.contains("<wp:inline"), "{body}");
    // 다시 열어도 도형이 그대로다.
    let mut again = open(HwpBackend::docx(), docx, &options()).unwrap();
    assert_eq!(again.info().dropped_objects, 0);
    let body = zip_text(&again.save(SaveFormat::Docx).unwrap(), "word/document.xml");
    assert_eq!(body.matches("<wps:wsp>").count(), 4, "{body}");
    // HWPX 로도 저장된다.
    assert!(again.save(SaveFormat::Hwpx).is_ok());
}

#[test]
fn docx_unsupported_drawings_are_counted() {
    // 빈 묶음 도형과 표 안 글상자: 빈 묶음은 빠진 것으로 세고, 표 안 글상자는 글자만 남긴다.
    let document = r#"<w:document xmlns:w="w" xmlns:wp="wp" xmlns:a="a" xmlns:wps="wps" xmlns:wpg="wpg"><w:body><w:p><w:r><w:drawing><wp:inline><wp:extent cx="100000" cy="100000"/><a:graphic><a:graphicData><wpg:wgp><wps:wsp/></wpg:wgp></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:drawing><wp:inline><wp:extent cx="100000" cy="100000"/><a:graphic><a:graphicData><wps:wsp><wps:txbx><w:txbxContent><w:p><w:r><w:t>칸 안 상자</w:t></w:r></w:p></w:txbxContent></wps:txbx></wps:wsp></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p></w:tc></w:tr></w:tbl><w:p/></w:body></w:document>"#;
    let bytes = zip_parts(&[
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", document),
    ]);
    let mut doc = open(HwpBackend::docx(), bytes, &options()).unwrap();
    assert_eq!(doc.info().dropped_objects, 2);
    let text = doc.extract_text(0).unwrap();
    assert!(text.contains("칸 안 상자"), "{text:?}");
}

#[test]
fn docx_group_shapes_are_unpacked() {
    // 묶음 좌표 200×100 을 2,540,000×1,270,000 EMU(200×100pt)로: 빨간 사각형(왼쪽 반), 파란 타원(오른쪽 반)
    let document = r#"<w:document xmlns:w="w" xmlns:wp="wp" xmlns:a="a" xmlns:wps="wps" xmlns:wpg="wpg"><w:body><w:p><w:r><w:t>묶음 앞</w:t></w:r><w:r><w:drawing><wp:anchor behindDoc="0"><wp:positionH relativeFrom="page"><wp:posOffset>1270000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV><wp:extent cx="2540000" cy="1270000"/><wp:wrapSquare/><wp:docPr id="1"/><a:graphic><a:graphicData><wpg:wgp><wpg:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="2540000" cy="1270000"/><a:chOff x="0" y="0"/><a:chExt cx="200" cy="100"/></a:xfrm></wpg:grpSpPr><wps:wsp><wps:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm><a:prstGeom prst="rect"/><a:solidFill><a:srgbClr val="FF0000"/></a:solidFill></wps:spPr><wps:bodyPr/></wps:wsp><wps:wsp><wps:spPr><a:xfrm><a:off x="100" y="0"/><a:ext cx="100" cy="100"/></a:xfrm><a:prstGeom prst="ellipse"/><a:solidFill><a:srgbClr val="0000FF"/></a:solidFill></wps:spPr><wps:bodyPr/></wps:wsp></wpg:wgp></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r><w:r><w:t xml:space="preserve"> 묶음 뒤</w:t></w:r></w:p></w:body></w:document>"#;
    let bytes = zip_parts(&[
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", document),
    ]);
    let mut doc = open(HwpBackend::docx(), bytes, &options()).unwrap();
    assert_eq!(doc.info().dropped_objects, 0);
    let page = doc.render_page(0, &RenderRequest::with_scale(1.0)).unwrap();
    let count = |want: fn(&[u8]) -> bool| page.pixels.chunks_exact(4).filter(|p| want(p)).count();
    let red = count(|p| p[0] > 200 && p[1] < 60 && p[2] < 60);
    let blue = count(|p| p[2] > 200 && p[0] < 60 && p[1] < 60);
    // 사각형 100×100pt = 10,000 px, 타원 약 7,850 px(배율 1 = 1pt 당 1px)
    assert!(red > 8_000, "red {red}");
    assert!(blue > 6_000, "blue {blue}");
    // 두 도형이 나란히: 빨강은 왼쪽, 파랑은 오른쪽
    let column = |want: fn(&[u8]) -> bool| {
        let width = page.width as usize;
        let xs: Vec<usize> = page
            .pixels
            .chunks_exact(4)
            .enumerate()
            .filter(|(_, p)| want(p))
            .map(|(i, _)| i % width)
            .collect();
        xs.iter().sum::<usize>() / xs.len().max(1)
    };
    let (red_x, blue_x) = (
        column(|p| p[0] > 200 && p[1] < 60 && p[2] < 60),
        column(|p| p[2] > 200 && p[0] < 60 && p[1] < 60),
    );
    assert!(blue_x > red_x + 60, "red x {red_x}, blue x {blue_x}");
    let text = doc.extract_text(0).unwrap();
    assert!(text.contains("묶음 앞 묶음 뒤"), "{text:?}");
    // DOCX 로 저장하면 도형 둘(묶음은 풀린 채)
    let body = zip_text(&doc.save(SaveFormat::Docx).unwrap(), "word/document.xml");
    assert_eq!(body.matches("<wps:wsp>").count(), 2, "{body}");
}

// ---- 표·그림 ----

fn table_of(doc: &mut Box<dyn Document>) -> papy_model::PageObject {
    doc.page_objects(0)
        .unwrap()
        .into_iter()
        .find(|o| o.kind == papy_model::ObjectKind::Table)
        .expect("table on page")
}

#[test]
fn tables_cells_rows_and_merge() {
    use papy_model::{CaretMotion, HitTarget, TableOp};
    let mut doc = editable(1);
    let depth = doc.undo_depth();
    doc.insert_table(pos(0, 0), 2, 3).unwrap();
    let table = table_of(&mut doc);
    assert_eq!((table.rows, table.cols, table.cells.len()), (2, 3, 6));
    // 둘째 칸 가운데를 누르면 그 칸 안
    let cell = table
        .cells
        .iter()
        .find(|c| c.row == 0 && c.col == 1)
        .unwrap();
    let hit = doc
        .hit_test_target(
            0,
            cell.rect.x + cell.rect.width / 2.0,
            cell.rect.y + cell.rect.height / 2.0,
        )
        .unwrap();
    let HitTarget::Cell(at) = hit else {
        panic!("expected a cell, got {hit:?}")
    };
    assert_eq!(at.cell, cell.cell);
    assert_eq!(at.control, table.object.control);
    // 칸 안에 치고, 줄 나누고, 지운다
    let after = doc.cell_insert_text(at, "칸").unwrap();
    let after = doc.cell_insert_text(after, "글").unwrap();
    assert_eq!(after.offset, 2);
    let rect = doc.cell_caret_rect(after).unwrap();
    assert!(
        rect.x >= cell.rect.x && rect.height > 0.0,
        "{rect:?} in {:?}",
        cell.rect
    );
    let split = doc.cell_insert_text(after, "\nX").unwrap();
    assert_eq!((split.cell_paragraph, split.offset), (1, 1));
    let back = doc.cell_delete_backward(split).unwrap();
    let joined = doc.cell_delete_backward(back).unwrap();
    assert_eq!((joined.cell_paragraph, joined.offset), (0, 2));
    assert_eq!(
        doc.cell_move_caret(joined, CaretMotion::Left)
            .unwrap()
            .offset,
        1
    );
    assert!(
        all_text(&mut doc).contains("칸글"),
        "{}",
        all_text(&mut doc)
    );
    // 행 넣기 → 3행, 열 지우기 → 2열, 칸 합치기
    let object = table.object;
    doc.edit_table(
        object,
        TableOp::InsertRow {
            row: 1,
            below: true,
        },
    )
    .unwrap();
    assert_eq!(table_of(&mut doc).rows, 3);
    doc.edit_table(object, TableOp::DeleteColumn { col: 2 })
        .unwrap();
    assert_eq!(table_of(&mut doc).cols, 2);
    doc.edit_table(
        object,
        TableOp::InsertColumn {
            col: 0,
            right: false,
        },
    )
    .unwrap();
    assert_eq!(table_of(&mut doc).cols, 3);
    doc.edit_table(
        object,
        TableOp::Merge {
            start_row: 2,
            start_col: 0,
            end_row: 2,
            end_col: 1,
        },
    )
    .unwrap();
    let merged = table_of(&mut doc);
    assert!(
        merged.cells.iter().any(|c| c.row == 2 && c.col_span == 2),
        "{:?}",
        merged.cells
    );
    doc.edit_table(object, TableOp::DeleteRow { row: 2 })
        .unwrap();
    assert_eq!(table_of(&mut doc).rows, 2);
    // 저장해도 표와 칸 글자가 남는다
    for format in [SaveFormat::Hwpx, SaveFormat::Hwp, SaveFormat::Docx] {
        assert!(!doc.save(format).unwrap().is_empty());
    }
    // 표 지우기 → 되돌리기로 표가 돌아온다
    doc.delete_object(object, papy_model::ObjectKind::Table)
        .unwrap();
    assert!(doc.page_objects(0).unwrap().is_empty());
    doc.undo().unwrap();
    assert_eq!(table_of(&mut doc).rows, 2);
    // 처음까지 되돌리면 표가 없다
    while doc.undo_depth() > depth {
        doc.undo().unwrap();
    }
    assert!(doc.page_objects(0).unwrap().is_empty());
    // 잘못된 크기
    assert!(doc.insert_table(pos(0, 0), 0, 3).is_err());
}

#[test]
fn pictures_insert_resize_delete() {
    let mut doc = editable(1);
    // 첫 문단 가운데에 넣으면 그 글줄에 글자처럼 놓인다(쪽 왼쪽 위에 떠 있지 않다)
    let line = doc.caret_rect(pos(0, 3)).unwrap();
    doc.insert_picture(pos(0, 3), RED_PNG, 120.0, 60.0).unwrap();
    let picture = doc
        .page_objects(0)
        .unwrap()
        .into_iter()
        .find(|o| o.kind == papy_model::ObjectKind::Picture)
        .expect("picture on page");
    assert!(
        (picture.rect.width - 120.0).abs() < 2.0 && (picture.rect.height - 60.0).abs() < 2.0,
        "{:?}",
        picture.rect
    );
    assert!(
        picture.rect.x > line.x - 2.0 && picture.rect.y + picture.rect.height > line.y,
        "{:?} vs line {line:?}",
        picture.rect
    );
    assert_eq!(picture.object.paragraph, 0, "{:?}", picture.object);
    // 빨간 그림이 그려진다
    let page = doc.render_page(0, &RenderRequest::with_scale(1.0)).unwrap();
    let red = page
        .pixels
        .chunks_exact(4)
        .filter(|p| p[0] > 200 && p[1] < 60 && p[2] < 60)
        .count();
    assert!(red > 3_000, "red pixels {red}");
    doc.resize_picture(picture.object, 60.0, 30.0).unwrap();
    let resized = doc
        .page_objects(0)
        .unwrap()
        .into_iter()
        .find(|o| o.kind == papy_model::ObjectKind::Picture)
        .unwrap();
    assert!(
        (resized.rect.width - 60.0).abs() < 2.0,
        "{:?}",
        resized.rect
    );
    // 크게 하면 글줄도 따라 커진다(글자가 그림 아래쪽 줄에 놓인다)
    let before = doc.extract_text(0).unwrap();
    let depth = doc.undo_depth();
    doc.resize_picture(picture.object, 240.0, 120.0).unwrap();
    // 글줄을 다시 재려고 넣고 지운 글자는 남지 않고, 되돌리기는 한 단계다
    assert_eq!(doc.extract_text(0).unwrap(), before);
    assert_eq!(doc.undo_depth(), depth + 1);
    let big = doc
        .page_objects(0)
        .unwrap()
        .into_iter()
        .find(|o| o.kind == papy_model::ObjectKind::Picture)
        .unwrap();
    let text = doc.caret_rect(pos(0, 0)).unwrap();
    assert!(
        text.y + text.height > big.rect.y + big.rect.height * 0.7,
        "text {text:?} vs picture {:?}",
        big.rect
    );
    doc.resize_picture(picture.object, 60.0, 30.0).unwrap();
    // 저장 → 다시 열어도 그림이 있다
    let hwpx = doc.save(SaveFormat::Hwpx).unwrap();
    let mut again = open(HwpBackend::hwpx(), hwpx, &options()).unwrap();
    assert!(
        again
            .page_objects(0)
            .unwrap()
            .iter()
            .any(|o| o.kind == papy_model::ObjectKind::Picture)
    );
    doc.delete_object(resized.object, papy_model::ObjectKind::Picture)
        .unwrap();
    assert!(
        doc.page_objects(0)
            .unwrap()
            .iter()
            .all(|o| o.kind != papy_model::ObjectKind::Picture)
    );
    // 그림이 아닌 바이트
    assert!(
        doc.insert_picture(pos(0, 0), b"not an image", 10.0, 10.0)
            .is_err()
    );
}

#[test]
fn table_after_inline_picture_at_line_end() {
    // 그림을 넣은 줄 끝을 누른 자리는 문단 안이고, 거기에 표를 넣을 수 있다
    let mut doc = editable(1);
    doc.insert_picture(pos(0, 3), RED_PNG, 120.0, 60.0).unwrap();
    let picture = doc
        .page_objects(0)
        .unwrap()
        .into_iter()
        .find(|o| o.kind == papy_model::ObjectKind::Picture)
        .unwrap()
        .rect;
    let hit = doc
        .hit_test_target(
            0,
            picture.x + picture.width + 200.0,
            picture.y + picture.height - 3.0,
        )
        .unwrap();
    let papy_model::HitTarget::Body(end) = hit else {
        panic!("{hit:?}")
    };
    assert_eq!(end.paragraph, 0);
    doc.insert_table(end, 2, 2).unwrap();
    // 표는 줄 끝에 들어간다(그림 때문에 한 글자 앞에서 나뉘지 않는다)
    let text = doc.extract_text(0).unwrap();
    assert!(text.lines().next().unwrap().ends_with("쪽 1"), "{text:?}");
    assert!(
        doc.page_objects(0)
            .unwrap()
            .iter()
            .any(|o| o.kind == papy_model::ObjectKind::Table)
    );
}

/// 쪽 0 의 그림 사각형
fn picture_rect(doc: &mut Box<dyn Document>) -> Option<papy_model::PageRect> {
    doc.page_objects(0)
        .unwrap()
        .into_iter()
        .find(|o| o.kind == papy_model::ObjectKind::Picture)
        .map(|o| o.rect)
}

#[test]
fn caret_positions_around_inline_picture() {
    // "Papy 쪽 1" 의 3 자리(p 와 y 사이)에 그림. 커서 자리는 그림도 한 칸: 3 은 그림 앞, 4 는 그림 뒤(y 앞)
    let mut doc = editable(1);
    doc.insert_picture(pos(0, 3), RED_PNG, 120.0, 60.0).unwrap();
    let rect = picture_rect(&mut doc).unwrap();
    let before = doc.caret_rect(pos(0, 3)).unwrap();
    let after = doc.caret_rect(pos(0, 4)).unwrap();
    assert!((before.x - rect.x).abs() < 1.0, "{before:?} {rect:?}");
    assert!(
        (after.x - (rect.x + rect.width)).abs() < 1.0,
        "{after:?} {rect:?}"
    );
    // 그림만 고르면 선택이 그림을 덮는다
    let picked = doc.selection_rects(pos(0, 3), pos(0, 4)).unwrap();
    assert!(
        picked
            .iter()
            .any(|r| r.x <= rect.x + 1.0 && r.x + r.width >= rect.x + rect.width - 1.0),
        "{picked:?} {rect:?}"
    );
    // 문단 글자는 개체 자리에 U+FFFC 를 둔다(자리 수 = 글자 수)
    let text = doc.text_in_range(pos(0, 0), pos(0, 9)).unwrap();
    assert_eq!(text, "Pap\u{FFFC}y 쪽 1");
    // 줄 끝을 누르면 문단 끝(글자 8 + 그림 1)
    let end = doc
        .hit_test(0, rect.x + rect.width + 200.0, rect.y + rect.height - 3.0)
        .unwrap();
    assert_eq!(end.offset, 9);
    // 그림 바로 뒤를 누르면 그 자리
    let hit = doc
        .hit_test(0, rect.x + rect.width + 0.5, rect.y + rect.height - 3.0)
        .unwrap();
    assert_eq!(hit.offset, 4, "{hit:?}");
    // 위아래로 옮겨도 문단 안 자리다
    let x = doc.caret_rect(end).unwrap().x;
    let down = doc.move_vertical(end, 1, x).unwrap();
    let back = doc.move_vertical(down, -1, x).unwrap();
    assert!(back.paragraph == 0 && back.offset <= 9, "{back:?}");
    // 그림 뒤에 친 글자는 그림 뒤에 들어간다
    let typed = doc.insert_text(pos(0, 4), "Z").unwrap().caret;
    assert_eq!(typed.offset, 5);
    assert_eq!(
        doc.text_in_range(pos(0, 0), pos(0, 10)).unwrap(),
        "Pap\u{FFFC}Zy 쪽 1"
    );
    let z = doc.caret_rect(typed).unwrap();
    assert!(z.x > rect.x + rect.width, "{z:?} {rect:?}");
    // 그림 앞에 친 글자는 그림 앞에
    doc.insert_text(pos(0, 3), "A").unwrap();
    assert_eq!(
        doc.text_in_range(pos(0, 0), pos(0, 11)).unwrap(),
        "PapA\u{FFFC}Zy 쪽 1"
    );
    // 그림 뒤에서 지우기 → 그림이 지워진다(글자는 그대로)
    doc.delete_backward(pos(0, 5)).unwrap();
    assert!(picture_rect(&mut doc).is_none());
    assert_eq!(
        doc.text_in_range(pos(0, 0), pos(0, 10)).unwrap(),
        "PapAZy 쪽 1"
    );
    // 되돌리면 그림이 돌아온다
    doc.undo().unwrap();
    assert!(picture_rect(&mut doc).is_some());
    // 그림 뒤에서 문단 나누기 → 그림은 앞 문단에 남는다
    doc.insert_text(pos(0, 5), "\n").unwrap();
    assert_eq!(
        doc.text_in_range(pos(0, 0), pos(0, 5)).unwrap(),
        "PapA\u{FFFC}"
    );
    let objects = doc.page_objects(0).unwrap();
    let moved = objects
        .iter()
        .find(|o| o.kind == papy_model::ObjectKind::Picture)
        .unwrap();
    assert_eq!(moved.object.paragraph, 0);
}

#[test]
fn docx_round_trip_keeps_inline_picture_place_and_table_borders() {
    let mut doc = editable(1);
    doc.insert_picture(pos(0, 3), RED_PNG, 60.0, 30.0).unwrap();
    doc.insert_table(pos(0, 9), 2, 2).unwrap();
    let docx = doc.save(SaveFormat::Docx).unwrap();
    let mut again = open(HwpBackend::docx(), docx, &options()).unwrap();
    let objects = again.page_objects(0).unwrap();
    let picture = objects
        .iter()
        .find(|o| o.kind == papy_model::ObjectKind::Picture)
        .expect("picture");
    // 그림은 글자 사이(p 와 y 사이)에 남는다: 문단 끝 글자보다 앞
    let end = again.caret_rect(pos(0, 9)).unwrap();
    assert!(
        picture.rect.x + picture.rect.width < end.x,
        "{:?} vs end {end:?}",
        picture.rect
    );
    // 표 테두리가 그려진다(위 테두리 줄에 어두운 점이 표 너비만큼)
    let table = objects
        .iter()
        .find(|o| o.kind == papy_model::ObjectKind::Table)
        .expect("table");
    let page = again
        .render_page(0, &RenderRequest::with_scale(1.0))
        .unwrap();
    let k = page.width as f32 / again.page_size(0).unwrap().width;
    let dark = |y: i64| {
        ((table.rect.x * k) as i64..((table.rect.x + table.rect.width) * k) as i64)
            .filter(|&x| {
                let i = ((y * page.width as i64 + x) * 4) as usize;
                page.pixels
                    .get(i..i + 3)
                    .is_some_and(|p| p.iter().all(|&c| c < 200))
            })
            .count()
    };
    let top = (table.rect.y * k).round() as i64;
    let best = (top - 3..=top + 3).map(dark).max().unwrap();
    assert!(
        best as f32 > table.rect.width * k * 0.8,
        "dark {best} of {}",
        table.rect.width
    );
}

#[test]
fn cell_selection_format_and_delete() {
    use papy_model::HitTarget;
    let mut doc = editable(1);
    doc.insert_table(pos(0, 0), 1, 2).unwrap();
    let table = table_of(&mut doc);
    let cell = table.cells.iter().find(|c| c.col == 0).unwrap();
    let HitTarget::Cell(at) = doc
        .hit_test_target(0, cell.rect.x + 4.0, cell.rect.y + cell.rect.height / 2.0)
        .unwrap()
    else {
        panic!("expected a cell")
    };
    let start = papy_model::CellPos { offset: 0, ..at };
    let end = doc.cell_insert_text(start, "Hello\nworld").unwrap();
    assert_eq!((end.cell_paragraph, end.offset), (1, 5));
    let hello = papy_model::CellPos { offset: 5, ..start };
    // 범위 글자·선택 사각형(칸 안)
    assert_eq!(doc.cell_text_in_range(start, end).unwrap(), "Hello\nworld");
    let rects = doc.cell_selection_rects(start, hello).unwrap();
    assert!(!rects.is_empty());
    assert!(
        rects
            .iter()
            .all(|r| r.x >= cell.rect.x - 1.0
                && r.x + r.width <= cell.rect.x + cell.rect.width + 1.0),
        "{rects:?} in {:?}",
        cell.rect
    );
    // 굵게: 첫 단어만
    let depth = doc.undo_depth();
    doc.cell_apply_char_format(
        start,
        hello,
        &CharFormat {
            bold: Some(true),
            ..CharFormat::default()
        },
    )
    .unwrap();
    assert_eq!(doc.undo_depth(), depth + 1);
    assert_eq!(
        doc.cell_char_format(papy_model::CellPos { offset: 3, ..start })
            .unwrap()
            .bold,
        Some(true)
    );
    assert_eq!(doc.cell_char_format(end).unwrap().bold, Some(false));
    // 다른 칸과 섞인 범위는 받지 않는다
    let other = papy_model::CellPos {
        cell: table.cells.iter().find(|c| c.col == 1).unwrap().cell,
        ..start
    };
    assert!(doc.cell_selection_rects(start, other).is_err());
    // 칸 문단을 넘는 범위 지우기 → 합쳐진다
    let caret = doc
        .cell_delete_range(
            papy_model::CellPos { offset: 2, ..start },
            papy_model::CellPos { offset: 2, ..end },
        )
        .unwrap();
    assert_eq!((caret.cell_paragraph, caret.offset), (0, 2));
    let rest = papy_model::CellPos { offset: 5, ..start };
    assert_eq!(doc.cell_text_in_range(start, rest).unwrap(), "Herld");
    // 되돌리기 한 번에 지우기 전으로
    doc.undo().unwrap();
    assert_eq!(doc.cell_text_in_range(start, end).unwrap(), "Hello\nworld");
}

#[test]
fn demultiply_matches_tiny_skia() {
    for a in 0..=255u8 {
        for c in 0..=a {
            let mut px = [c, c, c, a];
            demultiply_in_place(&mut px);
            let want = tiny_skia::PremultipliedColorU8::from_rgba(c, c, c, a)
                .unwrap()
                .demultiply();
            assert_eq!(
                px,
                [want.red(), want.green(), want.blue(), a],
                "c={c} a={a}"
            );
        }
    }
}

/// 다른 프로그램이 만든 HWPX: `mimetype` 이 압축됐거나 뒤에 있거나 없다. 앞부분 판별로는 ZIP 컨테이너지만 HWPX 로 열린다.
#[test]
fn hwpx_without_leading_stored_mimetype_opens() {
    use std::io::{Read, Write};
    let original = sample(2, true, None);
    let mut archive = zip::ZipArchive::new(std::io::Cursor::new(&original[..])).unwrap();
    let mut entries = Vec::new();
    for i in 0..archive.len() {
        let mut file = archive.by_index(i).unwrap();
        let mut data = Vec::new();
        file.read_to_end(&mut data).unwrap();
        entries.push((file.name().to_string(), data));
    }
    let rebuild = |order: &mut dyn Iterator<Item = &(String, Vec<u8>)>| {
        let mut buffer = std::io::Cursor::new(Vec::new());
        let mut zip = zip::ZipWriter::new(&mut buffer);
        for (name, data) in order {
            // 모두 deflate 로 압축한다(mimetype 포함).
            zip.start_file(name.as_str(), zip::write::SimpleFileOptions::default())
                .unwrap();
            zip.write_all(data).unwrap();
        }
        zip.finish().unwrap();
        buffer.into_inner()
    };
    let compressed = rebuild(&mut entries.iter());
    let mimetype_last = rebuild(
        &mut entries
            .iter()
            .filter(|(n, _)| n != "mimetype")
            .chain(entries.iter().filter(|(n, _)| n == "mimetype")),
    );
    let no_mimetype = rebuild(&mut entries.iter().filter(|(n, _)| n != "mimetype"));
    for (what, bytes) in [
        ("compressed", compressed),
        ("mimetype last", mimetype_last),
        ("no mimetype", no_mimetype),
    ] {
        assert!(
            !has_hwpx_mimetype(&bytes),
            "{what}: head sniff should not match"
        );
        let mut doc =
            open(HwpBackend::docx(), bytes, &options()).unwrap_or_else(|e| panic!("{what}: {e:?}"));
        let info = doc.info();
        assert_eq!(info.format, DocumentFormat::Hwpx, "{what}");
        assert_eq!(info.page_count, 2, "{what}");
        assert!(doc.extract_text(1).unwrap().contains("Papy 쪽 2"), "{what}");
    }
}

/// 개요 문단(한글 기본 스타일 "개요 1"·"개요 2")을 가진 HWP 문서. 셋째 문단은 둘째 쪽에 있다.
fn outlined(hwpx: bool) -> Vec<u8> {
    let mut core = DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    let style = |core: &DocumentCore, name: &str| {
        core.document()
            .doc_info
            .styles
            .iter()
            .position(|s| s.local_name == name)
            .unwrap_or_else(|| panic!("빈 문서에 {name} 스타일이 없다"))
    };
    let lines = ["서론", "배경", "본문 글", "본론"];
    for (i, line) in lines.iter().enumerate() {
        if i > 0 {
            let prev = lines[i - 1].chars().count();
            if i == 3 {
                core.insert_page_break_native(0, i - 1, prev).unwrap();
            } else {
                core.split_paragraph_native(0, i - 1, prev, None).unwrap();
            }
        }
        core.insert_text_native(0, i, 0, line).unwrap();
    }
    let (h1, h2) = (style(&core, "개요 1"), style(&core, "개요 2"));
    core.apply_style_native(0, 0, h1).unwrap();
    core.apply_style_native(0, 1, h2).unwrap();
    core.apply_style_native(0, 3, h1).unwrap();
    if hwpx {
        core.export_hwpx_native().unwrap()
    } else {
        core.export_hwp_native().unwrap()
    }
}

#[test]
fn hwp_outline_follows_outline_paragraphs() {
    for (backend, bytes) in [
        (HwpBackend::hwp(), outlined(false)),
        (HwpBackend::hwpx(), outlined(true)),
    ] {
        let mut doc = open(backend, bytes, &options()).unwrap();
        assert!(doc.info().capabilities.outline);
        let outline = doc.outline().unwrap();
        assert_eq!(outline.len(), 2, "{outline:?}");
        assert!(outline[0].title.ends_with("서론"), "{outline:?}");
        assert_eq!(outline[0].page, Some(0));
        assert_eq!(outline[0].children.len(), 1, "{outline:?}");
        assert!(outline[0].children[0].title.ends_with("배경"));
        assert!(outline[1].title.ends_with("본론"), "{outline:?}");
        assert_eq!(outline[1].page, Some(1));
        // 번호는 화면에 그려지는 그대로 앞에 붙는다("1. 서론" 등).
        assert_ne!(outline[0].title, "서론", "{outline:?}");
    }
}

#[test]
fn plain_hwp_has_empty_outline() {
    let mut doc = open(HwpBackend::hwp(), sample(2, false, None), &options()).unwrap();
    assert!(doc.outline().unwrap().is_empty());
}

fn docx_with_headings() -> Vec<u8> {
    use std::io::Write;
    let styles = r#"<w:styles xmlns:w="w"><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/></w:style><w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Heading1"/></w:style><w:style w:type="paragraph" w:styleId="Custom"><w:name w:val="내 제목"/><w:pPr><w:outlineLvl w:val="0"/></w:pPr></w:style></w:styles>"#;
    let document = r#"<w:document xmlns:w="w"><w:body><w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>첫 장</w:t></w:r></w:p><w:p><w:r><w:t>본문 &amp; 글</w:t></w:r></w:p><w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>첫 절</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>표 안 제목</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p><w:r><w:br w:type="page"/></w:r></w:p><w:p><w:pPr><w:pStyle w:val="Custom"/></w:pPr><w:r><w:t>둘째 장</w:t></w:r></w:p><w:p><w:pPr><w:pStyle w:val="Heading1"/><w:outlineLvl w:val="9"/></w:pPr><w:r><w:t>본문으로 바꾼 제목</w:t></w:r></w:p></w:body></w:document>"#;
    let mut buffer = std::io::Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    let options = zip::write::SimpleFileOptions::default();
    for (name, bytes) in [
        ("[Content_Types].xml", &b"<Types/>"[..]),
        ("word/document.xml", document.as_bytes()),
        ("word/styles.xml", styles.as_bytes()),
    ] {
        zip.start_file(name, options).unwrap();
        zip.write_all(bytes).unwrap();
    }
    zip.finish().unwrap();
    buffer.into_inner()
}

#[test]
fn docx_outline_uses_heading_styles() {
    let mut doc = open(HwpBackend::docx(), docx_with_headings(), &options()).unwrap();
    assert!(doc.info().capabilities.outline);
    let outline = doc.outline().unwrap();
    let titles: Vec<_> = outline.iter().map(|item| item.title.as_str()).collect();
    assert_eq!(titles, ["첫 장", "둘째 장"], "{outline:?}");
    assert_eq!(outline[0].page, Some(0));
    assert_eq!(outline[0].children.len(), 1);
    assert_eq!(outline[0].children[0].title, "첫 절");
    assert_eq!(
        outline[1].page,
        Some(doc.info().page_count - 1),
        "{outline:?}"
    );

    // DOCX 로 저장해도 제목 수준이 남는다.
    let saved = doc.save(SaveFormat::Docx).unwrap();
    let mut reopened = open(HwpBackend::docx(), saved, &options()).unwrap();
    assert_eq!(reopened.outline().unwrap(), outline);
}

#[test]
fn hwp_outline_survives_docx_export() {
    let mut doc = open(HwpBackend::hwp(), outlined(false), &options()).unwrap();
    let docx = doc.save(SaveFormat::Docx).unwrap();
    let mut reopened = open(HwpBackend::docx(), docx, &options()).unwrap();
    let outline = reopened.outline().unwrap();
    assert_eq!(outline.len(), 2, "{outline:?}");
    assert!(outline[0].title.ends_with("서론"), "{outline:?}");
    assert!(
        outline[0].children[0].title.ends_with("배경"),
        "{outline:?}"
    );
    assert!(outline[1].title.ends_with("본론"), "{outline:?}");
}

#[test]
fn outline_tree_nests_by_level() {
    use crate::document::outline_tree;
    let flat = vec![
        (1, "가".to_owned(), Some(0)),
        (3, "가-1".to_owned(), Some(0)),
        (2, "가-2".to_owned(), None),
        (1, "나".to_owned(), Some(2)),
        (2, "나-1".to_owned(), Some(3)),
    ];
    let tree = outline_tree(flat);
    assert_eq!(tree.len(), 2);
    let first: Vec<_> = tree[0].children.iter().map(|c| c.title.as_str()).collect();
    assert_eq!(first, ["가-1", "가-2"]);
    assert_eq!(tree[1].children[0].title, "나-1");
    assert_eq!(tree[1].page, Some(2));
}

#[test]
fn missing_picture_is_reported() {
    let mut doc = editable(1);
    assert!(doc.unsupported_objects(0).unwrap().is_empty());
    doc.insert_picture(pos(0, 3), RED_PNG, 120.0, 60.0).unwrap();
    assert!(doc.unsupported_objects(0).unwrap().is_empty());
    // 그림 데이터가 없는 떠 있는 그림(결재 문서의 서명 칸처럼 자리만 남은 것). 글자처럼 놓인 그림은
    // rhwp 가 자리표시 없이 건너뛰어서 세지 못한다.
    let mut core = DocumentCore::from_bytes(&doc.save(SaveFormat::Hwp).unwrap()).unwrap();
    for paragraph in &mut core.document_mut().sections[0].paragraphs {
        for control in &mut paragraph.controls {
            if let rhwp::model::control::Control::Picture(picture) = control {
                picture.image_attr.bin_data_id = 999;
                picture.common.treat_as_char = false;
            }
        }
    }
    let bytes = core.export_hwp_native().unwrap();
    let mut doc = open(HwpBackend::hwp(), bytes, &options()).unwrap();
    let objects = doc.unsupported_objects(0).unwrap();
    assert_eq!(objects.len(), 1, "{objects:?}");
    assert_eq!(objects[0].kind, papy_model::UnsupportedKind::MissingImage);
    let rect = objects[0].rect;
    assert!(
        (rect.width - 120.0).abs() < 2.0 && (rect.height - 60.0).abs() < 2.0,
        "{rect:?}"
    );
    // 그리기·저장은 그대로 된다.
    doc.render_page(0, &RenderRequest::with_scale(0.5)).unwrap();
}

#[test]
fn caret_moves_up_and_down_through_table_cells() {
    use papy_model::HitTarget;
    let mut doc = editable(1);
    // "Papy 쪽 1" 뒤에 문단을 하나 더 두고, 그 사이 표(2행 1열)
    doc.insert_text(pos(0, 8), "\n아래 문단").unwrap();
    doc.insert_table(pos(1, 0), 2, 1).unwrap();
    let table = table_of(&mut doc);
    let cell_at = |row: u32| *table.cells.iter().find(|c| c.row == row).unwrap();
    let (top, bottom) = (cell_at(0), cell_at(1));
    let HitTarget::Cell(first) = doc
        .hit_test_target(0, top.rect.x + 4.0, top.rect.y + top.rect.height / 2.0)
        .unwrap()
    else {
        panic!("expected the top cell")
    };
    // 칸 안에 두 줄: 아래로 한 줄은 같은 칸 둘째 문단
    let end = doc.cell_insert_text(first, "첫 줄\n둘째 줄").unwrap();
    let start = papy_model::CellPos {
        cell_paragraph: 0,
        offset: 0,
        ..end
    };
    let x = doc.cell_caret_rect(start).unwrap().x;
    let down = doc
        .move_vertical_target(HitTarget::Cell(start), 1, x)
        .unwrap();
    let HitTarget::Cell(second_line) = down else {
        panic!("expected to stay in the cell: {down:?}")
    };
    assert_eq!(
        (second_line.cell, second_line.cell_paragraph),
        (top.cell, 1)
    );
    // 한 줄 더 내려가면 아래 칸
    let down = doc
        .move_vertical_target(HitTarget::Cell(second_line), 1, x)
        .unwrap();
    let HitTarget::Cell(lower) = down else {
        panic!("expected the lower cell: {down:?}")
    };
    assert_eq!(lower.cell, bottom.cell);
    // 마지막 칸에서 더 내려가면 표 밖 본문
    let out = doc
        .move_vertical_target(HitTarget::Cell(lower), 1, x)
        .unwrap();
    assert!(matches!(out, HitTarget::Body(_)), "{out:?}");
    // 본문에서 위로 올라오면 다시 표 안
    let back = doc.move_vertical_target(out, -1, x).unwrap();
    assert!(
        matches!(back, HitTarget::Cell(c) if c.cell == bottom.cell),
        "{back:?}"
    );
}

// ---- DOCX: 묶음 안 그림·차트·첫 쪽 머리말 ----

/// 묶음 안 빨간 그림(왼쪽 반)과 녹색 막대 차트(글자처럼), "첫 쪽 다르게" 구역(첫 쪽 머리말은 비어 있음).
fn docx_with_group_picture_and_chart() -> Vec<u8> {
    use std::io::Write;
    let document = r#"<w:document xmlns:w="w" xmlns:wp="wp" xmlns:a="a" xmlns:pic="pic" xmlns:r="r" xmlns:wpg="wpg" xmlns:wps="wps" xmlns:c="c"><w:body>
<w:p><w:r><w:t>묶음 앞</w:t></w:r><w:r><w:drawing><wp:anchor behindDoc="0"><wp:positionH relativeFrom="page"><wp:posOffset>1270000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV><wp:extent cx="2540000" cy="1270000"/><wp:wrapSquare/><wp:docPr id="1"/><a:graphic><a:graphicData><wpg:wgp><wpg:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="2540000" cy="1270000"/><a:chOff x="0" y="0"/><a:chExt cx="200" cy="100"/></a:xfrm></wpg:grpSpPr>
<pic:pic><pic:blipFill><a:blip r:embed="rId9"/></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm><a:prstGeom prst="rect"/></pic:spPr></pic:pic>
<wps:wsp><wps:spPr><a:xfrm><a:off x="100" y="0"/><a:ext cx="100" cy="100"/></a:xfrm><a:prstGeom prst="rect"/><a:solidFill><a:srgbClr val="0000FF"/></a:solidFill></wps:spPr><wps:bodyPr/></wps:wsp>
</wpg:wgp></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r><w:r><w:t xml:space="preserve"> 묶음 뒤</w:t></w:r></w:p>
<w:p><w:r><w:drawing><wp:inline><wp:extent cx="3810000" cy="2540000"/><wp:docPr id="2"/><a:graphic><a:graphicData><c:chart r:id="rIdC"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
<w:p><w:r><w:t>차트 아래</w:t></w:r></w:p>
<w:sectPr><w:headerReference w:type="default" r:id="rIdH"/><w:headerReference w:type="first" r:id="rIdE"/><w:titlePg/></w:sectPr></w:body></w:document>"#;
    let chart = r#"<c:chartSpace xmlns:c="c" xmlns:a="a"><c:chart><c:title><c:tx><c:rich><a:p><a:r><a:t>분기 매출</a:t></a:r></a:p></c:rich></c:tx></c:title><c:autoTitleDeleted val="0"/><c:plotArea><c:barChart><c:barDir val="col"/><c:grouping val="clustered"/><c:ser><c:idx val="0"/><c:tx><c:strRef><c:strCache><c:pt idx="0"><c:v>매출</c:v></c:pt></c:strCache></c:strRef></c:tx><c:spPr><a:solidFill><a:srgbClr val="00B050"/></a:solidFill></c:spPr><c:cat><c:strRef><c:strCache><c:ptCount val="3"/><c:pt idx="0"><c:v>1분기</c:v></c:pt><c:pt idx="1"><c:v>2분기</c:v></c:pt><c:pt idx="2"><c:v>3분기</c:v></c:pt></c:strCache></c:strRef></c:cat><c:val><c:numRef><c:numCache><c:ptCount val="3"/><c:pt idx="0"><c:v>4</c:v></c:pt><c:pt idx="1"><c:v>8</c:v></c:pt><c:pt idx="2"><c:v>6</c:v></c:pt></c:numCache></c:numRef></c:val></c:ser></c:barChart><c:catAx/><c:valAx/></c:plotArea></c:chart></c:chartSpace>"#;
    let rels = r#"<Relationships xmlns="r"><Relationship Id="rId9" Type="image" Target="media/red.png"/><Relationship Id="rIdC" Type="chart" Target="charts/chart1.xml"/><Relationship Id="rIdH" Type="header" Target="header1.xml"/><Relationship Id="rIdE" Type="header" Target="header2.xml"/></Relationships>"#;
    let mut buffer = std::io::Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    let options = zip::write::SimpleFileOptions::default();
    for (name, bytes) in [
        ("[Content_Types].xml", &b"<Types/>"[..]),
        ("word/document.xml", document.as_bytes()),
        ("word/_rels/document.xml.rels", rels.as_bytes()),
        ("word/media/red.png", RED_PNG),
        ("word/charts/chart1.xml", chart.as_bytes()),
        (
            "word/header1.xml",
            r#"<w:hdr xmlns:w="w"><w:p><w:r><w:t>기본 머리말</w:t></w:r></w:p></w:hdr>"#.as_bytes(),
        ),
        (
            "word/header2.xml",
            r#"<w:hdr xmlns:w="w"><w:p/></w:hdr>"#.as_bytes(),
        ),
    ] {
        zip.start_file(name, options).unwrap();
        zip.write_all(bytes).unwrap();
    }
    zip.finish().unwrap();
    buffer.into_inner()
}

#[test]
fn docx_group_pictures_charts_and_first_page_header() {
    let mut doc = open(
        HwpBackend::docx(),
        docx_with_group_picture_and_chart(),
        &options(),
    )
    .unwrap();
    assert_eq!(doc.info().dropped_objects, 0);
    let page = doc.render_page(0, &RenderRequest::with_scale(1.0)).unwrap();
    let count = |want: fn(&[u8]) -> bool| page.pixels.chunks_exact(4).filter(|p| want(p)).count();
    // 묶음 안 그림(100×100pt), 옆 파란 사각형, 차트의 녹색 막대
    let red = count(|p| p[0] > 200 && p[1] < 80 && p[2] < 80);
    let blue = count(|p| p[2] > 200 && p[0] < 60 && p[1] < 60);
    let green = count(|p| p[1] > 150 && p[0] < 60 && p[2] > 40 && p[2] < 120);
    let bbox = |want: fn(&[u8]) -> bool| {
        let width = page.width as usize;
        let (mut x0, mut y0, mut x1, mut y1) = (usize::MAX, usize::MAX, 0, 0);
        for (i, p) in page.pixels.chunks_exact(4).enumerate() {
            if want(p) {
                let (x, y) = (i % width, i / width);
                x0 = x0.min(x);
                y0 = y0.min(y);
                x1 = x1.max(x);
                y1 = y1.max(y);
            }
        }
        (x0, y0, x1, y1)
    };
    let red_box = bbox(|p| p[0] > 200 && p[1] < 80 && p[2] < 80);
    let blue_box = bbox(|p| p[2] > 200 && p[0] < 60 && p[1] < 60);
    assert!(red > 8_000, "red {red} {red_box:?} blue {blue_box:?}");
    assert!(blue > 8_000, "blue {blue}");
    assert!(green > 2_000, "green {green}");
    // 묶음 안 그림과 사각형이 같은 높이에 나란히
    assert!(
        red_box.1.abs_diff(blue_box.1) <= 2,
        "{red_box:?} {blue_box:?}"
    );
    assert!(red_box.2 < blue_box.0 + 2, "{red_box:?} {blue_box:?}");
    let text = doc.extract_text(0).unwrap();
    assert!(text.contains("묶음 앞 묶음 뒤"), "{text:?}");
    assert!(text.contains("차트 아래"), "{text:?}");
    // 첫 쪽에서는 기본 머리말을 감춘다.
    assert!(!text.contains("기본 머리말"), "{text:?}");
    // 그림 둘(묶음 안 그림·차트)이 저장된다.
    let body = zip_text(&doc.save(SaveFormat::Docx).unwrap(), "word/document.xml");
    assert_eq!(body.matches("<pic:pic").count(), 2, "{body}");
    assert!(doc.save(SaveFormat::Pdf).is_ok());
}

#[test]
fn docx_table_footnotes_are_kept() {
    let document = r#"<w:document xmlns:w="w" xmlns:r="r"><w:body><w:p><w:r><w:t>앞</w:t></w:r><w:r><w:footnoteReference w:id="1"/></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>칸 글</w:t></w:r><w:r><w:footnoteReference w:id="2"/></w:r><w:r><w:t xml:space="preserve"> 뒤</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>옆 칸</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p><w:r><w:t>끝</w:t></w:r><w:r><w:footnoteReference w:id="3"/></w:r></w:p></w:body></w:document>"#;
    let footnotes = r#"<w:footnotes xmlns:w="w"><w:footnote w:id="1"><w:p><w:r><w:t>첫 각주</w:t></w:r></w:p></w:footnote><w:footnote w:id="2"><w:p><w:r><w:t>표 각주</w:t></w:r></w:p></w:footnote><w:footnote w:id="3"><w:p><w:r><w:t>셋째 각주</w:t></w:r></w:p></w:footnote></w:footnotes>"#;
    let bytes = zip_parts(&[
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", document),
        ("word/footnotes.xml", footnotes),
    ]);
    let mut doc = open(HwpBackend::docx(), bytes, &options()).unwrap();
    assert_eq!(doc.info().dropped_objects, 0);
    let text = doc.extract_text(0).unwrap();
    assert!(!text.contains(papy_office::FOOTNOTE_MARK), "{text:?}");
    for part in [
        "앞1)",
        "칸 글2) 뒤",
        "끝3)",
        "첫 각주",
        "표 각주",
        "셋째 각주",
    ] {
        assert!(text.contains(part), "{part}: {text:?}");
    }
    // 저장해도 각주 셋이 남는다.
    let body = zip_text(&doc.save(SaveFormat::Docx).unwrap(), "word/document.xml");
    assert!(body.matches("<w:footnoteReference").count() >= 2, "{body}");
    let mut hwp = open(
        HwpBackend::hwp(),
        doc.save(SaveFormat::Hwp).unwrap(),
        &options(),
    )
    .unwrap();
    let text = hwp.extract_text(0).unwrap();
    assert!(text.contains("표 각주"), "{text:?}");
}

#[test]
fn docx_tall_table_row_splits_across_pages() {
    let lines: String = (0..120)
        .map(|i| format!("<w:p><w:r><w:t>줄 {i}</w:t></w:r></w:p>"))
        .collect();
    let document = format!(
        r#"<w:document xmlns:w="w"><w:body><w:p><w:r><w:t>앞</w:t></w:r></w:p><w:tbl><w:tr><w:tc>{lines}</w:tc><w:tc><w:p><w:r><w:t>옆</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p><w:r><w:t>뒤</w:t></w:r></w:p></w:body></w:document>"#
    );
    let bytes = zip_parts(&[
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", &document),
    ]);
    let mut doc = open(HwpBackend::docx(), bytes, &options()).unwrap();
    // 쪽보다 긴 행은 잘리지 않고 다음 쪽으로 이어진다.
    let pages = doc.info().page_count;
    assert!(pages >= 3, "{pages}");
    let text: String = (0..pages).map(|p| doc.extract_text(p).unwrap()).collect();
    for part in ["줄 0", "줄 60", "줄 119", "뒤"] {
        assert!(text.contains(part), "{part}");
    }
}

#[test]
fn docx_first_page_hide_and_even_headers_survive_save() {
    let document = r#"<w:document xmlns:w="w" xmlns:r="r"><w:body><w:p><w:r><w:t>첫 쪽</w:t></w:r></w:p><w:p><w:r><w:br w:type="page"/><w:t>둘째 쪽</w:t></w:r></w:p><w:sectPr><w:headerReference w:type="even" r:id="rIdE"/><w:headerReference w:type="default" r:id="rIdH"/><w:titlePg/></w:sectPr></w:body></w:document>"#;
    let bytes = zip_parts(&[
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", document),
        (
            "word/_rels/document.xml.rels",
            r#"<Relationships xmlns="r"><Relationship Id="rIdH" Type="header" Target="header1.xml"/><Relationship Id="rIdE" Type="header" Target="header2.xml"/></Relationships>"#,
        ),
        (
            "word/header1.xml",
            r#"<w:hdr xmlns:w="w"><w:p><w:r><w:t>기본 머리말</w:t></w:r></w:p></w:hdr>"#,
        ),
        (
            "word/header2.xml",
            r#"<w:hdr xmlns:w="w"><w:p><w:r><w:t>짝수 머리말</w:t></w:r></w:p></w:hdr>"#,
        ),
    ]);
    let check = |doc: &mut Box<dyn Document>| {
        assert_eq!(doc.info().page_count, 2);
        let first = doc.extract_text(0).unwrap();
        assert!(!first.contains("머리말"), "{first:?}");
        // evenAndOddHeaders 가 없으니 짝수 쪽에도 기본 머리말이 나온다.
        let second = doc.extract_text(1).unwrap();
        assert!(
            second.contains("기본 머리말") && !second.contains("짝수"),
            "{second:?}"
        );
    };
    let mut doc = open(HwpBackend::docx(), bytes, &options()).unwrap();
    check(&mut doc);
    let saved = doc.save(SaveFormat::Docx).unwrap();
    assert!(zip_text(&saved, "word/document.xml").contains("<w:titlePg/>"));
    let mut reopened = open(HwpBackend::docx(), saved, &options()).unwrap();
    check(&mut reopened);
}
