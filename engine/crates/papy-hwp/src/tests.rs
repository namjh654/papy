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
    // 알려진 한계(rhwp 0.8.7): rhwp 가 HWPX 로 쓴 그림은 rhwp 로 다시 열면 그려지지 않는다
    // (HWP 에서 바꾼 HWPX 도 같다). docs/formats/hwp-hwpx.md "알려진 한계" 참고.
}
