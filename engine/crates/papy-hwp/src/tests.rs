//! 테스트 문서는 rhwp 로 직접 만든다 — 저작권 문제가 없고 내용이 정확히 알려져 있다.

use super::*;
use papy_render::CancelToken;

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
