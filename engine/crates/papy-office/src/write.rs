//! rhwp 문서 → DOCX. 문단·글자 서식(글꼴·크기·굵게·기울임·밑줄·취소선·색)·정렬·줄 간격·표(병합 포함)·
//! 쪽 크기와 여백·그림(PNG·JPEG·GIF·BMP, 글자처럼 놓기)을 옮긴다. 단위: HWPUNIT(1/7200 인치) → twip(1/1440 인치) 은 5 로,
//! EMU(1/914400 인치) 는 127 을 곱한다.

use std::collections::HashMap;
use std::fmt::Write as _;
use std::io::{Cursor, Write};

use papy_model::{Error, ErrorCode, Result};
use rhwp::model::bin_data::BinDataContent;
use rhwp::model::control::Control;
use rhwp::model::document::{DocInfo, Document};
use rhwp::model::footnote::Footnote;
use rhwp::model::header_footer::HeaderFooterApply;
use rhwp::model::image::Picture;
use rhwp::model::paragraph::Paragraph;
use rhwp::model::shape::{HorzRelTo, ShapeObject, TextWrap, VertRelTo};
use rhwp::model::style::FillType;
use rhwp::model::style::{Alignment, HeadType, LineSpacingType, UnderlineType};
use rhwp::model::table::Table;

/// 표 중첩 상한(순환·과도한 중첩 방어).
const MAX_DEPTH: usize = 16;

/// 문서에 넣을 그림 파일(word/media/…)과 관계 id.
struct Media<'a> {
    contents: &'a [BinDataContent],
    /// (bin_data_id, 파일 이름, 확장자, 바이트)
    files: Vec<(u16, String, &'static str, Vec<u8>)>,
    /// 그림 개체 번호(docPr id)
    next_object: u32,
    /// 각주 본문 XML(`<w:footnote>` 안쪽). 각주 id 는 순서 + 1.
    footnotes: Vec<String>,
}

impl Media<'_> {
    /// 그림을 등록하고 관계 id 를 돌려준다. 같은 그림은 한 번만 넣는다. Word 가 못 읽는 형식이면 None.
    fn add(&mut self, bin_data_id: u16) -> Option<String> {
        if let Some(index) = self.files.iter().position(|file| file.0 == bin_data_id) {
            return Some(format!("rIdImg{}", index + 1));
        }
        let content = self
            .contents
            .iter()
            .find(|content| content.id == bin_data_id)?;
        let bytes = content.data.load();
        let extension = crate::read::image_mime(&bytes).map(|mime| match mime {
            "image/png" => "png",
            "image/jpeg" => "jpeg",
            "image/gif" => "gif",
            _ => "bmp",
        })?;
        let index = self.files.len() + 1;
        self.files.push((
            bin_data_id,
            format!("image{index}.{extension}"),
            extension,
            bytes,
        ));
        Some(format!("rIdImg{index}"))
    }
}

/// HWPUNIT → EMU
fn emu(hwpunit: u32) -> u64 {
    u64::from(hwpunit) * 127
}

fn write_picture(out: &mut String, media: &mut Media, picture: &Picture) {
    let Some(rel) = media.add(picture.image_attr.bin_data_id) else {
        return;
    };
    media.next_object += 1;
    let id = media.next_object;
    let (cx, cy) = (
        emu(picture.common.width.max(1)),
        emu(picture.common.height.max(1)),
    );
    let _ = write!(
        out,
        r#"<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="{cx}" cy="{cy}"/><wp:docPr id="{id}" name="Picture {id}"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="{id}" name="Picture {id}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="{rel}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"#
    );
}

/// 도형(사각형·타원·선, 글상자 포함) → `wps:wsp`. 다른 도형(묶음·다각형 등)은 옮기지 않는다.
fn write_shape(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    shape: &ShapeObject,
    depth: usize,
) -> Result<()> {
    let (geometry, flip_h, flip_v) = match shape {
        ShapeObject::Rectangle(rect) if rect.round_rate > 0 => ("roundRect", false, false),
        ShapeObject::Rectangle(_) => ("rect", false, false),
        ShapeObject::Ellipse(_) => ("ellipse", false, false),
        ShapeObject::Line(line) => ("line", line.start.x > line.end.x, line.start.y > line.end.y),
        _ => return Ok(()),
    };
    let Some(drawing) = shape.drawing() else {
        return Ok(());
    };
    let common = shape.common();
    media.next_object += 1;
    let id = media.next_object;
    let (cx, cy) = (emu(common.width), emu(common.height));
    let solid = |color: u32| {
        rgb(color).map_or_else(
            || "<a:noFill/>".to_owned(),
            |hex| format!(r#"<a:solidFill><a:srgbClr val="{hex}"/></a:solidFill>"#),
        )
    };
    let fill = match (&drawing.fill.fill_type, &drawing.fill.solid) {
        (FillType::Solid, Some(solid_fill)) if geometry != "line" => {
            solid(solid_fill.background_color)
        }
        _ => "<a:noFill/>".to_owned(),
    };
    let border = &drawing.border_line;
    let line = if border.attr & 0x3F == 0 {
        "<a:ln><a:noFill/></a:ln>".to_owned()
    } else {
        format!(
            r#"<a:ln w="{}">{}</a:ln>"#,
            emu(u32::try_from(border.width.max(0)).unwrap_or(0)),
            solid(border.color)
        )
    };
    let flips = format!(
        "{}{}",
        if flip_h { r#" flipH="1""# } else { "" },
        if flip_v { r#" flipV="1""# } else { "" }
    );
    let mut text = String::new();
    if let Some(text_box) = &drawing.text_box {
        text.push_str("<wps:txbx><w:txbxContent>");
        for paragraph in &text_box.paragraphs {
            write_paragraph(&mut text, info, media, paragraph, depth + 1)?;
        }
        if text_box.paragraphs.is_empty() {
            text.push_str("<w:p/>");
        }
        text.push_str("</w:txbxContent></wps:txbx>");
    }
    let text_box_flag = if drawing.text_box.is_some() {
        r#" txBox="1""#
    } else {
        ""
    };
    let graphic = format!(
        r#"<wp:docPr id="{id}" name="Shape {id}"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"><wps:wsp><wps:cNvSpPr{text_box_flag}/><wps:spPr><a:xfrm{flips}><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="{geometry}"><a:avLst/></a:prstGeom>{fill}{line}</wps:spPr>{text}<wps:bodyPr/></wps:wsp></a:graphicData></a:graphic>"#
    );
    out.push_str("<w:r><w:drawing>");
    if common.treat_as_char {
        let _ = write!(
            out,
            r#"<wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="{cx}" cy="{cy}"/>{graphic}</wp:inline>"#
        );
    } else {
        let horizontal = match common.horz_rel_to {
            HorzRelTo::Paper => "page",
            HorzRelTo::Page => "margin",
            HorzRelTo::Column => "column",
            _ => "character",
        };
        let vertical = match common.vert_rel_to {
            VertRelTo::Paper => "page",
            VertRelTo::Page => "margin",
            _ => "paragraph",
        };
        let behind = matches!(common.text_wrap, TextWrap::BehindText);
        let wrap = match common.text_wrap {
            TextWrap::Square => r#"<wp:wrapSquare wrapText="bothSides"/>"#,
            TextWrap::TopAndBottom => "<wp:wrapTopAndBottom/>",
            TextWrap::Tight | TextWrap::Through => {
                r#"<wp:wrapTight wrapText="bothSides"><wp:wrapPolygon edited="0"><wp:start x="0" y="0"/><wp:lineTo x="0" y="21600"/><wp:lineTo x="21600" y="21600"/><wp:lineTo x="21600" y="0"/><wp:lineTo x="0" y="0"/></wp:wrapPolygon></wp:wrapTight>"#
            }
            _ => "<wp:wrapNone/>",
        };
        let _ = write!(
            out,
            r#"<wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="{}" behindDoc="{}" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="{horizontal}"><wp:posOffset>{}</wp:posOffset></wp:positionH><wp:positionV relativeFrom="{vertical}"><wp:posOffset>{}</wp:posOffset></wp:positionV><wp:extent cx="{cx}" cy="{cy}"/><wp:effectExtent l="0" t="0" r="0" b="0"/>{wrap}{graphic}</wp:anchor>"#,
            u32::try_from(common.z_order.max(0)).unwrap_or(0) + 1,
            u8::from(behind),
            emu(common.horizontal_offset),
            emu(common.vertical_offset),
        );
    }
    out.push_str("</w:drawing></w:r>");
    Ok(())
}

fn xml_escape(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for ch in text.chars() {
        match ch {
            '&' => out.push_str("&amp;"),
            '<' => out.push_str("&lt;"),
            '>' => out.push_str("&gt;"),
            '"' => out.push_str("&quot;"),
            // XML 1.0 에 넣을 수 없는 제어 문자는 버린다.
            c if (c as u32) < 0x20 && !matches!(c, '\t' | '\n' | '\r') => {}
            c => out.push(c),
        }
    }
    out
}

fn twips(hwpunit: u32) -> u32 {
    hwpunit / 5
}

/// HWP 색(0x00BBGGRR) → `RRGGBB`. 투명(상위 바이트 사용)이면 없음.
fn rgb(color: u32) -> Option<String> {
    (color >> 24 == 0).then(|| {
        format!(
            "{:02X}{:02X}{:02X}",
            color & 0xFF,
            (color >> 8) & 0xFF,
            (color >> 16) & 0xFF
        )
    })
}

fn font_name(info: &DocInfo, lang: usize, id: u16) -> Option<&str> {
    info.font_faces
        .get(lang)?
        .get(id as usize)
        .map(|font| font.name.as_str())
        .filter(|name| !name.is_empty())
}

fn run_props(info: &DocInfo, shape_id: u32) -> String {
    let Some(shape) = info.char_shapes.get(shape_id as usize) else {
        return String::new();
    };
    let mut props = String::from("<w:rPr>");
    // 0: 한글, 1: 영어 글꼴 묶음
    let east_asia = font_name(info, 0, shape.font_ids[0]);
    let latin = font_name(info, 1, shape.font_ids[1]).or(east_asia);
    if east_asia.is_some() || latin.is_some() {
        let latin = xml_escape(latin.unwrap_or(""));
        let east_asia = xml_escape(east_asia.unwrap_or(""));
        let _ = write!(
            props,
            r#"<w:rFonts w:ascii="{latin}" w:hAnsi="{latin}" w:eastAsia="{east_asia}"/>"#
        );
    }
    if shape.bold {
        props.push_str("<w:b/>");
    }
    if shape.italic {
        props.push_str("<w:i/>");
    }
    if shape.strikethrough {
        props.push_str("<w:strike/>");
    }
    if let Some(color) = rgb(shape.text_color) {
        let _ = write!(props, r#"<w:color w:val="{color}"/>"#);
    }
    if shape.base_size > 0 {
        // 1/100 pt → 반 포인트
        let half_points = (shape.base_size + 25) / 50;
        let _ = write!(
            props,
            r#"<w:sz w:val="{half_points}"/><w:szCs w:val="{half_points}"/>"#
        );
    }
    if shape.underline_type != UnderlineType::None {
        props.push_str(r#"<w:u w:val="single"/>"#);
    }
    if shape.superscript {
        props.push_str(r#"<w:vertAlign w:val="superscript"/>"#);
    } else if shape.subscript {
        props.push_str(r#"<w:vertAlign w:val="subscript"/>"#);
    }
    props.push_str("</w:rPr>");
    props
}

fn para_props(info: &DocInfo, paragraph: &Paragraph) -> String {
    let Some(shape) = info.para_shapes.get(paragraph.para_shape_id as usize) else {
        return String::new();
    };
    let jc = match shape.alignment {
        Alignment::Left => "left",
        Alignment::Right => "right",
        Alignment::Center => "center",
        Alignment::Distribute => "distribute",
        Alignment::Justify | Alignment::Split => "both",
    };
    let mut props = format!(r#"<w:pPr><w:jc w:val="{jc}"/>"#);
    if shape.line_spacing_type == LineSpacingType::Percent && shape.line_spacing > 0 {
        // 100% = 240
        let line = shape.line_spacing * 240 / 100;
        let _ = write!(props, r#"<w:spacing w:line="{line}" w:lineRule="auto"/>"#);
    }
    let (left, right) = (
        shape.margin_left.max(0) as u32,
        shape.margin_right.max(0) as u32,
    );
    if left > 0 || right > 0 {
        // 문단 여백은 HWPUNIT 의 2배 단위로 저장된다(rhwp ParaShape 규약).
        let _ = write!(
            props,
            r#"<w:ind w:left="{}" w:right="{}"/>"#,
            twips(left / 2),
            twips(right / 2)
        );
    }
    // 한글 개요 문단 → Word 개요 수준(탐색 창·목차에 나온다).
    if shape.head_type == HeadType::Outline {
        let _ = write!(
            props,
            r#"<w:outlineLvl w:val="{}"/>"#,
            shape.para_level.min(8)
        );
    }
    props.push_str("</w:pPr>");
    props
}

/// 각주 참조 런. 각주 본문을 모아 두고 번호(id)를 매긴다.
fn write_footnote(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    footnote: &Footnote,
    depth: usize,
) -> Result<()> {
    if depth > MAX_DEPTH {
        return Ok(());
    }
    // 자리를 먼저 잡아 각주 안 각주가 있어도 번호가 꼬이지 않게 한다.
    media.footnotes.push(String::new());
    let id = media.footnotes.len();
    let mut body = String::new();
    for (index, paragraph) in footnote.paragraphs.iter().enumerate() {
        let prefix = if index == 0 {
            r#"<w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:footnoteRef/></w:r><w:r><w:t xml:space="preserve"> </w:t></w:r>"#
        } else {
            ""
        };
        write_paragraph_with(&mut body, info, media, paragraph, depth + 1, prefix)?;
    }
    if footnote.paragraphs.is_empty() {
        body.push_str(r#"<w:p><w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:footnoteRef/></w:r></w:p>"#);
    }
    media.footnotes[id - 1] = body;
    let _ = write!(
        out,
        r#"<w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:footnoteReference w:id="{id}"/></w:r>"#
    );
    Ok(())
}

/// 문단 안 자리에 놓이는 개체: 각주 참조, 그림, 도형.
fn write_inline(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    control: &Control,
    depth: usize,
) -> Result<()> {
    match control {
        Control::Footnote(footnote) => write_footnote(out, info, media, footnote, depth)?,
        Control::Picture(picture) => write_picture(out, media, picture),
        Control::Shape(shape) if depth < MAX_DEPTH => write_shape(out, info, media, shape, depth)?,
        _ => {}
    }
    Ok(())
}

/// `body[start..]` 의 첫 문단 속성에 개요 수준(1부터)을 넣는다. 이미 있으면 그대로 둔다.
fn set_outline_level(body: &mut String, start: usize, level: u8) {
    let value = level.saturating_sub(1).min(8);
    let tag = format!(r#"<w:outlineLvl w:val="{value}"/>"#);
    let head = start + "<w:p>".len();
    if !body[start..].starts_with("<w:p>") {
        return;
    }
    if !body[head..].starts_with("<w:pPr>") {
        body.insert_str(head, &format!("<w:pPr>{tag}</w:pPr>"));
        return;
    }
    let Some(end) = body[head..].find("</w:pPr>").map(|i| head + i) else {
        return;
    };
    if !body[head..end].contains("<w:outlineLvl") {
        body.insert_str(end, &tag);
    }
}

/// 문단 하나와 그 안의 표.
fn write_paragraph(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    paragraph: &Paragraph,
    depth: usize,
) -> Result<()> {
    write_paragraph_with(out, info, media, paragraph, depth, "")
}

/// 문단 하나와 그 안의 표. `prefix` 는 문단 속성 바로 뒤에 넣는 런(각주 번호 표시 등).
fn write_paragraph_with(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    paragraph: &Paragraph,
    depth: usize,
    prefix: &str,
) -> Result<()> {
    out.push_str("<w:p>");
    out.push_str(&para_props(info, paragraph));
    out.push_str(prefix);
    let chars: Vec<char> = paragraph.text.chars().collect();
    // 각주·그림·도형 자리(글자 위치) — 그 앞에서 런을 끊고 그 자리에 넣는다(표는 문단 뒤).
    let mut notes: Vec<(usize, &Control)> = paragraph
        .control_text_positions()
        .into_iter()
        .zip(&paragraph.controls)
        .filter(|(_, control)| {
            matches!(
                control,
                Control::Footnote(_) | Control::Picture(_) | Control::Shape(_)
            )
        })
        .collect();
    // 같은 자리면 문서 순서를 지킨다(안정 정렬).
    notes.sort_by_key(|(position, _)| *position);
    let mut notes = notes.into_iter().peekable();
    let mut start = 0;
    while start < chars.len() {
        while let Some((_, control)) = notes.next_if(|(position, _)| *position <= start) {
            write_inline(out, info, media, control, depth)?;
        }
        let next_note = notes.peek().map_or(usize::MAX, |(position, _)| *position);
        let shape_of = |index: usize| {
            let raw = paragraph
                .char_offsets
                .get(index)
                .copied()
                .unwrap_or(index as u32);
            paragraph
                .char_shapes
                .iter()
                .rev()
                .find(|run| run.start_pos <= raw)
                .map(|run| run.char_shape_id)
                .unwrap_or(0)
        };
        let shape = shape_of(start);
        let mut end = start + 1;
        while end < chars.len() && end < next_note && shape_of(end) == shape {
            end += 1;
        }
        out.push_str("<w:r>");
        out.push_str(&run_props(info, shape));
        let mut text = String::new();
        let flush = |out: &mut String, text: &mut String| {
            if !text.is_empty() {
                let _ = write!(
                    out,
                    r#"<w:t xml:space="preserve">{}</w:t>"#,
                    xml_escape(text)
                );
                text.clear();
            }
        };
        for ch in &chars[start..end] {
            match ch {
                '\t' => {
                    flush(out, &mut text);
                    out.push_str("<w:tab/>");
                }
                '\n' | '\r' => {
                    flush(out, &mut text);
                    out.push_str("<w:br/>");
                }
                c => text.push(*c),
            }
        }
        flush(out, &mut text);
        out.push_str("</w:r>");
        start = end;
    }
    for (_, control) in notes {
        write_inline(out, info, media, control, depth)?;
    }
    out.push_str("</w:p>");
    for control in &paragraph.controls {
        if let Control::Table(table) = control {
            write_table(out, info, media, table, depth + 1)?;
            // Word 는 표 바로 뒤에 문단이 있어야 한다.
            out.push_str("<w:p/>");
        }
    }
    Ok(())
}

fn write_table(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    table: &Table,
    depth: usize,
) -> Result<()> {
    if depth > MAX_DEPTH {
        return Err(Error::new(
            ErrorCode::LimitExceeded,
            "tables are nested too deeply",
        ));
    }
    let (rows, cols) = (table.row_count as usize, table.col_count as usize);
    if rows == 0 || cols == 0 {
        return Ok(());
    }
    // 열 너비: 한 칸짜리 셀에서 가져온다(없으면 고르게).
    let mut widths = vec![0u32; cols];
    for cell in &table.cells {
        if cell.col_span == 1 && (cell.col as usize) < cols {
            widths[cell.col as usize] = widths[cell.col as usize].max(twips(cell.width));
        }
    }
    let fallback = widths
        .iter()
        .copied()
        .max()
        .filter(|w| *w > 0)
        .unwrap_or(2000);
    for width in &mut widths {
        if *width == 0 {
            *width = fallback;
        }
    }
    out.push_str(
        r#"<w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/><w:tblBorders><w:top w:val="single" w:sz="4"/><w:left w:val="single" w:sz="4"/><w:bottom w:val="single" w:sz="4"/><w:right w:val="single" w:sz="4"/><w:insideH w:val="single" w:sz="4"/><w:insideV w:val="single" w:sz="4"/></w:tblBorders></w:tblPr><w:tblGrid>"#,
    );
    for width in &widths {
        let _ = write!(out, r#"<w:gridCol w:w="{width}"/>"#);
    }
    out.push_str("</w:tblGrid>");

    // 격자 칸마다 그 칸을 시작하는 셀, 또는 위에서 세로 병합된 셀.
    let start_at = |row: usize, col: usize| {
        table
            .cells
            .iter()
            .find(|cell| cell.row as usize == row && cell.col as usize == col)
    };
    let covered_from_above = |row: usize, col: usize| {
        table.cells.iter().find(|cell| {
            let (r, c) = (cell.row as usize, cell.col as usize);
            c == col && r < row && row < r + cell.row_span.max(1) as usize
        })
    };
    for row in 0..rows {
        out.push_str("<w:tr>");
        let mut col = 0;
        while col < cols {
            if let Some(cell) = start_at(row, col) {
                let span = (cell.col_span.max(1) as usize).min(cols - col);
                out.push_str("<w:tc><w:tcPr>");
                let width: u32 = widths[col..col + span].iter().sum();
                let _ = write!(out, r#"<w:tcW w:w="{width}" w:type="dxa"/>"#);
                if span > 1 {
                    let _ = write!(out, r#"<w:gridSpan w:val="{span}"/>"#);
                }
                if cell.row_span > 1 {
                    out.push_str(r#"<w:vMerge w:val="restart"/>"#);
                }
                out.push_str("</w:tcPr>");
                if cell.paragraphs.is_empty() {
                    out.push_str("<w:p/>");
                }
                for paragraph in &cell.paragraphs {
                    write_paragraph(out, info, media, paragraph, depth)?;
                }
                out.push_str("</w:tc>");
                col += span;
            } else if let Some(cell) = covered_from_above(row, col) {
                let span = (cell.col_span.max(1) as usize).min(cols - col);
                out.push_str("<w:tc><w:tcPr>");
                if span > 1 {
                    let _ = write!(out, r#"<w:gridSpan w:val="{span}"/>"#);
                }
                out.push_str("<w:vMerge/></w:tcPr><w:p/></w:tc>");
                col += span;
            } else {
                // 가로 병합 안쪽 등 빈 칸: Word 가 열 수 없는 표가 되지 않게 빈 셀로 채운다.
                out.push_str("<w:tc><w:p/></w:tc>");
                col += 1;
            }
        }
        out.push_str("</w:tr>");
    }
    out.push_str("</w:tbl>");
    Ok(())
}

fn section_props(document: &Document, references: &str) -> String {
    let Some(page) = document.sections.first().map(|s| &s.section_def.page_def) else {
        return String::new();
    };
    let (mut width, mut height) = (twips(page.width), twips(page.height));
    if page.landscape && width < height {
        std::mem::swap(&mut width, &mut height);
    }
    if width == 0 || height == 0 {
        return String::new();
    }
    let orient = if width > height {
        r#" w:orient="landscape""#
    } else {
        ""
    };
    format!(
        r#"<w:sectPr>{references}<w:pgSz w:w="{width}" w:h="{height}"{orient}/><w:pgMar w:top="{}" w:right="{}" w:bottom="{}" w:left="{}" w:header="{}" w:footer="{}" w:gutter="{}"/></w:sectPr>"#,
        twips(page.margin_top + page.margin_header),
        twips(page.margin_right),
        twips(page.margin_bottom + page.margin_footer),
        twips(page.margin_left),
        twips(page.margin_header),
        twips(page.margin_footer),
        twips(page.margin_gutter),
    )
}

const CONTENT_TYPES: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Default Extension="jpeg" ContentType="image/jpeg"/><Default Extension="gif" ContentType="image/gif"/><Default Extension="bmp" ContentType="image/bmp"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>"#;

const ROOT_RELS: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"#;

const REL: &str = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
const WML: &str = "application/vnd.openxmlformats-officedocument.wordprocessingml";

/// 머리말·꼬리말·각주 같은 본문 밖 부분 하나.
struct Part {
    /// word/ 아래 파일 이름(header1.xml 등)
    name: String,
    /// 관계 id(본문 관계에 넣지 않는 부분은 None)
    rel: Option<String>,
    /// 관계 종류(header, footer, footnotes, settings)
    kind: &'static str,
    xml: String,
}

/// 관계 파일: `extra` 부분들 + 그림들. 본문에는 스타일도 넣는다.
fn rels_xml(media: &Media, extra: &[Part], styles: bool) -> String {
    let mut rels = String::from(
        r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">"#,
    );
    if styles {
        let _ = write!(
            rels,
            r#"<Relationship Id="rId1" Type="{REL}/styles" Target="styles.xml"/>"#
        );
    }
    for part in extra {
        if let Some(id) = &part.rel {
            let _ = write!(
                rels,
                r#"<Relationship Id="{id}" Type="{REL}/{}" Target="{}"/>"#,
                part.kind, part.name
            );
        }
    }
    for (index, file) in media.files.iter().enumerate() {
        let _ = write!(
            rels,
            r#"<Relationship Id="rIdImg{}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{}"/>"#,
            index + 1,
            file.1
        );
    }
    rels.push_str("</Relationships>");
    rels
}

const STYLES: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="20"/><w:lang w:val="ko-KR" w:eastAsia="ko-KR"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style></w:styles>"#;

const NAMESPACES: &str = r#"xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape""#;

/// 첫 구역의 머리말·꼬리말을 부분으로 쓰고 sectPr 참조를 돌려준다. 같은 종류·쪽은 처음 것만.
fn header_footer_parts(
    document: &Document,
    media: &mut Media,
    parts: &mut Vec<Part>,
) -> Result<String> {
    let info = &document.doc_info;
    let mut seen: Vec<(bool, &'static str)> = Vec::new();
    let mut references = (String::new(), String::new());
    let Some(section) = document.sections.first() else {
        return Ok(String::new());
    };
    for control in section.paragraphs.iter().flat_map(|p| &p.controls) {
        let (is_header, apply, paragraphs) = match control {
            Control::Header(header) => (true, header.apply_to, &header.paragraphs),
            Control::Footer(footer) => (false, footer.apply_to, &footer.paragraphs),
            _ => continue,
        };
        let kind = match apply {
            HeaderFooterApply::Even => "even",
            _ => "default",
        };
        if seen.contains(&(is_header, kind)) {
            continue;
        }
        seen.push((is_header, kind));
        let (tag, name) = if is_header {
            ("hdr", "header")
        } else {
            ("ftr", "footer")
        };
        let index = parts.len() + 1;
        let mut xml = format!(
            r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:{tag} {NAMESPACES}>"#
        );
        for paragraph in paragraphs {
            write_paragraph(&mut xml, info, media, paragraph, 1)?;
        }
        if paragraphs.is_empty() {
            xml.push_str("<w:p/>");
        }
        let _ = write!(xml, "</w:{tag}>");
        let rel = format!("rIdPart{index}");
        let reference = format!(r#"<w:{name}Reference w:type="{kind}" r:id="{rel}"/>"#);
        // sectPr 안에서는 머리말 참조가 꼬리말 참조보다 앞에 온다.
        if is_header {
            &mut references.0
        } else {
            &mut references.1
        }
        .push_str(&reference);
        parts.push(Part {
            name: format!("{name}{index}.xml"),
            rel: Some(rel),
            kind: name,
            xml,
        });
    }
    if seen.iter().any(|(_, kind)| *kind == "even") {
        parts.push(Part {
            name: "settings.xml".into(),
            rel: Some("rIdSettings".into()),
            kind: "settings",
            xml: format!(
                r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings {NAMESPACES}><w:evenAndOddHeaders/></w:settings>"#
            ),
        });
    }
    Ok(references.0 + &references.1)
}

/// rhwp 문서를 DOCX 바이트로 쓴다.
pub fn docx_from_document(document: &Document) -> Result<Vec<u8>> {
    docx_from_document_with_outline(document, &HashMap::new())
}

/// [`docx_from_document`] 와 같되, 본문 문단 `(구역, 문단)` 에 개요 수준(1부터)을 붙인다.
/// DOCX 에서 연 문서는 rhwp 문단에 개요 속성이 없어서, 읽을 때 찾은 제목을 이것으로 되살린다.
pub fn docx_from_document_with_outline(
    document: &Document,
    outline: &HashMap<(usize, usize), u8>,
) -> Result<Vec<u8>> {
    let info = &document.doc_info;
    let mut media = Media {
        contents: &document.bin_data_content,
        files: Vec::new(),
        next_object: 0,
        footnotes: Vec::new(),
    };
    let mut body = String::new();
    for (section_index, section) in document.sections.iter().enumerate() {
        for (paragraph_index, paragraph) in section.paragraphs.iter().enumerate() {
            let start = body.len();
            write_paragraph(&mut body, info, &mut media, paragraph, 0)?;
            if let Some(&level) = outline.get(&(section_index, paragraph_index)) {
                set_outline_level(&mut body, start, level);
            }
        }
    }
    let mut extra: Vec<Part> = Vec::new();
    let references = header_footer_parts(document, &mut media, &mut extra)?;
    if !media.footnotes.is_empty() {
        let mut xml = format!(
            r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:footnotes {NAMESPACES}><w:footnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:footnote><w:footnote w:type="continuationSeparator" w:id="0"><w:p><w:r><w:continuationSeparator/></w:r></w:p></w:footnote>"#
        );
        for (index, note) in media.footnotes.iter().enumerate() {
            let _ = write!(
                xml,
                r#"<w:footnote w:id="{}">{note}</w:footnote>"#,
                index + 1
            );
        }
        xml.push_str("</w:footnotes>");
        extra.push(Part {
            name: "footnotes.xml".into(),
            rel: Some("rIdFootnotes".into()),
            kind: "footnotes",
            xml,
        });
    }
    let xml = format!(
        r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document {NAMESPACES}><w:body>{body}{}</w:body></w:document>"#,
        section_props(document, &references)
    );

    let mut buffer = Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated);
    let io = |_| Error::new(ErrorCode::Internal, "could not write DOCX");
    let rels = rels_xml(&media, &extra, true);
    let mut content_types = String::from(CONTENT_TYPES);
    for part in &extra {
        let _ = write!(
            content_types,
            r#"<Override PartName="/word/{}" ContentType="{WML}.{}+xml"/>"#,
            part.name, part.kind
        );
    }
    content_types.push_str("</Types>");
    // 본문 밖 부분의 그림 관계: 그림 관계 id 를 본문과 같게 쓴다.
    let part_rels: Vec<(String, String)> = extra
        .iter()
        .filter(|part| part.kind != "settings" && !media.files.is_empty())
        .map(|part| {
            (
                format!("word/_rels/{}.rels", part.name),
                rels_xml(&media, &[], false),
            )
        })
        .collect();
    let mut parts: Vec<(String, &[u8])> = vec![
        ("[Content_Types].xml".into(), content_types.as_bytes()),
        ("_rels/.rels".into(), ROOT_RELS.as_bytes()),
        ("word/_rels/document.xml.rels".into(), rels.as_bytes()),
        ("word/styles.xml".into(), STYLES.as_bytes()),
        ("word/document.xml".into(), xml.as_bytes()),
    ];
    for part in &extra {
        parts.push((format!("word/{}", part.name), part.xml.as_bytes()));
    }
    for (name, xml) in &part_rels {
        parts.push((name.clone(), xml.as_bytes()));
    }
    for file in &media.files {
        parts.push((format!("word/media/{}", file.1), &file.3));
    }
    for (name, content) in parts {
        // 이미 압축된 그림은 저장만 한다.
        let part_options = if name.starts_with("word/media/") {
            options.compression_method(zip::CompressionMethod::Stored)
        } else {
            options
        };
        zip.start_file(name, part_options).map_err(io)?;
        zip.write_all(content)
            .map_err(|_| Error::new(ErrorCode::Internal, "could not write DOCX"))?;
    }
    zip.finish().map_err(io)?;
    Ok(buffer.into_inner())
}
