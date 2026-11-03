//! Papy 렌더링 추상화와 포맷 어댑터 계약.
//!
//! - 렌더 요청·결과(`RenderRequest`, `Bitmap`)와 취소 토큰(`CancelToken`)
//! - 포맷 어댑터가 구현하는 계약(`FormatBackend`, `Document`) — `papy-pdf`, `papy-hwp` 등이 구현하고
//!   `papy-core` 가 세션으로 관리한다.
//!
//! 페이지 캐시·작업 큐는 첫 실제 어댑터(PDF PoC)에서 측정한 뒤 이 crate 에 추가한다.

use std::path::Path;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use papy_model::{DocumentFormat, DocumentInfo, Error, ErrorCode, OutlineItem, PageSize, Result};

/// 렌더링 결과. RGBA 8비트, 알파는 premultiplied 가 아님, 행 사이 여백 없음(`stride == width * 4`).
#[derive(Clone, PartialEq, Eq)]
pub struct Bitmap {
    /// 너비(px).
    pub width: u32,
    /// 높이(px).
    pub height: u32,
    /// 픽셀 데이터, 길이 `width * height * 4`.
    pub pixels: Vec<u8>,
}

impl Bitmap {
    /// 한 행의 바이트 수.
    pub fn stride(&self) -> usize {
        self.width as usize * 4
    }
}

impl std::fmt::Debug for Bitmap {
    // 픽셀 내용은 찍지 않는다.
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Bitmap")
            .field("width", &self.width)
            .field("height", &self.height)
            .finish_non_exhaustive()
    }
}

/// 작업 취소 신호. 복제본끼리 같은 상태를 공유한다. 어댑터는 긴 작업 중간중간 확인한다.
#[derive(Debug, Clone, Default)]
pub struct CancelToken(Arc<AtomicBool>);

impl CancelToken {
    /// 새 토큰.
    pub fn new() -> Self {
        Self::default()
    }

    /// 취소를 요청한다.
    pub fn cancel(&self) {
        self.0.store(true, Ordering::Relaxed);
    }

    /// 취소됐는지 본다.
    pub fn is_cancelled(&self) -> bool {
        self.0.load(Ordering::Relaxed)
    }

    /// 취소됐으면 `Cancelled` 오류를 돌려준다.
    pub fn check(&self) -> Result<()> {
        if self.is_cancelled() {
            Err(Error::new(ErrorCode::Cancelled, "cancelled"))
        } else {
            Ok(())
        }
    }
}

/// 페이지 렌더 요청.
#[derive(Debug, Clone)]
pub struct RenderRequest {
    /// 배율. 1.0 = 1pt 를 1px 로(72 DPI). 화면 DPI 보정은 호출자가 곱한다.
    pub scale: f32,
    /// 배경을 흰색으로 채울지(아니면 투명).
    pub white_background: bool,
    /// 취소 신호.
    pub cancel: CancelToken,
}

impl RenderRequest {
    /// 배율만 정한 기본 요청(흰 배경).
    pub fn with_scale(scale: f32) -> Self {
        Self {
            scale,
            white_background: true,
            cancel: CancelToken::new(),
        }
    }

    /// 페이지 크기와 배율로 결과 픽셀 크기를 계산한다(올림, 최소 1px).
    pub fn pixel_size(&self, page: PageSize) -> (u32, u32) {
        let px = |pt: f32| ((pt * self.scale).ceil().max(1.0)).min(u32::MAX as f32) as u32;
        (px(page.width), px(page.height))
    }
}

/// 어댑터가 받는 문서 원본.
#[derive(Debug, Clone, Copy)]
pub enum Source<'a> {
    /// 파일 경로. 엔진이 크기 상한을 이미 확인했다.
    Path(&'a Path),
    /// 메모리 버퍼.
    Bytes(&'a Arc<[u8]>),
}

/// 어댑터가 받는 열기 옵션.
#[derive(Debug, Clone, Default)]
pub struct BackendOpenOptions {
    /// 문서 암호.
    pub password: Option<String>,
    /// 이 문서에서 허용하는 최대 페이지 수.
    pub max_pages: u32,
    /// 취소 신호.
    pub cancel: CancelToken,
}

/// 한 포맷을 여는 어댑터. 엔진에 등록되며 여러 스레드에서 공유된다.
pub trait FormatBackend: Send + Sync {
    /// 이 어댑터가 여는 포맷.
    fn format(&self) -> DocumentFormat;

    /// 문서를 연다.
    fn open(&self, source: Source<'_>, options: &BackendOpenOptions) -> Result<Box<dyn Document>>;
}

/// 열린 문서 하나. 엔진이 세션마다 잠금으로 감싸 한 번에 한 스레드만 호출한다.
///
/// 페이지 번호는 0부터, 범위 확인은 엔진이 먼저 한다. 지원하지 않는 기능은 `NotImplemented` 를 돌려준다.
pub trait Document: Send {
    /// 요약 정보.
    fn info(&self) -> DocumentInfo;

    /// 페이지 크기.
    fn page_size(&mut self, page: u32) -> Result<PageSize>;

    /// 페이지를 비트맵으로 그린다.
    fn render_page(&mut self, page: u32, request: &RenderRequest) -> Result<Bitmap>;

    /// 페이지 텍스트를 추출한다.
    fn extract_text(&mut self, _page: u32) -> Result<String> {
        Err(Error::new(
            ErrorCode::NotImplemented,
            "text extraction is not supported for this format",
        ))
    }

    /// 목차.
    fn outline(&mut self) -> Result<Vec<OutlineItem>> {
        Ok(Vec::new())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pixel_size_rounds_up_and_never_zero() {
        let request = RenderRequest::with_scale(2.0);
        assert_eq!(
            request.pixel_size(PageSize {
                width: 595.3,
                height: 841.9
            }),
            (1191, 1684)
        );
        let tiny = RenderRequest::with_scale(0.001);
        assert_eq!(
            tiny.pixel_size(PageSize {
                width: 10.0,
                height: 10.0
            }),
            (1, 1)
        );
    }

    #[test]
    fn cancel_token_is_shared_between_clones() {
        let token = CancelToken::new();
        let clone = token.clone();
        assert!(token.check().is_ok());
        clone.cancel();
        assert_eq!(token.check().unwrap_err().code, ErrorCode::Cancelled);
    }
}
