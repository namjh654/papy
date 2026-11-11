//! DOCX 에서 읽은 도형을 rhwp 문서에 넣는다(본문 HTML 을 붙여넣은 뒤).
//!
//! 본문 문단의 [`papy_office::SHAPE_MARK`] 자리마다 그 글자를 지우고 도형을 만든다(문서 순서 = 목록 순서).

use papy_office::{
    CELL_SHAPE_MARK, DocxShape, Relative, SHAPE_MARK, ShapeImage, ShapeKind, Wrap, pixel_size,
};
use std::collections::VecDeque;

use rhwp::HwpError;
use rhwp::model::control::Control;
use rhwp::model::paragraph::Paragraph;
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
        // rhwp 의 "빽빽하게"는 둘러싼 문단 높이를 잘못 재어 다음 문단과 겹친다. 그림 상자는 어울림과 같은 모양이라 어울림으로 둔다.
        Wrap::Tight => "Square",
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

/// 본문 도형 자리 표시마다 그림을 종이 기준으로 옮겨 놓을 세로 자리를 잰다([`paper_vertical`], 도형 차례대로).
///
/// 붙여넣은 바로 뒤, 일괄 편집을 시작하기 전에 부른다. 일괄 편집 중에는 문단을 고쳐도 쪽을 다시 짜지 않아, 그 뒤
/// 커서 자리를 물으면 낡은 조판으로 잰다. rhwp 0.9 는 글이 바뀐 문단을 예전 조판 계획으로 그리다 멈췄다.
pub(crate) fn measure(doc: &Rhwp, shapes: &[DocxShape]) -> Vec<Option<i64>> {
    let mut papers = Vec::with_capacity(shapes.len());
    let Some(section) = doc.document().sections.first() else {
        return papers;
    };
    for (para, paragraph) in section.paragraphs.iter().enumerate() {
        let marks: Vec<usize> = paragraph
            .text
            .chars()
            .enumerate()
            .filter(|&(_, c)| c == SHAPE_MARK)
            .map(|(offset, _)| offset)
            .collect();
        for offset in marks {
            let paper = shapes
                .get(papers.len())
                .and_then(|shape| paper_vertical(doc, para, offset, shape));
            papers.push(paper);
        }
    }
    papers
}

/// 넣지 못한 도형 수를 돌려준다. `papers` 는 [`measure`] 결과.
pub(crate) fn apply(
    doc: &mut Rhwp,
    shapes: &[DocxShape],
    papers: &[Option<i64>],
) -> Result<usize, HwpError> {
    let mut next = 0usize;
    let mut failed = 0usize;
    let paragraph_count = doc
        .document()
        .sections
        .first()
        .map_or(0, |s| s.paragraphs.len());
    for para in 0..paragraph_count {
        let mut inline = false;
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
            let paper = papers.get(next).copied().flatten();
            next += 1;
            if insert(doc, para, offset, shape, paper).is_err() {
                failed += 1;
            } else {
                // 그림은 그림 넣기 경로가 줄을 맞춘다(다시 나누면 큰 그림 문단이 빈 쪽을 만들었다, Bug47731.doc).
                inline |= shape.inline && shape.image.is_none();
            }
        }
        // rhwp 는 도형을 넣고 글줄을 다시 나누지 않아 글자처럼 놓은 도형 너비·높이가 줄에 들지 않았다
        // (줄이 단 밖으로 넘쳤다). 한 글자 넣고 지워 글자 편집 경로로 다시 나눈다.
        if inline {
            doc.insert_text_native(0, para, 0, " ")?;
            doc.delete_text_native(0, para, 0, 1)?;
        }
    }
    Ok(failed + shapes.len().saturating_sub(next))
}

/// 도형들을 만들어 컨트롤로 돌려준다(차례대로, 못 만든 것은 None). rhwp 는 칸·머리말 안에 도형을 만들지 못해서,
/// 도형마다 본문 끝에 빈 문단을 더해 그 안에 만든 뒤 한꺼번에 떼어 낸다(본문은 전과 같다).
pub(crate) fn build(
    doc: &mut Rhwp,
    shapes: &[DocxShape],
) -> Result<VecDeque<Option<Control>>, HwpError> {
    if shapes.is_empty() {
        return Ok(VecDeque::new());
    }
    let start = doc.document().sections[0].paragraphs.len();
    for (index, shape) in shapes.iter().enumerate() {
        doc.insert_paragraph_native(0, start + index)?;
        // 못 만든 도형은 그 문단에 컨트롤이 없어 아래에서 None 이 된다.
        // 잠깐 쓰는 문단이라 종이 기준으로 바꾸지 않는다(본문 끝 문단 자리는 뜻이 없고, 일괄 편집 중이라 낡은 조판으로 잰다).
        let _ = insert(doc, start + index, 0, shape, None);
    }
    let mut document = doc.document().clone();
    let section = &mut document.sections[0];
    let controls = section
        .paragraphs
        .drain(start..)
        .map(|mut paragraph| {
            let at = paragraph
                .controls
                .iter()
                .rposition(|c| matches!(c, Control::Shape(_) | Control::Picture(_)))?;
            Some(paragraph.controls.remove(at))
        })
        .collect();
    section.raw_stream = None;
    doc.set_document(document);
    Ok(controls)
}

/// 문단들(칸 안 포함)의 `mark` 자리마다 `controls` 를 차례로 넣는다. `cells_only` 면 칸 안 문단만 본다.
/// 넣은 수를 돌려준다.
pub(crate) fn place(
    paragraphs: &mut [Paragraph],
    controls: &mut VecDeque<Option<Control>>,
    mark: char,
    cells_only: bool,
) -> usize {
    fn walk(
        paragraphs: &mut [Paragraph],
        controls: &mut VecDeque<Option<Control>>,
        mark: char,
        cells_only: bool,
        placed: &mut usize,
        depth: usize,
    ) {
        if depth > 16 {
            return;
        }
        for paragraph in paragraphs {
            if depth > 0 || !cells_only {
                while let Some(offset) = paragraph.text.chars().position(|c| c == mark) {
                    paragraph.delete_text_at(offset, 1);
                    if let Some(Some(control)) = controls.pop_front() {
                        paragraph.insert_inline_control(offset, control);
                        *placed += 1;
                    }
                }
            }
            for control in &mut paragraph.controls {
                if let Control::Table(table) = control {
                    for cell in &mut table.cells {
                        walk(
                            &mut cell.paragraphs,
                            controls,
                            mark,
                            cells_only,
                            placed,
                            depth + 1,
                        );
                    }
                }
            }
        }
    }
    let mut placed = 0;
    walk(paragraphs, controls, mark, cells_only, &mut placed, 0);
    placed
}

/// 표 칸 문단의 [`CELL_SHAPE_MARK`] 자리에 도형을 넣는다(문서 순서 = 목록 순서). 넣지 못한 수를 돌려준다.
pub(crate) fn apply_in_cells(doc: &mut Rhwp, shapes: &[DocxShape]) -> Result<usize, HwpError> {
    if shapes.is_empty() {
        return Ok(0);
    }
    let mut controls = build(doc, shapes)?;
    let mut document = doc.document().clone();
    let section = &mut document.sections[0];
    let placed = place(
        &mut section.paragraphs,
        &mut controls,
        CELL_SHAPE_MARK,
        true,
    );
    section.raw_stream = None;
    doc.set_document(document);
    Ok(shapes.len().saturating_sub(placed))
}

/// `paper`: 그림을 종이 기준으로 옮겨 놓을 세로 자리(HWPUNIT, [`paper_vertical`]).
fn insert(
    doc: &mut Rhwp,
    para: usize,
    offset: usize,
    shape: &DocxShape,
    paper: Option<i64>,
) -> Result<(), HwpError> {
    if let Some(image) = &shape.image {
        return insert_picture(doc, para, offset, shape, image, paper);
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
        // 글자처럼 놓은 도형은 한글처럼 "위아래"(Word `wp:inline` 에는 배치가 없다)
        if shape.inline {
            "TopAndBottom"
        } else {
            wrap_name(shape.wrap)
        },
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
    if textbox {
        // 글상자 안 여백(EMU → HWPUNIT)과 글 세로 자리
        let [l, t, r, b] = shape.insets.map(|v| i64::from(hwpunit(v)).clamp(0, 0x7FFF));
        props["tbMarginLeft"] = l.into();
        props["tbMarginTop"] = t.into();
        props["tbMarginRight"] = r.into();
        props["tbMarginBottom"] = b.into();
        props["tbVerticalAlign"] = match shape.text_anchor {
            1 => "Center",
            2 => "Bottom",
            _ => "Top",
        }
        .into();
    }
    if !shape.inline {
        props["horzRelTo"] = horz_rel(shape.horizontal.0).into();
        props["vertRelTo"] = vert_rel(shape.vertical.0).into();
        // Word 정렬(`wp:align`) → 한글 가로·세로 정렬(가운데·오른쪽/아래)
        match shape.align.0 {
            1 => props["horzAlign"] = "Center".into(),
            2 | 4 => props["horzAlign"] = "Right".into(),
            _ => {}
        }
        match shape.align.1 {
            1 => props["vertAlign"] = "Center".into(),
            2 | 4 => props["vertAlign"] = "Bottom".into(),
            _ => {}
        }
    }
    doc.set_shape_properties_native(0, para, control, &props.to_string())?;

    // 글상자 내용 HTML(서식·그림·표): 못 넣으면 글자만 넣는다.
    if let Some(html) = shape.html.as_deref() {
        let saved = doc.document().clone();
        match fill_text_box(doc, para, control, html) {
            Ok(()) => return Ok(()),
            Err(_) => doc.set_document(saved),
        }
    }
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

/// 본문 문단 `para` 의 도형 `control`(글상자) 내용을 HTML 조각으로 채운다. 줄은 글상자 안 너비로 다시 나눈다.
fn fill_text_box(doc: &mut Rhwp, para: usize, control: usize, html: &str) -> Result<(), HwpError> {
    use rhwp::model::control::Control;
    let mut paragraphs = crate::fragment::paste(doc, html)?;
    let mut document = doc.document().clone();
    let missing = || HwpError::RenderError("글상자가 없습니다".into());
    let Some(Control::Shape(shape)) = document.sections[0]
        .paragraphs
        .get_mut(para)
        .and_then(|p| p.controls.get_mut(control))
    else {
        return Err(missing());
    };
    let width = shape.common().width;
    let text_box = shape
        .drawing_mut()
        .and_then(|d| d.text_box.as_mut())
        .ok_or_else(missing)?;
    let padding = u32::from(text_box.margin_left.max(0) as u16)
        + u32::from(text_box.margin_right.max(0) as u16);
    // 글상자 안 쪽 번호 필드 글자 → 쪽 번호 컨트롤. rhwp 는 머리말 문단의 필드 글자만 HWPX 쪽 번호로 쓴다
    // (머리말 묶음 안 글상자 쪽 번호가 HWPX 로 저장하면 빠졌다).
    page_number_controls(&mut paragraphs);
    doc.reflow_paragraphs_to_width(&mut paragraphs, width.saturating_sub(padding));
    text_box.paragraphs = paragraphs;
    document.sections[0].raw_stream = None;
    doc.set_document(document);
    Ok(())
}

/// 문단의 쪽 번호·전체 쪽 수 필드 글자를 쪽 번호 컨트롤로 바꾼다.
fn page_number_controls(paragraphs: &mut [Paragraph]) {
    use papy_office::{PAGE_COUNT_FIELD, PAGE_NUMBER_FIELD};
    use rhwp::model::control::{AutoNumber, AutoNumberType};
    for paragraph in paragraphs {
        while let Some((offset, c)) = paragraph
            .text
            .chars()
            .enumerate()
            .find(|(_, c)| matches!(*c, PAGE_NUMBER_FIELD | PAGE_COUNT_FIELD))
        {
            paragraph.delete_text_at(offset, 1);
            let number_type = if c == PAGE_COUNT_FIELD {
                AutoNumberType::TotalPage
            } else {
                AutoNumberType::Page
            };
            paragraph.insert_inline_control(
                offset,
                Control::AutoNumber(AutoNumber {
                    number_type,
                    number: 1,
                    assigned_number: 1,
                    ..AutoNumber::default()
                }),
            );
        }
    }
}

/// 차트 그림 해상도(쪽 크기 1 px 당 점 수). 확대해도 거칠지 않게 2배로 그린다.
const CHART_SCALE: f64 = 2.0;
/// EMU → CSS px(96 dpi)
const EMU_PER_PX: f64 = 9525.0;

/// 그림(묶음 안 그림·차트)을 도형 자리에 넣는다.
/// 문단 기준(세로)으로 떠 있는 그림을 종이 기준으로 옮겨 놓을 세로 자리(HWPUNIT). 옮기지 않으면 None.
/// 조판이 문단 글과 맞을 때(일괄 편집 전) 부른다.
fn paper_vertical(doc: &Rhwp, para: usize, offset: usize, shape: &DocxShape) -> Option<i64> {
    shape.image.as_ref()?;
    // 문단 기준(세로)으로 떠 있는 그림을 rhwp 는 이렇게 다룬다:
    // - 문단 기준 그대로 두면 그 문단 줄만 그림을 비켜 가고, 뒤 문단은 비키지 않는다(그림이 뒤 문단에 덮인다).
    //   글 앞·뒤 그림은 문단 줄 높이에 잘려 보이기도 한다.
    // - 종이 기준으로 바꾸면 뒤 문단은 비켜 가지만, 글이 둘러 흐르는 그림(어울림·위아래)은 제 문단 줄이 다음 문단과 겹친다.
    // 그래서 글 앞·뒤 그림, 또는 제 문단 밖으로 나가는 그림만 넣기 전에 문단 자리를 재서 종이 기준으로 바꾼다
    // (DOCX 를 연 뒤의 배치로 고정된다). 제 문단 안에 드는 둘러 흐르는 그림은 문단 기준으로 둔다.
    let cursor = |doc: &Rhwp, offset: usize| -> Option<(f64, f64, u64)> {
        let reply = doc.get_cursor_rect_native(0, para, offset).ok()?;
        let value = serde_json::from_str::<Value>(&reply).ok()?;
        Some((
            value.get("y")?.as_f64()?,
            value.get("height")?.as_f64()?,
            value.get("pageIndex")?.as_u64()?,
        ))
    };
    let fits_paragraph = || -> bool {
        let length = doc.document().sections[0].paragraphs[para]
            .text
            .chars()
            .count();
        let (Some(first), Some(last)) = (cursor(doc, 0), cursor(doc, length)) else {
            return false;
        };
        // 여러 쪽에 걸친 문단은 그 쪽 안에서 비켜 간다.
        if first.2 != last.2 {
            return true;
        }
        let paragraph_px = last.0 + last.1 - first.0;
        let bottom_px = (shape.vertical.1 + shape.height) as f64 / EMU_PER_PX;
        bottom_px <= paragraph_px + 1.0
    };
    match shape.vertical.0 {
        Relative::Paragraph | Relative::Column
            if !shape.inline
                && (matches!(shape.wrap, Wrap::InFront | Wrap::Behind) || !fits_paragraph()) =>
        {
            cursor(doc, offset).map(|(y_px, _, _)| {
                (y_px * 75.0).max(0.0) as i64 + i64::from(hwpunit(shape.vertical.1))
            })
        }
        _ => None,
    }
}

fn insert_picture(
    doc: &mut Rhwp,
    para: usize,
    offset: usize,
    shape: &DocxShape,
    image: &ShapeImage,
    paper: Option<i64>,
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
    let vertical = paper.map(|y| ("Paper", y)).unwrap_or((
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
    if shape.crop.iter().any(|v| *v > 0) {
        // 자르기는 그림 전체 좌표(`imgDim`, 넣을 때 픽셀 × 75 HWPUNIT)의 보이는 사각형이다.
        // rhwp 속성 API(`cropLeft`…)는 개체 크기를 기준으로 삼아 맞지 않으므로 모델에 바로 쓴다.
        // 배치는 바뀌지 않고, 바로 뒤의 속성 설정이 그리기 캐시를 비운다.
        if let Some(Control::Picture(picture)) = doc.document_mut().sections[0]
            .paragraphs
            .get_mut(para)
            .and_then(|p| p.controls.get_mut(control))
        {
            let (width, height) = picture.img_dim;
            let cut = |part: u32, whole: u32| {
                (f64::from(part) / 100_000.0 * f64::from(whole)).round() as i32
            };
            let whole = |v: u32| i32::try_from(v).unwrap_or(i32::MAX);
            picture.crop = rhwp::model::image::CropInfo {
                left: cut(shape.crop[0], width),
                top: cut(shape.crop[1], height),
                right: whole(width) - cut(shape.crop[2], width),
                bottom: whole(height) - cut(shape.crop[3], height),
            };
        }
    }
    doc.set_picture_properties_native(0, para, control, &props.to_string())?;
    Ok(())
}
