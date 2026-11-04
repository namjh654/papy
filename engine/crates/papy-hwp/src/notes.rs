//! DOCX 에서 읽은 머리말·꼬리말·각주를 rhwp 문서에 넣는다(본문 HTML 을 붙여넣은 뒤).
//!
//! 각주·미주: 본문 문단의 [`papy_office::FOOTNOTE_MARK`]·[`papy_office::ENDNOTE_MARK`] 자리마다 그 글자를 지우고
//! 각주·미주를 넣는다(문서 순서 = 번호 순서). 주 안 글자는 rhwp 의 각주 편집 함수로 넣는다(미주도 같은 함수가 다룬다).

use papy_office::{DocxReadReport, ENDNOTE_MARK, FOOTNOTE_MARK, HeaderFooterText};
use rhwp::HwpError;

use crate::Rhwp;

/// 넣지 못한 머리말·꼬리말·각주 수를 돌려준다.
pub(crate) fn apply(doc: &mut Rhwp, report: &DocxReadReport) -> Result<usize, HwpError> {
    let mut dropped = 0;
    for (is_header, items) in [(true, &report.headers), (false, &report.footers)] {
        for item in items {
            if header_footer(doc, is_header, item).is_err() {
                dropped += 1;
            }
        }
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

/// 본문 각주(미주) 자리 표시를 각주(미주)로 바꾼다. 자리 표시보다 주가 모자라거나 남으면 남는 것을 센다.
fn notes(doc: &mut Rhwp, notes: &[Vec<String>], mark: char) -> Result<usize, HwpError> {
    let mut next = 0usize;
    let paragraph_count = doc
        .document()
        .sections
        .first()
        .map_or(0, |s| s.paragraphs.len());
    for para in 0..paragraph_count {
        loop {
            let offset = doc.document().sections[0].paragraphs[para]
                .text
                .chars()
                .position(|c| c == mark);
            let Some(offset) = offset else { break };
            doc.delete_text_native(0, para, offset, 1)?;
            let Some(note) = notes.get(next) else {
                continue;
            };
            next += 1;
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
                    doc.split_paragraph_in_footnote_native(
                        0,
                        para,
                        control,
                        index - 1,
                        previous,
                        None,
                    )?;
                }
                if !text.is_empty() {
                    doc.insert_text_in_footnote_native(0, para, control, index, 0, text)?;
                }
            }
        }
    }
    Ok(notes.len().saturating_sub(next))
}
