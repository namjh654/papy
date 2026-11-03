//! 열 때 고치는 문서 구조 결함.
//!
//! rhwp 0.8.7 의 HWPX 저장은 그림만 든 문단(글자처럼 취급하는 그림 하나)에 줄 정보(`<hp:linesegarray>`)를
//! 쓰지 않고, HWPX 읽기는 그런 문단의 줄을 다시 계산하지 않아 그림이 그려지지 않는다. rhwp 가 HTML 그림을
//! 넣을 때 만드는 것(`html_table_import.rs` `parse_img_html`)과 같은 줄 정보를 채운다.

use rhwp::model::control::Control;
use rhwp::model::paragraph::{LineSeg, Paragraph};

/// 개체 제어문자가 있는 문단 표시(control_mask bit 11).
const OBJECT_MASK: u32 = 0x0000_0800;

fn lone_inline_picture(paragraph: &Paragraph) -> Option<(u32, u32)> {
    if !paragraph.line_segs.is_empty()
        || !paragraph.text.is_empty()
        || paragraph.controls.len() != 1
    {
        return None;
    }
    match &paragraph.controls[0] {
        Control::Picture(picture) if picture.common.treat_as_char => {
            Some((picture.common.width, picture.common.height))
        }
        _ => None,
    }
}

/// 줄 정보 없는 그림 문단을 고친다. 고친 문단 수를 돌려준다(0 이면 문서를 건드리지 않는다).
pub(crate) fn repair_inline_pictures(doc: &mut crate::Rhwp) -> usize {
    let needed = doc
        .document()
        .sections
        .iter()
        .flat_map(|section| &section.paragraphs)
        .any(|paragraph| lone_inline_picture(paragraph).is_some());
    if !needed {
        return 0;
    }
    let mut document = doc.document().clone();
    let mut repaired = 0;
    for paragraph in document
        .sections
        .iter_mut()
        .flat_map(|section| section.paragraphs.iter_mut())
    {
        let Some((width, height)) = lone_inline_picture(paragraph) else {
            continue;
        };
        let height = height.min(i32::MAX as u32) as i32;
        paragraph.control_mask |= OBJECT_MASK;
        paragraph.line_segs = vec![LineSeg {
            text_start: 0,
            line_height: height,
            text_height: height,
            baseline_distance: (f64::from(height) * 0.85) as i32,
            line_spacing: 600,
            segment_width: width.min(i32::MAX as u32) as i32,
            tag: LineSeg::TAG_SINGLE_SEGMENT_LINE,
            ..Default::default()
        }];
        repaired += 1;
    }
    doc.set_document(document);
    repaired
}
