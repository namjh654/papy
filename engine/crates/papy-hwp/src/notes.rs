//! DOCX 에서 읽은 머리말·꼬리말·각주를 rhwp 문서에 넣는다(본문 HTML 을 붙여넣은 뒤).
//!
//! 각주·미주: 본문 문단의 [`papy_office::FOOTNOTE_MARK`]·[`papy_office::ENDNOTE_MARK`] 자리마다 그 글자를 지우고
//! 각주·미주를 넣는다(문서 순서 = 번호 순서). 주 안 글자는 rhwp 의 각주 편집 함수로 넣는다(미주도 같은 함수가 다룬다).

use papy_office::{
    DocxReadReport, ENDNOTE_MARK, FOOTNOTE_MARK, HeaderFooterText, PAGE_BREAK_MARK,
    PAGE_COUNT_FIELD, PAGE_COUNT_MARK, PAGE_NUMBER_FIELD, PAGE_NUMBER_MARK, SECTION_MARK,
};
use rhwp::HwpError;
use rhwp::model::control::Control;

use crate::Rhwp;

/// 넣지 못한 머리말·꼬리말·각주 수를 돌려준다.
pub(crate) fn apply(doc: &mut Rhwp, report: &DocxReadReport) -> Result<usize, HwpError> {
    page_breaks(doc)?;
    let starts = section_starts(doc)?;
    let mut dropped = 0;
    for (is_header, items) in [(true, &report.headers), (false, &report.footers)] {
        for item in items {
            let placed = match item.section {
                0 => header_footer(doc, is_header, item),
                section => match starts.get(section - 1).copied() {
                    Some(para) => later_header_footer(doc, is_header, item, para),
                    None => Err(HwpError::RenderError("구역 자리가 없습니다".to_string())),
                },
            };
            if placed.is_err() {
                dropped += 1;
            }
        }
    }
    let (hide_header, hide_footer) = report.hide_first_page;
    if hide_header || hide_footer {
        // 첫 문단은 첫 쪽에 있다. 감추기는 그 문단이 놓인 쪽에만 듣는다.
        doc.set_page_hide_native(0, 0, hide_header, hide_footer, false, false, false, false)?;
    }
    dropped += notes(doc, &report.footnotes, FOOTNOTE_MARK)?;
    dropped += notes(doc, &report.endnotes, ENDNOTE_MARK)?;
    Ok(dropped)
}

/// 본문 [`SECTION_MARK`] 를 지우고, Word 구역(첫 구역 뒤)마다 그 구역이 시작하는 문단 번호를 돌려준다.
/// (`k` 번째 = 구역 `k + 1`.)
fn section_starts(doc: &mut Rhwp) -> Result<Vec<usize>, HwpError> {
    let mut starts = Vec::new();
    let count = doc.document().sections[0].paragraphs.len();
    for para in 0..count {
        while let Some(offset) = find_mark(
            &doc.document().sections[0].paragraphs[para].text,
            SECTION_MARK,
        ) {
            doc.delete_text_native(0, para, offset, 1)?;
            starts.push(para);
        }
    }
    Ok(starts)
}

/// 뒤 구역의 머리말·꼬리말: 첫 문단에 만들어 채운 뒤 구역이 시작하는 문단 `para` 로 옮긴다. rhwp 는 한 구역에
/// 같은 종류·쪽 머리말을 하나만 만들게 해서, 이미 있는 것들은 잠시 빼 두었다가 되돌린다. 문단 중간에 놓인
/// 머리말은 한글처럼 그 문단이 놓인 쪽부터 쓰인다.
fn later_header_footer(
    doc: &mut Rhwp,
    is_header: bool,
    item: &HeaderFooterText,
    para: usize,
) -> Result<(), HwpError> {
    use rhwp::model::header_footer::HeaderFooterApply;
    let apply = match item.apply_to {
        1 => HeaderFooterApply::Even,
        2 => HeaderFooterApply::Odd,
        _ => HeaderFooterApply::Both,
    };
    let same = move |c: &Control| match c {
        Control::Header(h) => is_header && h.apply_to == apply,
        Control::Footer(f) => !is_header && f.apply_to == apply,
        _ => false,
    };
    let take = |paragraph: &mut rhwp::model::paragraph::Paragraph, index: usize| {
        let control = paragraph.controls.remove(index);
        if index < paragraph.ctrl_data_records.len() {
            paragraph.ctrl_data_records.remove(index);
        }
        paragraph.char_count = paragraph.char_count.saturating_sub(8);
        control
    };
    let put = |paragraph: &mut rhwp::model::paragraph::Paragraph, control: Control| {
        paragraph.controls.push(control);
        paragraph.align_ctrl_data_records();
        paragraph.char_count += 8;
    };
    // 이미 있는 같은 종류 머리말을 빼 둔다.
    let mut document = doc.document().clone();
    let mut stash = Vec::new();
    for (index, paragraph) in document.sections[0].paragraphs.iter_mut().enumerate() {
        while let Some(at) = paragraph.controls.iter().position(same) {
            stash.push((index, take(paragraph, at)));
        }
    }
    document.sections[0].raw_stream = None;
    doc.set_document(document);
    let filled = header_footer(doc, is_header, item);
    let mut document = doc.document().clone();
    let paragraphs = &mut document.sections[0].paragraphs;
    if filled.is_ok() {
        if let Some(at) = paragraphs[0].controls.iter().position(same) {
            let control = take(&mut paragraphs[0], at);
            put(&mut paragraphs[para], control);
        }
    }
    for (index, control) in stash {
        put(&mut paragraphs[index], control);
    }
    document.sections[0].raw_stream = None;
    doc.set_document(document);
    filled
}

fn header_footer(doc: &mut Rhwp, is_header: bool, item: &HeaderFooterText) -> Result<(), HwpError> {
    doc.create_header_footer_native(0, is_header, item.apply_to)?;
    if let Some(html) = &item.html {
        // 표·그림이 있는 머리말: 글자만 넣기로 물러서지 않게 실패하면 글자로 넣는다.
        let saved = doc.document().clone();
        match header_footer_html(doc, is_header, item.apply_to, html) {
            Ok(()) => return Ok(()),
            Err(_) => doc.set_document(saved),
        }
    }
    for (index, text) in item.paragraphs.iter().enumerate() {
        if index > 0 {
            let previous = item.paragraphs[index - 1].chars().count();
            doc.split_paragraph_in_header_footer_native(
                0,
                is_header,
                item.apply_to,
                index - 1,
                previous,
                None,
            )?;
        }
        if !text.is_empty() {
            doc.insert_text_in_header_footer_native(0, is_header, item.apply_to, index, 0, text)?;
        }
    }
    Ok(())
}

/// 머리말·꼬리말 HTML 을 넣는다. rhwp 에는 머리말에 HTML 을 붙여넣는 기능이 없어 본문 끝에 붙여넣은 뒤
/// 그 문단들을 머리말로 옮긴다(같은 문서라 글자·문단 모양 번호가 그대로 맞는다). 붙여넣은 표 칸은 본문 표처럼
/// 칸 너비로 다시 나눈 뒤 옮긴다. 쪽 번호 자리 표시는 rhwp 머리말 필드 글자로 바꾼다(둘 다 한 칸이다).
fn header_footer_html(
    doc: &mut Rhwp,
    is_header: bool,
    apply_to: u8,
    html: &str,
) -> Result<(), HwpError> {
    use rhwp::model::header_footer::HeaderFooterApply;
    // 끝 문단을 나누면 문단 끝에 놓인 그림·도형 컨트롤이 새 문단(→ 머리말)으로 넘어간다. 빈 문단을 따로 더한다.
    let count = doc.document().sections[0].paragraphs.len();
    doc.insert_paragraph_native(0, count)?;
    // 빈 문단에 붙여넣으면 첫 문단이 그 문단 모양을 쓴다. 빈 문단을 앞에 두어 첫 문단을 새 문단으로 만든다.
    let padded = match html.find("<body>").map(|i| i + "<body>".len()) {
        Some(at) => format!("{}<p></p>{}", &html[..at], &html[at..]),
        None => format!("<p></p>{html}"),
    };
    doc.paste_html_native(0, count, 0, &padded)?;
    crate::unindent_table_paragraphs(doc);
    doc.reflow_table_cells_native(0)?;
    let mut document = doc.document().clone();
    let section = &mut document.sections[0];
    let mut moved: Vec<_> = section.paragraphs.drain(count..).collect();
    // 앞에 둔 빈 문단과 끝 빈 문단은 뺀다.
    let blank = |p: &rhwp::model::paragraph::Paragraph| p.text.is_empty() && p.controls.is_empty();
    if moved.first().is_some_and(blank) && moved.len() > 1 {
        moved.remove(0);
    }
    while moved.len() > 1 && moved.last().is_some_and(blank) {
        moved.pop();
    }
    for paragraph in &mut moved {
        if paragraph.text.contains([PAGE_NUMBER_MARK, PAGE_COUNT_MARK]) {
            paragraph.text = paragraph
                .text
                .chars()
                .map(|c| match c {
                    PAGE_NUMBER_MARK => PAGE_NUMBER_FIELD,
                    PAGE_COUNT_MARK => PAGE_COUNT_FIELD,
                    c => c,
                })
                .collect();
        }
    }
    let apply = match apply_to {
        1 => HeaderFooterApply::Even,
        2 => HeaderFooterApply::Odd,
        _ => HeaderFooterApply::Both,
    };
    let target = section
        .paragraphs
        .iter_mut()
        .flat_map(|p| p.controls.iter_mut())
        .find_map(|c| match c {
            Control::Header(h) if is_header && h.apply_to == apply => Some(&mut h.paragraphs),
            Control::Footer(f) if !is_header && f.apply_to == apply => Some(&mut f.paragraphs),
            _ => None,
        })
        .ok_or_else(|| HwpError::RenderError("머리말이 없습니다".to_string()))?;
    *target = moved;
    section.raw_stream = None;
    doc.set_document(document);
    Ok(())
}

/// 본문·표 칸의 각주(미주) 자리 표시를 각주(미주)로 바꾼다(문서 순서 = 목록 순서).
/// 자리 표시보다 주가 모자라거나 남으면 남는 것을 센다.
fn notes(doc: &mut Rhwp, notes: &[Vec<String>], mark: char) -> Result<usize, HwpError> {
    let mut next = 0usize;
    let mut failed = 0usize;
    let paragraph_count = doc
        .document()
        .sections
        .first()
        .map_or(0, |s| s.paragraphs.len());
    for para in 0..paragraph_count {
        while let Some(offset) = find_mark(&doc.document().sections[0].paragraphs[para].text, mark)
        {
            doc.delete_text_native(0, para, offset, 1)?;
            let Some(note) = notes.get(next) else {
                continue;
            };
            next += 1;
            insert_note(doc, para, offset, note, mark)?;
        }
        // 표 칸 안 자리 표시: 본문에 만들어 글자를 채운 뒤 칸으로 옮긴다.
        let tables: Vec<usize> = doc.document().sections[0].paragraphs[para]
            .controls
            .iter()
            .enumerate()
            .filter(|(_, c)| matches!(c, Control::Table(_)))
            .map(|(i, _)| i)
            .collect();
        for table in tables {
            let cells = match &doc.document().sections[0].paragraphs[para].controls[table] {
                Control::Table(t) => t
                    .cells
                    .iter()
                    .map(|c| c.paragraphs.len())
                    .collect::<Vec<_>>(),
                _ => continue,
            };
            for (cell, cell_paragraphs) in cells.into_iter().enumerate() {
                for cell_para in 0..cell_paragraphs {
                    loop {
                        let offset =
                            match &doc.document().sections[0].paragraphs[para].controls[table] {
                                Control::Table(t) => {
                                    find_mark(&t.cells[cell].paragraphs[cell_para].text, mark)
                                }
                                _ => None,
                            };
                        let Some(offset) = offset else { break };
                        doc.delete_text_in_cell_native(0, para, table, cell, cell_para, offset, 1)?;
                        let Some(note) = notes.get(next) else {
                            continue;
                        };
                        next += 1;
                        let control = insert_note(doc, para, 0, note, mark)?;
                        if doc
                            .move_note_into_cell_native(
                                0, para, control, para, table, cell, cell_para, offset,
                            )
                            .is_err()
                        {
                            failed += 1;
                        }
                    }
                }
            }
        }
    }
    Ok(failed + notes.len().saturating_sub(next))
}

fn find_mark(text: &str, mark: char) -> Option<usize> {
    text.chars().position(|c| c == mark)
}

/// 본문 문단 `para` 의 `offset` 에 각주(미주)를 넣고 글자를 채운다. 넣은 컨트롤 번호를 돌려준다.
fn insert_note(
    doc: &mut Rhwp,
    para: usize,
    offset: usize,
    note: &[String],
    mark: char,
) -> Result<usize, HwpError> {
    let result = if mark == ENDNOTE_MARK {
        doc.insert_endnote_native(0, para, offset)?
    } else {
        doc.insert_footnote_native(0, para, offset)?
    };
    let control = serde_json::from_str::<serde_json::Value>(&result)
        .ok()
        .and_then(|value| value.get("controlIdx")?.as_u64())
        .ok_or_else(|| HwpError::RenderError("note insert result".into()))?
        as usize;
    for (index, text) in note.iter().enumerate() {
        if index > 0 {
            let previous = note[index - 1].chars().count();
            doc.split_paragraph_in_footnote_native(0, para, control, index - 1, previous, None)?;
        }
        if !text.is_empty() {
            doc.insert_text_in_footnote_native(0, para, control, index, 0, text)?;
        }
    }
    Ok(control)
}

/// 쪽 나누기 표시를 지우고 그 문단 앞에서 쪽을 나눈다(한 번에 다시 짠다).
fn page_breaks(doc: &mut Rhwp) -> Result<(), HwpError> {
    doc.apply_page_break_marks_native(0, PAGE_BREAK_MARK)?;
    doc.apply_column_break_marks_native(0, papy_office::COLUMN_BREAK_MARK)?;
    Ok(())
}
