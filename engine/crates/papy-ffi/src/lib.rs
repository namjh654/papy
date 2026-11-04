//! Papy 모바일 경계(UniFFI). Android(Kotlin)·iOS(Swift) 바인딩을 이 파일의 내보낸 항목에서 만든다.
//!
//! - 문서는 바이트로 연다(모바일은 파일 경로 대신 content URI·보안 범위 URL 로 받는다).
//! - PDF 는 모바일 1단계에서 운영체제 렌더러(Android PdfRenderer, iOS PDFKit)를 쓰므로 PDFium 을 넣지 않는다.
//!   이 엔진은 HWP·HWPX·DOCX 를 맡는다(docs/decisions/0006-mobile-bridge.md).
//! - 문서 내용·경로는 기록하지 않는다.

use std::ffi::{CStr, c_char};
use std::path::PathBuf;
use std::sync::Arc;

use papy_core::{
    CaretMotion, Document, DocumentFormat, DocumentSource, Engine, EngineResources, ErrorCode,
    Limits, OpenOptions, RenderRequest, SaveFormat, SessionId, TextPos,
};

uniffi::setup_scaffolding!();

static VERSION: &CStr = {
    match CStr::from_bytes_with_nul(concat!(env!("CARGO_PKG_VERSION"), "\0").as_bytes()) {
        Ok(version) => version,
        Err(_) => panic!("version contains a NUL byte"),
    }
};

/// 엔진 버전 문자열(NUL 종료, 정적 수명). 호출하는 쪽은 해제하지 않는다. C 호스트용.
#[unsafe(no_mangle)]
pub extern "C" fn papy_engine_version() -> *const c_char {
    VERSION.as_ptr()
}

/// 엔진 버전.
#[uniffi::export]
pub fn engine_version() -> String {
    papy_core::ENGINE_VERSION.to_owned()
}

/// 엔진 오류. 메시지는 개발자용(영어)이며 화면 글자는 앱이 종류에 맞춰 고른다.
#[derive(Debug, uniffi::Error)]
#[allow(missing_docs)] // 각 변형의 detail 은 개발자용 설명이다(Kotlin Throwable.message 와 겹치지 않게).
pub enum PapyError {
    /// 인자가 잘못됨
    InvalidArgument { detail: String },
    /// 읽기 실패
    Io { detail: String },
    /// 지원하지 않는 형식(모바일 엔진은 PDF 를 열지 않는다)
    UnsupportedFormat { detail: String },
    /// 손상된 문서
    InvalidDocument { detail: String },
    /// 암호가 필요하거나 틀림
    PasswordRequired { detail: String },
    /// 크기·쪽 수 상한 초과
    LimitExceeded { detail: String },
    /// 없는 쪽
    PageOutOfRange { detail: String },
    /// 아직 안 되는 기능
    NotImplemented { detail: String },
    /// 닫혔거나 없는 문서
    SessionNotFound { detail: String },
    /// 그 밖의 내부 오류
    Internal { detail: String },
}

impl std::fmt::Display for PapyError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{self:?}")
    }
}
impl std::error::Error for PapyError {}

impl From<papy_core::Error> for PapyError {
    fn from(error: papy_core::Error) -> Self {
        let detail = error.message;
        match error.code {
            ErrorCode::InvalidArgument => Self::InvalidArgument { detail },
            ErrorCode::Io => Self::Io { detail },
            ErrorCode::UnsupportedFormat => Self::UnsupportedFormat { detail },
            ErrorCode::InvalidDocument => Self::InvalidDocument { detail },
            ErrorCode::PasswordRequired => Self::PasswordRequired { detail },
            ErrorCode::LimitExceeded => Self::LimitExceeded { detail },
            ErrorCode::PageOutOfRange => Self::PageOutOfRange { detail },
            ErrorCode::NotImplemented => Self::NotImplemented { detail },
            ErrorCode::SessionNotFound => Self::SessionNotFound { detail },
            _ => Self::Internal { detail },
        }
    }
}

/// 열린 문서 정보.
#[derive(Debug, Clone, uniffi::Record)]
pub struct DocumentSummary {
    /// 문서 핸들(닫을 때까지 유효)
    pub session: u64,
    /// 형식: "hwp" | "hwpx" | "docx"
    pub format: String,
    /// 쪽 수
    pub page_count: u32,
    /// 문서 제목(있으면)
    pub title: Option<String>,
    /// 열 때 옮기지 못한 요소 수
    pub dropped_objects: u32,
    /// 본문을 고칠 수 있는가(HWP·HWPX·DOCX)
    pub can_edit: bool,
}

/// 쪽 위 사각형(pt, 쪽 왼쪽 위 기준).
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct PageRegion {
    /// 쪽(0부터)
    pub page: u32,
    /// 왼쪽
    pub x: f32,
    /// 위
    pub y: f32,
    /// 너비
    pub width: f32,
    /// 높이
    pub height: f32,
}

/// 누른 자리의 문단: 위치·글자·화면에 그릴 사각형.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ParagraphAtPoint {
    /// 구역
    pub section: u32,
    /// 문단
    pub paragraph: u32,
    /// 문단 글자
    pub text: String,
    /// 문단을 덮는 줄 사각형(여러 쪽에 걸칠 수 있다)
    pub boxes: Vec<PageRegion>,
}

/// 쪽 크기(pt).
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct PageDimensions {
    /// 너비(pt)
    pub width: f32,
    /// 높이(pt)
    pub height: f32,
}

/// 그린 쪽: RGBA 8비트, 위에서 아래로, 한 줄 = width × 4 바이트.
#[derive(Debug, Clone, uniffi::Record)]
pub struct PageImage {
    /// 너비(px)
    pub width: u32,
    /// 높이(px)
    pub height: u32,
    /// 픽셀(RGBA)
    pub rgba: Vec<u8>,
}

/// 문서 엔진. 앱에 하나만 두고 여러 스레드에서 불러도 된다.
#[derive(uniffi::Object)]
pub struct PapyEngine {
    engine: Engine,
}

fn session(raw: u64) -> Result<SessionId, PapyError> {
    SessionId::from_raw(raw).ok_or_else(|| PapyError::SessionNotFound {
        detail: "invalid session handle".into(),
    })
}

/// 문단 끝 위치. 다음 문단(또는 다음 구역) 처음에서 한 글자 앞으로 가면 이 문단 끝이다.
fn paragraph_end(
    doc: &mut dyn Document,
    section: u32,
    paragraph: u32,
) -> papy_core::Result<TextPos> {
    let same = |pos: &TextPos| pos.section == section && pos.paragraph == paragraph;
    for next in [
        TextPos {
            section,
            paragraph: paragraph + 1,
            offset: 0,
        },
        TextPos {
            section: section + 1,
            paragraph: 0,
            offset: 0,
        },
    ] {
        if let Ok(pos) = doc.move_caret(next, CaretMotion::Left) {
            if same(&pos) {
                return Ok(pos);
            }
        }
    }
    let start = TextPos {
        section,
        paragraph,
        offset: 0,
    };
    let end = doc.move_caret(start, CaretMotion::DocumentEnd)?;
    if same(&end) {
        Ok(end)
    } else {
        Err(papy_core::Error::new(
            ErrorCode::InvalidArgument,
            "paragraph end not found",
        ))
    }
}

fn save_format(format: DocumentFormat) -> Option<SaveFormat> {
    match format {
        DocumentFormat::Hwpx => Some(SaveFormat::Hwpx),
        DocumentFormat::CompoundFile => Some(SaveFormat::Hwp),
        DocumentFormat::Docx | DocumentFormat::ZipContainer => Some(SaveFormat::Docx),
        _ => None,
    }
}

fn format_name(format: DocumentFormat) -> &'static str {
    match format {
        DocumentFormat::Pdf => "pdf",
        DocumentFormat::Hwpx => "hwpx",
        DocumentFormat::CompoundFile => "hwp",
        DocumentFormat::Docx | DocumentFormat::ZipContainer => "docx",
        _ => "unknown",
    }
}

#[uniffi::export]
impl PapyEngine {
    /// 엔진을 만든다. `korean_font` 는 앱에 넣은 한글 TrueType 글꼴 경로(없으면 기본 글꼴).
    #[uniffi::constructor]
    pub fn new(korean_font: Option<String>) -> Arc<Self> {
        let engine = Engine::with_resources(
            Limits::default(),
            EngineResources {
                // 모바일에는 PDFium 을 넣지 않는다. PDF 는 앱이 운영체제 렌더러로 연다.
                pdfium_library: PathBuf::new(),
                korean_font: korean_font.map(PathBuf::from).filter(|path| path.exists()),
            },
        );
        // 글꼴 목록은 처음 한 번만 만든다 — 한글 글꼴을 등록한 다음에 미리 읽는다.
        papy_core::warm_up();
        Arc::new(Self { engine })
    }

    /// 바이트로 문서를 연다.
    pub fn open(
        &self,
        bytes: Vec<u8>,
        password: Option<String>,
    ) -> Result<DocumentSummary, PapyError> {
        let id = self.engine.open(
            DocumentSource::Bytes(Arc::from(bytes)),
            OpenOptions {
                password,
                ..OpenOptions::default()
            },
        )?;
        match self.engine.info(id) {
            Ok(info) => Ok(DocumentSummary {
                session: id.get(),
                format: format_name(info.format).into(),
                page_count: info.page_count,
                title: info.title,
                dropped_objects: info.dropped_objects,
                can_edit: info.capabilities.edit,
            }),
            Err(error) => {
                let _ = self.engine.close(id);
                Err(error.into())
            }
        }
    }

    /// 쪽 크기(pt).
    pub fn page_size(&self, session_id: u64, page: u32) -> Result<PageDimensions, PapyError> {
        let size = self.engine.page_size(session(session_id)?, page)?;
        Ok(PageDimensions {
            width: size.width,
            height: size.height,
        })
    }

    /// 쪽을 그린다. `scale` 1.0 = 72 DPI(1pt = 1px). 흰 배경.
    pub fn render_page(
        &self,
        session_id: u64,
        page: u32,
        scale: f32,
    ) -> Result<PageImage, PapyError> {
        let request = RenderRequest {
            white_background: true,
            ..RenderRequest::with_scale(scale)
        };
        let bitmap = self
            .engine
            .render_page(session(session_id)?, page, &request)?;
        Ok(PageImage {
            width: bitmap.width,
            height: bitmap.height,
            rgba: bitmap.pixels,
        })
    }

    /// 쪽의 글자.
    pub fn extract_text(&self, session_id: u64, page: u32) -> Result<String, PapyError> {
        Ok(self.engine.extract_text(session(session_id)?, page)?)
    }

    /// 쪽 수(편집하면 바뀔 수 있다).
    pub fn page_count(&self, session_id: u64) -> Result<u32, PapyError> {
        Ok(self.engine.page_count(session(session_id)?)?)
    }

    /// 쪽의 (x, y)(pt) 자리에 있는 문단. 글자가 없는 곳이면 가장 가까운 문단.
    pub fn paragraph_at(
        &self,
        session_id: u64,
        page: u32,
        x: f32,
        y: f32,
    ) -> Result<ParagraphAtPoint, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            let pos = doc.hit_test(page, x, y)?;
            let start = TextPos { offset: 0, ..pos };
            let end = paragraph_end(doc, pos.section, pos.paragraph)?;
            let text = doc.text_in_range(start, end)?;
            let boxes = doc
                .selection_rects(start, end)?
                .into_iter()
                .map(|r| PageRegion {
                    page: r.page,
                    x: r.x,
                    y: r.y,
                    width: r.width,
                    height: r.height,
                })
                .collect::<Vec<_>>();
            // 빈 문단은 커서 자리라도 보이게 한다.
            let boxes = if boxes.is_empty() {
                let caret = doc.caret_rect(start)?;
                vec![PageRegion {
                    page: caret.page,
                    x: caret.x,
                    y: caret.y,
                    width: 4.0,
                    height: caret.height,
                }]
            } else {
                boxes
            };
            Ok(ParagraphAtPoint {
                section: pos.section,
                paragraph: pos.paragraph,
                text,
                boxes,
            })
        })?)
    }

    /// 문단 글자를 바꾼다(문단 서식은 그대로, `\n` 은 문단 나누기).
    /// 지우기·넣기로 하므로 되돌리기 단계 수(0~2)를 돌려준다 — 되돌릴 때 그만큼 [`Self::undo`] 를 부른다.
    pub fn replace_paragraph(
        &self,
        session_id: u64,
        section: u32,
        paragraph: u32,
        text: String,
    ) -> Result<u32, PapyError> {
        if text.chars().count() > 100_000 {
            return Err(PapyError::LimitExceeded {
                detail: "paragraph text is too long".into(),
            });
        }
        Ok(self.engine.edit(session(session_id)?, |doc| {
            let start = TextPos {
                section,
                paragraph,
                offset: 0,
            };
            let end = paragraph_end(doc, section, paragraph)?;
            let mut steps = 0;
            if end.offset > 0 {
                doc.delete_range(start, end)?;
                steps += 1;
            }
            if !text.is_empty() {
                doc.insert_text(start, &text)?;
                steps += 1;
            }
            Ok(steps)
        })?)
    }

    /// 마지막 편집을 되돌린다. 되돌린 것이 있으면 true.
    pub fn undo(&self, session_id: u64) -> Result<bool, PapyError> {
        Ok(self
            .engine
            .edit(session(session_id)?, |doc| Ok(doc.undo()?.is_some()))?)
    }

    /// 원래 형식으로 저장한 바이트(암호 문서는 같은 암호로).
    pub fn save(&self, session_id: u64) -> Result<Vec<u8>, PapyError> {
        let id = session(session_id)?;
        let format =
            save_format(self.engine.info(id)?.format).ok_or_else(|| PapyError::NotImplemented {
                detail: "this format can't be saved here".into(),
            })?;
        Ok(self.engine.save(id, format)?)
    }

    /// 문서를 닫는다. 이미 닫혔으면 아무것도 하지 않는다.
    pub fn close(&self, session_id: u64) {
        if let Ok(id) = session(session_id) {
            let _ = self.engine.close(id);
        }
    }
}

#[cfg(test)]
mod tests;
