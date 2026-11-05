//! DOCX 에서 읽은 도형을 rhwp 문서에 넣는다(본문 HTML 을 붙여넣은 뒤).
//!
//! 본문 문단의 [`papy_office::SHAPE_MARK`] 자리마다 그 글자를 지우고 도형을 만든다(문서 순서 = 목록 순서).

use papy_office::{DocxShape, Relative, SHAPE_MARK, ShapeImage, ShapeKind, Wrap};
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
    if let Some(image) = &shape.image {
        return insert_picture(doc, para, offset, shape, image);
    }
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

/// 차트 그림 해상도(쪽 크기 1 px 당 점 수). 확대해도 거칠지 않게 2배로 그린다.
const CHART_SCALE: f64 = 2.0;
/// EMU → CSS px(96 dpi)
const EMU_PER_PX: f64 = 9525.0;

/// 그림(묶음 안 그림·차트)을 도형 자리에 넣는다.
fn insert_picture(
    doc: &mut Rhwp,
    para: usize,
    offset: usize,
    shape: &DocxShape,
    image: &ShapeImage,
) -> Result<(), HwpError> {
    let (bytes, extension, natural) = match image {
        ShapeImage::Raster(bytes) => {
            let extension = match bytes.first() {
                Some(0x89) => "png",
                Some(0xFF) => "jpg",
                Some(b'G') => "gif",
                _ => "bmp",
            };
            // rhwp 는 원래 픽셀 크기로 자르기 범위를 잡는다. 모르면 보이는 크기(96 DPI)로 둔다.
            let natural = pixel_size(bytes).unwrap_or((
                (shape.width as f64 / EMU_PER_PX).round().max(1.0) as u32,
                (shape.height as f64 / EMU_PER_PX).round().max(1.0) as u32,
            ));
            (bytes.clone(), extension, natural)
        }
        ShapeImage::Svg(svg) => {
            let width = (shape.width as f64 / EMU_PER_PX * CHART_SCALE)
                .round()
                .clamp(1.0, 4096.0);
            let height = (shape.height as f64 / EMU_PER_PX * CHART_SCALE)
                .round()
                .clamp(1.0, 4096.0);
            let png = crate::svg_to_png(svg, width as u32, height as u32)
                .map_err(|_| HwpError::RenderError("chart image".into()))?;
            (png, "png", (width as u32, height as u32))
        }
    };
    // rhwp 는 문단 기준(세로)으로 떠 있는 그림을 그 문단 줄 높이로 잘라 그린다. 넣기 전에 문단 자리를 재서
    // 종이 기준 위치로 바꿔 넣는다(DOCX 를 연 뒤의 배치로 고정된다).
    let vertical = match shape.vertical.0 {
        Relative::Paragraph | Relative::Column if !shape.inline => doc
            .get_cursor_rect_native(0, para, offset)
            .ok()
            .and_then(|reply| serde_json::from_str::<Value>(&reply).ok())
            .and_then(|value| value.get("y")?.as_f64())
            .map(|y_px| {
                (
                    "Paper",
                    (y_px * 75.0).max(0.0) as i64 + i64::from(hwpunit(shape.vertical.1)),
                )
            }),
        _ => None,
    }
    .unwrap_or((
        vert_rel(shape.vertical.0),
        i64::from(hwpunit(shape.vertical.1)),
    ));
    let reply = doc.insert_picture_native(
        0,
        para,
        offset,
        &[],
        &bytes,
        hwpunit(shape.width).max(1),
        hwpunit(shape.height).max(1),
        natural.0,
        natural.1,
        extension,
        "",
        None,
        None,
    )?;
    let reply: Value = serde_json::from_str(&reply)
        .map_err(|_| HwpError::RenderError("picture insert result".into()))?;
    let index = |key: &str| {
        reply
            .get(key)
            .and_then(Value::as_u64)
            .map(|v| v as usize)
            .ok_or_else(|| HwpError::RenderError("picture insert result".into()))
    };
    let (para, control) = (index("paraIdx")?, index("controlIdx")?);
    let props = if shape.inline {
        json!({ "treatAsChar": true })
    } else {
        json!({
            "treatAsChar": false,
            "horzRelTo": horz_rel(shape.horizontal.0),
            "vertRelTo": vertical.0,
            "horzOffset": i64::from(hwpunit(shape.horizontal.1)),
            "vertOffset": vertical.1,
            "textWrap": wrap_name(shape.wrap),
        })
    };
    doc.set_picture_properties_native(0, para, control, &props.to_string())?;
    Ok(())
}

/// 그림 파일의 픽셀 크기(PNG·GIF·BMP·JPEG 머리).
pub(crate) fn pixel_size(bytes: &[u8]) -> Option<(u32, u32)> {
    let be16 = |i: usize| {
        Some(u32::from(u16::from_be_bytes([
            *bytes.get(i)?,
            *bytes.get(i + 1)?,
        ])))
    };
    let le16 = |i: usize| {
        Some(u32::from(u16::from_le_bytes([
            *bytes.get(i)?,
            *bytes.get(i + 1)?,
        ])))
    };
    let be32 = |i: usize| Some(u32::from_be_bytes(bytes.get(i..i + 4)?.try_into().ok()?));
    let le32 =
        |i: usize| Some(i32::from_le_bytes(bytes.get(i..i + 4)?.try_into().ok()?).unsigned_abs());
    let size = if bytes.starts_with(b"\x89PNG") {
        (be32(16)?, be32(20)?)
    } else if bytes.starts_with(b"GIF8") {
        (le16(6)?, le16(8)?)
    } else if bytes.starts_with(b"BM") {
        (le32(18)?, le32(22)?)
    } else if bytes.starts_with(&[0xFF, 0xD8]) {
        // SOFn 표지를 찾는다.
        let mut i = 2;
        loop {
            if *bytes.get(i)? != 0xFF {
                return None;
            }
            let marker = *bytes.get(i + 1)?;
            let length = be16(i + 2)? as usize;
            if matches!(marker, 0xC0..=0xCF) && !matches!(marker, 0xC4 | 0xC8 | 0xCC) {
                break (be16(i + 7)?, be16(i + 5)?);
            }
            i += 2 + length;
        }
    } else {
        return None;
    };
    (size.0 > 0 && size.1 > 0).then_some(size)
}
