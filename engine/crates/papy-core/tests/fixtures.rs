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
