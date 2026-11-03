//! Papy PDF 어댑터.
//!
//! PDF 엔진(PDFium 또는 MuPDF 후보)은 라이선스·빌드 검토와 승인 전까지 의존성으로 추가하지 않는다
//! (docs/decisions/0001-pdf-engine.md).

/// PDF 파일 시그니처.
pub const SIGNATURE: &[u8] = b"%PDF-";

/// 앞부분 바이트가 PDF 시그니처로 시작하는지 본다. 파일 전체가 올바른 PDF 라는 뜻은 아니다.
pub fn has_signature(head: &[u8]) -> bool {
    head.starts_with(SIGNATURE)
}
