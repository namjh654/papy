//! Papy 가 DOCX 머리말에 적어 둔 한글 바탕쪽(`papy:master` 표시 문단 뒤)을 rhwp 바탕쪽으로 되돌린다.
//!
//! 바탕쪽 HTML 은 본문 끝에 붙여넣어 문단으로 만든 뒤 첫 구역 바탕쪽으로 옮긴다([`crate::fragment::paste_placed`]).
//! 쪽·종이 기준 표(바탕쪽 틀)는 붙여넣은 동안 그 자리에 놓는다.

use papy_office::DocxReadReport;
use rhwp::HwpError;
use rhwp::model::header_footer::{HeaderFooterApply, MasterPage};
use rhwp::model::paragraph::Paragraph;

use crate::Rhwp;

/// 넣지 못한 바탕쪽 수를 돌려준다.
pub(crate) fn apply(doc: &mut Rhwp, report: &DocxReadReport) -> Result<usize, HwpError> {
    let mut dropped = 0;
    let mut masters = Vec::new();
    for item in report.masters.iter().filter(|m| m.section == 0) {
        let Some(html) = &item.html else {
            continue;
        };
        let saved = doc.document().clone();
        match build(doc, item, html) {
            Ok(paragraphs) => masters.push((item.apply_to, paragraphs)),
            Err(_) => {
                doc.set_document(saved);
                dropped += 1;
            }
        }
    }
    if masters.is_empty() {
        return Ok(dropped);
    }
    // HWP5 는 바탕쪽 적용 범위를 따로 적지 않고 차례(양쪽, 홀수, 짝수)로 정한다. 앞 것이 빠지면 다시 열 때
    // 범위가 밀리니(홀수·짝수만 → 양쪽·홀수) 빈 바탕쪽으로 자리를 채운다.
    let has = |masters: &[(u8, Vec<Paragraph>)], apply: u8| masters.iter().any(|m| m.0 == apply);
    if has(&masters, 1) && !has(&masters, 2) {
        masters.push((2, vec![Paragraph::new_empty()]));
    }
    if !has(&masters, 0) {
        masters.push((0, vec![Paragraph::new_empty()]));
    }
    // 한글 차례: 양쪽, 홀수, 짝수.
    masters.sort_by_key(|(apply, _)| match apply {
        0 => 0,
        2 => 1,
        _ => 2,
    });
    let mut document = doc.document().clone();
    let section = &mut document.sections[0];
    let page = &section.section_def.page_def;
    let text_width = page
        .width
        .saturating_sub(page.margin_left + page.margin_right + page.margin_gutter);
    let text_height = page.height.saturating_sub(
        page.margin_top + page.margin_bottom + page.margin_header + page.margin_footer,
    );
    for (apply, paragraphs) in masters {
        section.section_def.master_pages.push(MasterPage {
            apply_to: match apply {
                1 => HeaderFooterApply::Even,
                2 => HeaderFooterApply::Odd,
                _ => HeaderFooterApply::Both,
            },
            paragraphs,
            text_width,
            text_height,
            ..Default::default()
        });
    }
    if report.hide_first_master {
        section.section_def.hide_master_page = true;
        // HWP5 구역 속성 bit 2: 첫 쪽 바탕쪽 감추기.
        section.section_def.flags |= 0x0004;
    }
    // 저장은 첫 문단의 구역 정의 컨트롤을 쓴다. 바탕쪽을 그쪽에도 넣는다.
    let section_def = section.section_def.clone();
    if let Some(paragraph) = section.paragraphs.first_mut() {
        for control in &mut paragraph.controls {
            if let rhwp::model::control::Control::SectionDef(def) = control {
                def.master_pages = section_def.master_pages.clone();
                def.hide_master_page = section_def.hide_master_page;
                def.flags = section_def.flags;
            }
        }
    }
    section.raw_stream = None;
    doc.set_document(document);
    Ok(dropped)
}

fn build(
    doc: &mut Rhwp,
    item: &papy_office::HeaderFooterText,
    html: &str,
) -> Result<Vec<Paragraph>, HwpError> {
    let mut moved = crate::fragment::paste_placed(doc, html, &item.table_places)?;
    let mut shapes = crate::shapes::build(doc, &item.shapes)?;
    let mut cell_shapes = crate::shapes::build(doc, &item.cell_shapes)?;
    crate::shapes::place(&mut moved, &mut shapes, papy_office::SHAPE_MARK, false);
    crate::shapes::place(
        &mut moved,
        &mut cell_shapes,
        papy_office::CELL_SHAPE_MARK,
        true,
    );
    Ok(moved)
}
