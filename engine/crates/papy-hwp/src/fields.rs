//! DOCX 글 콘텐츠 컨트롤(`w:sdt`)을 한글 누름틀(ClickHere)로 넣는다(본문 HTML 을 붙여넣고 링크를 건 뒤).
//!
//! papy-office 는 본문·표 칸·글상자 문단의 콘텐츠 컨트롤 내용을 [`papy_office::FIELD_START_MARK`]·
//! [`papy_office::FIELD_END_MARK`] 로 감싸고 안내문·이름·메모를 [`papy_office::ClickHere`] 로 넘긴다.
//! 표시를 지우고, 시작 자리에 빈 누름틀을 넣은 뒤 그 끝을 내용 끝까지 늘린다. 글상자 안 것은 바로 넣는다.
//! 한글 계산식 필드도 같은 길로 되돌린다([`papy_office::ClickHere::formula`], 결과 글자를 감싼다).
//! 종전에는 누름틀을 DOCX 로 저장하면 글만 남아 다시 열었을 때 양식 칸이 사라졌다.

use papy_office::{ClickHere, FIELD_END_MARK, FIELD_START_MARK, field_index};
use rhwp::model::control::{Control, Field, FieldType};
use rhwp::model::paragraph::{FieldRange, Paragraph};
use rhwp::parser::tags::{FIELD_CLICKHERE, FIELD_FORMULA};

use crate::Rhwp;

/// (구역, 본문 문단, 표 칸 경로, 시작, 끝, 누름틀 차례)
type Found = (
    usize,
    usize,
    Vec<(usize, usize, usize)>,
    usize,
    usize,
    usize,
);

pub(crate) fn apply(doc: &mut Rhwp, fields: &[ClickHere]) {
    if fields.is_empty() {
        return;
    }
    let mut document = doc.document().clone();
    let mut found: Vec<Found> = Vec::new();
    // 글상자 것은 rhwp 가 매기는 번호와 겹치지 않게 높은 번호부터
    let mut next_id = 0x4000_0000u32;
    for (index, section) in document.sections.iter_mut().enumerate() {
        walk(
            &mut section.paragraphs,
            index,
            None,
            &[],
            fields,
            &mut next_id,
            &mut found,
            0,
        );
        section.raw_stream = None;
    }
    doc.set_document(document);

    // 빈 누름틀을 넣고 바로 그 끝을 내용 끝까지 늘린다(같은 문단의 다음 누름틀 자리를 바르게 잡도록).
    for (section, para, path, start, end, index) in found {
        let Some(field) = fields.get(index) else {
            continue;
        };
        if let Some(paragraph) = paragraph_mut(doc.document_mut(), section, para, &path) {
            fix_tab_offsets(paragraph);
            // 계산식은 rhwp 에 넣는 길이 없어 바로 넣는다.
            if field.formula.is_some() {
                if insert_direct(paragraph, start, end, field, next_id) {
                    next_id += 1;
                }
                continue;
            }
        }
        if field.formula.is_some() {
            continue;
        }
        let inserted = if path.is_empty() {
            doc.insert_click_here_field_at(
                section,
                para,
                start,
                &field.guide,
                &field.memo,
                &field.name,
                field.editable,
            )
        } else {
            doc.insert_click_here_field_at_by_path(
                section,
                para,
                &path,
                start,
                &field.guide,
                &field.memo,
                &field.name,
                field.editable,
            )
        };
        let Some(id) = inserted.ok().as_deref().and_then(field_id) else {
            continue;
        };
        if end > start {
            if let Some(paragraph) = paragraph_mut(doc.document_mut(), section, para, &path) {
                widen(paragraph, id, start, end);
            }
        }
    }
}

fn paragraph_mut<'a>(
    document: &'a mut rhwp::model::document::Document,
    section: usize,
    para: usize,
    path: &[(usize, usize, usize)],
) -> Option<&'a mut Paragraph> {
    let mut paragraph = document
        .sections
        .get_mut(section)?
        .paragraphs
        .get_mut(para)?;
    for &(control, cell, index) in path {
        let Some(Control::Table(table)) = paragraph.controls.get_mut(control) else {
            return None;
        };
        paragraph = table.cells.get_mut(cell)?.paragraphs.get_mut(index)?;
    }
    Some(paragraph)
}

/// 붙여넣은 문단은 탭을 원시 위치 1 로 센다(HWP 는 8). 누름틀 자리를 셀 수 있게 탭을 8 로 다시 센다.
/// 글자 앞 컨트롤 자리(8 단위)는 그대로 두고, 글자 모양·줄 시작 위치도 함께 옮긴다.
fn fix_tab_offsets(paragraph: &mut Paragraph) {
    let chars: Vec<char> = paragraph.text.chars().collect();
    if chars.len() != paragraph.char_offsets.len() {
        return;
    }
    let width = |c: char| if c == '\t' { 8 } else { c.len_utf16() as u32 };
    let mut fixed = Vec::with_capacity(chars.len());
    let (mut old_end, mut new_end) = (0u32, 0u32);
    let mut wrong = false;
    for (&old, &c) in paragraph.char_offsets.iter().zip(&chars) {
        let gap = old.saturating_sub(old_end);
        let new = new_end + gap;
        fixed.push(new);
        // 원래 탭이 8 로 잡혀 있으면 그대로(다음 글자까지 거리로 판단)
        old_end = old + if c == '\t' { 1 } else { width(c) };
        new_end = new + width(c);
        wrong |= c == '\t';
    }
    if !wrong {
        return;
    }
    // 이미 8 로 센 문단이면 바꾸지 않는다: 탭 뒤 글자 거리가 8 이상이면 맞는 것으로 본다.
    let already = paragraph
        .char_offsets
        .windows(2)
        .zip(&chars)
        .any(|(w, &c)| c == '\t' && w[1] - w[0] >= 8);
    if already {
        return;
    }
    let map =
        |raw: u32, offsets: &[u32], fixed: &[u32], total_old: u32, total_new: u32| match offsets
            .iter()
            .position(|&o| o >= raw)
        {
            Some(i) => fixed[i] - (offsets[i] - raw).min(fixed[i]),
            None => total_new + raw.saturating_sub(total_old),
        };
    let offsets = paragraph.char_offsets.clone();
    for shape in &mut paragraph.char_shapes {
        shape.start_pos = map(shape.start_pos, &offsets, &fixed, old_end, new_end);
    }
    for line in &mut paragraph.line_segs {
        line.text_start = map(line.text_start, &offsets, &fixed, old_end, new_end);
    }
    paragraph.char_count = paragraph.char_count + new_end - old_end;
    paragraph.char_offsets = fixed;
}

/// 넣은 빈 누름틀(`id`, `start` 자리)의 끝을 `end` 로: 범위를 늘리고 END 자리(8 단위)를 내용 뒤로 옮긴다.
fn widen(paragraph: &mut Paragraph, id: u32, start: usize, end: usize) {
    let end = end.min(paragraph.text.chars().count());
    if paragraph.char_offsets.len() != paragraph.text.chars().count() {
        return;
    }
    let controls = &paragraph.controls;
    let Some(range) = paragraph.field_ranges.iter_mut().find(|r| {
        r.start_char_idx == start
            && r.end_char_idx == start
            && matches!(controls.get(r.control_idx), Some(Control::Field(f)) if f.field_id == id)
    }) else {
        return;
    };
    // BEGIN·END 가 둘 다 start 글자 앞에 있어야 옮길 수 있다.
    let before = if start == 0 {
        0
    } else {
        let ch = paragraph.text.chars().nth(start - 1).unwrap_or(' ');
        paragraph.char_offsets[start - 1] + if ch == '\t' { 8 } else { ch.len_utf16() as u32 }
    };
    if paragraph
        .char_offsets
        .get(start)
        .is_none_or(|o| *o < before + 16)
    {
        return;
    }
    range.end_char_idx = end;
    // 내용 글자(원시 위치 from..to)가 8 앞으로 온다. 글자 모양·영역 경계도 함께 옮긴다.
    let from = paragraph.char_offsets[start];
    let to = paragraph
        .char_offsets
        .get(end)
        .copied()
        .unwrap_or(paragraph.char_count.saturating_sub(1));
    let back = |v: &mut u32| {
        if (from..to).contains(v) {
            *v -= 8;
        }
    };
    paragraph.char_offsets[start..end]
        .iter_mut()
        .for_each(|o| *o -= 8);
    for shape in &mut paragraph.char_shapes {
        back(&mut shape.start_pos);
    }
    for tag in &mut paragraph.range_tags {
        back(&mut tag.start);
        back(&mut tag.end);
    }
    paragraph.stored_text_partition_dirty = true;
}

/// rhwp 결과 JSON `{"ok":true,"fieldId":N,…}` 의 N
fn field_id(json: &str) -> Option<u32> {
    let rest = &json[json.find("\"fieldId\":")? + "\"fieldId\":".len()..];
    let end = rest
        .find(|c: char| !c.is_ascii_digit())
        .unwrap_or(rest.len());
    rest[..end].parse().ok()
}

/// 문단의 시작·끝 표시를 지우고 (시작, 끝, 누름틀 차례)를 돌려준다. 차례를 읽지 못한 표시는 지우기만 한다.
fn marks(paragraph: &mut Paragraph) -> Vec<(usize, usize, usize)> {
    let mut out = Vec::new();
    while let Some(start) = paragraph.text.chars().position(|c| c == FIELD_START_MARK) {
        let code: Vec<char> = paragraph.text.chars().skip(start + 1).take(2).collect();
        let index = match code.as_slice() {
            [high, low] => field_index(*high, *low),
            _ => None,
        };
        paragraph.delete_text_at(start, if index.is_some() { 3 } else { 1 });
        let end = paragraph
            .text
            .chars()
            .skip(start)
            .position(|c| c == FIELD_END_MARK || c == FIELD_START_MARK)
            .map(|i| start + i);
        let end = match end {
            Some(end) if paragraph.text.chars().nth(end) == Some(FIELD_END_MARK) => {
                paragraph.delete_text_at(end, 1);
                end
            }
            Some(end) => end,
            None => paragraph.text.chars().count(),
        };
        if let Some(index) = index {
            out.push((start, end, index));
        }
    }
    while let Some(end) = paragraph.text.chars().position(|c| c == FIELD_END_MARK) {
        paragraph.delete_text_at(end, 1);
    }
    out
}

/// 본문·표 칸 문단의 표시는 `found` 에 모으고(rhwp 로 넣는다), 글상자 문단의 것은 바로 넣는다.
#[allow(clippy::too_many_arguments)]
fn walk(
    paragraphs: &mut [Paragraph],
    section: usize,
    para: Option<usize>,
    path: &[(usize, usize, usize)],
    fields: &[ClickHere],
    next_id: &mut u32,
    found: &mut Vec<Found>,
    depth: usize,
) {
    if depth > 16 {
        return;
    }
    for (index, paragraph) in paragraphs.iter_mut().enumerate() {
        let (body, cell_path) = match para {
            None => (index, Vec::new()),
            Some(body) => {
                let mut path = path.to_vec();
                if let Some(last) = path.last_mut() {
                    last.2 = index;
                }
                (body, path)
            }
        };
        if paragraph.text.contains(FIELD_START_MARK) || paragraph.text.contains(FIELD_END_MARK) {
            for (start, end, order) in marks(paragraph) {
                found.push((section, body, cell_path.clone(), start, end, order));
            }
        }
        for (control_index, control) in paragraph.controls.iter_mut().enumerate() {
            match control {
                Control::Table(table) => {
                    for (cell_index, cell) in table.cells.iter_mut().enumerate() {
                        let mut path = cell_path.clone();
                        path.push((control_index, cell_index, 0));
                        walk(
                            &mut cell.paragraphs,
                            section,
                            Some(body),
                            &path,
                            fields,
                            next_id,
                            found,
                            depth + 1,
                        );
                    }
                }
                Control::Shape(shape) => {
                    if let Some(text_box) = shape.drawing_mut().and_then(|d| d.text_box.as_mut()) {
                        in_text_box(&mut text_box.paragraphs, fields, next_id, depth + 1);
                    }
                }
                _ => {}
            }
        }
    }
}

/// 글상자 문단: rhwp 에 글상자 안 누름틀을 넣는 길이 없어 바로 넣는다.
fn in_text_box(
    paragraphs: &mut [Paragraph],
    fields: &[ClickHere],
    next_id: &mut u32,
    depth: usize,
) {
    if depth > 16 {
        return;
    }
    for paragraph in paragraphs.iter_mut() {
        if paragraph.text.contains(FIELD_START_MARK) || paragraph.text.contains(FIELD_END_MARK) {
            // 뒤 것부터 넣어 앞 자리가 바뀌지 않게 한다.
            let mut found = marks(paragraph);
            found.sort_by_key(|(start, _, _)| std::cmp::Reverse(*start));
            for (start, end, order) in found {
                if let Some(field) = fields.get(order) {
                    if insert_direct(paragraph, start, end, field, *next_id) {
                        *next_id += 1;
                    }
                }
            }
        }
        for control in &mut paragraph.controls {
            match control {
                Control::Table(table) => {
                    for cell in &mut table.cells {
                        in_text_box(&mut cell.paragraphs, fields, next_id, depth + 1);
                    }
                }
                Control::Shape(shape) => {
                    if let Some(text_box) = shape.drawing_mut().and_then(|d| d.text_box.as_mut()) {
                        in_text_box(&mut text_box.paragraphs, fields, next_id, depth + 1);
                    }
                }
                _ => {}
            }
        }
    }
}

/// `start..end` 를 감싸는 누름틀을 문단에 바로 넣는다(rhwp 하이퍼링크 넣기와 같은 방법:
/// BEGIN·END 컨트롤 자리 8 단위씩을 원시 위치 축에 낸다).
fn insert_direct(
    paragraph: &mut Paragraph,
    start: usize,
    end: usize,
    field: &ClickHere,
    id: u32,
) -> bool {
    fix_tab_offsets(paragraph);
    let len = paragraph.text.chars().count();
    if start > end || end > len || paragraph.char_offsets.len() != len {
        return false;
    }
    let overlaps = paragraph.field_ranges.iter().any(|r| {
        start < r.end_char_idx && r.start_char_idx < end
            || (r.start_char_idx == r.end_char_idx
                && start < r.start_char_idx
                && r.start_char_idx < end)
    });
    if overlaps {
        return false;
    }
    let positions = paragraph.control_text_positions();
    let insert_idx = (0..paragraph.controls.len())
        .position(|idx| {
            paragraph
                .field_ranges
                .iter()
                .find(|r| r.control_idx == idx)
                .map_or(positions.get(idx).copied().unwrap_or(0), |r| {
                    r.start_char_idx
                })
                > start
        })
        .unwrap_or(paragraph.controls.len());
    let boundary = |p: &Paragraph, at: usize| {
        p.char_offsets
            .get(at)
            .copied()
            .unwrap_or(p.char_count.saturating_sub(1))
    };
    let (start_raw, end_raw) = (boundary(paragraph, start), boundary(paragraph, end));
    shift_axis(paragraph, end_raw);
    shift_axis(paragraph, start_raw);
    for range in &mut paragraph.field_ranges {
        if range.control_idx >= insert_idx {
            range.control_idx += 1;
        }
    }
    paragraph.controls.insert(
        insert_idx,
        Control::Field(match &field.formula {
            Some(command) => Field {
                field_type: FieldType::Formula,
                command: command.clone(),
                properties: u32::from(field.editable),
                field_id: id,
                ctrl_id: FIELD_FORMULA,
                ..Default::default()
            },
            None => Field {
                field_type: FieldType::ClickHere,
                command: Field::build_clickhere_command(&field.guide, &field.memo),
                properties: u32::from(field.editable),
                extra_properties: 0x09,
                field_id: id,
                ctrl_id: FIELD_CLICKHERE,
                ctrl_data_name: (!field.name.is_empty()).then(|| field.name.clone()),
                ..Default::default()
            },
        }),
    );
    let records = paragraph.controls.len() - 1;
    paragraph.ctrl_data_records.resize(records, None);
    paragraph.ctrl_data_records.insert(insert_idx, None);
    paragraph.field_ranges.push(FieldRange {
        start_char_idx: start,
        end_char_idx: end,
        control_idx: insert_idx,
        ..Default::default()
    });
    paragraph
        .field_ranges
        .sort_by_key(|r| (r.start_char_idx, r.control_idx));
    true
}

/// 원시 위치 `boundary` 부터 8 단위 뒤로(첫 글자 서식의 0 자리는 그대로).
fn shift_axis(paragraph: &mut Paragraph, boundary: u32) {
    let shift = |v: &mut u32| {
        if *v >= boundary {
            *v += 8;
        }
    };
    paragraph.char_offsets.iter_mut().for_each(shift);
    for shape in &mut paragraph.char_shapes {
        if shape.start_pos != 0 {
            shift(&mut shape.start_pos);
        }
    }
    for range in &mut paragraph.range_tags {
        shift(&mut range.start);
        shift(&mut range.end);
    }
    paragraph.stored_text_partition_dirty = true;
    paragraph.char_count += 8;
}
