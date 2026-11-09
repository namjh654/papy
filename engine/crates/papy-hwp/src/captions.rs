//! DOCX 에서 읽은 캡션 문단을 표·그림 캡션으로 되돌린다(본문 HTML 을 붙여넣고 각주·도형을 넣은 뒤).
//!
//! Papy 는 캡션을 Word 에 개체 앞(위 캡션)·뒤 문단으로 쓰고 다시 열 때 그 문단 맨 앞에
//! [`papy_office::CAPTION_MARKS`] 표시를 둔다. 표시를 지우고 그 문단들을 바로 뒤(위 캡션)·앞 문단의 표·그림
//! 캡션으로 옮긴다. 옮길 개체가 없으면 표시만 지우고 본문 문단으로 둔다.

use papy_office::{CAPTION_MARKS, CAPTION_MORE_MARK};
use rhwp::model::control::Control;
use rhwp::model::paragraph::Paragraph;
use rhwp::model::shape::{Caption, CaptionDirection};

use crate::Rhwp;

/// 캡션과 개체 사이 간격: 한글 기본값 3mm(HWPUNIT).
const CAPTION_SPACING: i16 = 850;
/// 왼쪽·오른쪽 캡션 너비: 한글 기본값 30mm(HWPUNIT).
const SIDE_CAPTION_WIDTH: u32 = 8504;

pub(crate) fn apply(doc: &mut Rhwp) {
    let has_mark = |p: &Paragraph| p.text.contains(is_mark);
    if !doc
        .document()
        .sections
        .iter()
        .any(|s| any_paragraph(&s.paragraphs, &has_mark, 0))
    {
        return;
    }
    let mut document = doc.document().clone();
    for section in &mut document.sections {
        walk(doc, &mut section.paragraphs, 0);
        section.raw_stream = None;
    }
    doc.set_document(document);
}

fn is_mark(c: char) -> bool {
    CAPTION_MARKS.contains(&c) || c == CAPTION_MORE_MARK
}

/// 문단들(칸·글상자 안 포함) 가운데 `test` 를 만족하는 것이 있는가.
fn any_paragraph(
    paragraphs: &[Paragraph],
    test: &dyn Fn(&Paragraph) -> bool,
    depth: usize,
) -> bool {
    depth <= 16
        && paragraphs.iter().any(|p| {
            test(p)
                || p.controls.iter().any(|control| match control {
                    Control::Table(table) => table
                        .cells
                        .iter()
                        .any(|cell| any_paragraph(&cell.paragraphs, test, depth + 1)),
                    Control::Shape(shape) => shape
                        .drawing()
                        .and_then(|d| d.text_box.as_ref())
                        .is_some_and(|t| any_paragraph(&t.paragraphs, test, depth + 1)),
                    _ => false,
                })
        })
}

/// 문단 맨 앞(앞 빈칸 뒤)의 캡션 표시를 지우고 그 표시를 돌려준다.
fn take_mark(paragraph: &mut Paragraph) -> Option<char> {
    let (offset, mark) = paragraph
        .text
        .chars()
        .enumerate()
        .find(|(_, c)| !c.is_whitespace())
        .filter(|(_, c)| is_mark(*c))?;
    paragraph.delete_text_at(offset, 1);
    Some(mark)
}

fn walk(doc: &Rhwp, paragraphs: &mut Vec<Paragraph>, depth: usize) {
    if depth > 16 {
        return;
    }
    for paragraph in paragraphs.iter_mut() {
        for control in &mut paragraph.controls {
            match control {
                Control::Table(table) => {
                    for cell in &mut table.cells {
                        walk(doc, &mut cell.paragraphs, depth + 1);
                    }
                }
                Control::Shape(shape) => {
                    if let Some(text_box) = shape.drawing_mut().and_then(|d| d.text_box.as_mut()) {
                        walk(doc, &mut text_box.paragraphs, depth + 1);
                    }
                }
                _ => {}
            }
        }
    }
    let mut index = 0;
    while index < paragraphs.len() {
        let Some(mark) = take_mark(&mut paragraphs[index]) else {
            index += 1;
            continue;
        };
        if mark == CAPTION_MORE_MARK {
            // 앞 캡션을 옮기지 못한 나머지 문단: 표시만 지운다.
            index += 1;
            continue;
        }
        let direction = match CAPTION_MARKS.iter().position(|m| *m == mark) {
            Some(0) => CaptionDirection::Left,
            Some(1) => CaptionDirection::Right,
            Some(2) => CaptionDirection::Top,
            _ => CaptionDirection::Bottom,
        };
        let mut end = index + 1;
        while end < paragraphs.len()
            && paragraphs[end]
                .text
                .chars()
                .find(|c| !c.is_whitespace())
                .is_some_and(|c| c == CAPTION_MORE_MARK)
        {
            take_mark(&mut paragraphs[end]);
            end += 1;
        }
        // 구역·단 정의 등 문단 컨트롤을 가진 문단(첫 문단)은 옮기지 않는다.
        let movable = paragraphs[index..end].iter().all(|p| {
            p.controls
                .iter()
                .all(|c| !matches!(c, Control::SectionDef(_) | Control::ColumnDef(_)))
        });
        let target = match direction {
            CaptionDirection::Top => (end < paragraphs.len()).then_some(end),
            _ => index.checked_sub(1),
        };
        let Some(target) = target.filter(|_| movable) else {
            index = end;
            continue;
        };
        let top = direction == CaptionDirection::Top;
        let width = {
            let controls = &paragraphs[target].controls;
            let pick = |c: &Control| match c {
                Control::Table(t) if t.caption.is_none() => Some(t.common.width),
                Control::Picture(p) if p.caption.is_none() => Some(p.common.width),
                _ => None,
            };
            if top {
                controls.iter().find_map(pick)
            } else {
                controls.iter().rev().find_map(pick)
            }
        };
        let Some(object_width) = width else {
            index = end;
            continue;
        };
        let mut moved: Vec<Paragraph> = paragraphs.drain(index..end).collect();
        let side = matches!(direction, CaptionDirection::Left | CaptionDirection::Right);
        let caption_width = if side {
            SIDE_CAPTION_WIDTH
        } else {
            object_width
        };
        doc.reflow_paragraphs_to_width(&mut moved, caption_width);
        let caption = Caption {
            direction,
            width: caption_width,
            spacing: CAPTION_SPACING,
            max_width: object_width,
            paragraphs: moved,
            ..Caption::default()
        };
        let target = if top { index } else { target };
        let controls = &mut paragraphs[target].controls;
        let slot = |c: &mut Control| -> bool {
            match c {
                Control::Table(t) if t.caption.is_none() => {
                    t.caption = Some(caption.clone());
                    true
                }
                Control::Picture(p) if p.caption.is_none() => {
                    p.caption = Some(caption.clone());
                    true
                }
                _ => false,
            }
        };
        if top {
            controls.iter_mut().any(slot);
        } else {
            controls.iter_mut().rev().any(slot);
        }
        // 뒤 캡션은 그 개체 문단 다음부터 다시 본다(지운 문단 자리).
        index = if top { index + 1 } else { index };
    }
}
