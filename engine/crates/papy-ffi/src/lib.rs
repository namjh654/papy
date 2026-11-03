//! Papy C ABI 경계.
//!
//! 모바일(Android JNI, iOS C Interop/UniFFI)과 기타 호스트가 쓰는 작은 C 함수 표면만 둔다.
//! 핸들 기반 API·오류 코드·메모리 소유권 규칙은 엔진 API 안정화 뒤 정한다(docs/architecture/platform-bridges.md).

use std::ffi::{CStr, c_char};

static VERSION: &CStr = {
    match CStr::from_bytes_with_nul(concat!(env!("CARGO_PKG_VERSION"), "\0").as_bytes()) {
        Ok(version) => version,
        Err(_) => panic!("version contains a NUL byte"),
    }
};

/// 엔진 버전 문자열(NUL 종료, 정적 수명). 호출하는 쪽은 해제하지 않는다.
#[unsafe(no_mangle)]
pub extern "C" fn papy_engine_version() -> *const c_char {
    VERSION.as_ptr()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_matches_core() {
        // SAFETY: papy_engine_version 은 정적 수명의 NUL 종료 문자열을 돌려준다.
        let version = unsafe { CStr::from_ptr(papy_engine_version()) };
        assert_eq!(version.to_str(), Ok(papy_core::ENGINE_VERSION));
    }
}
