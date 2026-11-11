//! DOCX 번호 목록을 한글 문단 번호·글머리표로 되돌린다(본문 HTML 을 붙여넣고 각주·도형·캡션을 넣은 뒤).
//!
//! papy-office 는 본문·표 칸의 번호 목록 문단 맨 앞에 [`papy_office::LIST_MARK`] 와 정의·수준 글자를 두고
//! 정의를 [`papy_office::ListDef`] 로 넘긴다. 표시를 지우고 문단 모양의 머리(번호·글머리표)·번호 정의·수준을 맞춘다.
//! 종전에는 번호를 글자로 넣어 한글에서 번호 목록으로 고칠 수 없었고, 다시 저장하면 번호가 글자로 남았다.

use papy_office::{LIST_MARK, ListDef, code_value};
use rhwp::model::control::Control;
use rhwp::model::document::DocInfo;
use rhwp::model::paragraph::Paragraph;
use rhwp::model::style::{Bullet, HeadType, Numbering, NumberingHead};

use crate::Rhwp;

pub(crate) fn apply(doc: &mut Rhwp, lists: &[ListDef]) {
    if lists.is_empty() {
        return;
    }
    let mut document = doc.document().clone();
    let mut made: Vec<Option<(HeadType, u16)>> = vec![None; lists.len()];
    let mut changed = false;
    let (info, sections) = (&mut document.doc_info, &mut document.sections);
    for section in sections.iter_mut() {
        let mut touched = false;
        walk(
            &mut section.paragraphs,
            info,
            lists,
            &mut made,
            &mut touched,
            0,
        );
        if touched {
            section.raw_stream = None;
            changed = true;
        }
    }
    if changed {
        document.doc_info.raw_stream_dirty = true;
        doc.set_document(document);
    }
}

fn walk(
    paragraphs: &mut [Paragraph],
    info: &mut DocInfo,
    lists: &[ListDef],
    made: &mut [Option<(HeadType, u16)>],
    touched: &mut bool,
    depth: usize,
) {
    if depth > 16 {
        return;
    }
    for paragraph in paragraphs.iter_mut() {
        if let Some((def, level)) = take_mark(paragraph) {
            *touched = true;
            if let Some(list) = lists.get(def) {
                let head = *made[def].get_or_insert_with(|| register(info, list));
                set_head(info, paragraph, head, level);
            }
        }
        for control in &mut paragraph.controls {
            match control {
                Control::Table(table) => {
                    for cell in &mut table.cells {
                        walk(&mut cell.paragraphs, info, lists, made, touched, depth + 1);
                    }
                }
                Control::Shape(shape) => {
                    if let Some(text_box) = shape.drawing_mut().and_then(|d| d.text_box.as_mut()) {
                        walk(
                            &mut text_box.paragraphs,
                            info,
                            lists,
                            made,
                            touched,
                            depth + 1,
                        );
                    }
                }
                _ => {}
            }
        }
    }
}

/// 문단 맨 앞(앞 빈칸·자리 표시 뒤)의 목록 표시 세 글자를 지우고 (정의 번호, 수준)을 돌려준다.
fn take_mark(paragraph: &mut Paragraph) -> Option<(usize, u8)> {
    let chars: Vec<char> = paragraph.text.chars().take(9).collect();
    let offset = chars.iter().position(|c| *c == LIST_MARK)?;
    let code = code_value(*chars.get(offset + 1)?, *chars.get(offset + 2)?)?;
    paragraph.delete_text_at(offset, 3);
    Some(((code / 8) as usize, (code % 8) as u8))
}

/// 정의를 문서에 넣고 (머리 종류, 1부터 센 번호·글머리표 id)를 돌려준다. 같은 글머리표 글자는 다시 쓴다.
fn register(info: &mut DocInfo, list: &ListDef) -> (HeadType, u16) {
    if let Some(ch) = list.bullet {
        if let Some(found) = info.bullets.iter().position(|b| b.bullet_char == ch) {
            return (HeadType::Bullet, found as u16 + 1);
        }
        info.bullets.push(Bullet {
            bullet_char: ch,
            text_distance: 50,
            ..Default::default()
        });
        return (HeadType::Bullet, info.bullets.len() as u16);
    }
    let mut numbering = Numbering::default();
    for (level, (code, format, start)) in list.levels.iter().take(7).enumerate() {
        numbering.heads[level] = NumberingHead {
            number_format: *code,
            text_distance: 50,
            ..Default::default()
        };
        numbering.level_formats[level] = format.clone();
        numbering.level_start_numbers[level] = (*start).max(1);
    }
    numbering.start_number = numbering.level_start_numbers[0].min(u32::from(u16::MAX)) as u16;
    info.numberings.push(numbering);
    (HeadType::Number, info.numberings.len() as u16)
}

/// 문단 모양을 복제해 머리를 바꾼다(같은 모양이 있으면 그것을 쓴다).
fn set_head(info: &mut DocInfo, paragraph: &mut Paragraph, head: (HeadType, u16), level: u8) {
    let mut shape = info
        .para_shapes
        .get(paragraph.para_shape_id as usize)
        .cloned()
        .unwrap_or_default();
    shape.head_type = head.0;
    shape.numbering_id = head.1;
    shape.para_level = level.min(6);
    shape.raw_data = None;
    let id = match info.para_shapes.iter().position(|s| *s == shape) {
        Some(found) => found,
        None => {
            info.para_shapes.push(shape);
            info.para_shapes.len() - 1
        }
    };
    if let Ok(id) = u16::try_from(id) {
        paragraph.para_shape_id = id;
    }
}
