//! 저장소에 넣은 공개 문서(tests/fixtures, 출처는 tests/README.md)로 보는 회귀 점검.

use std::path::{Path, PathBuf};

use papy_core::{
    DocumentFormat, DocumentSource, Engine, ErrorCode, Limits, OpenOptions, SaveFormat, TextPos,
};
use papy_render::RenderRequest;

fn fixture(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../../tests/fixtures")
        .join(name)
}

/// HWP 3.x 는 열고 그리고 PDF 로 내보낼 수 있지만, HWP 로 다시 쓰면 개체가 빠지므로 편집·HWP 저장은 막는다.
#[test]
fn hwp3_opens_view_only() {
    let engine = Engine::new(Limits::default());
    let id = engine
        .open(
            DocumentSource::Path(fixture("rhwp/hwp3-curve.hwp")),
            OpenOptions::default(),
        )
        .unwrap();
    let info = engine.info(id).unwrap();
    assert_eq!(info.format, DocumentFormat::CompoundFile);
    assert_eq!(info.page_count, 2);
    assert!(!info.capabilities.edit);
    assert_eq!(
        info.capabilities.save_formats,
        vec![SaveFormat::Pdf, SaveFormat::Text]
    );
    let bitmap = engine
        .render_page(id, 1, &RenderRequest::with_scale(0.5))
        .unwrap();
    assert!(bitmap.width > 0 && bitmap.height > 0);
    assert!(engine.extract_text(id, 1).unwrap().contains("통 계 카 드"));
    // 찾기: 공백 종류를 가리지 않고 쪽 번호를 돌려준다.
    let hits = engine
        .find_text(id, "통 계  카 드", 50, &papy_core::CancelToken::new())
        .unwrap();
    assert!(hits.iter().any(|hit| hit.page == 1), "{hits:?}");

    for format in [SaveFormat::Hwp, SaveFormat::Hwpx, SaveFormat::Docx] {
        let error = engine.save(id, format).unwrap_err();
        assert_eq!(error.code, ErrorCode::NotImplemented, "{format:?}");
    }
    let error = engine
        .edit(id, |doc| doc.insert_text(TextPos::default(), "x"))
        .unwrap_err();
    assert_eq!(error.code, ErrorCode::NotImplemented);
    assert!(
        engine
            .save(id, SaveFormat::Pdf)
            .unwrap()
            .starts_with(b"%PDF-")
    );
    engine.close(id).unwrap();
}

/// 암호 걸린 옛 Word(.doc): RC4(버전 1.1)·RC4 CryptoAPI 를 암호로 풀어 연다. 암호가 없거나 틀리면 PasswordRequired.
#[test]
fn encrypted_word97_doc_opens_with_password() {
    let engine = Engine::new(Limits::default());
    for (name, password, text) in [
        (
            "poi/password_tika_binaryrc4.doc",
            "tika",
            "This is an encrypted Word 2007 File",
        ),
        (
            "poi/password_password_cryptoapi.doc",
            "password",
            "This is a test",
        ),
    ] {
        for wrong in [None, Some("wrong")] {
            let error = engine
                .open(
                    DocumentSource::Path(fixture(name)),
                    OpenOptions {
                        password: wrong.map(str::to_string),
                        ..OpenOptions::default()
                    },
                )
                .unwrap_err();
            assert_eq!(error.code, ErrorCode::PasswordRequired, "{name} {wrong:?}");
        }
        let id = engine
            .open(
                DocumentSource::Path(fixture(name)),
                OpenOptions {
                    password: Some(password.into()),
                    ..OpenOptions::default()
                },
            )
            .unwrap();
        assert_eq!(engine.info(id).unwrap().format, DocumentFormat::Doc);
        let first = engine.extract_text(id, 0).unwrap();
        assert!(first.contains(text), "{name}: {first:?}");
        engine.close(id).unwrap();
    }
}

/// 옛 Word(.doc) 그리기 개체: 떠 있는 도형(사각형·타원, 채우기 색)과 OLE 수식의 미리보기 그림(EMF)을 옮긴다.
#[test]
fn word97_doc_shapes_and_ole_preview() {
    let engine = Engine::new(Limits::default());
    for (name, want) in [
        // 연한 파랑 채우기(#99CCFF 근처)
        (
            "poi/WithArtShapes.doc",
            (|p: &[u8]| p[0] > 130 && p[0] < 180 && p[1] > 180 && p[2] > 230) as fn(&[u8]) -> bool,
        ),
        // 수식 글자(어두운 점)
        ("poi/equation.doc", |p: &[u8]| {
            p[0] < 100 && p[1] < 100 && p[2] < 100 && p[3] > 0
        }),
    ] {
        let id = engine
            .open(DocumentSource::Path(fixture(name)), OpenOptions::default())
            .unwrap();
        let info = engine.info(id).unwrap();
        assert_eq!(info.dropped_objects, 0, "{name}");
        let page = engine
            .render_page(id, 0, &RenderRequest::with_scale(2.0))
            .unwrap();
        let count = page.pixels.chunks_exact(4).filter(|p| want(p)).count();
        assert!(count > 200, "{name}: {count}");
        engine.close(id).unwrap();
    }
}

/// 옛 Word(.doc): 열고, 첫 쪽 머리말을 감추고, 병합 칸 표를 옮기고, DOCX·PDF 로 내보낸다.
#[test]
fn word97_doc_opens_and_converts() {
    let engine = Engine::new(Limits::default());
    let id = engine
        .open(
            DocumentSource::Path(fixture("poi/DiffFirstPageHeadFoot.doc")),
            OpenOptions::default(),
        )
        .unwrap();
    let info = engine.info(id).unwrap();
    assert_eq!(info.format, DocumentFormat::Doc);
    assert_eq!(info.page_count, 2);
    assert!(info.capabilities.edit);
    assert!(info.capabilities.save_formats.contains(&SaveFormat::Docx));
    let first = engine.extract_text(id, 0).unwrap();
    assert!(
        first.starts_with("This is a sample word document."),
        "{first:?}"
    );
    // 첫 쪽은 기본 머리말·꼬리말을 감춘다(첫 쪽용은 따로 없어 빠진 것으로 센다).
    assert!(!first.contains("Mid header"), "{first:?}");
    let second = engine.extract_text(id, 1).unwrap();
    for part in ["Mid header", "This is page two.", "Footer Middle"] {
        assert!(second.contains(part), "{part}: {second:?}");
    }
    let docx = engine.save(id, SaveFormat::Docx).unwrap();
    let reopened = engine
        .open(DocumentSource::Bytes(docx.into()), OpenOptions::default())
        .unwrap();
    assert_eq!(engine.info(reopened).unwrap().page_count, 2);
    assert!(
        !engine
            .extract_text(reopened, 0)
            .unwrap()
            .contains("Mid header")
    );
    assert!(
        engine
            .save(id, SaveFormat::Pdf)
            .unwrap()
            .starts_with(b"%PDF-")
    );

    let table = engine
        .open(
            DocumentSource::Path(fixture("poi/table-merges.doc")),
            OpenOptions::default(),
        )
        .unwrap();
    let info = engine.info(table).unwrap();
    assert_eq!((info.page_count, info.dropped_objects), (1, 0));
    let text = engine.extract_text(table, 0).unwrap();
    for cell in ["A", "F", "G", "K"] {
        assert!(text.lines().any(|line| line == cell), "{cell}: {text:?}");
    }
}

#[test]
fn word6_and_95_docs_open_as_text() {
    // Word 6.0·95(옛 형식): 글과 문단만. 글은 언어·글꼴 문자 집합에 맞는 코드 페이지로 푼다(러시아어 1251).
    let engine = Engine::new(Limits::default());
    for (name, text) in [
        (
            "poi/Word6.doc",
            "The quick brown fox jumps over the lazy dog",
        ),
        ("poi/Bug60942b.doc", "Четыре балла и семь лет"),
    ] {
        let id = engine
            .open(DocumentSource::Path(fixture(name)), OpenOptions::default())
            .unwrap();
        assert_eq!(engine.info(id).unwrap().format, DocumentFormat::Doc);
        let page = engine.extract_text(id, 0).unwrap();
        assert!(page.contains(text), "{name}: {page:?}");
        engine.close(id).unwrap();
    }
}
