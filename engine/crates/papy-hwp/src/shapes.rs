//! DOCX 에서 읽은 도형을 rhwp 문서에 넣는다(본문 HTML 을 붙여넣은 뒤).
//!
//! 본문 문단의 [`papy_office::SHAPE_MARK`] 자리마다 그 글자를 지우고 도형을 만든다(문서 순서 = 목록 순서).

use papy_office::{DocxShape, Relative, SHAPE_MARK, ShapeKind, Wrap};
use rhwp::HwpError;
use serde_json::{Value, json};

use crate::Rhwp;

/// EMU(1/914400 인치) → HWPUNIT(1/7200 인치)
fn hwpunit(emu: i64) -> u32 {
    u32::try_from((emu / 127).max(0)).unwrap_or(u32::MAX)
}

/// 0xRRGGBB → HWP 색(0x00BBGGRR)
fn color_ref(rgb: u32) -> i64 {
    i64::from(((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF))
}

fn wrap_name(wrap: Wrap) -> &'static str {
    match wrap {
        Wrap::InFront => "InFrontOfText",
        Wrap::Behind => "BehindText",
        Wrap::Square => "Square",
        Wrap::TopAndBottom => "TopAndBottom",
        Wrap::Tight => "Tight",
    }
}

fn vert_rel(relative: Relative) -> &'static str {
    match relative {
        Relative::Page => "Paper",
        Relative::Margin => "Page",
        Relative::Column | Relative::Paragraph => "Para",
    }
}

fn horz_rel(relative: Relative) -> &'static str {
    match relative {
        Relative::Page => "Paper",
        Relative::Margin => "Page",
        Relative::Column => "Column",
        Relative::Paragraph => "Para",
    }
}

/// 넣지 못한 도형 수를 돌려준다.
pub(crate) fn apply(doc: &mut Rhwp, shapes: &[DocxShape]) -> Result<usize, HwpError> {
    let mut next = 0usize;
    let mut failed = 0usize;
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
                .position(|c| c == SHAPE_MARK);
            let Some(offset) = offset else { break };
            doc.delete_text_native(0, para, offset, 1)?;
            let Some(shape) = shapes.get(next) else {
                continue;
            };
            next += 1;
            if insert(doc, para, offset, shape).is_err() {
                failed += 1;
            }
        }
    }
    Ok(failed + shapes.len().saturating_sub(next))
}

fn insert(doc: &mut Rhwp, para: usize, offset: usize, shape: &DocxShape) -> Result<(), HwpError> {
    let textbox = !shape.text.is_empty();
    let kind = match (textbox, shape.kind) {
        (true, _) => "textbox",
        (false, ShapeKind::Ellipse) => "ellipse",
        (false, ShapeKind::Line) => "line",
        (false, _) => "rectangle",
    };
    let (horizontal, vertical) = if shape.inline {
        (0, 0)
    } else {
        (hwpunit(shape.horizontal.1), hwpunit(shape.vertical.1))
    };
    let reply = doc.create_shape_control_native(
        0,
        para,
        offset,
        hwpunit(shape.width).max(1),
        hwpunit(shape.height).max(1),
        horizontal,
        vertical,
        shape.inline,
        wrap_name(shape.wrap),
        kind,
        shape.flip_h,
        shape.flip_v,
        &[],
    )?;
    let reply: Value = serde_json::from_str(&reply)
        .map_err(|_| HwpError::RenderError("shape insert result".into()))?;
    let index = |key: &str| {
        reply
            .get(key)
            .and_then(Value::as_u64)
            .map(|v| v as usize)
            .ok_or_else(|| HwpError::RenderError("shape insert result".into()))
    };
    let (para, control) = (index("paraIdx")?, index("controlIdx")?);

    let mut props = json!({
        "fillType": if shape.fill.is_some() { "solid" } else { "none" },
        "borderWidth": i64::from(hwpunit(shape.line_width)),
    });
    if let Some(fill) = shape.fill {
        props["fillBgColor"] = color_ref(fill).into();
    }
    match shape.line {
        Some(line) => props["borderColor"] = color_ref(line).into(),
        None => props["lineType"] = 0.into(),
    }
    if shape.kind == ShapeKind::RoundRectangle {
        props["roundRate"] = 20.into();
    }
    if !shape.inline {
        props["horzRelTo"] = horz_rel(shape.horizontal.0).into();
        props["vertRelTo"] = vert_rel(shape.vertical.0).into();
    }
    doc.set_shape_properties_native(0, para, control, &props.to_string())?;

    for (index, text) in shape.text.iter().enumerate() {
        if index > 0 {
            let previous = shape.text[index - 1].chars().count();
            doc.split_paragraph_in_cell_native(0, para, control, 0, index - 1, previous, None)?;
        }
        if !text.is_empty() {
            doc.insert_text_in_cell_native(0, para, control, 0, index, 0, text)?;
        }
    }
    Ok(())
}
