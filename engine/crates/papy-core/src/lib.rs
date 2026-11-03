//! Papy 엔진 파사드와 세션 관리.
//!
//! 앱(Tauri·KMP)은 이 crate(또는 `papy-ffi`)만 호출한다. 이 crate 는 UI 프레임워크 타입에 의존하지 않는다.
//!
//! ```text
//! Engine ──open()──▶ SessionId ──render_page / extract_text / ...──▶ close()
//!   └ FormatBackend (포맷별 어댑터, 등록식)      └ Document (열린 문서, 세션마다 잠금)
//! ```
//!
//! - **스레드**: `Engine` 은 `Send + Sync`. 서로 다른 세션은 동시에 작업할 수 있고, 한 세션 안의 호출은 순서대로 실행된다.
//! - **상한**: 파일 크기·세션 수·페이지 수·렌더 픽셀을 [`Limits`] 로 막는다(손상·악성 파일 방어).
//! - **오류**: 모든 실패는 [`Error`] + 고정 번호 [`ErrorCode`]. 메시지에 경로·본문을 넣지 않는다.
//!
//! 포맷 어댑터: PDF = `papy-pdf`(PDFium), HWPX·HWP = `papy-hwp`(rhwp). API 초안은 docs/engine/api-draft.md.

use std::collections::HashMap;
use std::fs::File;
use std::io::Read;
use std::num::NonZeroU64;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, MutexGuard};

pub use papy_model::{
    Alignment, Annotation, Capabilities, CaretMotion, CharFormat, DocumentFormat, DocumentInfo,
    EditOutcome, Error, ErrorCode, MarkupKind, OutlineItem, PageOp, PageRect, PageSize, ParaFormat,
    Result, SaveFormat, TextPos,
};
pub use papy_render::{
    BackendOpenOptions, Bitmap, CancelToken, Document, FormatBackend, RenderRequest, Source,
};

/// 엔진 버전(crate 버전).
pub const ENGINE_VERSION: &str = env!("CARGO_PKG_VERSION");

/// 현재 플랫폼의 PDFium 라이브러리 파일 이름. 앱이 번들한 라이브러리 경로를 만들 때 쓴다.
pub use papy_pdf::library_file_name as pdfium_library_file_name;

/// 무거운 초기화(시스템 글꼴 목록 읽기 등)를 미리 해 둔다. 앱 시작 때 백그라운드 스레드에서 부르면
/// 첫 문서 렌더가 빨라진다. 여러 번 불러도 된다.
pub fn warm_up() {
    papy_hwp::HwpBackend::warm_up();
}

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

/// 안전 상한. 기본값은 일반 문서에 넉넉하고 악성 입력은 막는 수준이며, 측정 후 조정한다.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Limits {
    /// 열 수 있는 최대 파일 크기(바이트).
    pub max_file_size: u64,
    /// 동시에 열 수 있는 최대 문서 수.
    pub max_sessions: usize,
    /// 문서 하나의 최대 페이지 수.
    pub max_pages: u32,
    /// 렌더 결과 하나의 최대 픽셀 수(너비×높이).
    pub max_render_pixels: u64,
    /// 허용 배율 범위.
    pub scale_range: (f32, f32),
}

impl Default for Limits {
    fn default() -> Self {
        Self {
            max_file_size: 1 << 30, // 1 GiB
            max_sessions: 64,
            max_pages: 100_000,
            max_render_pixels: 8192 * 8192, // RGBA 256 MiB
            scale_range: (0.05, 16.0),
        }
    }
}

/// 앱이 함께 배포하는 엔진용 파일.
#[derive(Debug, Clone)]
pub struct EngineResources {
    /// PDFium 동적 라이브러리.
    pub pdfium_library: PathBuf,
    /// 한글 TrueType 글꼴(Noto Sans KR 등). PDF 텍스트 상자와, 시스템에 한글 글꼴이 없을 때 HWP 대체 글꼴로 쓴다.
    pub korean_font: Option<PathBuf>,
}

/// 문서 원본.
#[derive(Debug, Clone)]
pub enum DocumentSource {
    /// 사용자가 고른 파일 경로.
    Path(PathBuf),
    /// 메모리 버퍼(모바일 content URI, 웹 File 등).
    Bytes(Arc<[u8]>),
}

/// 열기 옵션.
#[derive(Debug, Clone, Default)]
pub struct OpenOptions {
    /// 문서 암호.
    pub password: Option<String>,
    /// 판별 결과 대신 쓸 포맷(확장자 기반 힌트 등). 보통 `None`.
    pub format: Option<DocumentFormat>,
    /// 취소 신호.
    pub cancel: CancelToken,
}

/// 열린 문서의 핸들. 0 이 아닌 값이며 닫힌 핸들은 다시 쓰이지 않는다.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct SessionId(NonZeroU64);

impl SessionId {
    /// 앱·C ABI 로 넘기는 숫자 값.
    pub fn get(self) -> u64 {
        self.0.get()
    }

    /// 숫자 값에서 핸들을 만든다. 0 이면 `None`.
    pub fn from_raw(raw: u64) -> Option<Self> {
        NonZeroU64::new(raw).map(Self)
    }
}

struct Session {
    /// 원본 크기. 편집해도 바뀌지 않는다(저장 결과 크기는 `save` 의 반환값).
    byte_size: u64,
    document: Mutex<Box<dyn Document>>,
}

impl Session {
    /// 편집으로 쪽 수가 바뀔 수 있어 매번 어댑터에서 읽는다.
    fn info(&self, doc: &dyn Document) -> DocumentInfo {
        let mut info = doc.info();
        info.byte_size = self.byte_size;
        info
    }
}

/// 문서 엔진. 포맷 어댑터를 등록하고 열린 문서를 세션으로 관리한다.
pub struct Engine {
    limits: Limits,
    backends: Vec<Box<dyn FormatBackend>>,
    sessions: Mutex<HashMap<SessionId, Arc<Session>>>,
    next_id: AtomicU64,
}

impl std::fmt::Debug for Engine {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let formats: Vec<_> = self.backends.iter().map(|b| b.format()).collect();
        f.debug_struct("Engine")
            .field("limits", &self.limits)
            .field("formats", &formats)
            .field("sessions", &self.session_count())
            .finish()
    }
}

impl Default for Engine {
    fn default() -> Self {
        Self::new(Limits::default())
    }
}

impl Engine {
    /// HWPX·HWP 어댑터를 등록한 엔진. PDF 는 PDFium 라이브러리 위치가 필요하므로 [`Engine::with_pdfium`] 을 쓴다.
    pub fn new(limits: Limits) -> Self {
        Self::with_backends(limits, Self::hwp_backends())
    }

    /// PDF(PDFium)·HWPX·HWP 어댑터를 등록한 엔진. `pdfium_library` 는 PDFium 동적 라이브러리 파일 경로이며
    /// 첫 PDF 를 열 때 로드한다. 로드에 실패하면 PDF 열기만 `UnsupportedFormat` 으로 실패한다.
    pub fn with_pdfium(limits: Limits, pdfium_library: impl Into<PathBuf>) -> Self {
        Self::with_resources(
            limits,
            EngineResources {
                pdfium_library: pdfium_library.into(),
                korean_font: None,
            },
        )
    }

    /// 앱이 함께 배포하는 파일로 모든 어댑터를 등록한다.
    pub fn with_resources(limits: Limits, resources: EngineResources) -> Self {
        let mut pdf = papy_pdf::PdfBackend::new(resources.pdfium_library);
        if let Some(font) = resources.korean_font {
            papy_hwp::HwpBackend::set_fallback_font(&font);
            pdf = pdf.with_text_font(font);
        }
        let mut backends: Vec<Box<dyn FormatBackend>> = vec![Box::new(pdf)];
        backends.extend(Self::hwp_backends());
        Self::with_backends(limits, backends)
    }

    fn hwp_backends() -> Vec<Box<dyn FormatBackend>> {
        vec![
            Box::new(papy_hwp::HwpBackend::hwpx()),
            Box::new(papy_hwp::HwpBackend::hwp()),
        ]
    }

    /// 어댑터를 직접 지정한다. 같은 포맷이 여럿이면 앞의 것을 쓴다.
    pub fn with_backends(limits: Limits, backends: Vec<Box<dyn FormatBackend>>) -> Self {
        Self {
            limits,
            backends,
            sessions: Mutex::new(HashMap::new()),
            next_id: AtomicU64::new(1),
        }
    }

    /// 현재 상한.
    pub fn limits(&self) -> Limits {
        self.limits
    }

    /// 열 수 있는 포맷 목록.
    pub fn supported_formats(&self) -> Vec<DocumentFormat> {
        self.backends.iter().map(|b| b.format()).collect()
    }

    /// 문서를 연다.
    pub fn open(&self, source: DocumentSource, options: OpenOptions) -> Result<SessionId> {
        if self.session_count() >= self.limits.max_sessions {
            return Err(Error::new(
                ErrorCode::LimitExceeded,
                "too many open documents",
            ));
        }
        let (head, byte_size) = self.sniff(&source)?;
        let format = match options.format.or_else(|| detect_format(&head)) {
            Some(format) => format,
            None => {
                return Err(Error::new(
                    ErrorCode::UnsupportedFormat,
                    "unknown file format",
                ));
            }
        };
        let backend = self
            .backends
            .iter()
            .find(|b| b.format() == format)
            .ok_or_else(|| {
                Error::new(
                    ErrorCode::UnsupportedFormat,
                    format!("{} is not supported yet", format.name()),
                )
            })?;
        options.cancel.check()?;

        let backend_options = BackendOpenOptions {
            password: options.password,
            max_pages: self.limits.max_pages,
            cancel: options.cancel,
        };
        let backend_source = match &source {
            DocumentSource::Path(path) => Source::Path(path),
            DocumentSource::Bytes(bytes) => Source::Bytes(bytes),
        };
        let document = backend.open(backend_source, &backend_options)?;
        let mut info = document.info();
        info.byte_size = byte_size;
        if info.page_count > self.limits.max_pages {
            return Err(Error::new(ErrorCode::LimitExceeded, "too many pages"));
        }

        let id = SessionId::from_raw(self.next_id.fetch_add(1, Ordering::Relaxed))
            .ok_or_else(|| Error::new(ErrorCode::Internal, "session id overflow"))?;
        let session = Arc::new(Session {
            byte_size: info.byte_size,
            document: Mutex::new(document),
        });
        let mut sessions = self.sessions();
        // 열기 도중 다른 스레드가 열었을 수 있으니 다시 확인한다.
        if sessions.len() >= self.limits.max_sessions {
            return Err(Error::new(
                ErrorCode::LimitExceeded,
                "too many open documents",
            ));
        }
        sessions.insert(id, session);
        Ok(id)
    }

    /// 문서 요약 정보.
    pub fn info(&self, id: SessionId) -> Result<DocumentInfo> {
        let session = self.session(id)?;
        let doc = lock(&session.document);
        Ok(session.info(doc.as_ref()))
    }

    /// 페이지 수.
    pub fn page_count(&self, id: SessionId) -> Result<u32> {
        Ok(self.info(id)?.page_count)
    }

    /// 페이지 크기(pt).
    pub fn page_size(&self, id: SessionId, page: u32) -> Result<PageSize> {
        self.with_page(id, page, |doc| doc.page_size(page))
    }

    /// 페이지를 그린다. 결과 크기가 상한을 넘으면 어댑터를 부르기 전에 거절한다.
    pub fn render_page(&self, id: SessionId, page: u32, request: &RenderRequest) -> Result<Bitmap> {
        let (min, max) = self.limits.scale_range;
        if !request.scale.is_finite() || request.scale < min || request.scale > max {
            return Err(Error::new(
                ErrorCode::InvalidArgument,
                format!("scale must be within {min}..={max}"),
            ));
        }
        self.with_page(id, page, |doc| {
            let (width, height) = request.pixel_size(doc.page_size(page)?);
            if u64::from(width) * u64::from(height) > self.limits.max_render_pixels {
                return Err(Error::new(
                    ErrorCode::LimitExceeded,
                    "rendered page would be too large",
                ));
            }
            request.cancel.check()?;
            let bitmap = doc.render_page(page, request)?;
            let expected = width as usize * height as usize * 4;
            if bitmap.width != width || bitmap.height != height || bitmap.pixels.len() != expected {
                return Err(Error::new(
                    ErrorCode::Internal,
                    "backend returned a bitmap of unexpected size",
                ));
            }
            Ok(bitmap)
        })
    }

    /// 페이지 텍스트.
    pub fn extract_text(&self, id: SessionId, page: u32) -> Result<String> {
        self.with_page(id, page, |doc| doc.extract_text(page))
    }

    /// 목차.
    pub fn outline(&self, id: SessionId) -> Result<Vec<OutlineItem>> {
        let session = self.session(id)?;
        let mut doc = lock(&session.document);
        doc.outline()
    }

    /// 지금 상태를 `format` 으로 직렬화한다. 파일 쓰기는 호출자가 한다(임시 파일에 쓴 뒤 바꿔치기 권장).
    pub fn save(&self, id: SessionId, format: SaveFormat) -> Result<Vec<u8>> {
        let session = self.session(id)?;
        let mut doc = lock(&session.document);
        if !doc.info().capabilities.save_formats.contains(&format) {
            return Err(Error::new(
                ErrorCode::NotImplemented,
                format!("cannot save this document as {}", format.extension()),
            ));
        }
        doc.save(format)
    }

    /// 편집 작업(본문 편집·주석·쪽 편집)을 한 번에 실행한다. 어느 것도 지원하지 않으면 `NotImplemented`.
    ///
    /// 클로저는 세션 잠금 안에서 돈다 — 그동안 같은 문서의 렌더는 기다린다.
    pub fn edit<T>(
        &self,
        id: SessionId,
        f: impl FnOnce(&mut dyn Document) -> Result<T>,
    ) -> Result<T> {
        let session = self.session(id)?;
        let mut doc = lock(&session.document);
        let capabilities = doc.info().capabilities;
        if !(capabilities.edit || capabilities.annotate || capabilities.page_edit) {
            return Err(Error::new(
                ErrorCode::NotImplemented,
                "editing is not supported for this document",
            ));
        }
        f(doc.as_mut())
    }

    /// 문서를 닫는다. 진행 중인 작업이 있으면 그 작업이 끝난 뒤 해제된다.
    pub fn close(&self, id: SessionId) -> Result<()> {
        self.sessions()
            .remove(&id)
            .map(|_| ())
            .ok_or_else(|| Error::new(ErrorCode::SessionNotFound, "session not found"))
    }

    /// 열린 문서 수.
    pub fn session_count(&self) -> usize {
        self.sessions().len()
    }

    fn sessions(&self) -> MutexGuard<'_, HashMap<SessionId, Arc<Session>>> {
        lock(&self.sessions)
    }

    fn session(&self, id: SessionId) -> Result<Arc<Session>> {
        self.sessions()
            .get(&id)
            .cloned()
            .ok_or_else(|| Error::new(ErrorCode::SessionNotFound, "session not found"))
    }

    fn with_page<T>(
        &self,
        id: SessionId,
        page: u32,
        f: impl FnOnce(&mut dyn Document) -> Result<T>,
    ) -> Result<T> {
        let session = self.session(id)?;
        let mut doc = lock(&session.document);
        if page >= doc.info().page_count {
            return Err(Error::new(
                ErrorCode::PageOutOfRange,
                format!("page {page} is out of range"),
            ));
        }
        f(doc.as_mut())
    }

    /// 크기 상한을 확인하고 판별용 앞부분을 읽는다.
    fn sniff(&self, source: &DocumentSource) -> Result<(Vec<u8>, u64)> {
        let (head, size) = match source {
            DocumentSource::Path(path) => {
                let file = File::open(path)?;
                let size = file.metadata()?.len();
                self.check_size(size)?;
                let mut head = Vec::with_capacity(SNIFF_LEN);
                file.take(SNIFF_LEN as u64).read_to_end(&mut head)?;
                (head, size)
            }
            DocumentSource::Bytes(bytes) => {
                let size = bytes.len() as u64;
                self.check_size(size)?;
                (bytes[..bytes.len().min(SNIFF_LEN)].to_vec(), size)
            }
        };
        Ok((head, size))
    }

    fn check_size(&self, size: u64) -> Result<()> {
        if size > self.limits.max_file_size {
            Err(Error::new(ErrorCode::LimitExceeded, "file is too large"))
        } else {
            Ok(())
        }
    }
}

/// 다른 스레드가 패닉해 잠금이 오염돼도 엔진 전체를 멈추지 않는다(세션 데이터는 호출 단위로 일관).
fn lock<T: ?Sized>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

#[cfg(test)]
mod tests;
