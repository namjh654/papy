//! HTML 조각(머리말·글상자·각주 내용)을 rhwp 문단으로 만든다.
//!
//! rhwp 에는 머리말·글상자·각주에 HTML 을 붙여넣는 기능이 없다. 본문 끝에 붙여넣은 뒤 그 문단들을 떼어 낸다
//! (같은 문서라 글자·문단 모양·그림 자료 번호가 그대로 맞는다). 붙여넣은 표 칸은 본문 표처럼 칸 너비로 다시 나눈 뒤 뗀다.

use rhwp::HwpError;
use rhwp::model::paragraph::Paragraph;

use crate::Rhwp;

/// `html` 을 본문 끝에 붙여넣고 그 문단들을 떼어 돌려준다(본문은 붙여넣기 전과 같다). 앞뒤 빈 문단은 뺀다.
pub(crate) fn paste(doc: &mut Rhwp, html: &str) -> Result<Vec<Paragraph>, HwpError> {
    paste_placed(doc, html, &[])
}

/// [`paste`] 와 같고, 맨 바깥 표마다 쪽·종이 기준 자리(`places`, rhwp 표 속성 JSON 조각, 빈 글은 그대로)를 준다.
pub(crate) fn paste_placed(
    doc: &mut Rhwp,
    html: &str,
    places: &[String],
) -> Result<Vec<Paragraph>, HwpError> {
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
    if places.iter().any(|p| !p.is_empty()) {
        use rhwp::model::control::Control;
        let tables: Vec<(usize, usize)> = doc.document().sections[0].paragraphs[count..]
            .iter()
            .enumerate()
            .flat_map(|(i, p)| {
                p.controls
                    .iter()
                    .enumerate()
                    .filter(|(_, c)| matches!(c, Control::Table(_)))
                    .map(move |(c, _)| (count + i, c))
            })
            .collect();
        for ((para, control), place) in tables.into_iter().zip(places) {
            if place.is_empty() {
                continue;
            }
            let wrap = if place.contains(r#""textWrap""#) {
                ""
            } else {
                r#","textWrap":"TopAndBottom""#
            };
            doc.set_table_properties_native(
                0,
                para,
                control,
                &format!(r#"{{"treatAsChar":false,"outerLeft":0{wrap}{place}}}"#),
            )?;
        }
    }
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
    // 문서 끝에 붙여넣어 줄 세로 자리가 본문 끝 기준이다. 조각 처음을 0 으로 옮긴다
    // (글상자 글이 상자 아래 멀리 그려지고 상자가 길게 늘어났다).
    if let Some(base) = moved
        .iter()
        .find_map(|p| p.line_segs.first().map(|seg| seg.vertical_pos))
    {
        for seg in moved.iter_mut().flat_map(|p| p.line_segs.iter_mut()) {
            seg.vertical_pos -= base;
        }
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
            if depth > 0 {
                auto_numbers(paragraph);
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

/// 표 칸 안 쪽 번호·전체 쪽 수 필드 글자를 rhwp 자동 번호(`atno`)로 바꾼다. rhwp 는 머리말·바탕쪽 맨 바깥 문단의
/// 필드 글자만 쪽마다 바꿔 그리고, 칸 안에서는 자동 번호 컨트롤만 바꾼다(바탕쪽 틀의 쪽 번호가 모든 쪽 1 이었다).
/// 만드는 꼴은 rhwp 파서가 읽은 것과 같다: 컨트롤 몫 8칸의 첫 칸에 자리표 글자(빈칸), 다음 글자는 8칸 뒤.
/// 자리가 어긋나면 rhwp 가 앞 빈칸("Page 1 of 3" 의 "Page" 뒤)을 자리표로 잘못 집는다.
fn auto_numbers(paragraph: &mut Paragraph) {
    use papy_office::{PAGE_COUNT_FIELD, PAGE_NUMBER_FIELD};
    use rhwp::model::control::{AutoNumber, AutoNumberType, Control};
    let markers: Vec<(usize, char)> = paragraph
        .text
        .chars()
        .enumerate()
        .filter(|(_, c)| matches!(*c, PAGE_NUMBER_FIELD | PAGE_COUNT_FIELD))
        .collect();
    // 뒤 것부터 바꿔야 앞 것의 글자 자리가 그대로다.
    for (index, marker) in markers.into_iter().rev() {
        let Some(slot) = paragraph.char_offsets.get(index).copied() else {
            continue;
        };
        paragraph.delete_text_at(index, 1);
        paragraph.insert_inline_control(
            index,
            Control::AutoNumber(AutoNumber {
                number_type: if marker == PAGE_NUMBER_FIELD {
                    AutoNumberType::Page
                } else {
                    AutoNumberType::TotalPage
                },
                ..Default::default()
            }),
        );
        // 자리표 글자: 컨트롤 몫의 첫 칸을 쓴다(글자 수·뒤 글자 자리는 그대로).
        let byte = paragraph
            .text
            .char_indices()
            .nth(index)
            .map_or(paragraph.text.len(), |(b, _)| b);
        paragraph.text.insert(byte, ' ');
        paragraph.char_offsets.insert(index, slot);
    }
}
