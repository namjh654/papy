use super::*;

/// 압축 없이 저장된 첫 항목 하나를 가진 ZIP 로컬 헤더를 만든다.
fn zip_head(name: &[u8], data: &[u8]) -> Vec<u8> {
    let mut head = b"PK\x03\x04".to_vec();
    head.extend_from_slice(&[0; 22]);
    head.extend_from_slice(&(name.len() as u16).to_le_bytes());
    head.extend_from_slice(&0u16.to_le_bytes());
    head.extend_from_slice(name);
    head.extend_from_slice(data);
    head
}

#[test]
fn detects_pdf() {
    assert_eq!(detect_format(b"%PDF-1.7\n"), Some(DocumentFormat::Pdf));
}

#[test]
fn detects_hwpx() {
    let head = zip_head(b"mimetype", b"application/hwp+zip");
    assert_eq!(detect_format(&head), Some(DocumentFormat::Hwpx));
}

#[test]
fn other_zip_is_container() {
    let head = zip_head(b"[Content_Types].xml", b"<?xml");
    assert_eq!(detect_format(&head), Some(DocumentFormat::ZipContainer));
}

#[test]
fn detects_compound_file() {
    let head = [papy_hwp::CFB_SIGNATURE, &[0; 8]].concat();
    assert_eq!(detect_format(&head), Some(DocumentFormat::CompoundFile));
    assert_eq!(
        detect_format(b"HWP Document File V3.00 \x1a\x01\x02\x03\x04\x05"),
        Some(DocumentFormat::CompoundFile)
    );
}

#[test]
fn truncated_or_unknown_input_is_safe() {
    assert_eq!(detect_format(b""), None);
    assert_eq!(
        detect_format(b"PK\x03\x04"),
        Some(DocumentFormat::ZipContainer)
    );
    assert_eq!(detect_format(b"hello"), None);
    // 이름 길이가 입력보다 길어도 패닉하지 않는다.
    let mut head = zip_head(b"mimetype", b"");
    head[26] = 0xFF;
    head[27] = 0xFF;
    assert_eq!(detect_format(&head), Some(DocumentFormat::ZipContainer));
}

// ---- 세션 동작: 가짜 PDF 어댑터로 검증한다 ----

/// 페이지마다 단색을 그리는 가짜 어댑터. 첫 바이트 뒤 숫자를 페이지 수로 읽는다("%PDF-3" → 3쪽).
struct FakePdf;

struct FakeDoc {
    pages: u32,
}

impl FormatBackend for FakePdf {
    fn format(&self) -> DocumentFormat {
        DocumentFormat::Pdf
    }

    fn open(&self, source: Source<'_>, options: &BackendOpenOptions) -> Result<Box<dyn Document>> {
        options.cancel.check()?;
        let bytes = match source {
            Source::Bytes(bytes) => bytes.to_vec(),
            Source::Path(path) => std::fs::read(path)?,
        };
        if options.password.as_deref() == Some("wrong") {
            return Err(Error::new(ErrorCode::PasswordRequired, "wrong password"));
        }
        let pages = std::str::from_utf8(&bytes[5..])
            .ok()
            .and_then(|s| s.trim().parse().ok())
            .ok_or_else(|| Error::new(ErrorCode::InvalidDocument, "bad fake pdf"))?;
        Ok(Box::new(FakeDoc { pages }))
    }
}

impl Document for FakeDoc {
    fn info(&self) -> DocumentInfo {
        DocumentInfo {
            format: DocumentFormat::Pdf,
            page_count: self.pages,
            byte_size: 0,
            title: None,
            dropped_objects: 0,
            capabilities: Capabilities {
                render: true,
                extract_text: true,
                ..Capabilities::default()
            },
        }
    }

    fn page_size(&mut self, _page: u32) -> Result<PageSize> {
        Ok(PageSize {
            width: 100.0,
            height: 200.0,
        })
    }

    fn render_page(&mut self, page: u32, request: &RenderRequest) -> Result<Bitmap> {
        let (width, height) = request.pixel_size(self.page_size(page)?);
        let shade = (page * 40) as u8;
        Ok(Bitmap {
            width,
            height,
            pixels: [shade, shade, shade, 255].repeat((width * height) as usize),
        })
    }

    fn extract_text(&mut self, page: u32) -> Result<String> {
        Ok(format!("page {page}"))
    }
}

fn fake_engine(limits: Limits) -> Engine {
    Engine::with_backends(limits, vec![Box::new(FakePdf)])
}

fn pdf(pages: u32) -> DocumentSource {
    DocumentSource::Bytes(Arc::from(format!("%PDF-{pages}").into_bytes()))
}

#[test]
fn open_render_and_close_session() {
    let engine = fake_engine(Limits::default());
    let id = engine.open(pdf(3), OpenOptions::default()).unwrap();
    let info = engine.info(id).unwrap();
    assert_eq!(
        (info.format, info.page_count, info.byte_size),
        (DocumentFormat::Pdf, 3, 6)
    );
    assert_eq!(engine.extract_text(id, 2).unwrap(), "page 2");

    let bitmap = engine
        .render_page(id, 1, &RenderRequest::with_scale(1.5))
        .unwrap();
    assert_eq!(
        (bitmap.width, bitmap.height, bitmap.stride()),
        (150, 300, 600)
    );
    assert_eq!(&bitmap.pixels[..4], &[40, 40, 40, 255]);

    engine.close(id).unwrap();
    assert_eq!(
        engine.info(id).unwrap_err().code,
        ErrorCode::SessionNotFound
    );
    assert_eq!(
        engine.close(id).unwrap_err().code,
        ErrorCode::SessionNotFound
    );
}

#[test]
fn opens_from_path() {
    let dir = std::env::temp_dir().join(format!("papy-core-test-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let path = dir.join("doc.pdf");
    std::fs::write(&path, "%PDF-2").unwrap();
    let engine = fake_engine(Limits::default());
    let id = engine
        .open(DocumentSource::Path(path.clone()), OpenOptions::default())
        .unwrap();
    assert_eq!(engine.page_count(id).unwrap(), 2);
    std::fs::remove_dir_all(&dir).unwrap();

    // 없는 파일의 오류 메시지에 경로가 들어가지 않는다.
    let missing = engine
        .open(DocumentSource::Path(path), OpenOptions::default())
        .unwrap_err();
    assert_eq!(missing.code, ErrorCode::Io);
    assert!(!missing.message.contains("papy-core-test"));
}

#[test]
fn unknown_and_unregistered_formats_are_rejected() {
    let engine = fake_engine(Limits::default());
    let unknown = engine.open(
        DocumentSource::Bytes(Arc::from(&b"hello"[..])),
        OpenOptions::default(),
    );
    assert_eq!(unknown.unwrap_err().code, ErrorCode::UnsupportedFormat);
    let hwpx = DocumentSource::Bytes(Arc::from(zip_head(b"mimetype", b"application/hwp+zip")));
    let error = engine.open(hwpx, OpenOptions::default()).unwrap_err();
    assert_eq!(error.code, ErrorCode::UnsupportedFormat);
    assert!(error.message.contains("HWPX"));
    assert_eq!(
        Engine::default().supported_formats(),
        [
            DocumentFormat::Hwpx,
            DocumentFormat::CompoundFile,
            DocumentFormat::ZipContainer
        ]
    );
    assert_eq!(
        Engine::with_pdfium(Limits::default(), "missing-pdfium").supported_formats(),
        [
            DocumentFormat::Pdf,
            DocumentFormat::Hwpx,
            DocumentFormat::CompoundFile,
            DocumentFormat::ZipContainer
        ]
    );
}

#[test]
fn missing_pdfium_fails_only_pdf() {
    let engine = Engine::with_pdfium(Limits::default(), "/nonexistent/libpdfium");
    let error = engine.open(pdf(1), OpenOptions::default()).unwrap_err();
    assert_eq!(error.code, ErrorCode::UnsupportedFormat);
    assert!(!error.message.contains("nonexistent"));
}

#[test]
fn backend_errors_pass_through() {
    let engine = fake_engine(Limits::default());
    let wrong = OpenOptions {
        password: Some("wrong".into()),
        ..OpenOptions::default()
    };
    assert_eq!(
        engine.open(pdf(1), wrong).unwrap_err().code,
        ErrorCode::PasswordRequired
    );
    let broken = DocumentSource::Bytes(Arc::from(&b"%PDF-x"[..]));
    assert_eq!(
        engine
            .open(broken, OpenOptions::default())
            .unwrap_err()
            .code,
        ErrorCode::InvalidDocument
    );
    assert_eq!(engine.session_count(), 0);
}

#[test]
fn page_and_argument_checks() {
    let engine = fake_engine(Limits::default());
    let id = engine.open(pdf(2), OpenOptions::default()).unwrap();
    assert_eq!(
        engine.extract_text(id, 2).unwrap_err().code,
        ErrorCode::PageOutOfRange
    );
    for scale in [0.0, -1.0, f32::NAN, f32::INFINITY, 100.0] {
        let error = engine
            .render_page(id, 0, &RenderRequest::with_scale(scale))
            .unwrap_err();
        assert_eq!(error.code, ErrorCode::InvalidArgument, "scale {scale}");
    }
}

#[test]
fn limits_are_enforced() {
    let limits = Limits {
        max_file_size: 6,
        max_sessions: 2,
        max_pages: 10,
        max_render_pixels: 100 * 200,
        ..Limits::default()
    };
    let engine = fake_engine(limits);
    let big = DocumentSource::Bytes(Arc::from(&b"%PDF-1 padding"[..]));
    assert_eq!(
        engine.open(big, OpenOptions::default()).unwrap_err().code,
        ErrorCode::LimitExceeded
    );

    let too_many_pages = fake_engine(Limits {
        max_file_size: 64,
        ..limits
    });
    assert_eq!(
        too_many_pages
            .open(pdf(11), OpenOptions::default())
            .unwrap_err()
            .code,
        ErrorCode::LimitExceeded
    );

    let a = engine.open(pdf(1), OpenOptions::default()).unwrap();
    engine.open(pdf(1), OpenOptions::default()).unwrap();
    assert_eq!(
        engine
            .open(pdf(1), OpenOptions::default())
            .unwrap_err()
            .code,
        ErrorCode::LimitExceeded
    );

    assert!(
        engine
            .render_page(a, 0, &RenderRequest::with_scale(1.0))
            .is_ok()
    );
    let error = engine
        .render_page(a, 0, &RenderRequest::with_scale(1.01))
        .unwrap_err();
    assert_eq!(error.code, ErrorCode::LimitExceeded);
}

#[test]
fn cancelled_work_stops() {
    let engine = fake_engine(Limits::default());
    let cancel = CancelToken::new();
    cancel.cancel();
    let options = OpenOptions {
        cancel: cancel.clone(),
        ..OpenOptions::default()
    };
    assert_eq!(
        engine.open(pdf(1), options).unwrap_err().code,
        ErrorCode::Cancelled
    );

    let id = engine.open(pdf(1), OpenOptions::default()).unwrap();
    let request = RenderRequest {
        cancel,
        ..RenderRequest::with_scale(1.0)
    };
    assert_eq!(
        engine.render_page(id, 0, &request).unwrap_err().code,
        ErrorCode::Cancelled
    );
}

#[test]
fn sessions_work_across_threads() {
    let engine = Arc::new(fake_engine(Limits::default()));
    let handles: Vec<_> = (1..=8)
        .map(|pages| {
            let engine = Arc::clone(&engine);
            std::thread::spawn(move || {
                let id = engine.open(pdf(pages), OpenOptions::default()).unwrap();
                for page in 0..pages {
                    engine
                        .render_page(id, page, &RenderRequest::with_scale(0.5))
                        .unwrap();
                }
                engine.close(id).unwrap();
            })
        })
        .collect();
    for handle in handles {
        handle.join().unwrap();
    }
    assert_eq!(engine.session_count(), 0);
}

#[test]
fn session_ids_are_never_reused() {
    let engine = fake_engine(Limits::default());
    let a = engine.open(pdf(1), OpenOptions::default()).unwrap();
    engine.close(a).unwrap();
    let b = engine.open(pdf(1), OpenOptions::default()).unwrap();
    assert_ne!(a, b);
    assert_eq!(SessionId::from_raw(b.get()), Some(b));
    assert_eq!(SessionId::from_raw(0), None);
}

#[test]
fn find_text_walks_pages_in_order() {
    let engine = fake_engine(Limits::default());
    let id = engine.open(pdf(12), OpenOptions::default()).unwrap();
    let none = CancelToken::new();
    // "page 1", "page 10", "page 11" 에 "page 1" 이 들어 있다.
    let hits = engine.find_text(id, "PAGE 1", 100, &none).unwrap();
    let pages: Vec<u32> = hits.iter().map(|hit| hit.page).collect();
    assert_eq!(pages, [1, 10, 11]);
    assert_eq!(hits[1].after, "0");
    assert_eq!(engine.find_text(id, "page", 5, &none).unwrap().len(), 5);
    assert!(engine.find_text(id, "  ", 100, &none).unwrap().is_empty());

    let cancelled = CancelToken::new();
    cancelled.cancel();
    assert_eq!(
        engine
            .find_text(id, "page", 100, &cancelled)
            .unwrap_err()
            .code,
        ErrorCode::Cancelled
    );
    engine.close(id).unwrap();
}
