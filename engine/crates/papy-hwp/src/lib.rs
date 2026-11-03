//! Papy HWP/HWPX 어댑터.
//!
//! HWPX(ZIP + XML)는 P0, HWP 5.x(OLE 복합 문서)는 기술 검증 게이트다(docs/formats/hwp-hwpx.md).
//! 외부 파서(OpenHWP 등)는 라이선스·구현 범위 검토와 승인 전까지 의존성으로 추가하지 않는다.

/// HWPX 의 `mimetype` 항목 내용.
pub const HWPX_MIMETYPE: &[u8] = b"application/hwp+zip";

/// OLE 복합 문서(CFB) 시그니처. HWP 5.x 가 이 컨테이너를 쓴다.
pub const CFB_SIGNATURE: &[u8] = &[0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1];

const ZIP_LOCAL_HEADER: &[u8] = b"PK\x03\x04";
const ZIP_LOCAL_HEADER_LEN: usize = 30;

/// 앞부분 바이트가 CFB 시그니처로 시작하는지 본다.
pub fn has_cfb_signature(head: &[u8]) -> bool {
    head.starts_with(CFB_SIGNATURE)
}

/// 앞부분 바이트가 ZIP 로컬 헤더로 시작하는지 본다.
pub fn has_zip_signature(head: &[u8]) -> bool {
    head.starts_with(ZIP_LOCAL_HEADER)
}

/// ZIP 의 첫 항목이 압축 없이 저장된 `mimetype` 이고 내용이 HWPX mimetype 인지 본다.
///
/// 앞부분 바이트만 읽고 할당하지 않는다. 헤더 길이 값은 입력 범위를 넘지 않는지 확인한다.
pub fn has_hwpx_mimetype(head: &[u8]) -> bool {
    if !has_zip_signature(head) || head.len() < ZIP_LOCAL_HEADER_LEN {
        return false;
    }
    let u16_at = |i: usize| usize::from(u16::from_le_bytes([head[i], head[i + 1]]));
    let stored = u16_at(8) == 0;
    let name_len = u16_at(26);
    let extra_len = u16_at(28);
    let name_end = ZIP_LOCAL_HEADER_LEN + name_len;
    let data_start = name_end + extra_len;
    stored
        && head.get(ZIP_LOCAL_HEADER_LEN..name_end) == Some(b"mimetype".as_slice())
        && head
            .get(data_start..data_start + HWPX_MIMETYPE.len())
            .is_some_and(|data| data == HWPX_MIMETYPE)
}
