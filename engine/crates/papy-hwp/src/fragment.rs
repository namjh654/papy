//! HTML 조각(머리말·글상자·각주 내용)을 rhwp 문단으로 만든다.
//!
//! rhwp 에는 머리말·글상자·각주에 HTML 을 붙여넣는 기능이 없다. 본문 끝에 붙여넣은 뒤 그 문단들을 떼어 낸다
//! (같은 문서라 글자·문단 모양·그림 자료 번호가 그대로 맞는다). 붙여넣은 표 칸은 본문 표처럼 칸 너비로 다시 나눈 뒤 뗀다.

use rhwp::HwpError;
use rhwp::model::paragraph::Paragraph;

use crate::Rhwp;

/// `html` 을 본문 끝에 붙여넣고 그 문단들을 떼어 돌려준다(본문은 붙여넣기 전과 같다). 앞뒤 빈 문단은 뺀다.
pub(crate) fn paste(doc: &mut Rhwp, html: &str) -> Result<Vec<Paragraph>, HwpError> {
    // 끝 문단을 나누면 문단 끝에 놓인 그림·도형 컨트롤이 새 문단(→ 조각)으로 넘어간다. 빈 문단을 따로 더한다.
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
    section.raw_stream = None;
    doc.set_document(document);
    let blank = |p: &Paragraph| p.text.is_empty() && p.controls.is_empty();
    if moved.first().is_some_and(blank) && moved.len() > 1 {
        moved.remove(0);
    }
    while moved.len() > 1 && moved.last().is_some_and(blank) {
        moved.pop();
    }
    if moved.is_empty() {
        moved.push(Paragraph::new_empty());
    }
    // 쪽 번호·전체 쪽 수 자리 표시 → rhwp 머리말 필드 글자(둘 다 한 칸이다). 글상자(머리말 묶음 안 등)도 같다.
    fn fields(paragraphs: &mut [Paragraph], depth: usize) {
        use papy_office::{PAGE_COUNT_FIELD, PAGE_COUNT_MARK, PAGE_NUMBER_FIELD, PAGE_NUMBER_MARK};
        use rhwp::model::control::Control;
        if depth > 16 {
            return;
        }
        for paragraph in paragraphs {
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
            for control in &mut paragraph.controls {
                if let Control::Table(table) = control {
                    for cell in &mut table.cells {
                        fields(&mut cell.paragraphs, depth + 1);
                    }
                }
            }
        }
    }
    fields(&mut moved, 0);
    Ok(moved)
}
