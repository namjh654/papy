//! PDF 편집: 주석(형광펜·밑줄·취소선·메모), 텍스트 상자·펜(쪽 내용), 쪽 회전·삭제·이동, 되돌리기.
//!
//! 좌표: Papy 는 "화면에 보이는 쪽"(회전 반영)의 왼쪽 위 기준 pt, PDF 는 회전 전 쪽의 왼쪽 아래 기준이다.
//! [`Frame`] 이 둘을 바꾼다.
//!
//! 되돌리기: 바꾸기 전 문서를 바이트로 저장해 두고, 되돌릴 때 그 바이트로 다시 연다(단계·용량 상한).

use papy_model::{Annotation, Error, ErrorCode, MarkupKind, PageOp, PageRect, Result};
use pdfium_render::prelude::*;

use crate::{Entry, State, map_error};

/// 되돌리기 단계 상한.
const MAX_UNDO: usize = 30;
/// 되돌리기 스냅샷 전체 용량 상한(바이트).
const MAX_UNDO_BYTES: usize = 256 * 1024 * 1024;
/// 메모 아이콘 크기(pt).
const NOTE_SIZE: f32 = 20.0;

fn bad(message: &str) -> Error {
    Error::new(ErrorCode::InvalidArgument, message)
}

/// `#rrggbb` → 색.
pub(crate) fn parse_color(color: &str, alpha: u8) -> Result<PdfColor> {
    let hex = color.strip_prefix('#').unwrap_or("");
    if hex.len() != 6 || !hex.chars().all(|c| c.is_ascii_hexdigit()) {
        return Err(bad("color must be #rrggbb"));
    }
    let channel = |i: usize| u8::from_str_radix(&hex[i..i + 2], 16).unwrap_or(0);
    Ok(PdfColor::new(channel(0), channel(2), channel(4), alpha))
}

/// 화면 좌표(회전 반영, 왼쪽 위 기준) ↔ PDF 사용자 좌표(회전 전, 왼쪽 아래 기준).
struct Frame {
    /// 회전 전 쪽의 너비·높이
    width: f32,
    height: f32,
    /// 사용자 좌표계 원점 보정(크롭 상자의 왼쪽 아래)
    left: f32,
    bottom: f32,
    quarter_turns: u8,
}

impl Frame {
    fn of(page: &PdfPage) -> Result<Self> {
        let quarter_turns = match page.rotation().map_err(map_error)? {
            PdfPageRenderRotation::None => 0,
            PdfPageRenderRotation::Degrees90 => 1,
            PdfPageRenderRotation::Degrees180 => 2,
            PdfPageRenderRotation::Degrees270 => 3,
        };
        // width()/height() 는 회전을 반영한 크기다.
        let (shown_w, shown_h) = (page.width().value, page.height().value);
        let (width, height) = if quarter_turns % 2 == 1 {
            (shown_h, shown_w)
        } else {
            (shown_w, shown_h)
        };
        let crop = page
            .boundaries()
            .crop()
            .or_else(|_| page.boundaries().media())
            .map(|boundary| boundary.bounds)
            .ok();
        let (left, bottom) = crop
            .map(|rect| (rect.left().value, rect.bottom().value))
            .unwrap_or((0.0, 0.0));
        Ok(Self {
            width,
            height,
            left,
            bottom,
            quarter_turns,
        })
    }

    /// 화면 점 → 사용자 좌표 점.
    fn to_user(&self, dx: f32, dy: f32) -> (f32, f32) {
        let (w, h) = (self.width, self.height);
        let (x, y) = match self.quarter_turns {
            0 => (dx, h - dy),
            1 => (dy, dx),
            2 => (w - dx, dy),
            _ => (w - dy, h - dx),
        };
        (x + self.left, y + self.bottom)
    }

    /// 사용자 좌표 점 → 화면 점.
    fn to_screen(&self, x: f32, y: f32) -> (f32, f32) {
        let (x, y) = (x - self.left, y - self.bottom);
        let (w, h) = (self.width, self.height);
        match self.quarter_turns {
            0 => (x, h - y),
            1 => (y, x),
            2 => (w - x, y),
            _ => (h - y, w - x),
        }
    }

    fn rect_to_user(&self, rect: PageRect) -> PdfRect {
        let (x1, y1) = self.to_user(rect.x, rect.y);
        let (x2, y2) = self.to_user(rect.x + rect.width, rect.y + rect.height);
        PdfRect::new_from_values(y1.min(y2), x1.min(x2), y1.max(y2), x1.max(x2))
    }

    fn rect_to_screen(&self, page: u32, rect: &PdfRect) -> PageRect {
        let (x1, y1) = self.to_screen(rect.left().value, rect.bottom().value);
        let (x2, y2) = self.to_screen(rect.right().value, rect.top().value);
        PageRect {
            page,
            x: x1.min(x2),
            y: y1.min(y2),
            width: (x1 - x2).abs(),
            height: (y1 - y2).abs(),
        }
    }
}

/// PDF 규약 순서(왼쪽 위, 오른쪽 위, 왼쪽 아래, 오른쪽 아래)의 사각형 꼭짓점.
/// `PdfQuadPoints::from_rect` 는 다른 순서라 PDFium 이 폭 0 짜리 표시를 만든다.
fn quad(rect: &PdfRect) -> PdfQuadPoints {
    PdfQuadPoints::new(
        rect.left(),
        rect.top(),
        rect.right(),
        rect.top(),
        rect.left(),
        rect.bottom(),
        rect.right(),
        rect.bottom(),
    )
}

/// 영역(사용자 좌표)과 겹치는 글 조각을 줄마다 하나의 사각형으로 합친다(사용자 좌표).
///
/// PDF 안의 글자 순서는 읽는 순서와 다를 수 있어(글꼴별로 따로 적는 등) 글자 번호 범위가 아니라
/// 위치로 고른다. 같은 줄의 조각은 합쳐서 낱말 사이 빈틈 없이 칠한다.
fn line_rects(page: &PdfPage, area: PdfRect) -> Result<Vec<PdfRect>> {
    let text = page.text().map_err(map_error)?;
    let overlaps = |rect: &PdfRect| {
        rect.left() < area.right()
            && rect.right() > area.left()
            && rect.bottom() < area.top()
            && rect.top() > area.bottom()
    };
    let mut lines: Vec<PdfRect> = Vec::new();
    for segment in text.segments().iter() {
        let rect = segment.bounds();
        if !overlaps(&rect) || rect.width().value <= 0.0 || rect.height().value <= 0.0 {
            continue;
        }
        let center = (rect.top().value + rect.bottom().value) / 2.0;
        let same_line = lines.iter_mut().find(|line| {
            let half = (line.top().value - line.bottom().value)
                .max(rect.top().value - rect.bottom().value)
                / 2.0;
            ((line.top().value + line.bottom().value) / 2.0 - center).abs() < half
        });
        match same_line {
            Some(line) => {
                *line = PdfRect::new_from_values(
                    line.bottom().value.min(rect.bottom().value),
                    line.left().value.min(rect.left().value),
                    line.top().value.max(rect.top().value),
                    line.right().value.max(rect.right().value),
                );
            }
            None => lines.push(rect),
        }
    }
    // 위에서 아래로(사용자 좌표는 y 가 위로 커진다).
    lines.sort_by(|a, b| b.top().value.total_cmp(&a.top().value));
    Ok(lines)
}

impl State {
    fn entry(&mut self, id: u64) -> Result<&mut Entry> {
        self.docs
            .get_mut(&id)
            .ok_or_else(|| Error::new(ErrorCode::SessionNotFound, "pdf document is closed"))
    }

    fn page_index(&mut self, id: u64, page: u32) -> Result<PdfPageIndex> {
        let count = self.entry(id)?.doc.pages().len();
        PdfPageIndex::try_from(page)
            .ok()
            .filter(|index| *index < count)
            .ok_or_else(|| Error::new(ErrorCode::PageOutOfRange, "page is out of range"))
    }

    /// 바꾸기 전 상태를 되돌리기 목록에 넣는다.
    fn checkpoint(&mut self, id: u64) -> Result<()> {
        let entry = self.entry(id)?;
        let bytes = entry.doc.save_to_bytes().map_err(map_error)?;
        entry.undo.push(bytes);
        entry.redo.clear();
        while entry.undo.len() > MAX_UNDO
            || entry.undo.iter().map(Vec::len).sum::<usize>() > MAX_UNDO_BYTES
        {
            entry.undo.remove(0);
        }
        Ok(())
    }

    fn reload(&mut self, id: u64, bytes: Vec<u8>) -> Result<()> {
        let pdfium = self.pdfium;
        let entry = self.entry(id)?;
        let doc = pdfium
            .load_pdf_from_byte_vec(bytes, entry.password.as_deref())
            .map_err(map_error)?;
        entry.doc = doc;
        entry.font = None;
        Ok(())
    }

    /// 되돌리기/다시 하기. 바뀐 쪽 수를 돌려준다(없으면 `None`).
    pub(crate) fn history(&mut self, id: u64, redo: bool) -> Result<Option<u32>> {
        let entry = self.entry(id)?;
        let Some(bytes) = (if redo {
            entry.redo.pop()
        } else {
            entry.undo.pop()
        }) else {
            return Ok(None);
        };
        let current = entry.doc.save_to_bytes().map_err(map_error)?;
        if redo {
            entry.undo.push(current);
        } else {
            entry.redo.push(current);
        }
        self.reload(id, bytes)?;
        Ok(Some(self.entry(id)?.doc.pages().len() as u32))
    }

    pub(crate) fn text_rects(
        &mut self,
        id: u64,
        page: u32,
        area: PageRect,
    ) -> Result<Vec<PageRect>> {
        let index = self.page_index(id, page)?;
        let page_handle = self.entry(id)?.doc.pages().get(index).map_err(map_error)?;
        let frame = Frame::of(&page_handle)?;
        Ok(line_rects(&page_handle, frame.rect_to_user(area))?
            .iter()
            .map(|rect| frame.rect_to_screen(page, rect))
            .filter(|rect| rect.width > 0.5 && rect.height > 0.5)
            .collect())
    }

    pub(crate) fn text_in_area(&mut self, id: u64, page: u32, area: PageRect) -> Result<String> {
        let index = self.page_index(id, page)?;
        let page_handle = self.entry(id)?.doc.pages().get(index).map_err(map_error)?;
        let frame = Frame::of(&page_handle)?;
        let text = page_handle.text().map_err(map_error)?;
        Ok(text.inside_rect(frame.rect_to_user(area)).trim().to_owned())
    }

    pub(crate) fn annotate(&mut self, id: u64, annotation: &Annotation) -> Result<()> {
        let page = match annotation {
            Annotation::TextBox { page, .. }
            | Annotation::Markup { page, .. }
            | Annotation::Ink { page, .. }
            | Annotation::Note { page, .. }
            | Annotation::ReplaceText { page, .. } => *page,
        };
        let index = self.page_index(id, page)?;
        // 입력을 먼저 검사해 잘못된 요청이 되돌리기 단계를 남기지 않게 한다.
        match annotation {
            Annotation::TextBox {
                text,
                font_size,
                color,
                ..
            } => {
                parse_color(color, 255)?;
                if text.trim().is_empty() || !(4.0..=200.0).contains(font_size) {
                    return Err(bad("text box needs text and a font size between 4 and 200"));
                }
            }
            Annotation::Markup { color, .. } => {
                parse_color(color, 255)?;
            }
            Annotation::Ink {
                points,
                color,
                width,
                ..
            } => {
                parse_color(color, 255)?;
                if points.len() < 2 || !(0.2..=50.0).contains(width) {
                    return Err(bad(
                        "ink needs two or more points and a width between 0.2 and 50",
                    ));
                }
            }
            Annotation::Note { text, .. } => {
                if text.trim().is_empty() {
                    return Err(bad("note needs text"));
                }
            }
            Annotation::ReplaceText {
                color, font_size, ..
            } => {
                parse_color(color, 255)?;
                if font_size.is_some_and(|size| !(4.0..=200.0).contains(&size)) {
                    return Err(bad("font size must be between 4 and 200"));
                }
            }
        }
        if let Annotation::Markup { area, .. } = annotation {
            if self.text_rects(id, page, *area)?.is_empty() {
                return Err(bad("no text in the selected area"));
            }
        }
        self.checkpoint(id)?;
        let result = self.apply(id, index, annotation);
        if result.is_err() {
            // 반쯤 바뀐 상태를 남기지 않는다.
            if let Some(bytes) = self.entry(id)?.undo.pop() {
                self.reload(id, bytes)?;
            }
        }
        result
    }

    fn apply(&mut self, id: u64, index: PdfPageIndex, annotation: &Annotation) -> Result<()> {
        let font_path = self.text_font.clone();
        let entry = self.entry(id)?;
        let mut page = entry.doc.pages().get(index).map_err(map_error)?;
        let frame = Frame::of(&page)?;
        match annotation {
            Annotation::Markup {
                kind, area, color, ..
            } => {
                let segments = line_rects(&page, frame.rect_to_user(*area))?;
                let color = parse_color(color, 255)?;
                let bounds = segments.iter().skip(1).fold(segments[0], |acc, rect| {
                    PdfRect::new_from_values(
                        acc.bottom().value.min(rect.bottom().value),
                        acc.left().value.min(rect.left().value),
                        acc.top().value.max(rect.top().value),
                        acc.right().value.max(rect.right().value),
                    )
                });
                let annotations = page.annotations_mut();
                // 종류마다 타입이 달라 같은 설정을 매크로로 반복한다.
                macro_rules! markup {
                    ($create:ident) => {{
                        let mut annotation = annotations.$create().map_err(map_error)?;
                        for rect in &segments {
                            annotation
                                .attachment_points_mut()
                                .create_attachment_point_at_end(quad(rect))
                                .map_err(map_error)?;
                        }
                        annotation.set_stroke_color(color).map_err(map_error)?;
                        annotation.set_bounds(bounds).map_err(map_error)?;
                    }};
                }
                match kind {
                    MarkupKind::Highlight => markup!(create_highlight_annotation),
                    MarkupKind::Underline => markup!(create_underline_annotation),
                    MarkupKind::StrikeOut => markup!(create_strikeout_annotation),
                }
            }
            Annotation::Note { x, y, text, .. } => {
                let (ux, uy) = frame.to_user(*x, *y + NOTE_SIZE);
                let mut note = page
                    .annotations_mut()
                    .create_text_annotation(text)
                    .map_err(map_error)?;
                note.set_bounds(PdfRect::new_from_values(
                    uy,
                    ux,
                    uy + NOTE_SIZE,
                    ux + NOTE_SIZE,
                ))
                .map_err(map_error)?;
                note.set_stroke_color(PdfColor::new(250, 204, 21, 255))
                    .map_err(map_error)?;
            }
            Annotation::Ink {
                points,
                color,
                width,
                ..
            } => {
                let color = parse_color(color, 255)?;
                let (x0, y0) = frame.to_user(points[0].0, points[0].1);
                let mut path = PdfPagePathObject::new(
                    &entry.doc,
                    PdfPoints::new(x0),
                    PdfPoints::new(y0),
                    Some(color),
                    Some(PdfPoints::new(*width)),
                    None,
                )
                .map_err(map_error)?;
                for (x, y) in &points[1..] {
                    let (ux, uy) = frame.to_user(*x, *y);
                    path.line_to(PdfPoints::new(ux), PdfPoints::new(uy))
                        .map_err(map_error)?;
                }
                page.objects_mut()
                    .add_path_object(path)
                    .map_err(map_error)?;
                page.regenerate_content().map_err(map_error)?;
            }
            Annotation::TextBox {
                x,
                y,
                text,
                font_size,
                color,
                ..
            } => {
                let color = parse_color(color, 255)?;
                place_text(
                    entry,
                    &mut page,
                    &frame,
                    font_path,
                    (*x, *y),
                    text,
                    *font_size,
                    color,
                )?;
                page.regenerate_content().map_err(map_error)?;
            }
            Annotation::ReplaceText {
                area,
                text,
                color,
                font_size,
                ..
            } => {
                let color = parse_color(color, 255)?;
                let area = frame.rect_to_user(*area);
                let inside = |rect: &PdfRect| {
                    let cx = (rect.left().value + rect.right().value) / 2.0;
                    let cy = (rect.bottom().value + rect.top().value) / 2.0;
                    cx >= area.left().value
                        && cx <= area.right().value
                        && cy >= area.bottom().value
                        && cy <= area.top().value
                };
                // 가운데가 영역 안에 있는 글자 개체: 위치·크기를 모은 뒤 뒤에서부터 지운다.
                let mut hits = Vec::new();
                let mut union: Option<PdfRect> = None;
                let mut size: Option<f32> = None;
                for (index, object) in page.objects().iter().enumerate() {
                    let Some(text_object) = object.as_text_object() else {
                        continue;
                    };
                    let Ok(bounds) = object.bounds() else {
                        continue;
                    };
                    let rect = bounds.to_rect();
                    if !inside(&rect) {
                        continue;
                    }
                    hits.push(index);
                    size.get_or_insert(text_object.scaled_font_size().value);
                    union = Some(match union {
                        None => rect,
                        Some(acc) => PdfRect::new_from_values(
                            acc.bottom().value.min(rect.bottom().value),
                            acc.left().value.min(rect.left().value),
                            acc.top().value.max(rect.top().value),
                            acc.right().value.max(rect.right().value),
                        ),
                    });
                }
                let Some(union) = union else {
                    return Err(bad("no text in the selected area"));
                };
                for index in hits.into_iter().rev() {
                    page.objects_mut()
                        .remove_object_at_index(index)
                        .map_err(map_error)?;
                }
                if !text.trim().is_empty() {
                    let size = font_size.or(size).unwrap_or(12.0).clamp(4.0, 200.0);
                    // 지운 글자의 왼쪽 위(화면 좌표)에서 시작한다.
                    let screen = frame.rect_to_screen(0, &union);
                    place_text(
                        entry,
                        &mut page,
                        &frame,
                        font_path,
                        (screen.x, screen.y),
                        text,
                        size,
                        color,
                    )?;
                }
                page.regenerate_content().map_err(map_error)?;
            }
        }
        Ok(())
    }

    /// 쪽 편집. 바뀐 쪽 수를 돌려준다.
    pub(crate) fn edit_pages(&mut self, id: u64, op: PageOp) -> Result<u32> {
        let count = self.entry(id)?.doc.pages().len() as u32;
        match op {
            PageOp::Rotate {
                page,
                quarter_turns,
            } => {
                let index = self.page_index(id, page)?;
                self.checkpoint(id)?;
                let mut handle = self.entry(id)?.doc.pages().get(index).map_err(map_error)?;
                let current = Frame::of(&handle)?.quarter_turns as i32;
                handle.set_rotation(match (current + quarter_turns).rem_euclid(4) {
                    0 => PdfPageRenderRotation::None,
                    1 => PdfPageRenderRotation::Degrees90,
                    2 => PdfPageRenderRotation::Degrees180,
                    _ => PdfPageRenderRotation::Degrees270,
                });
            }
            PageOp::Delete { page } => {
                let index = self.page_index(id, page)?;
                if count <= 1 {
                    return Err(bad("cannot delete the only page"));
                }
                self.checkpoint(id)?;
                let handle = self.entry(id)?.doc.pages().get(index).map_err(map_error)?;
                handle.delete().map_err(map_error)?;
            }
            PageOp::Move { from, to } => {
                let from_index = self.page_index(id, from)?;
                let to_index = self.page_index(id, to)?;
                if from_index == to_index {
                    return Ok(count);
                }
                self.checkpoint(id)?;
                // 같은 문서끼리는 복사할 수 없어 사본을 하나 열어 거기서 가져온다.
                let pdfium = self.pdfium;
                let entry = self.entry(id)?;
                let bytes = entry.doc.save_to_bytes().map_err(map_error)?;
                let source = pdfium
                    .load_pdf_from_byte_vec(bytes, entry.password.as_deref())
                    .map_err(map_error)?;
                let (insert_at, delete_at) = if to_index > from_index {
                    (to_index + 1, from_index)
                } else {
                    (to_index, from_index + 1)
                };
                entry
                    .doc
                    .pages_mut()
                    .copy_page_from_document(&source, from_index, insert_at)
                    .map_err(map_error)?;
                let old = entry.doc.pages().get(delete_at).map_err(map_error)?;
                old.delete().map_err(map_error)?;
            }
        }
        Ok(self.entry(id)?.doc.pages().len() as u32)
    }
}

/// 글자를 쪽 내용으로 넣는다(화면 좌표 `(x, y)` = 첫 줄 왼쪽 위). ASCII 는 내장 Helvetica, 그 밖은 한글 글꼴.
#[allow(clippy::too_many_arguments)]
fn place_text(
    entry: &mut Entry,
    page: &mut PdfPage,
    frame: &Frame,
    font_path: Option<std::path::PathBuf>,
    (x, y): (f32, f32),
    text: &str,
    font_size: f32,
    color: PdfColor,
) -> Result<()> {
    // ASCII 만 있으면 내장 Helvetica(파일 크기 증가 없음), 아니면 한글 글꼴을 넣는다.
    let font = if text.is_ascii() {
        entry.doc.fonts_mut().helvetica()
    } else if let Some(font) = entry.font {
        font
    } else {
        let path = font_path.ok_or_else(|| {
            Error::new(
                ErrorCode::NotImplemented,
                "no font for non-Latin text is available",
            )
        })?;
        let bytes = std::fs::read(&path)?;
        let font = entry
            .doc
            .fonts_mut()
            .load_true_type_from_bytes(&bytes, true)
            .map_err(map_error)?;
        entry.font = Some(font);
        font
    };
    let line_height = font_size * 1.25;
    for (line_no, line) in text.split('\n').enumerate() {
        if line.is_empty() {
            continue;
        }
        // 기준선은 줄 위에서 글자 크기만큼 아래.
        let (ux, uy) = frame.to_user(x, y + font_size + line_no as f32 * line_height);
        let mut object = page
            .objects_mut()
            .create_text_object(
                PdfPoints::new(ux),
                PdfPoints::new(uy),
                line,
                font,
                PdfPoints::new(font_size),
            )
            .map_err(map_error)?;
        object.set_fill_color(color).map_err(map_error)?;
        if frame.quarter_turns != 0 {
            // 회전한 쪽에서도 화면에서 바로 읽히게 글자를 반대로 돌린다.
            let degrees = -90.0 * frame.quarter_turns as f32;
            object
                .rotate_counter_clockwise_degrees(-degrees)
                .map_err(map_error)?;
        }
    }
    Ok(())
}
