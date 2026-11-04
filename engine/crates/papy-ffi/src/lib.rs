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
    CaretMotion, CellPos, CharFormat, Document, DocumentFormat, DocumentSource, Engine,
    EngineResources, ErrorCode, HitTarget, Limits, ObjectKind, ObjectRef, OpenOptions, PageRect,
    RenderRequest, SaveFormat, SessionId, TableOp, TextPos,
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

/// 본문 위치. 구역·문단은 0부터, `offset` 은 문단 안 글자(유니코드 스칼라) 위치.
/// (Swift 에서 앱 쪽 이름과 겹치지 않게 Engine 을 붙인다.)
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct EnginePosition {
    /// 구역
    pub section: u32,
    /// 문단
    pub paragraph: u32,
    /// 문단 안 글자 위치(칸 안이면 칸 문단 안 위치)
    pub offset: u32,
    /// 표 칸·글상자 안이면 그 칸(`paragraph` 는 표를 가진 본문 문단)
    pub cell: Option<EngineCell>,
}

/// 표 칸·글상자: 본문 문단 안 개체 번호, 칸 번호, 칸 안 문단.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct EngineCell {
    /// 문단 안 개체(표·글상자) 번호
    pub control: u32,
    /// 칸 번호(글상자는 0)
    pub cell: u32,
    /// 칸 안 문단
    pub cell_paragraph: u32,
}

impl From<TextPos> for EnginePosition {
    fn from(pos: TextPos) -> Self {
        Self {
            section: pos.section,
            paragraph: pos.paragraph,
            offset: pos.offset,
            cell: None,
        }
    }
}

impl From<CellPos> for EnginePosition {
    fn from(pos: CellPos) -> Self {
        Self {
            section: pos.section,
            paragraph: pos.paragraph,
            offset: pos.offset,
            cell: Some(EngineCell {
                control: pos.control,
                cell: pos.cell,
                cell_paragraph: pos.cell_paragraph,
            }),
        }
    }
}

impl EnginePosition {
    /// 칸 안 위치면 `Err(CellPos)`, 본문이면 `Ok(TextPos)`
    fn split(self) -> std::result::Result<TextPos, CellPos> {
        match self.cell {
            None => Ok(TextPos::from(self)),
            Some(cell) => Err(CellPos {
                section: self.section,
                paragraph: self.paragraph,
                control: cell.control,
                cell: cell.cell,
                cell_paragraph: cell.cell_paragraph,
                offset: self.offset,
            }),
        }
    }
}

/// 본문 위치만 받는 기능(선택·글자 모양 등). 칸 안이면 아직 안 된다.
fn body(position: EnginePosition) -> papy_core::Result<TextPos> {
    position.split().map_err(|_| {
        papy_core::Error::new(
            ErrorCode::NotImplemented,
            "not supported inside table cells yet",
        )
    })
}

impl From<EnginePosition> for TextPos {
    fn from(pos: EnginePosition) -> Self {
        Self {
            section: pos.section,
            paragraph: pos.paragraph,
            offset: pos.offset,
        }
    }
}

impl From<PageRect> for PageRegion {
    fn from(r: PageRect) -> Self {
        Self {
            page: r.page,
            x: r.x,
            y: r.y,
            width: r.width,
            height: r.height,
        }
    }
}

/// 커서: 위치와 화면에 그릴 세로 막대(너비 0).
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct EngineCaret {
    /// 위치
    pub position: EnginePosition,
    /// 커서 사각형(pt)
    pub rect: PageRegion,
}

/// 편집 결과: 커서와 지금 되돌릴 수 있는 단계 수(이어 친 글자는 한 단계로 묶인다).
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct EngineEdit {
    /// 편집 뒤 커서
    pub caret: EngineCaret,
    /// 되돌리기 단계 수
    pub undo_depth: u32,
}

/// 범위(`start` ≤ `end`).
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct EngineRange {
    /// 처음
    pub start: EnginePosition,
    /// 끝
    pub end: EnginePosition,
}

/// 커서 이동 방향.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum CaretDirection {
    /// 한 글자 앞
    Left,
    /// 한 글자 뒤
    Right,
    /// 한 줄 위
    Up,
    /// 한 줄 아래
    Down,
    /// 줄 처음
    LineStart,
    /// 줄 끝
    LineEnd,
}

/// 글자 모양. 읽을 때는 다 채워지고, 적용할 때는 값이 있는 것만 바꾼다.
#[derive(Debug, Clone, Default, PartialEq, uniffi::Record)]
pub struct EngineTextStyle {
    /// 크기(pt)
    pub font_size: Option<f32>,
    /// 굵게
    pub bold: Option<bool>,
    /// 기울임
    pub italic: Option<bool>,
    /// 밑줄
    pub underline: Option<bool>,
    /// 취소선
    pub strikethrough: Option<bool>,
    /// 색(`#rrggbb`)
    pub color: Option<String>,
}

/// 되돌리기 결과.
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct EngineUndo {
    /// 되돌린 자리의 커서(되돌린 것이 없거나 자리가 사라졌으면 없음)
    pub caret: Option<EngineCaret>,
    /// 남은 되돌리기 단계 수
    pub undo_depth: u32,
}

/// 쪽 위 개체 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum EngineObjectKind {
    /// 표
    Table,
    /// 그림
    Picture,
}

/// 문서 안 개체(본문 문단과 그 안 개체 번호).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct EngineObjectRef {
    /// 구역
    pub section: u32,
    /// 개체를 가진 본문 문단
    pub paragraph: u32,
    /// 문단 안 개체 번호
    pub control: u32,
}

impl From<EngineObjectRef> for ObjectRef {
    fn from(o: EngineObjectRef) -> Self {
        Self {
            section: o.section,
            paragraph: o.paragraph,
            control: o.control,
        }
    }
}

/// 표 칸 자리.
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct EngineTableCell {
    /// 칸 번호
    pub cell: u32,
    /// 행
    pub row: u32,
    /// 열
    pub col: u32,
    /// 합친 행 수
    pub row_span: u32,
    /// 합친 열 수
    pub col_span: u32,
    /// 칸 사각형(pt)
    pub rect: PageRegion,
}

/// 쪽에 그려진 표·그림.
#[derive(Debug, Clone, uniffi::Record)]
pub struct EngineObject {
    /// 종류
    pub kind: EngineObjectKind,
    /// 가리키는 개체
    pub object: EngineObjectRef,
    /// 쪽 위 사각형(pt)
    pub rect: PageRegion,
    /// 표 행 수(그림은 0)
    pub rows: u32,
    /// 표 열 수
    pub cols: u32,
    /// 표 칸
    pub cells: Vec<EngineTableCell>,
}

/// 표 고치기.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum EngineTableOp {
    /// 행 넣기
    InsertRow {
        /// 기준 행
        row: u32,
        /// 아래에
        below: bool,
    },
    /// 열 넣기
    InsertColumn {
        /// 기준 열
        col: u32,
        /// 오른쪽에
        right: bool,
    },
    /// 행 지우기
    DeleteRow {
        /// 행
        row: u32,
    },
    /// 열 지우기
    DeleteColumn {
        /// 열
        col: u32,
    },
    /// 칸 합치기(양 끝 포함)
    Merge {
        /// 처음 행
        start_row: u32,
        /// 처음 열
        start_col: u32,
        /// 끝 행
        end_row: u32,
        /// 끝 열
        end_col: u32,
    },
}

impl From<EngineTableOp> for TableOp {
    fn from(op: EngineTableOp) -> Self {
        match op {
            EngineTableOp::InsertRow { row, below } => TableOp::InsertRow { row, below },
            EngineTableOp::InsertColumn { col, right } => TableOp::InsertColumn { col, right },
            EngineTableOp::DeleteRow { row } => TableOp::DeleteRow { row },
            EngineTableOp::DeleteColumn { col } => TableOp::DeleteColumn { col },
            EngineTableOp::Merge {
                start_row,
                start_col,
                end_row,
                end_col,
            } => TableOp::Merge {
                start_row,
                start_col,
                end_row,
                end_col,
            },
        }
    }
}

fn object_kind(kind: EngineObjectKind) -> ObjectKind {
    match kind {
        EngineObjectKind::Table => ObjectKind::Table,
        EngineObjectKind::Picture => ObjectKind::Picture,
    }
}

/// 그림 한 장의 최대 크기
const MAX_PICTURE_BYTES: usize = 32 * 1024 * 1024;

/// 한 번에 넣을 수 있는 글자 수 상한.
const MAX_INSERT_CHARS: usize = 100_000;

fn too_long() -> PapyError {
    PapyError::LimitExceeded {
        detail: "text is too long".into(),
    }
}

fn cell_caret(doc: &mut dyn Document, pos: CellPos) -> papy_core::Result<EngineCaret> {
    Ok(EngineCaret {
        position: pos.into(),
        rect: doc.cell_caret_rect(pos)?.into(),
    })
}

fn any_caret(doc: &mut dyn Document, position: EnginePosition) -> papy_core::Result<EngineCaret> {
    match position.split() {
        Ok(pos) => caret(doc, pos),
        Err(cell) => cell_caret(doc, cell),
    }
}

fn cell_edit_result(doc: &mut dyn Document, pos: CellPos) -> papy_core::Result<EngineEdit> {
    Ok(EngineEdit {
        caret: cell_caret(doc, pos)?,
        undo_depth: depth(doc),
    })
}

fn caret(doc: &mut dyn Document, pos: TextPos) -> papy_core::Result<EngineCaret> {
    Ok(EngineCaret {
        position: pos.into(),
        rect: doc.caret_rect(pos)?.into(),
    })
}

fn depth(doc: &dyn Document) -> u32 {
    u32::try_from(doc.undo_depth()).unwrap_or(u32::MAX)
}

fn edit_result(doc: &mut dyn Document, pos: TextPos) -> papy_core::Result<EngineEdit> {
    Ok(EngineEdit {
        caret: caret(doc, pos)?,
        undo_depth: depth(doc),
    })
}

/// 단어 글자인가(공백·문장 부호가 아니면 단어로 본다).
fn is_word_char(c: char) -> bool {
    c.is_alphanumeric() || c == '_'
}

/// 문단의 글자 위치 `prefix..old_len-suffix` 만 바꾸면 되는 공통 앞·뒤 길이.
fn common_ends(old: &[char], new: &[char]) -> (usize, usize) {
    let prefix = old.iter().zip(new).take_while(|(a, b)| a == b).count();
    let room = old.len().min(new.len()) - prefix;
    let suffix = old
        .iter()
        .rev()
        .zip(new.iter().rev())
        .take(room)
        .take_while(|(a, b)| a == b)
        .count();
    (prefix, suffix)
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

    /// 문단 글자를 바꾼다(`\n` 은 문단 나누기). 앞뒤로 같은 부분은 두고 바뀐 가운데만 지우고 넣어
    /// 바뀌지 않은 글자의 모양(굵게·색 등)을 지킨다. 편집 뒤 되돌리기 단계 수를 돌려준다.
    pub fn replace_paragraph(
        &self,
        session_id: u64,
        section: u32,
        paragraph: u32,
        text: String,
    ) -> Result<u32, PapyError> {
        if text.chars().count() > MAX_INSERT_CHARS {
            return Err(too_long());
        }
        Ok(self.engine.edit(session(session_id)?, |doc| {
            let start = TextPos {
                section,
                paragraph,
                offset: 0,
            };
            let end = paragraph_end(doc, section, paragraph)?;
            let old: Vec<char> = doc.text_in_range(start, end)?.chars().collect();
            let new: Vec<char> = text.chars().collect();
            let at = |offset: usize| TextPos {
                offset: offset as u32,
                ..start
            };
            // 글자 수가 위치 수와 다르면(문단 안 개체 등) 겹치는 부분을 믿을 수 없으니 통째로 바꾼다.
            let (prefix, suffix, delete_end) = if old.len() == end.offset as usize {
                let (prefix, suffix) = common_ends(&old, &new);
                (prefix, suffix, at(old.len() - suffix))
            } else {
                (0, 0, end)
            };
            if delete_end.offset as usize > prefix {
                doc.delete_range(at(prefix), delete_end)?;
            }
            let middle: String = new[prefix..new.len() - suffix].iter().collect();
            if !middle.is_empty() {
                doc.insert_text(at(prefix), &middle)?;
            }
            Ok(depth(doc))
        })?)
    }

    /// 쪽의 (x, y)(pt) 자리에 가장 가까운 커서.
    pub fn caret_at_point(
        &self,
        session_id: u64,
        page: u32,
        x: f32,
        y: f32,
    ) -> Result<EngineCaret, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            match doc.hit_test_target(page, x, y)? {
                HitTarget::Body(pos) => caret(doc, pos),
                HitTarget::Cell(cell) => cell_caret(doc, cell),
            }
        })?)
    }

    /// 위치의 커서.
    pub fn caret_at(
        &self,
        session_id: u64,
        position: EnginePosition,
    ) -> Result<EngineCaret, PapyError> {
        Ok(self
            .engine
            .edit(session(session_id)?, |doc| any_caret(doc, position))?)
    }

    /// 커서를 옮긴다. `preferred_x` 는 위·아래로 옮길 때 지킬 가로 위치(pt).
    pub fn move_caret(
        &self,
        session_id: u64,
        position: EnginePosition,
        direction: CaretDirection,
        preferred_x: f32,
    ) -> Result<EngineCaret, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            let pos = match position.split() {
                Ok(pos) => pos,
                // 칸 안: 왼쪽·오른쪽만(위·아래·줄 끝은 제자리)
                Err(cell) => {
                    let moved = match direction {
                        CaretDirection::Left => doc.cell_move_caret(cell, CaretMotion::Left)?,
                        CaretDirection::Right => doc.cell_move_caret(cell, CaretMotion::Right)?,
                        _ => cell,
                    };
                    return cell_caret(doc, moved);
                }
            };
            let moved = match direction {
                CaretDirection::Left => doc.move_caret(pos, CaretMotion::Left)?,
                CaretDirection::Right => doc.move_caret(pos, CaretMotion::Right)?,
                CaretDirection::LineStart => doc.move_caret(pos, CaretMotion::LineStart)?,
                CaretDirection::LineEnd => doc.move_caret(pos, CaretMotion::LineEnd)?,
                CaretDirection::Up => doc.move_vertical(pos, -1, preferred_x)?,
                CaretDirection::Down => doc.move_vertical(pos, 1, preferred_x)?,
            };
            caret(doc, moved)
        })?)
    }

    /// 커서 자리에 글자를 넣는다(`\n` 은 문단 나누기). 앞 글자의 모양을 따른다.
    pub fn insert_text(
        &self,
        session_id: u64,
        position: EnginePosition,
        text: String,
    ) -> Result<EngineEdit, PapyError> {
        if text.chars().count() > MAX_INSERT_CHARS {
            return Err(too_long());
        }
        Ok(self
            .engine
            .edit(session(session_id)?, |doc| match position.split() {
                Ok(pos) => {
                    let outcome = doc.insert_text(pos, &text)?;
                    edit_result(doc, outcome.caret)
                }
                Err(cell) => {
                    let at = doc.cell_insert_text(cell, &text)?;
                    cell_edit_result(doc, at)
                }
            })?)
    }

    /// 커서 앞 글자를 지운다(문단 처음이면 앞 문단과 합친다).
    pub fn delete_backward(
        &self,
        session_id: u64,
        position: EnginePosition,
    ) -> Result<EngineEdit, PapyError> {
        Ok(self
            .engine
            .edit(session(session_id)?, |doc| match position.split() {
                Ok(pos) => {
                    let outcome = doc.delete_backward(pos)?;
                    edit_result(doc, outcome.caret)
                }
                Err(cell) => {
                    let at = doc.cell_delete_backward(cell)?;
                    cell_edit_result(doc, at)
                }
            })?)
    }

    /// 범위를 지운다(문단을 넘으면 합친다). 같은 구역 안이어야 한다.
    pub fn delete_range(
        &self,
        session_id: u64,
        range: EngineRange,
    ) -> Result<EngineEdit, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            let outcome = doc.delete_range(body(range.start)?, body(range.end)?)?;
            edit_result(doc, outcome.caret)
        })?)
    }

    /// 범위를 줄마다 덮는 사각형(선택 표시).
    pub fn selection_regions(
        &self,
        session_id: u64,
        range: EngineRange,
    ) -> Result<Vec<PageRegion>, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            Ok(doc
                .selection_rects(body(range.start)?, body(range.end)?)?
                .into_iter()
                .map(PageRegion::from)
                .collect())
        })?)
    }

    /// 범위의 글자(문단 사이는 `\n`).
    pub fn text_in_range(&self, session_id: u64, range: EngineRange) -> Result<String, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            doc.text_in_range(body(range.start)?, body(range.end)?)
        })?)
    }

    /// 위치가 든 단어의 범위(두 번 눌러 고르기). 단어가 아니면 빈 범위.
    pub fn word_at(
        &self,
        session_id: u64,
        position: EnginePosition,
    ) -> Result<EngineRange, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            // 칸 안은 아직 단어를 고르지 않는다(빈 범위 = 커서만)
            let Ok(pos) = position.split() else {
                return Ok(EngineRange {
                    start: position,
                    end: position,
                });
            };
            let start = TextPos { offset: 0, ..pos };
            let end = paragraph_end(doc, pos.section, pos.paragraph)?;
            let chars: Vec<char> = doc.text_in_range(start, end)?.chars().collect();
            let at = (pos.offset as usize).min(chars.len());
            let mut from = at;
            while from > 0 && is_word_char(chars[from - 1]) {
                from -= 1;
            }
            let mut to = at;
            while to < chars.len() && is_word_char(chars[to]) {
                to += 1;
            }
            let make = |offset: usize| EnginePosition {
                offset: offset as u32,
                ..position
            };
            Ok(EngineRange {
                start: make(from),
                end: make(to),
            })
        })?)
    }

    /// 위치의 글자 모양(커서 앞 글자).
    pub fn text_style(
        &self,
        session_id: u64,
        position: EnginePosition,
    ) -> Result<EngineTextStyle, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            let format = doc.char_format(body(position)?)?;
            Ok(EngineTextStyle {
                font_size: format.font_size,
                bold: format.bold,
                italic: format.italic,
                underline: format.underline,
                strikethrough: format.strikethrough,
                color: format.color,
            })
        })?)
    }

    /// 범위에 글자 모양을 적용한다(값이 있는 것만). 편집 뒤 되돌리기 단계 수를 돌려준다.
    pub fn apply_text_style(
        &self,
        session_id: u64,
        range: EngineRange,
        style: EngineTextStyle,
    ) -> Result<u32, PapyError> {
        if let Some(size) = style.font_size {
            if !(1.0..=4096.0).contains(&size) {
                return Err(PapyError::InvalidArgument {
                    detail: "font size out of range".into(),
                });
            }
        }
        if let Some(color) = &style.color {
            let hex = color.strip_prefix('#').unwrap_or("");
            if hex.len() != 6 || !hex.chars().all(|c| c.is_ascii_hexdigit()) {
                return Err(PapyError::InvalidArgument {
                    detail: "color must be #rrggbb".into(),
                });
            }
        }
        Ok(self.engine.edit(session(session_id)?, |doc| {
            let format = CharFormat {
                font_family: None,
                font_size: style.font_size,
                bold: style.bold,
                italic: style.italic,
                underline: style.underline,
                strikethrough: style.strikethrough,
                color: style.color.clone(),
            };
            doc.apply_char_format(body(range.start)?, body(range.end)?, &format)?;
            Ok(depth(doc))
        })?)
    }

    /// 마지막 편집 한 단계를 되돌린다.
    pub fn undo(&self, session_id: u64) -> Result<EngineUndo, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            let caret = match doc.undo()? {
                Some(outcome) => caret(doc, outcome.caret).ok(),
                None => None,
            };
            Ok(EngineUndo {
                caret,
                undo_depth: depth(doc),
            })
        })?)
    }

    /// 지금 되돌릴 수 있는 단계 수.
    pub fn undo_depth(&self, session_id: u64) -> Result<u32, PapyError> {
        Ok(self
            .engine
            .edit(session(session_id)?, |doc| Ok(depth(doc)))?)
    }

    /// 쪽에 그려진 표·그림(본문 것만).
    pub fn page_objects(&self, session_id: u64, page: u32) -> Result<Vec<EngineObject>, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            Ok(doc
                .page_objects(page)?
                .into_iter()
                .map(|o| EngineObject {
                    kind: match o.kind {
                        ObjectKind::Table => EngineObjectKind::Table,
                        _ => EngineObjectKind::Picture,
                    },
                    object: EngineObjectRef {
                        section: o.object.section,
                        paragraph: o.object.paragraph,
                        control: o.object.control,
                    },
                    rect: o.rect.into(),
                    rows: o.rows,
                    cols: o.cols,
                    cells: o
                        .cells
                        .into_iter()
                        .map(|c| EngineTableCell {
                            cell: c.cell,
                            row: c.row,
                            col: c.col,
                            row_span: c.row_span,
                            col_span: c.col_span,
                            rect: c.rect.into(),
                        })
                        .collect(),
                })
                .collect())
        })?)
    }

    /// 본문 위치에 표를 넣는다. 편집 뒤 되돌리기 단계 수.
    pub fn insert_table(
        &self,
        session_id: u64,
        position: EnginePosition,
        rows: u32,
        cols: u32,
    ) -> Result<u32, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            doc.insert_table(body(position)?, rows, cols)?;
            Ok(depth(doc))
        })?)
    }

    /// 표를 고친다(행·열 넣기·지우기, 칸 합치기). 편집 뒤 되돌리기 단계 수.
    pub fn edit_table(
        &self,
        session_id: u64,
        table: EngineObjectRef,
        op: EngineTableOp,
    ) -> Result<u32, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            doc.edit_table(table.into(), op.into())?;
            Ok(depth(doc))
        })?)
    }

    /// 본문 위치에 그림(PNG·JPEG·GIF·BMP)을 글자처럼 넣는다. 크기는 pt.
    pub fn insert_picture(
        &self,
        session_id: u64,
        position: EnginePosition,
        image: Vec<u8>,
        width: f32,
        height: f32,
    ) -> Result<u32, PapyError> {
        if image.len() > MAX_PICTURE_BYTES {
            return Err(PapyError::LimitExceeded {
                detail: "picture is too large".into(),
            });
        }
        Ok(self.engine.edit(session(session_id)?, |doc| {
            doc.insert_picture(body(position)?, &image, width, height)?;
            Ok(depth(doc))
        })?)
    }

    /// 그림 크기를 바꾼다(pt).
    pub fn resize_picture(
        &self,
        session_id: u64,
        picture: EngineObjectRef,
        width: f32,
        height: f32,
    ) -> Result<u32, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            doc.resize_picture(picture.into(), width, height)?;
            Ok(depth(doc))
        })?)
    }

    /// 표·그림을 지운다.
    pub fn delete_object(
        &self,
        session_id: u64,
        object: EngineObjectRef,
        kind: EngineObjectKind,
    ) -> Result<u32, PapyError> {
        Ok(self.engine.edit(session(session_id)?, |doc| {
            doc.delete_object(object.into(), object_kind(kind))?;
            Ok(depth(doc))
        })?)
    }

    /// 다른 형식으로 내보낸 바이트. `extension`: "pdf" | "hwpx" | "hwp" | "docx" | "txt".
    /// 작업 문서는 그대로 둔다.
    pub fn export(&self, session_id: u64, extension: String) -> Result<Vec<u8>, PapyError> {
        let format =
            SaveFormat::from_extension(&extension).ok_or_else(|| PapyError::InvalidArgument {
                detail: "unknown format".into(),
            })?;
        Ok(self.engine.save(session(session_id)?, format)?)
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
