//! Papy 엔진 파사드와 세션 관리.
//!
//! 앱(Tauri·KMP)은 이 crate(또는 `papy-ffi`)만 호출한다. 이 crate 는 UI 프레임워크 타입에 의존하지 않는다.
//! `DocumentEngine` / `DocumentSession` API 는 PDF·HWPX PoC 뒤에 확정한다(docs/engine/api-draft.md).

pub use papy_model::DocumentFormat;

/// 엔진 버전(crate 버전).
pub const ENGINE_VERSION: &str = env!("CARGO_PKG_VERSION");

/// 판별에 필요한 앞부분 바이트 수. 호출하는 쪽은 파일에서 이만큼만 읽어 넘기면 된다.
pub const SNIFF_LEN: usize = 128;

/// 파일 앞부분 바이트로 포맷을 판별한다. 알 수 없으면 `None`.
///
/// 확장자는 보지 않는다. 결과는 후보일 뿐이며 실제 열기에서 다시 검증한다.
pub fn detect_format(head: &[u8]) -> Option<DocumentFormat> {
    if papy_pdf::has_signature(head) {
        Some(DocumentFormat::Pdf)
    } else if papy_hwp::has_hwpx_mimetype(head) {
        Some(DocumentFormat::Hwpx)
    } else if papy_hwp::has_zip_signature(head) {
        Some(DocumentFormat::ZipContainer)
    } else if papy_hwp::has_cfb_signature(head) {
        Some(DocumentFormat::CompoundFile)
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
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
}
