//! DOCX 에서 읽은 머리말·꼬리말·각주를 rhwp 문서에 넣는다(본문 HTML 을 붙여넣은 뒤).
//!
//! 각주·미주: 본문 문단의 [`papy_office::FOOTNOTE_MARK`]·[`papy_office::ENDNOTE_MARK`] 자리마다 그 글자를 지우고
//! 각주·미주를 넣는다(문서 순서 = 번호 순서). 주 안 글자는 rhwp 의 각주 편집 함수로 넣는다(미주도 같은 함수가 다룬다).

use papy_office::{DocxReadReport, ENDNOTE_MARK, FOOTNOTE_MARK, HeaderFooterText, PAGE_BREAK_MARK};
use rhwp::HwpError;
use rhwp::model::control::Control;

use crate::Rhwp;

/// 넣지 못한 머리말·꼬리말·각주 수를 돌려준다.
pub(crate) fn apply(doc: &mut Rhwp, report: &DocxReadReport) -> Result<usize, HwpError> {
    page_breaks(doc)?;
    let mut dropped = 0;
    for (is_header, items) in [(true, &report.headers), (false, &report.footers)] {
        for item in items {
            if header_footer(doc, is_header, item).is_err() {
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

fn header_footer(doc: &mut Rhwp, is_header: bool, item: &HeaderFooterText) -> Result<(), HwpError> {
    doc.create_header_footer_native(0, is_header, item.apply_to)?;
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
