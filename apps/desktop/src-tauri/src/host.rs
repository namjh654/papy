//! 앱 명령의 본체. Tauri 에 의존하지 않는다 — 명령(`lib.rs`)은 이 함수들을 블로킹 스레드에서 부르는
//! 얇은 껍데기다. 개발용 브리지(브라우저에서 실제 엔진으로 화면 점검)도 같은 함수를 쓴다.

use std::path::PathBuf;

use papy_core::{
    Alignment, Annotation, CancelToken, CaretMotion, CharFormat, Document, DocumentSource, Engine,
    Error, ErrorCode, MarkupKind, OpenOptions, OutlineItem, PageOp, PageRect, ParaFormat,
    RenderRequest, SaveFormat, SessionId, TextPos, UnsupportedKind,
};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

/// 앱이 넘긴 숫자 핸들을 세션 ID 로 바꾼다.
pub fn session(id: u64) -> papy_core::Result<SessionId> {
    SessionId::from_raw(id).ok_or_else(|| Error::new(ErrorCode::SessionNotFound, "invalid session"))
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PageSizeDto {
    width: f32,
    height: f32,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OpenedDocument {
    id: u64,
    file_name: String,
    format: &'static str,
    page_count: u32,
    byte_size: u64,
    title: Option<String>,
    can_extract_text: bool,
    has_outline: bool,
    can_edit: bool,
    can_annotate: bool,
    can_edit_pages: bool,
    /// 저장·변환 가능한 형식의 확장자.
    save_formats: Vec<&'static str>,
    /// 쪽마다 크기(pt). 앱이 스크롤 영역을 미리 잡는 데 쓴다.
    pages: Vec<PageSizeDto>,
    /// 열 때 옮기지 못한 요소 수(DOCX 도형 등). 0 보다 크면 앱이 알린다.
    dropped_objects: u32,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CommentDto {
    author: String,
    date: String,
    text: String,
    anchor: String,
    page: Option<u32>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReviewDto {
    comments: Vec<CommentDto>,
    insertions: u32,
    deletions: u32,
}

/// 메모와 변경 추적.
pub fn review(engine: &Engine, id: u64) -> papy_core::Result<ReviewDto> {
    let review = engine.review(session(id)?)?;
    Ok(ReviewDto {
        comments: review
            .comments
            .into_iter()
            .map(|c| CommentDto {
                author: c.author,
                date: c.date,
                text: c.text,
                anchor: c.anchor,
                page: c.page,
            })
            .collect(),
        insertions: review.insertions,
        deletions: review.deletions,
    })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OutlineDto {
    title: String,
    page: Option<u32>,
    children: Vec<OutlineDto>,
}

impl From<OutlineItem> for OutlineDto {
    fn from(item: OutlineItem) -> Self {
        Self {
            title: item.title,
            page: item.page,
            children: item.children.into_iter().map(Into::into).collect(),
        }
    }
}

pub fn page_sizes(engine: &Engine, id: SessionId) -> papy_core::Result<Vec<PageSizeDto>> {
    let count = engine.page_count(id)?;
    (0..count)
        .map(|page| {
            engine.page_size(id, page).map(|size| PageSizeDto {
                width: size.width,
                height: size.height,
            })
        })
        .collect()
}

// ---- 편집 ----

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Pos {
    section: u32,
    paragraph: u32,
    offset: u32,
}

impl From<Pos> for TextPos {
    fn from(pos: Pos) -> Self {
        TextPos {
            section: pos.section,
            paragraph: pos.paragraph,
            offset: pos.offset,
        }
    }
}

impl From<TextPos> for Pos {
    fn from(pos: TextPos) -> Self {
        Pos {
            section: pos.section,
            paragraph: pos.paragraph,
            offset: pos.offset,
        }
    }
}

fn rect_json(rect: PageRect) -> Value {
    json!({ "page": rect.page, "x": rect.x, "y": rect.y, "width": rect.width, "height": rect.height })
}

#[derive(Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CharFormatDto {
    font_family: Option<String>,
    font_size: Option<f32>,
    bold: Option<bool>,
    italic: Option<bool>,
    underline: Option<bool>,
    strikethrough: Option<bool>,
    color: Option<String>,
}

#[derive(Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ParaFormatDto {
    /// left | center | right | justify
    alignment: Option<String>,
    line_spacing: Option<f32>,
}

/// 편집 요청. `kind` 로 구분한다.
#[derive(Debug, Deserialize)]
#[serde(
    tag = "kind",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
pub enum EditOp {
    HitTest {
        page: u32,
        x: f32,
        y: f32,
    },
    CaretRect {
        pos: Pos,
    },
    MoveVertical {
        pos: Pos,
        lines: i32,
        preferred_x: f32,
    },
    MoveCaret {
        pos: Pos,
        motion: String,
    },
    SelectionRects {
        start: Pos,
        end: Pos,
    },
    TextInRange {
        start: Pos,
        end: Pos,
    },
    InsertText {
        pos: Pos,
        text: String,
    },
    DeleteRange {
        start: Pos,
        end: Pos,
    },
    DeleteBackward {
        pos: Pos,
    },
    DeleteForward {
        pos: Pos,
    },
    CharFormat {
        pos: Pos,
    },
    ApplyCharFormat {
        start: Pos,
        end: Pos,
        format: CharFormatDto,
    },
    ParaFormat {
        pos: Pos,
    },
    ApplyParaFormat {
        start: Pos,
        end: Pos,
        format: ParaFormatDto,
    },
    Undo,
    Redo,
    /// 영역 안 글줄 사각형(형광펜 미리 보기).
    TextRects {
        page: u32,
        area: RectDto,
    },
    /// 영역 안 글자(PDF 글자 고치기 입력란).
    TextInArea {
        page: u32,
        area: RectDto,
    },
    Annotate {
        annotation: AnnotationDto,
    },
    EditPages {
        op: PageOpDto,
    },
}

#[derive(Debug, Clone, Copy, Deserialize)]
pub struct RectDto {
    x: f32,
    y: f32,
    width: f32,
    height: f32,
}

/// 주석 요청. 좌표는 pt(화면에 보이는 쪽의 왼쪽 위 기준), 색은 `#rrggbb`.
#[derive(Debug, Deserialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
pub enum AnnotationDto {
    TextBox {
        page: u32,
        x: f32,
        y: f32,
        text: String,
        font_size: f32,
        color: String,
    },
    /// kind: highlight | underline | strikeOut
    Markup {
        page: u32,
        kind: String,
        area: RectDto,
        color: String,
    },
    Ink {
        page: u32,
        points: Vec<(f32, f32)>,
        color: String,
        width: f32,
    },
    Note {
        page: u32,
        x: f32,
        y: f32,
        text: String,
    },
    /// 쪽에 있는 글자 고쳐 쓰기. font_size 가 없으면 원래 크기.
    ReplaceText {
        page: u32,
        area: RectDto,
        text: String,
        color: String,
        font_size: Option<f32>,
    },
}

#[derive(Debug, Clone, Copy, Deserialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
pub enum PageOpDto {
    Rotate { page: u32, quarter_turns: i32 },
    Delete { page: u32 },
    Move { from: u32, to: u32 },
}

fn page_rect(page: u32, rect: RectDto) -> PageRect {
    PageRect {
        page,
        x: rect.x,
        y: rect.y,
        width: rect.width,
        height: rect.height,
    }
}

fn annotation(dto: AnnotationDto) -> papy_core::Result<Annotation> {
    Ok(match dto {
        AnnotationDto::TextBox {
            page,
            x,
            y,
            text,
            font_size,
            color,
        } => Annotation::TextBox {
            page,
            x,
            y,
            text,
            font_size,
            color,
        },
        AnnotationDto::Markup {
            page,
            kind,
            area,
            color,
        } => Annotation::Markup {
            page,
            kind: match kind.as_str() {
                "highlight" => MarkupKind::Highlight,
                "underline" => MarkupKind::Underline,
                "strikeOut" => MarkupKind::StrikeOut,
                _ => return Err(bad_argument("unknown markup kind")),
            },
            area: page_rect(page, area),
            color,
        },
        AnnotationDto::Ink {
            page,
            points,
            color,
            width,
        } => {
            if points.len() > 10_000 {
                return Err(bad_argument("too many points"));
            }
            Annotation::Ink {
                page,
                points,
                color,
                width,
            }
        }
        AnnotationDto::Note { page, x, y, text } => Annotation::Note { page, x, y, text },
        AnnotationDto::ReplaceText {
            page,
            area,
            text,
            color,
            font_size,
        } => {
            if text.chars().count() > 10_000 {
                return Err(bad_argument("text is too long"));
            }
            Annotation::ReplaceText {
                page,
                area: page_rect(page, area),
                text,
                color,
                font_size,
            }
        }
    })
}

/// 주석·쪽 편집 뒤 응답. 커서가 없는 문서라 쪽 수만 알린다.
fn pages_changed(doc: &mut dyn Document) -> Value {
    json!({ "changed": true, "pageCount": doc.info().page_count })
}

fn bad_argument(message: &str) -> Error {
    Error::new(ErrorCode::InvalidArgument, message)
}

fn alignment(name: &str) -> papy_core::Result<Alignment> {
    Ok(match name {
        "left" => Alignment::Left,
        "center" => Alignment::Center,
        "right" => Alignment::Right,
        "justify" => Alignment::Justify,
        _ => return Err(bad_argument("unknown alignment")),
    })
}

fn alignment_name(alignment: Alignment) -> &'static str {
    match alignment {
        Alignment::Left => "left",
        Alignment::Center => "center",
        Alignment::Right => "right",
        Alignment::Justify => "justify",
    }
}

fn motion(name: &str) -> papy_core::Result<CaretMotion> {
    Ok(match name {
        "left" => CaretMotion::Left,
        "right" => CaretMotion::Right,
        "lineStart" => CaretMotion::LineStart,
        "lineEnd" => CaretMotion::LineEnd,
        "documentStart" => CaretMotion::DocumentStart,
        "documentEnd" => CaretMotion::DocumentEnd,
        _ => return Err(bad_argument("unknown caret motion")),
    })
}

/// 바꾸는 작업 뒤의 공통 응답: 새 커서와 그 사각형. 쪽 크기 목록은 앱이 따로 받는다.
fn changed(doc: &mut dyn Document, caret: TextPos) -> papy_core::Result<Value> {
    let rect = doc.caret_rect(caret).ok().map(rect_json);
    Ok(
        json!({ "changed": true, "caret": Pos::from(caret), "caretRect": rect, "pageCount": doc.info().page_count }),
    )
}

pub fn run_edit(doc: &mut dyn Document, op: EditOp) -> papy_core::Result<Value> {
    Ok(match op {
        EditOp::HitTest { page, x, y } => {
            let pos = doc.hit_test(page, x, y)?;
            let rect = doc.caret_rect(pos)?;
            json!({ "pos": Pos::from(pos), "rect": rect_json(rect) })
        }
        EditOp::CaretRect { pos } => rect_json(doc.caret_rect(pos.into())?),
        EditOp::MoveVertical {
            pos,
            lines,
            preferred_x,
        } => {
            let pos = doc.move_vertical(pos.into(), lines, preferred_x)?;
            json!({ "pos": Pos::from(pos), "rect": rect_json(doc.caret_rect(pos)?) })
        }
        EditOp::MoveCaret { pos, motion: name } => {
            let pos = doc.move_caret(pos.into(), motion(&name)?)?;
            json!({ "pos": Pos::from(pos), "rect": rect_json(doc.caret_rect(pos)?) })
        }
        EditOp::SelectionRects { start, end } => {
            let rects = doc.selection_rects(start.into(), end.into())?;
            Value::Array(rects.into_iter().map(rect_json).collect())
        }
        EditOp::TextInRange { start, end } => doc.text_in_range(start.into(), end.into())?.into(),
        EditOp::InsertText { pos, text } => {
            let outcome = doc.insert_text(pos.into(), &text)?;
            changed(doc, outcome.caret)?
        }
        EditOp::DeleteRange { start, end } => {
            let outcome = doc.delete_range(start.into(), end.into())?;
            changed(doc, outcome.caret)?
        }
        EditOp::DeleteBackward { pos } => {
            let outcome = doc.delete_backward(pos.into())?;
            changed(doc, outcome.caret)?
        }
        EditOp::DeleteForward { pos } => {
            let outcome = doc.delete_forward(pos.into())?;
            changed(doc, outcome.caret)?
        }
        EditOp::CharFormat { pos } => {
            let format = doc.char_format(pos.into())?;
            serde_json::to_value(CharFormatDto {
                font_family: format.font_family,
                font_size: format.font_size,
                bold: format.bold,
                italic: format.italic,
                underline: format.underline,
                strikethrough: format.strikethrough,
                color: format.color,
            })
            .unwrap_or_default()
        }
        EditOp::ApplyCharFormat { start, end, format } => {
            if let Some(color) = &format.color {
                let hex = color.strip_prefix('#').unwrap_or("");
                if hex.len() != 6 || !hex.chars().all(|c| c.is_ascii_hexdigit()) {
                    return Err(bad_argument("color must be #rrggbb"));
                }
            }
            if format
                .font_size
                .is_some_and(|size| !(1.0..=500.0).contains(&size))
            {
                return Err(bad_argument("font size out of range"));
            }
            let format = CharFormat {
                font_family: format.font_family,
                font_size: format.font_size,
                bold: format.bold,
                italic: format.italic,
                underline: format.underline,
                strikethrough: format.strikethrough,
                color: format.color,
            };
            doc.apply_char_format(start.into(), end.into(), &format)?;
            changed(doc, end.into())?
        }
        EditOp::ParaFormat { pos } => {
            let format = doc.para_format(pos.into())?;
            json!({
                "alignment": format.alignment.map(alignment_name),
                "lineSpacing": format.line_spacing,
            })
        }
        EditOp::ApplyParaFormat { start, end, format } => {
            let format = ParaFormat {
                alignment: format.alignment.as_deref().map(alignment).transpose()?,
                line_spacing: format.line_spacing,
            };
            doc.apply_para_format(start.into(), end.into(), &format)?;
            changed(doc, end.into())?
        }
        EditOp::Undo | EditOp::Redo => {
            let outcome = if matches!(op, EditOp::Undo) {
                doc.undo()?
            } else {
                doc.redo()?
            };
            match outcome {
                // 본문 편집 문서는 커서를 돌려주고, PDF 처럼 커서가 없는 문서는 쪽 수만.
                Some(outcome) if doc.info().capabilities.edit => changed(doc, outcome.caret)?,
                Some(_) => pages_changed(doc),
                None => json!({ "changed": false }),
            }
        }
        EditOp::TextRects { page, area } => {
            let rects = doc.text_rects(page, page_rect(page, area))?;
            Value::Array(rects.into_iter().map(rect_json).collect())
        }
        EditOp::TextInArea { page, area } => {
            Value::String(doc.text_in_area(page, page_rect(page, area))?)
        }
        EditOp::Annotate { annotation: dto } => {
            doc.annotate(&annotation(dto)?)?;
            pages_changed(doc)
        }
        EditOp::EditPages { op } => {
            doc.edit_pages(match op {
                PageOpDto::Rotate {
                    page,
                    quarter_turns,
                } => PageOp::Rotate {
                    page,
                    quarter_turns,
                },
                PageOpDto::Delete { page } => PageOp::Delete { page },
                PageOpDto::Move { from, to } => PageOp::Move { from, to },
            })?;
            pages_changed(doc)
        }
    })
}

/// 파일을 연다. 암호가 필요하면 `PasswordRequired`(5)로 실패하고, 앱이 암호를 받아 다시 부른다.
pub fn open(
    engine: &Engine,
    path: PathBuf,
    password: Option<String>,
) -> papy_core::Result<OpenedDocument> {
    let file_name = path
        .file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .unwrap_or_default();
    if !path.is_file() {
        return Err(Error::new(ErrorCode::Io, "not a regular file"));
    }
    open_source(engine, DocumentSource::Path(path), file_name, password)
}

/// 메모리에 있는 문서(예: HWP 고급 편집기가 돌려준 바이트)를 새 세션으로 연다.
/// `inherit` 세션이 있으면 그 저장 암호를 이어받는다(암호 문서는 암호 없이 넘겼다가 되돌린다).
pub fn open_bytes(
    engine: &Engine,
    bytes: Vec<u8>,
    file_name: String,
    inherit: Option<u64>,
) -> papy_core::Result<OpenedDocument> {
    let opened = open_source(engine, DocumentSource::Bytes(bytes.into()), file_name, None)?;
    if let Some(from) = inherit {
        let to = session(opened.id)?;
        if let Err(error) = engine.copy_save_password(session(from)?, to) {
            let _ = engine.close(to);
            return Err(error);
        }
    }
    Ok(opened)
}

/// 문서를 `extension` 형식의 바이트로 내보낸다(파일에 쓰지 않는다). 다른 편집기에 넘기는 용도라
/// 암호는 걸지 않는다 — 돌려받을 때 [`open_bytes`] 의 `inherit` 으로 되돌린다.
pub fn export(engine: &Engine, id: u64, extension: &str) -> papy_core::Result<Vec<u8>> {
    let format = SaveFormat::from_extension(extension)
        .ok_or_else(|| Error::new(ErrorCode::UnsupportedFormat, "unknown format"))?;
    engine.save_unprotected(session(id)?, format)
}

fn open_source(
    engine: &Engine,
    source: DocumentSource,
    file_name: String,
    password: Option<String>,
) -> papy_core::Result<OpenedDocument> {
    let options = OpenOptions {
        password,
        ..OpenOptions::default()
    };
    let id = engine.open(source, options)?;
    let describe = || -> papy_core::Result<OpenedDocument> {
        let info = engine.info(id)?;
        let pages = page_sizes(engine, id)?;
        Ok(OpenedDocument {
            id: id.get(),
            file_name,
            format: info.format.name(),
            page_count: info.page_count,
            byte_size: info.byte_size,
            title: info.title,
            can_extract_text: info.capabilities.extract_text,
            has_outline: info.capabilities.outline,
            can_edit: info.capabilities.edit,
            can_annotate: info.capabilities.annotate,
            can_edit_pages: info.capabilities.page_edit,
            save_formats: info
                .capabilities
                .save_formats
                .iter()
                .map(|format| format.extension())
                .collect(),
            pages,
            dropped_objects: info.dropped_objects,
        })
    };
    describe().inspect_err(|_| {
        let _ = engine.close(id);
    })
}

/// 페이지를 그린다. `scale` 1.0 = 1pt 를 1px 로. 결과: `[너비 u32 LE][높이 u32 LE][RGBA…]`.
pub fn render(engine: &Engine, id: u64, page: u32, scale: f32) -> papy_core::Result<Vec<u8>> {
    let bitmap = engine.render_page(session(id)?, page, &RenderRequest::with_scale(scale))?;
    let mut bytes = Vec::with_capacity(8 + bitmap.pixels.len());
    bytes.extend_from_slice(&bitmap.width.to_le_bytes());
    bytes.extend_from_slice(&bitmap.height.to_le_bytes());
    bytes.extend_from_slice(&bitmap.pixels);
    Ok(bytes)
}

/// 편집 작업 하나. 좌표는 pt(쪽 왼쪽 위 기준).
pub fn edit(engine: &Engine, id: u64, op: EditOp) -> papy_core::Result<Value> {
    engine.edit(session(id)?, |doc| run_edit(doc, op))
}

/// 문서를 `path` 에 저장한다. 형식은 확장자로 정한다. 같은 폴더의 임시 파일에 쓴 뒤 바꿔치기해서
/// 쓰다가 실패해도 원래 파일이 깨지지 않게 한다.
pub fn save(engine: &Engine, id: u64, path: PathBuf) -> papy_core::Result<()> {
    let format = path
        .extension()
        .and_then(|ext| ext.to_str())
        .and_then(SaveFormat::from_extension)
        .ok_or_else(|| Error::new(ErrorCode::UnsupportedFormat, "unknown file extension"))?;
    let bytes = engine.save(session(id)?, format)?;
    let parent = path
        .parent()
        .filter(|dir| !dir.as_os_str().is_empty())
        .ok_or_else(|| bad_argument("invalid path"))?;
    let name = path
        .file_name()
        .ok_or_else(|| bad_argument("invalid path"))?;
    let mut temp_name = std::ffi::OsString::from(".");
    temp_name.push(name);
    temp_name.push(".papy-tmp");
    let temp = parent.join(temp_name);
    let write = || -> std::io::Result<()> {
        std::fs::write(&temp, &bytes)?;
        std::fs::rename(&temp, &path)
    };
    write().map_err(|error| {
        let _ = std::fs::remove_file(&temp);
        Error::from(error)
    })
}

/// 찾은 글자 하나.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TextMatchDto {
    page: u32,
    before: String,
    matched: String,
    after: String,
}

/// 찾기 결과 상한. 넘으면 앱이 "더 있음"을 보인다.
pub const MAX_MATCHES: usize = 500;

/// 문서 글자 찾기. `cancel` 은 다음 찾기가 시작되면 앞 것을 멈추는 데 쓴다.
pub fn find_text(
    engine: &Engine,
    id: u64,
    query: &str,
    cancel: &CancelToken,
) -> papy_core::Result<Vec<TextMatchDto>> {
    Ok(engine
        .find_text(session(id)?, query, MAX_MATCHES + 1, cancel)?
        .into_iter()
        .map(|hit| TextMatchDto {
            page: hit.page,
            before: hit.before,
            matched: hit.matched,
            after: hit.after,
        })
        .collect())
}

/// 쪽에서 제대로 그리지 못한 개체의 종류별 개수.
#[derive(Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct UnsupportedDto {
    charts: u32,
    embedded: u32,
    missing_images: u32,
}

/// 쪽에서 제대로 그리지 못한 개체 수.
pub fn unsupported(engine: &Engine, id: u64, page: u32) -> papy_core::Result<UnsupportedDto> {
    let mut out = UnsupportedDto::default();
    for object in engine.unsupported_objects(session(id)?, page)? {
        match object.kind {
            UnsupportedKind::Chart => out.charts += 1,
            UnsupportedKind::EmbeddedObject => out.embedded += 1,
            UnsupportedKind::MissingImage => out.missing_images += 1,
        }
    }
    Ok(out)
}

/// 목차.
pub fn outline(engine: &Engine, id: u64) -> papy_core::Result<Vec<OutlineDto>> {
    Ok(engine
        .outline(session(id)?)?
        .into_iter()
        .map(Into::into)
        .collect())
}

/// 문서를 닫는다. 이미 닫혔으면 아무것도 하지 않는다.
pub fn close(engine: &Engine, id: u64) -> papy_core::Result<()> {
    match engine.close(session(id)?) {
        Err(error) if error.code == ErrorCode::SessionNotFound => Ok(()),
        other => other,
    }
}
