//! Papy 공통 문서 모델.
//!
//! 문서·페이지·스타일 표현과 편집 명령을 둔다. 렌더링 결과물(화면에 그릴 데이터)은
//! 이 crate 가 아니라 `papy-render` 에 둔다 — 편집 가능한 구조와 그린 결과를 같은 타입으로 다루지 않는다.
//!
//! 현재는 포맷 판별 결과 타입만 있다. 모델 본체는 PDF·HWPX PoC 후 정한다(docs/engine/api-draft.md).

/// 파일 시그니처로 판별한 문서 포맷.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[non_exhaustive]
pub enum DocumentFormat {
    /// PDF (`%PDF-` 시그니처).
    Pdf,
    /// HWPX — ZIP 컨테이너, 첫 항목 `mimetype` 이 `application/hwp+zip`.
    Hwpx,
    /// HWP 5.x — OLE 복합 문서(CFB) 컨테이너. 다른 CFB 포맷(구형 DOC/XLS/PPT)과 구분하려면 스트림 확인이 더 필요하다.
    CompoundFile,
    /// HWPX 로 판별되지 않은 ZIP 컨테이너(DOCX/XLSX/PPTX 후보). 내부 항목 확인이 더 필요하다.
    ZipContainer,
}
