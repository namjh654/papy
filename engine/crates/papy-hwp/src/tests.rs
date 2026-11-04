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
    // 묶음 도형과 표 안 글상자: 묶음은 빠진 것으로 세고, 표 안 글상자는 글자만 남긴다.
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

#[test]
fn caret_positions_around_inline_picture() {
    // "Papy 쪽 1" 의 3 자리(p 와 y 사이)에 그림. 글자 자리 3 은 그림 앞, 4 는 y 뒤다.
    let mut doc = editable(1);
    doc.insert_picture(pos(0, 3), RED_PNG, 120.0, 60.0).unwrap();
    let picture = doc
        .page_objects(0)
        .unwrap()
        .into_iter()
        .find(|o| o.kind == papy_model::ObjectKind::Picture)
        .unwrap()
        .rect;
    let before = doc.caret_rect(pos(0, 3)).unwrap();
    let after_y = doc.caret_rect(pos(0, 4)).unwrap();
    assert!((before.x - picture.x).abs() < 1.0, "{before:?} {picture:?}");
    assert!(
        after_y.x > picture.x + picture.width,
        "{after_y:?} {picture:?}"
    );
    // 줄 끝을 누르면 문단 끝(글자 8개)
    let end = doc
        .hit_test(
            0,
            picture.x + picture.width + 200.0,
            picture.y + picture.height - 3.0,
        )
        .unwrap();
    assert_eq!(end.offset, 8);
    // 아래로 옮겨도 글자 자리다(그림 뒤 줄 끝 → 다음 줄)
    let x = doc.caret_rect(end).unwrap().x;
    let down = doc.move_vertical(end, 1, x).unwrap();
    assert!(down.paragraph > 0 || down.offset <= 8, "{down:?}");
    let back = doc.move_vertical(down, -1, x).unwrap();
    assert_eq!(back.paragraph, 0, "{back:?}");
    assert!(back.offset <= 8, "{back:?}");
    // 그 자리에 친 글자는 문단 끝에 붙는다
    doc.insert_text(end, "Z").unwrap();
    assert!(
        doc.extract_text(0)
            .unwrap()
            .lines()
            .next()
            .unwrap()
            .ends_with("1Z")
    );
}

#[test]
fn docx_round_trip_keeps_inline_picture_place_and_table_borders() {
    let mut doc = editable(1);
    doc.insert_picture(pos(0, 3), RED_PNG, 60.0, 30.0).unwrap();
    doc.insert_table(pos(0, 8), 2, 2).unwrap();
    let docx = doc.save(SaveFormat::Docx).unwrap();
    let mut again = open(HwpBackend::docx(), docx, &options()).unwrap();
    let objects = again.page_objects(0).unwrap();
    let picture = objects
        .iter()
        .find(|o| o.kind == papy_model::ObjectKind::Picture)
        .expect("picture");
    // 그림은 글자 사이(p 와 y 사이)에 남는다: 문단 끝 글자보다 앞
    let end = again.caret_rect(pos(0, 8)).unwrap();
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
