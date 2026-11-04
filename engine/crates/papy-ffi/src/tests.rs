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
