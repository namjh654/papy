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

    let steps = engine
        .replace_paragraph(doc.session, 0, 0, "바꾼 글".into())
        .unwrap();
    assert_eq!(steps, 2);
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

    // 되돌리기: 단계 수만큼 부르면 처음으로
    for _ in 0..(2 + steps) {
        assert!(engine.undo(doc.session).unwrap());
    }
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
