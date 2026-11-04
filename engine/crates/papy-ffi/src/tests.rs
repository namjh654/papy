use super::*;
use std::io::Write;

fn docx(text: &str) -> Vec<u8> {
    let document = format!(
        r#"<w:document xmlns:w="w"><w:body><w:p><w:r><w:t>{text}</w:t></w:r></w:p></w:body></w:document>"#
    );
    let mut buffer = std::io::Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    for (name, xml) in [
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", document.as_str()),
    ] {
        zip.start_file(name, zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(xml.as_bytes()).unwrap();
    }
    zip.finish().unwrap();
    buffer.into_inner()
}

#[test]
fn version_matches_core() {
    // SAFETY: papy_engine_version 은 정적 수명의 NUL 종료 문자열을 돌려준다.
    let version = unsafe { CStr::from_ptr(papy_engine_version()) };
    assert_eq!(version.to_str(), Ok(papy_core::ENGINE_VERSION));
    assert_eq!(engine_version(), papy_core::ENGINE_VERSION);
}

#[test]
fn open_render_extract_close() {
    let engine = PapyEngine::new(None);
    let doc = engine.open(docx("모바일 엔진"), None).unwrap();
    assert_eq!(doc.format, "docx");
    assert_eq!(doc.page_count, 1);
    let size = engine.page_size(doc.session, 0).unwrap();
    assert!(size.width > 100.0 && size.height > 100.0);
    let image = engine.render_page(doc.session, 0, 0.5).unwrap();
    assert_eq!(image.rgba.len(), (image.width * image.height * 4) as usize);
    // 흰 배경
    assert_eq!(&image.rgba[..4], &[255, 255, 255, 255]);
    assert!(
        engine
            .extract_text(doc.session, 0)
            .unwrap()
            .contains("모바일 엔진")
    );
    assert!(matches!(
        engine.render_page(doc.session, 5, 1.0),
        Err(PapyError::PageOutOfRange { .. })
    ));
    engine.close(doc.session);
    engine.close(doc.session);
    assert!(matches!(
        engine.extract_text(doc.session, 0),
        Err(PapyError::SessionNotFound { .. })
    ));
    assert!(matches!(
        engine.page_size(0, 0),
        Err(PapyError::SessionNotFound { .. })
    ));
}

#[test]
fn rejects_pdf_and_garbage() {
    let engine = PapyEngine::new(Some("/nonexistent/font.ttf".into()));
    // PDF 는 모바일 엔진이 열지 않는다(운영체제 렌더러가 맡는다).
    assert!(engine.open(b"%PDF-1.7\n".to_vec(), None).is_err());
    assert!(matches!(
        engine.open(b"not a document".to_vec(), None),
        Err(PapyError::UnsupportedFormat { .. })
    ));
}

fn docx_paragraphs(paragraphs: &[&str]) -> Vec<u8> {
    let body: String = paragraphs
        .iter()
        .map(|text| format!("<w:p><w:r><w:t>{text}</w:t></w:r></w:p>"))
        .collect();
    let document = format!(r#"<w:document xmlns:w="w"><w:body>{body}</w:body></w:document>"#);
    let mut buffer = std::io::Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    for (name, xml) in [
        ("[Content_Types].xml", "<Types/>"),
        ("word/document.xml", document.as_str()),
    ] {
        zip.start_file(name, zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(xml.as_bytes()).unwrap();
    }
    zip.finish().unwrap();
    buffer.into_inner()
}

#[test]
fn tap_edit_undo_and_save_a_paragraph() {
    let engine = PapyEngine::new(None);
    let doc = engine
        .open(docx_paragraphs(&["첫 문단입니다", "둘째 문단"]), None)
        .unwrap();
    assert!(doc.can_edit);
    // 첫 문단 자리를 찾는다: 문서 처음 문단의 사각형 가운데를 다시 누른다.
    let first = engine.paragraph_at(doc.session, 0, 0.0, 0.0).unwrap();
    assert_eq!((first.section, first.paragraph), (0, 0));
    assert_eq!(first.text, "첫 문단입니다");
    let b = first.boxes[0];
    let again = engine
        .paragraph_at(doc.session, 0, b.x + b.width / 2.0, b.y + b.height / 2.0)
        .unwrap();
    assert_eq!(again.paragraph, 0);

    let depth = engine
        .replace_paragraph(doc.session, 0, 0, "바꾼 글".into())
        .unwrap();
    assert_eq!(depth, 2);
    let text = engine.extract_text(doc.session, 0).unwrap();
    assert!(
        text.contains("바꾼 글") && text.contains("둘째 문단") && !text.contains("첫 문단입니다"),
        "{text}"
    );
    // 마지막 문단도 고칠 수 있다(다음 문단이 없을 때 끝 찾기)
    engine
        .replace_paragraph(doc.session, 0, 1, "끝 문단\n새 문단".into())
        .unwrap();
    assert!(
        engine
            .extract_text(doc.session, 0)
            .unwrap()
            .contains("새 문단")
    );

    // 저장한 DOCX 를 다시 열면 바뀐 글이 있다
    let saved = engine.save(doc.session).unwrap();
    let reopened = engine.open(saved, None).unwrap();
    let text = engine.extract_text(reopened.session, 0).unwrap();
    assert!(
        text.contains("바꾼 글") && text.contains("새 문단"),
        "{text}"
    );

    // 되돌리기: 남은 단계가 0 이 될 때까지 부르면 처음으로
    let mut left = engine.undo_depth(doc.session).unwrap();
    assert!(left >= 3);
    while left > 0 {
        let undo = engine.undo(doc.session).unwrap();
        assert_eq!(undo.undo_depth, left - 1);
        left = undo.undo_depth;
    }
    assert!(engine.undo(doc.session).unwrap().caret.is_none());
    let text = engine.extract_text(doc.session, 0).unwrap();
    assert!(
        text.contains("첫 문단입니다") && !text.contains("새 문단"),
        "{text}"
    );
    assert!(engine.page_count(doc.session).unwrap() >= 1);
    assert!(
        engine
            .replace_paragraph(doc.session, 0, 99, "x".into())
            .is_err()
    );
}

#[test]
fn replacing_a_paragraph_keeps_untouched_character_styles() {
    let engine = PapyEngine::new(None);
    let doc = engine
        .open(docx_paragraphs(&["굵은 글 보통"]), None)
        .unwrap();
    let pos = |offset| EnginePosition {
        section: 0,
        paragraph: 0,
        offset,
    };
    let bold = EngineTextStyle {
        bold: Some(true),
        ..EngineTextStyle::default()
    };
    // "굵은" 을 굵게
    engine
        .apply_text_style(
            doc.session,
            EngineRange {
                start: pos(0),
                end: pos(2),
            },
            bold,
        )
        .unwrap();
    assert_eq!(
        engine.text_style(doc.session, pos(1)).unwrap().bold,
        Some(true)
    );
    // 뒤쪽만 고친다: 앞의 굵은 글자는 그대로 굵다
    engine
        .replace_paragraph(doc.session, 0, 0, "굵은 글 고침".into())
        .unwrap();
    assert_eq!(
        engine.text_style(doc.session, pos(1)).unwrap().bold,
        Some(true)
    );
    assert_eq!(
        engine.text_style(doc.session, pos(7)).unwrap().bold,
        Some(false)
    );
    let text = engine.extract_text(doc.session, 0).unwrap();
    assert!(text.contains("굵은 글 고침"), "{text}");
}

#[test]
fn cursor_typing_deleting_and_moving() {
    let engine = PapyEngine::new(None);
    let doc = engine
        .open(docx_paragraphs(&["가나다", "라마"]), None)
        .unwrap();
    let start = engine.caret_at_point(doc.session, 0, 0.0, 0.0).unwrap();
    assert_eq!(start.position.paragraph, 0);
    let end = engine
        .move_caret(doc.session, start.position, CaretDirection::LineEnd, 0.0)
        .unwrap();
    assert_eq!(end.position.offset, 3);
    // 이어 친 글자는 되돌리기 한 단계
    let typed = engine
        .insert_text(doc.session, end.position, "라".into())
        .unwrap();
    let typed = engine
        .insert_text(doc.session, typed.caret.position, "바".into())
        .unwrap();
    assert_eq!(typed.undo_depth, 1);
    assert_eq!(typed.caret.position.offset, 5);
    assert!(typed.caret.rect.height > 0.0);
    let deleted = engine
        .delete_backward(doc.session, typed.caret.position)
        .unwrap();
    assert_eq!(deleted.caret.position.offset, 4);
    // 다음 줄로
    let down = engine
        .move_caret(
            doc.session,
            deleted.caret.position,
            CaretDirection::Down,
            deleted.caret.rect.x,
        )
        .unwrap();
    assert_eq!(down.position.paragraph, 1);
    // 문단 처음에서 지우면 앞 문단과 합친다
    let line_start = engine
        .move_caret(doc.session, down.position, CaretDirection::LineStart, 0.0)
        .unwrap();
    let merged = engine
        .delete_backward(doc.session, line_start.position)
        .unwrap();
    assert_eq!(merged.caret.position.paragraph, 0);
    let all = EngineRange {
        start: EnginePosition {
            section: 0,
            paragraph: 0,
            offset: 0,
        },
        end: EnginePosition {
            section: 0,
            paragraph: 0,
            offset: 6,
        },
    };
    assert_eq!(
        engine.text_in_range(doc.session, all).unwrap(),
        "가나다라라마"
    );
    assert!(
        !engine
            .selection_regions(doc.session, all)
            .unwrap()
            .is_empty()
    );
    // 범위 지우기
    let cut = engine
        .delete_range(
            doc.session,
            EngineRange {
                start: all.start,
                end: EnginePosition {
                    offset: 3,
                    ..all.start
                },
            },
        )
        .unwrap();
    assert_eq!(cut.caret.position.offset, 0);
    assert!(
        engine
            .extract_text(doc.session, 0)
            .unwrap()
            .starts_with("라라마")
    );
}

#[test]
fn word_selection_and_style_validation() {
    let engine = PapyEngine::new(None);
    let doc = engine
        .open(docx_paragraphs(&["hello 세계 ok"]), None)
        .unwrap();
    let pos = EnginePosition {
        section: 0,
        paragraph: 0,
        offset: 7,
    };
    let word = engine.word_at(doc.session, pos).unwrap();
    assert_eq!((word.start.offset, word.end.offset), (6, 8));
    let bad = EngineTextStyle {
        color: Some("red".into()),
        ..EngineTextStyle::default()
    };
    assert!(engine.apply_text_style(doc.session, word, bad).is_err());
    let red = EngineTextStyle {
        color: Some("#ff0000".into()),
        font_size: Some(20.0),
        ..EngineTextStyle::default()
    };
    engine.apply_text_style(doc.session, word, red).unwrap();
    let style = engine.text_style(doc.session, word.end).unwrap();
    assert_eq!(style.color.as_deref(), Some("#ff0000"));
    assert_eq!(style.font_size, Some(20.0));
}

#[test]
fn exports_pdf_with_korean_text() {
    let engine = PapyEngine::new(None);
    let doc = engine
        .open(docx_paragraphs(&["한글 PDF 변환"]), None)
        .unwrap();
    let pdf = engine.export(doc.session, "pdf".into()).unwrap();
    assert!(pdf.starts_with(b"%PDF-"));
    // 글꼴이 들어갔다(글자가 빠지지 않았다)
    assert!(
        pdf.windows(9).any(|w| w == b"/FontFile"),
        "no embedded font"
    );
    assert!(engine.export(doc.session, "xyz".into()).is_err());
    // 작업 문서는 그대로
    assert!(engine.save(doc.session).is_ok());
}
