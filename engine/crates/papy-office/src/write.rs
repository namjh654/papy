//! rhwp 문서 → DOCX. 문단·글자 서식(글꼴·크기·굵게·기울임·밑줄·취소선·색)·정렬·줄 간격·표(병합 포함)·
//! 쪽 크기와 여백·그림(PNG·JPEG·GIF·BMP, 글자처럼 놓기)을 옮긴다. 단위: HWPUNIT(1/7200 인치) → twip(1/1440 인치) 은 5 로,
//! EMU(1/914400 인치) 는 127 을 곱한다.

use std::collections::HashMap;
use std::fmt::Write as _;
use std::io::{Cursor, Write};

use papy_model::{Error, ErrorCode, Result};
use rhwp::model::bin_data::BinDataContent;
use rhwp::model::control::{Control, Equation, FormObject, FormType};
use rhwp::model::document::{DocInfo, Document};
use rhwp::model::header_footer::HeaderFooterApply;
use rhwp::model::image::Picture;
use rhwp::model::page::ColumnDef;
use rhwp::model::paragraph::Paragraph as NoteParagraph;
use rhwp::model::paragraph::Paragraph;
use rhwp::model::shape::{Caption, CaptionDirection, HorzRelTo, ShapeObject, TextWrap, VertRelTo};
use rhwp::model::style::FillType;
use rhwp::model::style::{Alignment, HeadType, LineSpacingType, UnderlineType};
use rhwp::model::table::Table;

/// 표 중첩 상한(순환·과도한 중첩 방어).
const MAX_DEPTH: usize = 16;

/// 수식을 PNG 그림으로 그리는 함수(없으면 수식 스크립트를 글로 넣는다).
pub type EquationRenderer<'a> = &'a dyn Fn(&Equation) -> Option<Vec<u8>>;

/// 문서에 넣을 그림 파일(word/media/…)과 관계 id.
struct Media<'a> {
    contents: &'a [BinDataContent],
    /// (bin_data_id — 만든 그림은 None, 파일 이름, 확장자, 바이트)
    files: Vec<(Option<u16>, String, &'static str, Vec<u8>)>,
    render_equation: Option<EquationRenderer<'a>>,
    /// 문서의 개요 번호 정의(1부터, 0 이면 한컴 기본 개요 번호)
    outline_numbering: u16,
    /// 쓴 번호 목록: (글머리표인가, 정의 id) → Word numId(1부터)
    lists: Vec<(bool, u16)>,
    /// 각주·미주 번호 표시(앞 글자, 뒤 글자, 위 첨자): 한글 시험지는 미주 번호를 "문1)" 처럼 본문 크기로 쓴다.
    note_marks: [(String, String, bool); 2],
    /// 그림 개체 번호(docPr id)
    next_object: u32,
    /// 각주 본문 XML(`<w:footnote>` 안쪽). 각주 id 는 순서 + 1.
    footnotes: Vec<String>,
    /// 미주 본문 XML(`<w:endnote>` 안쪽). 미주 id 는 순서 + 1.
    endnotes: Vec<String>,
    /// 메모(작성자, 본문 XML — `<w:comment>` 안쪽; 한글 메모·숨은 설명). 메모 id 는 순서(0 부터).
    comments: Vec<(String, String)>,
    /// 하이퍼링크 주소(관계 id 는 `rIdLink{차례 + 1}`)
    links: Vec<String>,
    /// 쓴 책갈피 수(책갈피 id)
    bookmarks: u32,
}

impl Media<'_> {
    /// 그림을 등록하고 관계 id 를 돌려준다. 같은 그림은 한 번만 넣는다. Word 가 못 읽는 형식이면 None.
    fn add(&mut self, bin_data_id: u16) -> Option<String> {
        if let Some(index) = self
            .files
            .iter()
            .position(|file| file.0 == Some(bin_data_id))
        {
            return Some(format!("rIdImg{}", index + 1));
        }
        // rhwp 와 같은 규칙: 1 부터 매긴 차례로 먼저 찾고, 범위 밖이면(HWPX 차트 등) id 로 찾는다.
        // id 로만 찾으면 저장 id 와 차례가 다른 문서(그림 자료 순서가 바뀐 HWP)에서 그림이 빠진다.
        let index = usize::from(bin_data_id).checked_sub(1)?;
        let content = self.contents.get(index).or_else(|| {
            self.contents
                .iter()
                .find(|content| content.id == bin_data_id)
        })?;
        let bytes = content.data.load();
        self.push(Some(bin_data_id), bytes)
    }

    /// 만든 그림(수식 등)을 넣는다.
    fn add_bytes(&mut self, bytes: Vec<u8>) -> Option<String> {
        self.push(None, bytes)
    }

    fn push(&mut self, bin_data_id: Option<u16>, mut bytes: Vec<u8>) -> Option<String> {
        // BMP 는 압축이 없어 문서가 수십 MB 가 된다. PNG 로 바꿔 넣는다(실패하면 그대로).
        if crate::read::image_mime(&bytes) == Some("image/bmp") {
            if let Some(png) = bmp_to_png(&bytes) {
                bytes = png;
            }
        }
        let extension = crate::read::picture_mime(&bytes).map(|mime| match mime {
            "image/png" => "png",
            "image/jpeg" => "jpeg",
            "image/gif" => "gif",
            "image/x-wmf" => "wmf",
            "image/x-emf" => "emf",
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

#[cfg(test)]
pub(crate) fn bmp_to_png_for_test(bmp: &[u8]) -> Option<Vec<u8>> {
    bmp_to_png(bmp)
}

fn bmp_to_png(bmp: &[u8]) -> Option<Vec<u8>> {
    let image = image::load_from_memory_with_format(bmp, image::ImageFormat::Bmp).ok()?;
    let mut png = Cursor::new(Vec::new());
    image.write_to(&mut png, image::ImageFormat::Png).ok()?;
    Some(png.into_inner())
}

/// HWPUNIT → EMU
fn emu(hwpunit: u32) -> u64 {
    u64::from(hwpunit) * 127
}

fn write_picture(out: &mut String, media: &mut Media, picture: &Picture) {
    let Some(rel) = media.add(picture.image_attr.bin_data_id) else {
        return;
    };
    if picture.common.treat_as_char {
        write_inline_image(
            out,
            media,
            &rel,
            picture.common.width,
            picture.common.height,
            0,
        );
        return;
    }
    // 떠 있는 그림: 도형과 같은 자리(기준·위치·글 배치)에 놓는다.
    media.next_object += 1;
    let id = media.next_object;
    let (cx, cy) = (
        emu(picture.common.width.max(1)),
        emu(picture.common.height.max(1)),
    );
    let graphic = format!(
        r#"<wp:docPr id="{id}" name="Picture {id}"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">{}</a:graphicData></a:graphic>"#,
        pic_xml(id, &rel, (0, 0, cx, cy))
    );
    write_drawing(out, &picture.common, cx, cy, &graphic);
}

/// `pic:pic`(관계 id 는 이미 등록한 것). 상자는 EMU (x, y, 너비, 높이).
fn pic_xml(id: u32, rel: &str, (x, y, cx, cy): (i64, i64, u64, u64)) -> String {
    format!(
        r#"<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="{id}" name="Picture {id}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="{rel}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="{x}" y="{y}"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>"#
    )
}

/// 글자처럼 놓는 그림 런(관계 id 는 이미 등록한 것). `lower` 만큼(HWPUNIT) 글줄 기준선 아래로 내린다.
fn write_inline_image(
    out: &mut String,
    media: &mut Media,
    rel: &str,
    width: u32,
    height: u32,
    lower: u32,
) {
    media.next_object += 1;
    let id = media.next_object;
    let (cx, cy) = (emu(width.max(1)), emu(height.max(1)));
    // 반 포인트 = 100 HWPUNIT
    let position = lower / 100;
    let run_props = if position > 0 {
        format!(r#"<w:rPr><w:position w:val="-{position}"/></w:rPr>"#)
    } else {
        String::new()
    };
    let _ = write!(
        out,
        r#"<w:r>{run_props}<w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="{cx}" cy="{cy}"/><wp:docPr id="{id}" name="Picture {id}"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">{}</a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"#,
        pic_xml(id, rel, (0, 0, cx, cy))
    );
}

/// 그리기 런: 글자처럼이면 `wp:inline`, 아니면 기준·위치·글 배치를 지킨 `wp:anchor`.
/// `graphic` 은 `wp:docPr` 와 `a:graphic`.
fn write_drawing(
    out: &mut String,
    common: &rhwp::model::shape::CommonObjAttr,
    cx: u64,
    cy: u64,
    graphic: &str,
) {
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
}

/// 도형 모양: 정해진 모양(`a:prstGeom`) 또는 꼭짓점 경로(`a:custGeom`). 그릴 수 없는 개체는 None.
fn geometry_xml(shape: &ShapeObject) -> Option<(String, bool, bool)> {
    let preset = |name: &str| format!(r#"<a:prstGeom prst="{name}"><a:avLst/></a:prstGeom>"#);
    let attr = shape.shape_attr();
    let (w, h) = (
        i64::from(attr.original_width.max(1)),
        i64::from(attr.original_height.max(1)),
    );
    // 경로 좌표는 개체 원래 크기(original) 기준이고, Word 가 상자 크기에 맞춰 늘인다.
    let path = |commands: String, closed: bool| {
        format!(
            r#"<a:custGeom><a:avLst/><a:gdLst/><a:ahLst/><a:cxnLst/><a:rect l="0" t="0" r="r" b="b"/><a:pathLst><a:path w="{w}" h="{h}">{commands}{}</a:path></a:pathLst></a:custGeom>"#,
            if closed { "<a:close/>" } else { "" }
        )
    };
    let pt = |x: i64, y: i64| format!(r#"<a:pt x="{}" y="{}"/>"#, x.clamp(0, w), y.clamp(0, h));
    match shape {
        ShapeObject::Rectangle(rect) if rect.round_rate > 0 => {
            Some((preset("roundRect"), false, false))
        }
        ShapeObject::Rectangle(_) => Some((preset("rect"), false, false)),
        ShapeObject::Ellipse(_) => Some((preset("ellipse"), false, false)),
        ShapeObject::Line(line) => Some((
            preset("line"),
            line.start.x > line.end.x,
            line.start.y > line.end.y,
        )),
        ShapeObject::Polygon(polygon) if polygon.points.len() >= 2 => {
            let mut commands = String::new();
            for (i, p) in polygon.points.iter().enumerate() {
                let tag = if i == 0 { "moveTo" } else { "lnTo" };
                let _ = write!(
                    commands,
                    "<a:{tag}>{}</a:{tag}>",
                    pt(i64::from(p.x), i64::from(p.y))
                );
            }
            // 처음과 끝 점이 같으면 닫힌 다각형.
            let closed = polygon.points.len() > 2
                && polygon.points.first().map(|p| (p.x, p.y))
                    == polygon.points.last().map(|p| (p.x, p.y));
            Some((path(commands, closed), false, false))
        }
        ShapeObject::Curve(curve) if curve.points.len() >= 2 => {
            let p = |i: usize| pt(i64::from(curve.points[i].x), i64::from(curve.points[i].y));
            let mut commands = format!("<a:moveTo>{}</a:moveTo>", p(0));
            let (mut i, mut segment) = (1, 0);
            while i < curve.points.len() {
                if curve.segment_types.get(segment) == Some(&1) && i + 2 < curve.points.len() {
                    let _ = write!(
                        commands,
                        "<a:cubicBezTo>{}{}{}</a:cubicBezTo>",
                        p(i),
                        p(i + 1),
                        p(i + 2)
                    );
                    i += 3;
                } else {
                    let _ = write!(commands, "<a:lnTo>{}</a:lnTo>", p(i));
                    i += 1;
                }
                segment += 1;
            }
            Some((path(commands, false), false, false))
        }
        ShapeObject::Arc(arc) => {
            // 축 정렬 타원의 호를 짧은 선분들로 그린다(rhwp 그리기와 같은 방향·각도).
            let (cx, cy) = (f64::from(arc.center.x), f64::from(arc.center.y));
            let (dx1, dy1) = (f64::from(arc.axis1.x) - cx, f64::from(arc.axis1.y) - cy);
            let (dx2, dy2) = (f64::from(arc.axis2.x) - cx, f64::from(arc.axis2.y) - cy);
            let (r1, r2) = (dx1.hypot(dy1), dx2.hypot(dy2));
            if r1 < 1.0 || r2 < 1.0 {
                return None;
            }
            let (a1, a2) = (dy1.atan2(dx1), dy2.atan2(dx2));
            let near = |a: f64, b: f64| (a - b).abs() < 0.3;
            let half_pi = std::f64::consts::FRAC_PI_2;
            let (rx, ry) = if near(a1.abs(), half_pi) && a2.abs() < 0.3 {
                (r2, r1)
            } else if a1.abs() < 0.3 && near(a2.abs(), half_pi) {
                (r1, r2)
            } else {
                (r1.max(r2), r1.min(r2))
            };
            let mut sweep = a1 - a2;
            if sweep < 0.0 {
                sweep += 2.0 * std::f64::consts::PI;
            }
            let steps = 24;
            let mut commands = String::new();
            for step in 0..=steps {
                let angle = a1 - sweep * f64::from(step) / f64::from(steps);
                let (x, y) = (cx + rx * angle.cos(), cy + ry * angle.sin());
                let tag = if step == 0 { "moveTo" } else { "lnTo" };
                let _ = write!(
                    commands,
                    "<a:{tag}>{}</a:{tag}>",
                    pt(x.round() as i64, y.round() as i64)
                );
            }
            match arc.arc_type {
                // 부채꼴: 가운데로 이어 닫는다.
                1 => {
                    let _ = write!(commands, "<a:lnTo>{}</a:lnTo>", pt(cx as i64, cy as i64));
                    Some((path(commands, true), false, false))
                }
                // 활꼴
                2 => Some((path(commands, true), false, false)),
                _ => Some((path(commands, false), false, false)),
            }
        }
        _ => None,
    }
}

/// 도형 하나 → `wps:wsp`. 상자는 EMU (x, y, 너비, 높이). `in_group` 이면 묶음 안 도형(이름 요소가 필요하다).
fn wsp_xml(
    info: &DocInfo,
    media: &mut Media,
    shape: &ShapeObject,
    id: u32,
    (x, y, cx, cy): (i64, i64, u64, u64),
    depth: usize,
    in_group: bool,
) -> Result<Option<String>> {
    let Some((geometry, flip_h, flip_v)) = geometry_xml(shape) else {
        return Ok(None);
    };
    let Some(drawing) = shape.drawing() else {
        return Ok(None);
    };
    let open_path = matches!(shape, ShapeObject::Line(_) | ShapeObject::Curve(_))
        || matches!(shape, ShapeObject::Arc(arc) if arc.arc_type == 0);
    let solid = |color: u32| {
        rgb(color).map_or_else(
            || "<a:noFill/>".to_owned(),
            |hex| format!(r#"<a:solidFill><a:srgbClr val="{hex}"/></a:solidFill>"#),
        )
    };
    let fill = match (&drawing.fill.fill_type, &drawing.fill.solid) {
        (FillType::Solid, Some(solid_fill)) if !open_path => solid(solid_fill.background_color),
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
    // 글상자 안 여백·글 세로 자리(없으면 Word 는 왼쪽·오른쪽 0.1인치, 위·아래 0.05인치로 넓게 잡는다)
    let body = drawing.text_box.as_ref().map_or_else(
        || "<wps:bodyPr/>".to_owned(),
        |text_box| {
            let inset = |v: i16| emu(u32::from(v.max(0) as u16));
            let anchor = match text_box.vertical_align {
                rhwp::model::table::VerticalAlign::Center => "ctr",
                rhwp::model::table::VerticalAlign::Bottom => "b",
                _ => "t",
            };
            format!(
                r#"<wps:bodyPr lIns="{}" tIns="{}" rIns="{}" bIns="{}" anchor="{anchor}"/>"#,
                inset(text_box.margin_left),
                inset(text_box.margin_top),
                inset(text_box.margin_right),
                inset(text_box.margin_bottom)
            )
        },
    );
    let text_box_flag = if drawing.text_box.is_some() {
        r#" txBox="1""#
    } else {
        ""
    };
    let name = if in_group {
        format!(r#"<wps:cNvPr id="{id}" name="Shape {id}"/>"#)
    } else {
        String::new()
    };
    Ok(Some(format!(
        r#"<wps:wsp>{name}<wps:cNvSpPr{text_box_flag}/><wps:spPr><a:xfrm{flips}><a:off x="{x}" y="{y}"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm>{geometry}{fill}{line}</wps:spPr>{text}{body}</wps:wsp>"#
    )))
}

/// 묶음 안 개체 하나와 맨 바깥 묶음 좌표(HWPUNIT)의 상자 (x, y, 너비, 높이).
type GroupLeaf<'s> = (&'s ShapeObject, (f64, f64, f64, f64));

/// 묶음 안 개체들을 맨 바깥 묶음 좌표(HWPUNIT)로 편다. rhwp 가 읽을 때 합성해 둔 변환(render_*)을 쓴다.
/// 회전·기울임은 둘러싼 상자로 근사한다.
fn group_leaves<'s>(
    group: &'s rhwp::model::shape::GroupShape,
    out: &mut Vec<GroupLeaf<'s>>,
    depth: usize,
) {
    if depth > MAX_DEPTH {
        return;
    }
    for child in &group.children {
        if let ShapeObject::Group(inner) = child {
            group_leaves(inner, out, depth + 1);
            continue;
        }
        let a = child.shape_attr();
        let (w, h) = (f64::from(a.original_width), f64::from(a.original_height));
        let corners = [(0.0, 0.0), (w, 0.0), (0.0, h), (w, h)].map(|(x, y)| {
            (
                a.render_tx + a.render_sx * x + a.render_c * y,
                a.render_ty + a.render_b * x + a.render_sy * y,
            )
        });
        let min_x = corners.iter().map(|c| c.0).fold(f64::INFINITY, f64::min);
        let max_x = corners
            .iter()
            .map(|c| c.0)
            .fold(f64::NEG_INFINITY, f64::max);
        let min_y = corners.iter().map(|c| c.1).fold(f64::INFINITY, f64::min);
        let max_y = corners
            .iter()
            .map(|c| c.1)
            .fold(f64::NEG_INFINITY, f64::max);
        if min_x.is_finite() && min_y.is_finite() {
            out.push((child, (min_x, min_y, max_x - min_x, max_y - min_y)));
        }
    }
}

/// 묶음 개체 → `wpg:wgp`(Word 그리기 묶음). 안의 도형·글상자·그림을 같은 자리에 넣는다. 차트·OLE 는 뺀다.
fn group_graphic(
    info: &DocInfo,
    media: &mut Media,
    group: &rhwp::model::shape::GroupShape,
    id: u32,
    cx: u64,
    cy: u64,
    depth: usize,
) -> Result<Option<String>> {
    let mut leaves = Vec::new();
    group_leaves(group, &mut leaves, 0);
    let mut children = String::new();
    for (child, (x, y, w, h)) in leaves {
        let to_emu = |v: f64| (v * 127.0).round() as i64;
        let (x, y) = (to_emu(x), to_emu(y));
        let (w, h) = (to_emu(w).max(1) as u64, to_emu(h).max(1) as u64);
        media.next_object += 1;
        let child_id = media.next_object;
        match child {
            ShapeObject::Picture(picture) => {
                if let Some(rel) = media.add(picture.image_attr.bin_data_id) {
                    children.push_str(&pic_xml(child_id, &rel, (x, y, w, h)));
                }
            }
            _ => {
                if let Some(xml) = wsp_xml(info, media, child, child_id, (x, y, w, h), depth, true)?
                {
                    children.push_str(&xml);
                }
            }
        }
    }
    if children.is_empty() {
        return Ok(None);
    }
    Ok(Some(format!(
        r#"<wp:docPr id="{id}" name="Group {id}"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"><wpg:wgp><wpg:cNvGrpSpPr/><wpg:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/><a:chOff x="0" y="0"/><a:chExt cx="{cx}" cy="{cy}"/></a:xfrm></wpg:grpSpPr>{children}</wpg:wgp></a:graphicData></a:graphic>"#
    )))
}

/// 도형(사각형·타원·선·다각형·곡선·호, 글상자 포함)과 묶음 → 그리기 런. 차트·OLE 는 옮기지 않는다.
fn write_shape(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    shape: &ShapeObject,
    depth: usize,
) -> Result<()> {
    let common = shape.common();
    let (cx, cy) = (emu(common.width.max(1)), emu(common.height.max(1)));
    media.next_object += 1;
    let id = media.next_object;
    let graphic = if let ShapeObject::Group(group) = shape {
        group_graphic(info, media, group, id, cx, cy, depth)?
    } else {
        wsp_xml(info, media, shape, id, (0, 0, cx, cy), depth, false)?.map(|wsp| {
            format!(
                r#"<wp:docPr id="{id}" name="Shape {id}"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">{wsp}</a:graphicData></a:graphic>"#
            )
        })
    };
    if let Some(graphic) = graphic {
        write_drawing(out, common, cx, cy, &graphic);
    }
    Ok(())
}

/// 수식 → 그림(그릴 수 없으면 수식 스크립트 글).
fn write_equation(out: &mut String, media: &mut Media, equation: &Equation) {
    let png = media.render_equation.and_then(|render| render(equation));
    if let Some(rel) = png.and_then(|png| media.add_bytes(png)) {
        // 기준선은 위에서 높이의 baseline% 자리. 그 아래 부분만큼 내린다.
        let height = equation.common.height;
        let baseline = u32::try_from(equation.baseline.clamp(0, 100)).unwrap_or(0);
        let lower = if baseline == 0 {
            0
        } else {
            height.saturating_mul(100 - baseline) / 100
        };
        write_inline_image(out, media, &rel, equation.common.width, height, lower);
        return;
    }
    let script = equation.script.trim();
    if !script.is_empty() {
        let _ = write!(
            out,
            r#"<w:r><w:t xml:space="preserve">{}</w:t></w:r>"#,
            xml_escape(script)
        );
    }
}

/// 양식 개체 → 글. 선택 상자·라디오 단추는 표시 글자(☑ ☐ ◉ ○)와 캡션, 단추는 [캡션], 입력·목록 상자는 내용.
fn write_form(out: &mut String, form: &FormObject) {
    let checked = form.value != 0;
    let text = match form.form_type {
        FormType::CheckBox => format!("{} {}", if checked { '☑' } else { '☐' }, form.caption),
        FormType::RadioButton => format!("{} {}", if checked { '◉' } else { '○' }, form.caption),
        FormType::PushButton => format!("[{}]", form.caption),
        _ => form.text.clone(),
    };
    let text = text.trim_end();
    if text.is_empty() {
        return;
    }
    let color = rgb(form.fore_color)
        .map(|hex| format!(r#"<w:rPr><w:color w:val="{hex}"/></w:rPr>"#))
        .unwrap_or_default();
    let _ = write!(
        out,
        r#"<w:r>{color}<w:t xml:space="preserve">{}</w:t></w:r>"#,
        xml_escape(text)
    );
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

/// HWP 글자 언어 묶음(0 한글, 1 영어, 2 한자, 3 일본어, 4 기타, 5 기호, 6 사용자). 빈칸·구두점은 앞 글자를
/// 따른다(rhwp 조판과 같은 규칙).
fn lang_slots(chars: &[char]) -> Vec<usize> {
    let mut carry = 0;
    chars
        .iter()
        .map(|&ch| {
            let neutral = matches!(ch as u32,
                0x0000..=0x002F | 0x003A..=0x0040 | 0x005B..=0x0060 | 0x007B..=0x00BF);
            if !neutral {
                carry = rhwp::renderer::style_resolver::detect_lang_category(ch);
            }
            carry
        })
        .collect()
}

/// 글자 모양 `shape_id` 에서 언어 묶음 `slot` 글자를 쓸 Word 런 묶음. 한글(0)과 장평·자간·상대 크기가 같으면 0
/// (한 런에 ascii·eastAsia 글꼴로 함께 쓴다), 기호 등(2~)은 글꼴까지 같아야 0.
fn run_slot(info: &DocInfo, shape_id: u32, slot: usize) -> usize {
    let Some(shape) = info.char_shapes.get(shape_id as usize) else {
        return 0;
    };
    let same = |i: usize| {
        shape.ratios[i] == shape.ratios[0]
            && shape.spacings[i] == shape.spacings[0]
            && shape.relative_sizes[i] == shape.relative_sizes[0]
    };
    let same_font =
        |i: usize| font_name(info, i, shape.font_ids[i]) == font_name(info, 0, shape.font_ids[0]);
    match slot {
        0 => 0,
        1 if same(1) => 0,
        s if s >= 2 && (s > 6 || (same(s) && same_font(s))) => 0,
        s => s,
    }
}

fn run_props(info: &DocInfo, shape_id: u32, slot: usize) -> String {
    let Some(shape) = info.char_shapes.get(shape_id as usize) else {
        return String::new();
    };
    let slot = slot.min(6);
    let mut props = String::from("<w:rPr>");
    // 0: 한글, 1: 영어 글꼴 묶음. 한자·기호 등(2~)만 든 런은 그 묶음 글꼴로 모두 쓴다.
    let east_asia = font_name(info, 0, shape.font_ids[0]);
    let latin = font_name(info, 1, shape.font_ids[1]).or(east_asia);
    let own = (slot >= 2)
        .then(|| font_name(info, slot, shape.font_ids[slot]))
        .flatten();
    if let Some(own) = own {
        let own = xml_escape(own);
        let _ = write!(
            props,
            r#"<w:rFonts w:ascii="{own}" w:hAnsi="{own}" w:eastAsia="{own}"/>"#
        );
    } else if east_asia.is_some() || latin.is_some() {
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
    // 자간(글자 크기의 %) → twip, 장평(%) → w:w. 이 런의 언어 묶음 값.
    // 상대 크기는 장평에 곱한다(글자 너비는 같고 줄 높이는 기준 크기대로).
    let relative = i32::from(shape.relative_sizes[slot]).clamp(1, 250);
    let spacing = i32::from(shape.spacings[slot]);
    if spacing != 0 && shape.base_size > 0 {
        // base_size 는 1/100 pt, twip = pt × 20
        let twip = shape.base_size * relative / 100 * spacing / 500;
        if twip != 0 {
            let _ = write!(props, r#"<w:spacing w:val="{twip}"/>"#);
        }
    }
    let ratio = (i32::from(shape.ratios[slot]) * relative + 50) / 100;
    if ratio != 100 && (1..=600).contains(&ratio) {
        let _ = write!(props, r#"<w:w w:val="{ratio}"/>"#);
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

/// 번호·개요·글머리표 문단 → Word 번호 목록(numId, 수준). 한글이 실제로 번호를 그리는 문단만(rhwp 가 계산해 둔 번호 글자가 있는 것).
fn list_of(info: &DocInfo, media: &mut Media, paragraph: &Paragraph) -> Option<(usize, u8)> {
    let shape = info.para_shapes.get(paragraph.para_shape_id as usize)?;
    let level = shape.para_level.min(6);
    let key = match shape.head_type {
        HeadType::Bullet => {
            let id = shape.numbering_id;
            info.bullets.get(usize::from(id).checked_sub(1)?)?;
            (true, id)
        }
        HeadType::Number | HeadType::Outline => {
            if !matches!(
                paragraph.numbering_marker,
                rhwp::model::paragraph::NumberingMarker::Text(..)
            ) {
                return None;
            }
            let id = if shape.head_type == HeadType::Outline {
                media.outline_numbering
            } else {
                shape.numbering_id
            };
            (false, id)
        }
        _ => return None,
    };
    let index = match media.lists.iter().position(|k| *k == key) {
        Some(index) => index,
        None => {
            media.lists.push(key);
            media.lists.len() - 1
        }
    };
    Some((index + 1, level))
}

/// HWP 번호 형식 코드(표 41) → Word `w:numFmt`.
fn word_num_format(code: u8) -> &'static str {
    match code {
        1 => "decimalEnclosedCircle",
        2 => "upperRoman",
        3 => "lowerRoman",
        4 => "upperLetter",
        5 => "lowerLetter",
        8 => "ganada",
        10 => "chosung",
        12 => "koreanCounting",
        13 => "chineseCounting",
        _ => "decimal",
    }
}

/// HWP 번호 모양(`^1.`, `^N`) → Word `w:lvlText`(`%1.`).
fn word_level_text(format: &str, level: usize) -> String {
    let mut out = String::new();
    let mut chars = format.chars().peekable();
    while let Some(ch) = chars.next() {
        if ch == '^' {
            match chars.peek().copied() {
                Some(d @ '1'..='7') => {
                    chars.next();
                    out.push('%');
                    out.push(d);
                    continue;
                }
                Some(code @ ('n' | 'N')) => {
                    chars.next();
                    for i in 0..=level {
                        if i > 0 {
                            out.push('.');
                        }
                        let _ = write!(out, "%{}", i + 1);
                    }
                    if code == 'N' {
                        out.push('.');
                    }
                    continue;
                }
                _ => {}
            }
        }
        out.push(ch);
    }
    out
}

/// `word/numbering.xml`: 쓴 번호 목록마다 추상 번호(수준 7개)와 번호.
fn numbering_xml(info: &DocInfo, lists: &[(bool, u16)]) -> String {
    let mut xml = format!(
        r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering {NAMESPACES}>"#
    );
    let default_outline = ["^1.", "^2.", "^3)", "^4)", "(^5)", "(^6)", "^7"];
    for (index, &(bullet, id)) in lists.iter().enumerate() {
        let _ = write!(
            xml,
            r#"<w:abstractNum w:abstractNumId="{index}"><w:multiLevelType w:val="multilevel"/>"#
        );
        for (level, outline_format) in default_outline.iter().enumerate() {
            let (format, text, start) = if bullet {
                let ch = info
                    .bullets
                    .get(usize::from(id).saturating_sub(1))
                    .map_or('•', |b| b.bullet_char);
                let ch = if ch == '\0' { '•' } else { ch };
                ("bullet", ch.to_string(), 1)
            } else {
                match usize::from(id)
                    .checked_sub(1)
                    .and_then(|i| info.numberings.get(i))
                {
                    Some(n) => (
                        word_num_format(n.heads[level].number_format),
                        word_level_text(&n.level_formats[level], level),
                        n.level_start_numbers[level].max(1),
                    ),
                    // 정의 없는 개요: 한컴 기본 개요 번호
                    None => ("decimal", word_level_text(outline_format, level), 1),
                }
            };
            let _ = write!(
                xml,
                r#"<w:lvl w:ilvl="{level}"><w:start w:val="{start}"/><w:numFmt w:val="{format}"/><w:suff w:val="space"/><w:lvlText w:val="{}"/><w:lvlJc w:val="left"/></w:lvl>"#,
                xml_escape(&text)
            );
        }
        xml.push_str("</w:abstractNum>");
    }
    for index in 0..lists.len() {
        let _ = write!(
            xml,
            r#"<w:num w:numId="{}"><w:abstractNumId w:val="{index}"/></w:num>"#,
            index + 1
        );
    }
    xml.push_str("</w:numbering>");
    xml
}

/// 문단 속성. `list` 는 Word 번호 목록(numId, 수준).
/// 탭 정의 → `w:tabs`. 위치는 단 왼쪽 끝에서 잰 값으로 문단 여백처럼 2배 단위로 저장된다.
fn tab_stops(info: &DocInfo, tab_def_id: u16) -> String {
    let Some(def) = info.tab_defs.get(tab_def_id as usize) else {
        return String::new();
    };
    let mut out = String::new();
    // 한글 탭 위치는 부호 있는 값이다(음수 = 큰 u32). Word 탭은 0~31680 twip(22 인치)만 받는다.
    const WORD_MAX_TAB: u32 = 31680;
    for tab in def
        .tabs
        .iter()
        .filter(|t| (t.position as i32) > 0 && twips(t.position / 2) <= WORD_MAX_TAB)
    {
        let kind = match tab.tab_type {
            1 => "right",
            2 => "center",
            3 => "decimal",
            _ => "left",
        };
        // 채움: 0 없음, 1 실선, 2 파선, 3 점선, 7 원형 점선, 그 밖은 점선으로
        let leader = match tab.fill_type {
            0 => "",
            1 | 8..=11 => r#" w:leader="underscore""#,
            2 | 6 => r#" w:leader="hyphen""#,
            7 => r#" w:leader="middleDot""#,
            _ => r#" w:leader="dot""#,
        };
        let _ = write!(
            out,
            r#"<w:tab w:val="{kind}"{leader} w:pos="{}"/>"#,
            twips(tab.position / 2)
        );
    }
    if out.is_empty() {
        return out;
    }
    format!("<w:tabs>{out}</w:tabs>")
}

/// 문단에 줄 높이를 키우는 개체(그림·도형·수식·글자처럼 놓은 표·양식)가 있는가.
fn has_inline_object(paragraph: &Paragraph) -> bool {
    paragraph.controls.iter().any(|control| match control {
        Control::Picture(_) | Control::Shape(_) | Control::Equation(_) | Control::Form(_) => true,
        Control::Table(table) => table.common.treat_as_char,
        _ => false,
    })
}

fn para_props_with(info: &DocInfo, paragraph: &Paragraph, list: Option<(usize, u8)>) -> String {
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
    // 쪽 나누기(문단 앞). 스키마 순서상 jc 보다 앞에 둔다.
    let page_break = if paragraph.column_type == rhwp::model::paragraph::ColumnBreakType::Page {
        "<w:pageBreakBefore/>"
    } else {
        ""
    };
    // 스키마 순서: pageBreakBefore … spacing, ind, jc, outlineLvl
    let mut props = format!("<w:pPr>{page_break}");
    if let Some((num_id, level)) = list {
        let _ = write!(
            props,
            r#"<w:numPr><w:ilvl w:val="{level}"/><w:numId w:val="{num_id}"/></w:numPr>"#
        );
    }
    props.push_str(&para_border(info, shape));
    props.push_str(&tab_stops(info, shape.tab_def_id));
    // 문단 여백·간격은 HWPUNIT 의 2배 단위로 저장된다(rhwp ParaShape 규약).
    let half = |v: i32| twips(u32::try_from(v.max(0) / 2).unwrap_or(0));
    let mut spacing = String::new();
    if shape.spacing_before > 0 {
        let _ = write!(spacing, r#" w:before="{}""#, half(shape.spacing_before));
    }
    if shape.spacing_after > 0 {
        let _ = write!(spacing, r#" w:after="{}""#, half(shape.spacing_after));
    }
    match shape.line_spacing_type {
        // 한글 배수 줄 간격은 글자 크기의 %다. Word 의 배수(auto)는 글꼴 고유 줄 높이(한글 글꼴은 글자 크기의
        // 약 1.3배)의 배수라 그대로 옮기면 줄이 30% 쯤 높아진다. 문단에서 가장 큰 글자 × % 를 "최소"로 쓴다
        // (그림·수식처럼 더 큰 것이 있으면 줄이 늘어난다).
        LineSpacingType::Percent if shape.line_spacing > 0 => {
            let size = paragraph
                .char_shapes
                .iter()
                .filter_map(|r| info.char_shapes.get(r.char_shape_id as usize))
                .map(|c| c.base_size)
                .max()
                .filter(|s| *s > 0)
                .unwrap_or(1000);
            // 1/100 pt × % ÷ 100 → twip(× 20 ÷ 100)
            let line = size * shape.line_spacing / 500;
            // 글꼴 고유 줄 높이(약 1.3배)보다 좁은 줄은 "최소"면 Word 가 고유 높이로 늘린다. 글자만 든 문단은
            // 한글처럼 고정 높이로 쓴다(그림·수식·글자처럼 놓은 표가 있으면 잘리지 않게 "최소").
            let rule = if shape.line_spacing < 130 && !has_inline_object(paragraph) {
                "exact"
            } else {
                "atLeast"
            };
            // Papy 가 다시 열 때는 원래 % 를 쓴다(Word 는 모르는 papy: 속성을 무시한다).
            let _ = write!(
                spacing,
                r#" w:line="{line}" w:lineRule="{rule}" papy:linePercent="{}""#,
                shape.line_spacing
            );
        }
        LineSpacingType::Fixed if shape.line_spacing > 0 => {
            let _ = write!(
                spacing,
                r#" w:line="{}" w:lineRule="exact""#,
                twips(shape.line_spacing as u32)
            );
        }
        LineSpacingType::Minimum if shape.line_spacing > 0 => {
            let _ = write!(
                spacing,
                r#" w:line="{}" w:lineRule="atLeast""#,
                twips(shape.line_spacing as u32)
            );
        }
        _ => {}
    }
    // 한글 "최소 공백"(양쪽 정렬 줄에서 빈칸을 줄여 한 줄에 맞추는 %) — Word 에는 없어 Papy 만 다시 읽는다.
    let condense = (shape.attr1 >> 9) & 0x7f;
    if condense > 0 {
        let _ = write!(spacing, r#" papy:condense="{condense}""#);
    }
    if !spacing.is_empty() {
        let _ = write!(props, "<w:spacing{spacing}/>");
    }
    // HWP: 들여쓰기(+)는 첫 줄만 더 들어가고, 내어쓰기(-)는 둘째 줄부터 |indent| 만큼 더 들어간다.
    let (left, right, indent) = (
        half(shape.margin_left),
        half(shape.margin_right),
        shape.indent / 2 / 5,
    );
    let mut ind = String::new();
    if indent < 0 {
        let hanging = indent.unsigned_abs();
        let _ = write!(ind, r#" w:left="{}" w:hanging="{hanging}""#, left + hanging);
    } else if left > 0 {
        let _ = write!(ind, r#" w:left="{left}""#);
    }
    if right > 0 {
        let _ = write!(ind, r#" w:right="{right}""#);
    }
    if indent > 0 {
        let _ = write!(ind, r#" w:firstLine="{indent}""#);
    }
    if !ind.is_empty() {
        let _ = write!(props, "<w:ind{ind}/>");
    }
    let _ = write!(props, r#"<w:jc w:val="{jc}"/>"#);
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

/// 각주·미주 참조 런. 본문을 모아 두고 번호(id)를 매긴다. `endnote` 면 미주.
fn write_note(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    paragraphs: &[NoteParagraph],
    endnote: bool,
    depth: usize,
) -> Result<()> {
    if depth > MAX_DEPTH {
        return Ok(());
    }
    let tag = if endnote { "endnote" } else { "footnote" };
    // 자리를 먼저 잡아 주 안 주가 있어도 번호가 꼬이지 않게 한다.
    fn notes<'m>(media: &'m mut Media<'_>, endnote: bool) -> &'m mut Vec<String> {
        if endnote {
            &mut media.endnotes
        } else {
            &mut media.footnotes
        }
    }
    notes(media, endnote).push(String::new());
    let id = notes(media, endnote).len();
    // 번호 표시: 위 첨자 여부와 앞뒤 글자(한글 주 모양)
    let (before, after, superscript) = media.note_marks[usize::from(endnote)].clone();
    let raise = if superscript {
        r#"<w:rPr><w:vertAlign w:val="superscript"/></w:rPr>"#
    } else {
        ""
    };
    let decorate = |inner: &str| {
        let text = |t: &str| {
            if t.is_empty() {
                String::new()
            } else {
                format!(
                    r#"<w:r {NOTE_MARK_RUN}>{raise}<w:t xml:space="preserve">{}</w:t></w:r>"#,
                    xml_escape(t)
                )
            }
        };
        format!("{}<w:r>{raise}{inner}</w:r>{}", text(&before), text(&after))
    };
    let mark = decorate(&format!("<w:{tag}Ref/>"));
    let mut body = String::new();
    for (index, paragraph) in paragraphs.iter().enumerate() {
        let prefix = if index == 0 {
            format!(r#"{mark}<w:r {NOTE_MARK_RUN}><w:t xml:space="preserve"> </w:t></w:r>"#)
        } else {
            String::new()
        };
        write_paragraph_with(&mut body, info, media, paragraph, depth + 1, &prefix)?;
    }
    if paragraphs.is_empty() {
        let _ = write!(body, "<w:p>{mark}</w:p>");
    }
    notes(media, endnote)[id - 1] = body;
    out.push_str(&decorate(&format!(r#"<w:{tag}Reference w:id="{id}"/>"#)));
    Ok(())
}

/// 주 번호를 꾸미는 글(앞뒤 글자·번호 뒤 빈칸) 런 표시. Word 는 무시하고(`mc:Ignorable`), Papy 는 다시 열 때
/// 이 런을 건너뛴다 — 주 번호 모양이 이미 그 글자를 그리므로 글로 읽으면 왕복마다 글자가 는다.
pub(crate) const NOTE_MARK_RUN: &str = r#"papy:noteMark="1""#;

/// 문단 안 자리에 놓이는 개체: 각주 참조, 그림, 도형.
fn write_inline(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    control: &Control,
    depth: usize,
) -> Result<()> {
    match control {
        Control::Footnote(note) => write_note(out, info, media, &note.paragraphs, false, depth)?,
        Control::Endnote(note) => write_note(out, info, media, &note.paragraphs, true, depth)?,
        Control::Picture(picture) => write_picture(out, media, picture),
        Control::Shape(shape) if depth < MAX_DEPTH => write_shape(out, info, media, shape, depth)?,
        Control::Equation(equation) => write_equation(out, media, equation),
        Control::AutoNumber(number) => {
            use rhwp::model::control::AutoNumberType as Kind;
            let text = |c: char| {
                if c == '\0' {
                    String::new()
                } else {
                    format!(
                        r#"<w:r><w:t xml:space="preserve">{}</w:t></w:r>"#,
                        xml_escape(&c.to_string())
                    )
                }
            };
            let body = match number.number_type {
                // 주 안 번호: 주 번호 표시(footnoteRef)가 대신한다. 글로 쓰면 다시 열 때 번호가 겹친다.
                Kind::Footnote | Kind::Endnote => return Ok(()),
                Kind::Page => page_field(false, number.format),
                Kind::TotalPage => page_field(true, number.format),
                // 그림·표·수식 번호 등은 매겨 둔 번호를 글로
                _ => format!(
                    r#"<w:r><w:t xml:space="preserve">{}</w:t></w:r>"#,
                    number.assigned_number
                ),
            };
            let _ = write!(
                out,
                "{}{body}{}",
                text(number.prefix_char),
                text(number.suffix_char)
            );
        }
        Control::Form(form) => write_form(out, form),
        Control::Bookmark(bookmark) => {
            let id = media.bookmarks;
            media.bookmarks += 1;
            let _ = write!(
                out,
                r#"<w:bookmarkStart w:id="{id}" w:name="{}"/><w:bookmarkEnd w:id="{id}"/>"#,
                xml_escape(&bookmark.name)
            );
        }
        // 숨은 설명 → Word 메모(그 자리에 메모 표시). 작성자는 한글에 없어 "Papy" 로 둔다.
        Control::HiddenComment(comment) if depth < MAX_DEPTH => {
            let mut body = String::new();
            for paragraph in &comment.paragraphs {
                write_paragraph(&mut body, info, media, paragraph, depth + 1)?;
            }
            if comment.paragraphs.is_empty() {
                body.push_str("<w:p/>");
            }
            let id = media.comments.len();
            media.comments.push(("Papy".into(), body));
            let _ = write!(out, r#"<w:r><w:commentReference w:id="{id}"/></w:r>"#);
        }
        _ => {}
    }
    Ok(())
}

/// 문단 안 필드 범위(하이퍼링크, 한글 메모)를 Word 로: 하이퍼링크는 `w:hyperlink`, 메모는 메모 범위와 메모.
struct FieldSpans {
    /// (시작, 끝, 여는 XML, 닫는 XML) — 글자 위치
    spans: Vec<(usize, usize, String, String)>,
    /// 열린 범위(닫는 XML, 끝)
    open: Vec<(String, usize)>,
}

impl FieldSpans {
    /// `position` 에서 끝나는 범위를 닫고 시작하는 범위를 연다.
    fn emit(&mut self, out: &mut String, position: usize) {
        while let Some(at) = self.open.iter().rposition(|(_, end)| *end <= position) {
            let (close, _) = self.open.remove(at);
            out.push_str(&close);
        }
        for (start, end, open, close) in &self.spans {
            if *start == position {
                out.push_str(open);
                if *end <= position {
                    // 빈 범위(빈 누름틀·메모 자리)는 바로 닫는다. 열어 두면 뒤 글이 그 안에 들어간다.
                    out.push_str(close);
                } else {
                    self.open.push((close.clone(), *end));
                }
            }
        }
    }

    /// `position` 뒤 첫 범위 경계(런을 거기서 끊는다).
    fn next_boundary(&self, position: usize) -> usize {
        self.spans
            .iter()
            .flat_map(|(start, end, _, _)| [*start, *end])
            .filter(|&p| p > position)
            .min()
            .unwrap_or(usize::MAX)
    }

    fn close_all(&mut self, out: &mut String) {
        while let Some((close, _)) = self.open.pop() {
            out.push_str(&close);
        }
    }
}

/// 문단의 하이퍼링크·누름틀·메모 필드 범위. 메모 본문은 여기서 써 둔다.
fn field_spans(
    info: &DocInfo,
    media: &mut Media,
    paragraph: &Paragraph,
    len: usize,
    depth: usize,
) -> Result<FieldSpans> {
    use rhwp::model::control::FieldType;
    let mut spans = Vec::new();
    for range in &paragraph.field_ranges {
        let Some(Control::Field(field)) = paragraph.controls.get(range.control_idx) else {
            continue;
        };
        let (start, end) = (range.start_char_idx.min(len), range.end_char_idx.min(len));
        if field.field_type == FieldType::Hyperlink {
            let uri = rhwp::model::hyperlink::command_uri(&field.command);
            if uri.is_empty() || start >= end {
                continue;
            }
            media.links.push(uri);
            let id = media.links.len();
            spans.push((
                start,
                end,
                format!(r#"<w:hyperlink r:id="rIdLink{id}">"#),
                "</w:hyperlink>".into(),
            ));
        } else if field.field_type == FieldType::ClickHere {
            // 누름틀 → 글 콘텐츠 컨트롤. 비었으면 안내문을 자리 표시 글로 둔다(Word 가 회색으로 보인다).
            let click = crate::ClickHere {
                guide: field.guide_text().unwrap_or("").to_owned(),
                memo: field.memo_text().unwrap_or("").to_owned(),
                name: field.field_name().unwrap_or("").to_owned(),
                editable: field.is_editable_in_form(),
                formula: None,
            };
            let empty = start >= end;
            let open = format!(
                r#"<w:sdt><w:sdtPr><w:alias w:val="{}"/><w:tag w:val="{}"/>{}<w:text/></w:sdtPr><w:sdtContent>"#,
                xml_escape(&click.guide),
                xml_escape(&click.tag()),
                if empty { "<w:showingPlcHdr/>" } else { "" }
            );
            let placeholder = if empty && !click.guide.is_empty() {
                format!(
                    r#"<w:r><w:rPr><w:color w:val="808080"/></w:rPr><w:t xml:space="preserve">{}</w:t></w:r>"#,
                    xml_escape(&click.guide)
                )
            } else {
                String::new()
            };
            spans.push((
                start,
                end.max(start),
                open,
                format!("{placeholder}</w:sdtContent></w:sdt>"),
            ));
        } else if field.field_type == FieldType::Formula && !field.command.is_empty() {
            // 계산식 → 결과 글자를 담은 콘텐츠 컨트롤(한글 명령은 tag 에). Word 에서는 계산하지 않는다.
            let formula = crate::ClickHere {
                editable: field.is_editable_in_form(),
                formula: Some(field.command.clone()),
                ..Default::default()
            };
            spans.push((
                start,
                end.max(start),
                format!(
                    r#"<w:sdt><w:sdtPr><w:tag w:val="{}"/><w:text/></w:sdtPr><w:sdtContent>"#,
                    xml_escape(&formula.tag())
                ),
                "</w:sdtContent></w:sdt>".into(),
            ));
        } else if field.command.starts_with("MEMO/") && depth < MAX_DEPTH {
            // 한글 메모: `MEMO/<모양>/<번호>/<id>/<id>/<작성자>/…`
            let author = field.command.split('/').nth(5).unwrap_or("").to_owned();
            let mut body = String::new();
            for memo in &field.memo_paragraphs {
                write_paragraph(&mut body, info, media, memo, depth + 1)?;
            }
            if field.memo_paragraphs.is_empty() {
                body.push_str("<w:p/>");
            }
            let id = media.comments.len();
            media.comments.push((author, body));
            spans.push((
                start,
                end.max(start),
                format!(r#"<w:commentRangeStart w:id="{id}"/>"#),
                format!(
                    r#"<w:commentRangeEnd w:id="{id}"/><w:r><w:commentReference w:id="{id}"/></w:r>"#
                ),
            ));
        }
    }
    Ok(FieldSpans {
        spans,
        open: Vec::new(),
    })
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
    // 표만 든 빈 문단: 한글은 표를 그 문단 자리에 놓고 빈 줄은 표 아래에 둔다. 표 → 그 문단 순서로 쓰면
    // 빈 줄이 하나만 생긴다(문단 → 표 → 빈 문단이면 표 위아래로 빈 줄이 둘).
    let has_table = paragraph
        .controls
        .iter()
        .any(|c| matches!(c, Control::Table(_)));
    let table_only = has_table
        && prefix.is_empty()
        && paragraph
            .text
            .chars()
            .all(|c| c.is_whitespace() || c.is_control())
        && paragraph.controls.iter().all(|c| {
            !matches!(
                c,
                Control::Footnote(_)
                    | Control::Endnote(_)
                    | Control::Picture(_)
                    | Control::Shape(_)
                    | Control::Equation(_)
                    | Control::Form(_)
                    | Control::HiddenComment(_)
            )
        });
    if table_only {
        // 표만 든 문단의 쪽·단 나누기: Word 는 표에 걸 수 없어 표 앞 1pt 문단에 건다(전에는 빠졌다).
        if depth == 0 {
            use rhwp::model::paragraph::ColumnBreakType;
            match paragraph.column_type {
                ColumnBreakType::Page => {
                    out.push_str(&TINY_PARAGRAPH.replace("<w:pPr>", "<w:pPr><w:pageBreakBefore/>"))
                }
                ColumnBreakType::Column => out.push_str(&TINY_PARAGRAPH.replace(
                    "</w:pPr></w:p>",
                    r#"</w:pPr><w:r><w:br w:type="column"/></w:r></w:p>"#,
                )),
                _ => {}
            }
        }
        let tables: Vec<&Table> = paragraph
            .controls
            .iter()
            .filter_map(|c| match c {
                Control::Table(table) => Some(&**table),
                _ => None,
            })
            .collect();
        // 글자처럼 놓은 표 여럿은 한 줄에 나란히 선다. Word 는 표를 나란히 놓지 못하니 테두리 없는
        // 한 줄짜리 표의 칸에 하나씩 넣는다.
        if tables.len() >= 2 && tables.iter().all(|t| t.common.treat_as_char) && depth < MAX_DEPTH {
            write_side_by_side(out, info, media, paragraph, &tables, depth)?;
            out.push_str(TINY_PARAGRAPH);
            return Ok(());
        }
        for (index, table) in tables.iter().enumerate() {
            write_table(out, info, media, table, depth + 1)?;
            // Word 는 표 사이·뒤에 문단이 있어야 한다(한글에는 없는 줄이라 1pt 로, 마지막은 아래에서).
            if index + 1 < tables.len() {
                out.push_str(TINY_PARAGRAPH);
            }
        }
        // 표만 든 문단의 빈 줄은 표 자리 안에 든다(글자처럼 놓은 표는 그 줄 안에, 떠 있는 표는 그 줄에서 시작).
        // Word 가 요구하는 문단만 1pt 로 둔다.
        out.push_str(TINY_PARAGRAPH);
        return Ok(());
    }
    // 문단 기준으로 떠 있는(글자처럼이 아닌) 표는 문단 위쪽에 놓이고 글은 그 아래로 흐른다. 표를 먼저 쓴다.
    // 표가 문단 첫 줄 안에서 시작할 때만 앞에 쓴다(제목 아래로 내려 놓은 표는 글 뒤에 둔다).
    let first_line = paragraph
        .line_segs
        .first()
        .map_or(1000, |seg| seg.line_height.max(1));
    let table_first = |table: &Table| {
        !table.common.treat_as_char
            && (!matches!(table.common.vert_rel_to, VertRelTo::Para)
                || (table.common.vertical_offset as i32) < first_line)
    };
    // 쪽 나누기 문단의 표를 문단 앞에 쓰면 표가 앞 쪽에 남는다: 나누기를 표 앞 1pt 문단으로 옮긴다.
    let break_before_tables = depth == 0
        && paragraph.column_type == rhwp::model::paragraph::ColumnBreakType::Page
        && paragraph
            .controls
            .iter()
            .any(|c| matches!(c, Control::Table(table) if table_first(table)));
    if break_before_tables {
        out.push_str(&TINY_PARAGRAPH.replace("<w:pPr>", "<w:pPr><w:pageBreakBefore/>"));
    }
    for control in &paragraph.controls {
        if let Control::Table(table) = control {
            if table_first(table) {
                write_table(out, info, media, table, depth + 1)?;
            }
        }
    }
    let captions: Vec<&Caption> = paragraph
        .controls
        .iter()
        .filter_map(|c| match c {
            Control::Picture(picture) => picture.caption.as_ref(),
            _ => None,
        })
        .collect();
    for caption in captions.iter().filter(|c| caption_on_top(c)) {
        write_caption(out, info, media, caption, depth)?;
    }
    out.push_str("<w:p>");
    let list = list_of(info, media, paragraph);
    let props = para_props_with(info, paragraph, list);
    if break_before_tables {
        out.push_str(&props.replacen("<w:pageBreakBefore/>", "", 1));
    } else {
        out.push_str(&props);
    }
    out.push_str(prefix);
    // 단 나누기(문단 앞). 쪽 나누기는 문단 속성(pageBreakBefore)으로 쓴다.
    if paragraph.column_type == rhwp::model::paragraph::ColumnBreakType::Column && depth == 0 {
        out.push_str(r#"<w:r><w:br w:type="column"/></w:r>"#);
    }
    let chars: Vec<char> = paragraph.text.chars().collect();
    // 각주·그림·도형 자리(글자 위치) — 그 앞에서 런을 끊고 그 자리에 넣는다(표는 문단 뒤).
    let mut notes: Vec<(usize, &Control)> = paragraph
        .control_text_positions()
        .into_iter()
        .zip(&paragraph.controls)
        .filter(|(_, control)| {
            matches!(
                control,
                Control::Footnote(_)
                    | Control::Endnote(_)
                    | Control::Picture(_)
                    | Control::Shape(_)
                    | Control::Equation(_)
                    | Control::Form(_)
                    | Control::AutoNumber(_)
                    | Control::HiddenComment(_)
                    | Control::Bookmark(_)
            )
        })
        .collect();
    // 같은 자리면 문서 순서를 지킨다(안정 정렬).
    notes.sort_by_key(|(position, _)| *position);
    let mut notes = notes.into_iter().peekable();
    let mut spans = field_spans(info, media, paragraph, chars.len(), depth)?;
    let slots = lang_slots(&chars);
    let mut start = 0;
    while start < chars.len() {
        spans.emit(out, start);
        while let Some((_, control)) = notes.next_if(|(position, _)| *position <= start) {
            write_inline(out, info, media, control, depth)?;
        }
        let next_note = notes
            .peek()
            .map_or(usize::MAX, |(position, _)| *position)
            .min(spans.next_boundary(start));
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
        let key_of = |index: usize| {
            let shape = shape_of(index);
            (shape, run_slot(info, shape, slots[index]))
        };
        let (shape, slot) = key_of(start);
        let mut end = start + 1;
        while end < chars.len() && end < next_note && key_of(end) == (shape, slot) {
            end += 1;
        }
        out.push_str("<w:r>");
        out.push_str(&run_props(info, shape, slot));
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
                // 머리말·꼬리말의 쪽 번호·전체 쪽 수 필드 글자(rhwp) → Word 필드
                '\u{0015}' | '\u{0016}' => {
                    flush(out, &mut text);
                    out.push_str("</w:r>");
                    out.push_str(&page_field(*ch == '\u{0016}', 0));
                    out.push_str("<w:r>");
                    out.push_str(&run_props(info, shape, slot));
                }
                c => text.push(*c),
            }
        }
        flush(out, &mut text);
        out.push_str("</w:r>");
        start = end;
    }
    // 문단 끝에서 열리는 범위(문단 끝의 빈 누름틀)
    spans.emit(out, chars.len());
    for (_, control) in notes {
        write_inline(out, info, media, control, depth)?;
    }
    spans.close_all(out);
    out.push_str("</w:p>");
    for caption in captions.iter().filter(|c| !caption_on_top(c)) {
        write_caption(out, info, media, caption, depth)?;
    }
    for control in &paragraph.controls {
        if let Control::Table(table) = control {
            if table_first(table) {
                continue;
            }
            write_table(out, info, media, table, depth + 1)?;
            // Word 는 표 바로 뒤에 문단이 있어야 한다(한글에는 없는 줄이라 1pt 로).
            out.push_str(TINY_PARAGRAPH);
        }
    }
    Ok(())
}

/// 영역 `k` 를 끝내는 자리(`<!--papy-region-k-->`, 뒤에 구역 속성으로 바꾼다)를 본문 끝 문단 속성에 넣는다.
/// 끝이 표 뒤 1pt 문단이면 그 문단에, 마지막 문단(`last_start` 에서 시작)이 문단 하나면 그 문단 속성 끝에,
/// 그 밖은 1pt 문단을 더해 넣는다.
fn end_region(body: &mut String, last_start: usize, k: usize) {
    let marker = format!("<!--papy-region-{k}-->");
    if body.ends_with(TINY_PARAGRAPH) {
        let at = body.len() - "</w:pPr></w:p>".len();
        body.insert_str(at, &marker);
        return;
    }
    let last = &body[last_start..];
    if last.starts_with("<w:p><w:pPr>") && last.ends_with("</w:p>") {
        if let Some(end) = last.find("</w:pPr>") {
            body.insert_str(last_start + end, &marker);
            return;
        }
    }
    body.push_str(&TINY_PARAGRAPH.replace("</w:pPr>", &format!("{marker}</w:pPr>")));
}

/// 표·그림 캡션이 개체 위에 놓이는가(나머지 — 아래·왼쪽·오른쪽 — 는 개체 뒤 문단으로 쓴다).
fn caption_on_top(caption: &Caption) -> bool {
    caption.direction == CaptionDirection::Top
}

/// 캡션 문단들. Word 에는 캡션 자리가 없어 개체 앞(위 캡션)·뒤 문단으로 쓰고, 첫 문단에 방향을
/// (`papy:caption`, Word 는 무시), 이어지는 문단에 `+` 를 붙여 Papy 가 다시 열 때 캡션으로 되돌린다.
fn write_caption(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    caption: &Caption,
    depth: usize,
) -> Result<()> {
    let direction = match caption.direction {
        CaptionDirection::Left => "left",
        CaptionDirection::Right => "right",
        CaptionDirection::Top => "top",
        CaptionDirection::Bottom => "bottom",
    };
    for (index, paragraph) in caption.paragraphs.iter().enumerate() {
        let mut body = String::new();
        write_paragraph(&mut body, info, media, paragraph, depth)?;
        let more = if index == 0 { "" } else { "+" };
        let tag = format!(r#"<w:p {CAPTION_ATTR}="{direction}{more}">"#);
        if let Some(at) = body.find("<w:p>") {
            body.replace_range(at..at + "<w:p>".len(), &tag);
        }
        out.push_str(&body);
    }
    Ok(())
}

/// 캡션 문단 표시 속성(Word 는 무시한다, `mc:Ignorable`).
pub(crate) const CAPTION_ATTR: &str = "papy:caption";

/// 표 뒤에 Word 가 요구하는 문단을 거의 높이 없이(1pt 고정 줄, 1pt 글자) 둔다.
const TINY_PARAGRAPH: &str = r#"<w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="20" w:lineRule="exact"/><w:rPr><w:sz w:val="2"/></w:rPr></w:pPr></w:p>"#;

/// 나란히 놓인 표들 → 테두리 없는 한 줄 표, 칸마다 표 하나. 표 사이 빈칸(문단의 공백)은 칸 너비에 나눠 더한다.
fn write_side_by_side(
    out: &mut String,
    info: &DocInfo,
    media: &mut Media,
    paragraph: &Paragraph,
    tables: &[&Table],
    depth: usize,
) -> Result<()> {
    let spaces = paragraph.text.chars().filter(|c| *c == ' ').count() as u32;
    // 빈칸 하나 ≈ 글자 크기의 절반(HWPUNIT). 글자 크기는 첫 글자 모양.
    let size = paragraph
        .char_shapes
        .first()
        .and_then(|r| info.char_shapes.get(r.char_shape_id as usize))
        .map_or(1000, |c| c.base_size.max(100) as u32);
    let gap = spaces * size / 2 / tables.len() as u32;
    out.push_str(
        r#"<w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/><w:tblBorders><w:top w:val="nil"/><w:left w:val="nil"/><w:bottom w:val="nil"/><w:right w:val="nil"/><w:insideH w:val="nil"/><w:insideV w:val="nil"/></w:tblBorders><w:tblLayout w:type="fixed"/><w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="0" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/><w:right w:w="0" w:type="dxa"/></w:tblCellMar></w:tblPr><w:tblGrid>"#,
    );
    let widths: Vec<u32> = tables
        .iter()
        .map(|t| twips(t.common.width.saturating_add(gap)).max(144))
        .collect();
    for width in &widths {
        let _ = write!(out, r#"<w:gridCol w:w="{width}"/>"#);
    }
    out.push_str("</w:tblGrid><w:tr>");
    for (table, width) in tables.iter().zip(&widths) {
        let _ = write!(
            out,
            r#"<w:tc><w:tcPr><w:tcW w:w="{width}" w:type="dxa"/></w:tcPr>"#
        );
        write_table(out, info, media, table, depth + 2)?;
        // 칸은 문단으로 끝나야 한다. 1pt 줄로 둬 칸이 높아지지 않게 한다.
        out.push_str(TINY_PARAGRAPH);
        out.push_str("</w:tc>");
    }
    out.push_str("</w:tr></w:tbl>");
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
    let widths = grid_widths(table, cols);
    if let Some(caption) = table.caption.as_ref().filter(|c| caption_on_top(c)) {
        write_caption(out, info, media, caption, depth)?;
    }
    // 테두리는 칸마다 쓴다(한글 표는 칸마다 테두리·배경이 다르다). 머리글 행은 쪽마다 되풀이.
    // 왼쪽 자리: 바깥 여백 + (문단·단 기준 떠 있는 표면) 가로 위치. 맨 바깥 표만.
    let mut indent = i32::from(table.common.margin.left);
    if !table.common.treat_as_char
        && matches!(
            table.common.horz_rel_to,
            HorzRelTo::Para | HorzRelTo::Column
        )
    {
        indent = indent.saturating_add(table.common.horizontal_offset as i32);
    }
    let indent = if depth <= 1 { indent / 5 } else { 0 };
    let float = if depth <= 1 {
        float_table_position(table)
    } else {
        String::new()
    };
    // 한글 "글자처럼 취급" 표: Papy 가 다시 열 때 그대로 둔다(Word 는 무시한다).
    let inline = if table.common.treat_as_char {
        r#" papy:inline="1""#
    } else {
        ""
    };
    let mut properties = format!(
        r#"<w:tbl><w:tblPr{inline}>{float}<w:tblW w:w="0" w:type="auto"/><w:tblInd w:w="{indent}" w:type="dxa"/><w:tblLayout w:type="fixed"/>"#
    );
    let margin = |v: i16| twips(u32::try_from(v.max(0)).unwrap_or(0));
    let _ = write!(
        properties,
        r#"<w:tblCellMar><w:top w:w="{}" w:type="dxa"/><w:left w:w="{}" w:type="dxa"/><w:bottom w:w="{}" w:type="dxa"/><w:right w:w="{}" w:type="dxa"/></w:tblCellMar></w:tblPr><w:tblGrid>"#,
        margin(table.padding.top),
        margin(table.padding.left),
        margin(table.padding.bottom),
        margin(table.padding.right),
    );
    out.push_str(&properties);
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
        // 행 높이: 이 행에서 시작하고 끝나는 칸의 높이(가장 큰 것)를 "최소"로 둔다.
        let height = table
            .cells
            .iter()
            .filter(|cell| cell.row as usize == row && cell.row_span <= 1)
            .map(|cell| cell.height)
            .max()
            .unwrap_or(0);
        let mut row_props = String::new();
        if height > 0 {
            // 한글 칸 높이는 테두리를 품지만 Word 는 행 높이에 테두리 굵기를 더한다(쪽을 꽉 채운 서식 표가 넘친다).
            let border = table
                .cells
                .iter()
                .filter(|cell| cell.row as usize == row)
                .map(|cell| cell_border_twips(info, cell.border_fill_id))
                .max()
                .unwrap_or(0);
            let value = twips(height);
            let _ = write!(
                row_props,
                r#"<w:trHeight w:val="{}" w:hRule="atLeast"/>"#,
                value.saturating_sub(border).max(value / 2)
            );
        }
        if table.repeat_header
            && table
                .cells
                .iter()
                .any(|c| c.row as usize == row && c.is_header)
        {
            row_props.push_str("<w:tblHeader/>");
        }
        // 행 끝의 빈 격자 칸(원본 행이 짧다): 빈 셀을 만들지 않고 `w:gridAfter` 로 둔다. 빈 셀로 채우면
        // 다시 열 때 칸이 는다.
        let trailing = {
            let mut col = 0;
            let mut end = 0;
            while col < cols {
                if let Some(cell) = start_at(row, col).or_else(|| covered_from_above(row, col)) {
                    col += (cell.col_span.max(1) as usize).min(cols - col);
                    end = col;
                } else {
                    col += 1;
                }
            }
            cols - end
        };
        if trailing > 0 && trailing < cols {
            let _ = write!(row_props, r#"<w:gridAfter w:val="{trailing}"/>"#);
        }
        if !row_props.is_empty() {
            let _ = write!(out, "<w:trPr>{row_props}</w:trPr>");
        }
        let mut col = 0;
        let cols_in_row = if trailing < cols {
            cols - trailing
        } else {
            cols
        };
        while col < cols_in_row {
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
                out.push_str(&cell_style(info, cell.border_fill_id));
                let v_align = match cell.vertical_align {
                    rhwp::model::table::VerticalAlign::Top => "top",
                    rhwp::model::table::VerticalAlign::Center => "center",
                    rhwp::model::table::VerticalAlign::Bottom => "bottom",
                };
                let _ = write!(out, r#"<w:vAlign w:val="{v_align}"/>"#);
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
                let width: u32 = widths[col..col + span].iter().sum();
                let _ = write!(out, r#"<w:tcW w:w="{width}" w:type="dxa"/>"#);
                if span > 1 {
                    let _ = write!(out, r#"<w:gridSpan w:val="{span}"/>"#);
                }
                out.push_str("<w:vMerge/>");
                out.push_str(&cell_style(info, cell.border_fill_id));
                out.push_str("</w:tcPr><w:p/></w:tc>");
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
    if let Some(caption) = table.caption.as_ref().filter(|c| !caption_on_top(c)) {
        write_caption(out, info, media, caption, depth)?;
    }
    Ok(())
}

/// 쪽·종이 기준으로 놓은 떠 있는 표(결재란을 쪽 아래에 붙이는 공문서 등) → `w:tblpPr`.
/// 문단 기준·글자처럼 놓은 표는 본문 흐름 그대로 둔다.
fn float_table_position(table: &Table) -> String {
    use rhwp::model::shape::{HorzAlign, TextWrap, VertAlign};
    let common = &table.common;
    // 글 앞·뒤에 띄운 표(그림 테두리 위에 겹친 안내문 표 등): Word 에는 그런 배치가 없어 글 기준 떠 있는 표로
    // 쓰고 배치를 `papy:wrap` 에 남긴다(Papy 로 다시 열면 겹친 채로 되돌린다. 전에는 위아래 배치가 되어 높이를 차지했다).
    let wrap = match common.text_wrap {
        TextWrap::InFrontOfText if !common.treat_as_char => r#" papy:wrap="front""#,
        TextWrap::BehindText if !common.treat_as_char => r#" papy:wrap="behind""#,
        _ => "",
    };
    if common.treat_as_char
        || (wrap.is_empty() && !matches!(common.vert_rel_to, VertRelTo::Page | VertRelTo::Paper))
    {
        return String::new();
    }
    let vert_anchor = match common.vert_rel_to {
        VertRelTo::Paper => "page",
        VertRelTo::Page => "margin",
        _ => "text",
    };
    let vertical = match common.vert_align {
        VertAlign::Center => r#" w:tblpYSpec="center""#.to_owned(),
        VertAlign::Bottom | VertAlign::Outside => r#" w:tblpYSpec="bottom""#.to_owned(),
        _ => format!(r#" w:tblpY="{}""#, common.vertical_offset as i32 / 5),
    };
    let horz_anchor = match common.horz_rel_to {
        HorzRelTo::Paper => "page",
        HorzRelTo::Page => "margin",
        _ => "text",
    };
    let horizontal = match common.horz_align {
        HorzAlign::Center => r#" w:tblpXSpec="center""#.to_owned(),
        HorzAlign::Right | HorzAlign::Outside => r#" w:tblpXSpec="right""#.to_owned(),
        _ => format!(r#" w:tblpX="{}""#, common.horizontal_offset as i32 / 5),
    };
    format!(
        r#"<w:tblpPr w:leftFromText="0" w:rightFromText="0" w:vertAnchor="{vert_anchor}" w:horzAnchor="{horz_anchor}"{horizontal}{vertical}{wrap}/>"#
    )
}

/// 열 너비(twip). 행마다 그 행을 차지하는 칸(위에서 세로 병합된 칸 포함)의 너비를 왼쪽부터 더해 열 경계 자리를
/// 얻고, 경계마다 여러 행이 낸 값의 가운데 값을 쓴다(행마다 칸 너비가 조금씩 어긋나는 한글 양식 표). 어느 행도
/// 정하지 않은 경계는 앞뒤 경계 사이를 고르게 나눈다.
fn grid_widths(table: &Table, cols: usize) -> Vec<u32> {
    let rows = table.row_count as usize;
    let mut votes: Vec<Vec<i64>> = vec![Vec::new(); cols + 1];
    votes[0].push(0);
    for row in 0..rows {
        let mut cells: Vec<(usize, usize, i64)> = table
            .cells
            .iter()
            .filter(|c| {
                let (r, span) = (c.row as usize, c.row_span.max(1) as usize);
                r <= row && row < r + span && (c.col as usize) < cols
            })
            .map(|c| {
                let start = c.col as usize;
                let end = (start + c.col_span.max(1) as usize).min(cols);
                (start, end, i64::from(c.width))
            })
            .collect();
        cells.sort_by_key(|c| c.0);
        // 왼쪽부터 빈틈없이 이어진 칸만 더한다(빈 칸이 있으면 거기서 멈춘다).
        let (mut x, mut next) = (0i64, 0usize);
        for (start, end, width) in cells {
            if start != next || width <= 0 {
                break;
            }
            x += width;
            votes[end].push(x);
            next = end;
        }
    }
    let mut edge: Vec<Option<i64>> = votes
        .iter_mut()
        .map(|v| {
            if v.is_empty() {
                return None;
            }
            v.sort_unstable();
            Some(v[v.len() / 2])
        })
        .collect();
    edge[0] = Some(0);
    if edge[cols].is_none() {
        let total = table
            .cells
            .iter()
            .fold(
                std::collections::HashMap::<u16, i64>::new(),
                |mut rows, c| {
                    *rows.entry(c.row).or_default() += i64::from(c.width);
                    rows
                },
            )
            .into_values()
            .max()
            .filter(|w| *w > 0)
            .unwrap_or(10_000 * cols as i64);
        edge[cols] = Some(total.max(edge.iter().flatten().copied().max().unwrap_or(0)));
    }
    // 앞 경계보다 작거나 같은 값(어긋난 행)은 버리고 다시 채운다.
    let mut last = 0i64;
    for slot in edge.iter_mut().skip(1) {
        if let Some(v) = *slot {
            if v <= last {
                *slot = None;
            } else {
                last = v;
            }
        }
    }
    if edge[cols].is_none() {
        edge[cols] = Some(last + 720);
    }
    let mut i = 0;
    while i < cols {
        if edge[i + 1].is_some() {
            i += 1;
            continue;
        }
        let next = (i + 1..=cols).find(|&k| edge[k].is_some()).unwrap_or(cols);
        let (a, b) = (edge[i].unwrap_or(0), edge[next].unwrap_or(0));
        for (k, slot) in edge.iter_mut().enumerate().take(next).skip(i + 1) {
            *slot = Some(a + (b - a) * (k - i) as i64 / (next - i) as i64);
        }
        i = next;
    }
    edge.windows(2)
        .map(|w| {
            let width = w[1].unwrap_or(0) - w[0].unwrap_or(0);
            // 한글 표는 격자 열이 아주 가늘 수 있다(칸 하나가 열 수십 개에 걸침). 0 만 막는다.
            twips(u32::try_from(width).unwrap_or(0)).max(10)
        })
        .collect()
}

/// HWP 선 굵기 번호 → mm.
const BORDER_WIDTH_MM: [f32; 16] = [
    0.1, 0.12, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0,
];

/// 칸 테두리(`w:tcBorders`)와 배경(`w:shd`).
/// 칸 위·아래 테두리 가운데 굵은 것(twip). 테두리가 없으면 0.
fn cell_border_twips(info: &DocInfo, border_fill_id: u16) -> u32 {
    let Some(fill) = border_fill_id
        .checked_sub(1)
        .and_then(|i| info.border_fills.get(i as usize))
    else {
        return 0;
    };
    [2usize, 3]
        .iter()
        .map(|&index| &fill.borders[index])
        .filter(|line| line.line_type != rhwp::model::style::BorderLineType::None)
        .map(|line| {
            let mm = BORDER_WIDTH_MM[usize::from(line.width.min(15))];
            (mm * 72.0 / 25.4 * 20.0).round() as u32
        })
        .max()
        .unwrap_or(0)
}

/// 한글 테두리선 → Word 선 (`w:val`, `w:sz`(1/8 pt), 색). 선이 없으면 None.
fn border_line(line: &rhwp::model::style::BorderLine) -> Option<(&'static str, u32, String)> {
    use rhwp::model::style::BorderLineType as Line;
    let value = match line.line_type {
        Line::None => return None,
        Line::Solid => "single",
        Line::Dash | Line::LongDash => "dashed",
        Line::Dot | Line::Circle => "dotted",
        Line::DashDot => "dotDash",
        Line::DashDotDot => "dotDotDash",
        Line::Double => "double",
        _ => "single",
    };
    // 1/8 pt, Word 범위 2~96
    let mm = BORDER_WIDTH_MM[usize::from(line.width.min(15))];
    let size = ((mm * 72.0 / 25.4 * 8.0).round() as u32).clamp(2, 96);
    Some((
        value,
        size,
        rgb(line.color).unwrap_or_else(|| "000000".into()),
    ))
}

/// 문단 테두리·배경(`w:pBdr`, `w:shd`). 테두리와 글 사이 간격은 `w:space`(pt)로 쓴다.
/// 이어진 문단의 같은 테두리를 Word 는 늘 한 상자로 묶고, 한글은 "문단 테두리 연결"이 켜졌을 때 묶는다.
fn para_border(info: &DocInfo, shape: &rhwp::model::style::ParaShape) -> String {
    let Some(fill) = shape
        .border_fill_id
        .checked_sub(1)
        .and_then(|i| info.border_fills.get(i as usize))
    else {
        return String::new();
    };
    let mut out = String::new();
    let mut sides = String::new();
    // HWP 순서는 왼쪽·오른쪽·위·아래, Word 스키마 순서는 위·왼쪽·아래·오른쪽·사이.
    let space = |index: usize| {
        let hwpunit = i32::from(shape.border_spacing[index]).max(0);
        ((hwpunit + 50) / 100).min(31)
    };
    for (side, index) in [("top", 2), ("left", 0), ("bottom", 3), ("right", 1)] {
        if let Some((value, size, color)) = border_line(&fill.borders[index]) {
            let _ = write!(
                sides,
                r#"<w:{side} w:val="{value}" w:sz="{size}" w:space="{}" w:color="{color}"/>"#,
                space(index)
            );
        }
    }
    if !sides.is_empty() {
        // 한글 "문단 테두리 연결"(attr1 비트 28)이 꺼졌으면 Papy 가 다시 열 때만 따로 그린다(Word 는 늘 묶는다).
        let connect = if shape.attr1 & (1 << 28) != 0 {
            ""
        } else {
            r#" papy:connect="0""#
        };
        let _ = write!(out, "<w:pBdr{connect}>{sides}</w:pBdr>");
    }
    if let (FillType::Solid, Some(solid)) = (&fill.fill.fill_type, &fill.fill.solid) {
        if let Some(color) = rgb(solid.background_color).filter(|c| c != "FFFFFF") {
            let _ = write!(
                out,
                r#"<w:shd w:val="clear" w:color="auto" w:fill="{color}"/>"#
            );
        }
    }
    out
}

fn cell_style(info: &DocInfo, border_fill_id: u16) -> String {
    let Some(fill) = border_fill_id
        .checked_sub(1)
        .and_then(|i| info.border_fills.get(i as usize))
    else {
        return String::new();
    };
    use rhwp::model::style::BorderLineType as Line;
    let mut out = String::from("<w:tcBorders>");
    // HWP 순서는 왼쪽·오른쪽·위·아래, Word 스키마 순서는 위·왼쪽·아래·오른쪽.
    for (side, index) in [("top", 2), ("left", 0), ("bottom", 3), ("right", 1)] {
        let line = &fill.borders[index];
        let value = match line.line_type {
            Line::None => "nil",
            Line::Solid => "single",
            Line::Dash | Line::LongDash => "dashed",
            Line::Dot | Line::Circle => "dotted",
            Line::DashDot => "dotDash",
            Line::DashDotDot => "dotDotDash",
            Line::Double => "double",
            _ => "single",
        };
        if value == "nil" {
            let _ = write!(out, r#"<w:{side} w:val="nil"/>"#);
            continue;
        }
        // 1/8 pt, Word 범위 2~96
        let mm = BORDER_WIDTH_MM[usize::from(line.width.min(15))];
        let size = ((mm * 72.0 / 25.4 * 8.0).round() as u32).clamp(2, 96);
        let color = rgb(line.color).unwrap_or_else(|| "000000".into());
        let _ = write!(
            out,
            r#"<w:{side} w:val="{value}" w:sz="{size}" w:space="0" w:color="{color}"/>"#
        );
    }
    // 칸 대각선: 속성 비트 2~4 = "/"(오른쪽 위 → 왼쪽 아래), 5~7 = "\"(왼쪽 위 → 오른쪽 아래).
    let diagonal = &fill.diagonal;
    if diagonal.diagonal_type != 0 {
        let value = match diagonal.diagonal_type {
            2 | 6 => "dashed",
            3 | 7 => "dotted",
            8 => "double",
            _ => "single",
        };
        let mm = BORDER_WIDTH_MM[usize::from(diagonal.width.min(15))];
        let size = ((mm * 72.0 / 25.4 * 8.0).round() as u32).clamp(2, 96);
        let color = rgb(diagonal.color).unwrap_or_else(|| "000000".into());
        for (side, bits) in [
            ("tl2br", (fill.attr >> 5) & 0x07),
            ("tr2bl", (fill.attr >> 2) & 0x07),
        ] {
            if bits != 0 {
                let _ = write!(
                    out,
                    r#"<w:{side} w:val="{value}" w:sz="{size}" w:space="0" w:color="{color}"/>"#
                );
            }
        }
    }
    out.push_str("</w:tcBorders>");
    if let (FillType::Solid, Some(solid)) = (&fill.fill.fill_type, &fill.fill.solid) {
        if let Some(color) = rgb(solid.background_color) {
            let _ = write!(
                out,
                r#"<w:shd w:val="clear" w:color="auto" w:fill="{color}"/>"#
            );
        }
    }
    out
}

/// `word/fontTable.xml`: 문서가 쓰는 글꼴마다 계열(명조·고딕·고정폭)·한글 문자 집합·대체 글꼴.
/// 글꼴이 없는 PC 의 Word·LibreOffice 는 이 정보로 대체 글꼴을 고른다(명조 글꼴이 고딕으로 바뀌지 않게).
fn font_table_xml(info: &DocInfo) -> String {
    let mut names: Vec<&str> = Vec::new();
    for lang in 0..2 {
        for font in info.font_faces.get(lang).into_iter().flatten() {
            let name = font.name.as_str();
            if !name.is_empty() && !names.contains(&name) {
                names.push(name);
            }
        }
    }
    let mut xml = format!(
        r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:fonts {NAMESPACES}>"#
    );
    for name in names {
        let lower = name.to_lowercase();
        let fixed = [
            "굴림체",
            "돋움체",
            "바탕체",
            "궁서체",
            "coding",
            "mono",
            "courier",
        ]
        .iter()
        .any(|k| lower.contains(k));
        let serif = [
            "명조", "바탕", "궁서", "myeongjo", "batang", "serif", "times", "신명", "바른",
        ]
        .iter()
        .any(|k| lower.contains(k))
            && !lower.contains("sans");
        let (family, alt) = if fixed {
            ("modern", if serif { "BatangChe" } else { "GulimChe" })
        } else if serif {
            ("roman", "Batang")
        } else {
            ("swiss", "Malgun Gothic")
        };
        let _ = write!(
            xml,
            r#"<w:font w:name="{}"><w:altName w:val="{alt}"/><w:charset w:val="81"/><w:family w:val="{family}"/><w:pitch w:val="{}"/></w:font>"#,
            xml_escape(name),
            if fixed { "fixed" } else { "variable" }
        );
    }
    xml.push_str("</w:fonts>");
    xml
}

/// 쪽 번호 필드 런(`PAGE`·`NUMPAGES`). `format` 은 한글 번호 형식 코드(표 134: 0 숫자, 2·3 로마 숫자).
fn page_field(total: bool, format: u8) -> String {
    let name = if total { "NUMPAGES" } else { "PAGE" };
    let switch = match format {
        2 => r" \* ROMAN",
        3 => r" \* roman",
        4 => r" \* ALPHABETIC",
        5 => r" \* alphabetic",
        _ => "",
    };
    format!(r#"<w:fldSimple w:instr=" {name}{switch} "><w:r><w:t>1</w:t></w:r></w:fldSimple>"#)
}

/// 한글 주 번호 모양 → Word 번호 형식.
fn note_num_format(format: rhwp::model::footnote::NumberFormat) -> &'static str {
    use rhwp::model::footnote::NumberFormat as F;
    match format {
        F::CircledDigit => "decimalEnclosedCircle",
        F::UpperRoman => "upperRoman",
        F::LowerRoman => "lowerRoman",
        F::UpperAlpha => "upperLetter",
        F::LowerAlpha => "lowerLetter",
        F::HangulSyllable => "ganada",
        F::HangulJamo => "chosung",
        F::HangulDigit => "koreanCounting",
        F::HanjaDigit => "ideographTraditional",
        _ => "decimal",
    }
}

/// 단 정의가 바뀌는 자리마다 나눈 영역: (시작 구역, 시작 문단, 단 정의). 첫 영역은 문서 처음부터.
/// rhwp 는 한 구역 안에서도 문단마다 단 정의를 새로 둘 수 있어(1단 ↔ 2단 등), DOCX 에서는 이어지는 구역으로 나눈다.
/// 구역 중간 문단에 놓인 머리말·꼬리말(그 쪽부터 바뀐다)도 Word 에서는 구역마다라서 그 문단에서 영역을 나눈다.
fn column_regions(document: &Document) -> Vec<(usize, usize, Option<&ColumnDef>)> {
    let mut regions: Vec<(usize, usize, Option<&ColumnDef>)> = vec![(0, 0, None)];
    for (s, section) in document.sections.iter().enumerate() {
        for (p, paragraph) in section.paragraphs.iter().enumerate() {
            let def = paragraph.controls.iter().find_map(|c| match c {
                Control::ColumnDef(def) => Some(def),
                _ => None,
            });
            let header = paragraph
                .controls
                .iter()
                .any(|c| matches!(c, Control::Header(_) | Control::Footer(_)));
            // 한글 구역은 첫 문단에 단 정의가 없어도 새 쪽 영역으로 시작한다.
            if def.is_none() && !(header && p > 0) && !(s > 0 && p == 0) {
                continue;
            }
            let def = def.or_else(|| regions.last().and_then(|r| r.2));
            if (s, p) == (0, 0) {
                regions[0].2 = def;
            } else {
                regions.push((s, p, def));
            }
        }
    }
    regions
}

/// 영역 하나의 구역 속성. `start` 는 이 영역이 어떻게 시작하는가(`w:type`, 첫 영역은 빈 문자열).
fn region_props(
    document: &Document,
    references: &str,
    title_page: &str,
    columns: Option<&ColumnDef>,
    start: &str,
    column_leading: u32,
) -> String {
    let multi = columns.is_some_and(|def| def.column_count > 1);
    let columns = columns
        .filter(|def| def.column_count > 1)
        .map(|def| {
            format!(
                r#"<w:cols w:num="{}" w:space="{}"/>"#,
                def.column_count.min(16),
                twips(u32::try_from(def.spacing.max(0)).unwrap_or(0))
            )
        })
        .unwrap_or_default();
    // 다단: 한글은 단 마지막 줄의 줄 간격(글자 아래 빈 곳)이 본문 아래로 넘쳐도 되지만 Word 는 줄 높이 전체가
    // 단 안에 들어야 해서 단마다 그만큼(본문 10~11pt 160% 면 6~7pt) 넘쳐 단 나누기가 한 단씩 밀렸다(2단 시험지).
    // 아래 여백을 그만큼 줄여 글자가 한글과 같은 자리에서 끝나게 한다.
    let extend = if multi { column_leading } else { 0 };
    section_props(document, references, title_page, &columns, start, extend)
}

/// 본문 글(글자 수로 무게)에서 흔히 쓰인 줄 간격 여분(twip) 가운데 가장 큰 것: 배수 줄 간격의 (줄 높이 − 글자 크기), 고정 줄 간격의 (간격 − 글자 크기).
fn body_leading(document: &Document) -> u32 {
    let info = &document.doc_info;
    let mut counts: HashMap<u32, usize> = HashMap::new();
    for paragraph in document.sections.iter().flat_map(|s| &s.paragraphs) {
        if paragraph.text.trim().is_empty() {
            continue;
        }
        let Some(shape) = info.para_shapes.get(paragraph.para_shape_id as usize) else {
            continue;
        };
        let size = paragraph
            .char_shapes
            .iter()
            .filter_map(|r| info.char_shapes.get(r.char_shape_id as usize))
            .map(|c| c.base_size)
            .max()
            .filter(|s| *s > 0)
            .unwrap_or(1000);
        // 1/100 pt → twip(× 20 ÷ 100)
        let leading = match shape.line_spacing_type {
            LineSpacingType::Percent if shape.line_spacing > 100 => {
                size * (shape.line_spacing - 100) / 500
            }
            LineSpacingType::Fixed => {
                (twips(shape.line_spacing.max(0) as u32) as i32).saturating_sub(size / 5)
            }
            _ => 0,
        };
        // 글자 수로 무게를 단다(짧은 보기 문단이 많아도 본문 글의 줄 간격을 고른다).
        *counts
            .entry(u32::try_from(leading.max(0)).unwrap_or(0))
            .or_default() += paragraph.text.chars().count();
    }
    // 글의 5% 넘게 쓰인 줄 간격 가운데 가장 큰 것: 단 마지막 줄이 어느 것이어도 들어가게 한다
    // (시험지: 본문 11.5pt 150% 는 5.75pt, 지문 13.5pt 150% 는 6.75pt — Word 실측 넘침 7pt).
    let total: usize = counts.values().sum();
    counts
        .into_iter()
        .filter(|(_, count)| *count * 20 >= total)
        .map(|(leading, _)| leading)
        .max()
        .map_or(0, |leading| leading.min(400))
}

fn section_props(
    document: &Document,
    references: &str,
    title_page: &str,
    columns: &str,
    start: &str,
    extend_bottom: u32,
) -> String {
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
    // 아래 여백(본문 아래 + 꼬리말). 줄인 경우 원래 값을 `papy:hwpBottom` 으로 남겨 Papy 가 다시 열 때 쓴다.
    let bottom = twips(page.margin_bottom + page.margin_footer);
    let extend = extend_bottom.min(twips(page.margin_bottom));
    let hwp_bottom = if extend > 0 {
        format!(r#" papy:hwpBottom="{bottom}""#)
    } else {
        String::new()
    };
    let orient = if width > height {
        r#" w:orient="landscape""#
    } else {
        ""
    };
    let notes = document
        .sections
        .first()
        .map(|s| {
            let def = &s.section_def;
            format!(
                r#"<w:footnotePr><w:numFmt w:val="{}"/><w:numStart w:val="{}"/></w:footnotePr><w:endnotePr><w:numFmt w:val="{}"/><w:numStart w:val="{}"/></w:endnotePr>"#,
                note_num_format(def.footnote_shape.number_format),
                def.footnote_shape.start_number.max(1),
                note_num_format(def.endnote_shape.number_format),
                def.endnote_shape.start_number.max(1),
            )
        })
        .unwrap_or_default();
    format!(
        r#"<w:sectPr>{references}{notes}{start}<w:pgSz w:w="{width}" w:h="{height}"{orient}/><w:pgMar w:top="{}" w:right="{}" w:bottom="{}"{hwp_bottom} w:left="{}" w:header="{}" w:footer="{}" w:gutter="{}"/>{columns}{title_page}</w:sectPr>"#,
        twips(page.margin_top + page.margin_header),
        twips(page.margin_right),
        bottom.saturating_sub(extend).max(twips(page.margin_footer)),
        twips(page.margin_left),
        twips(page.margin_header),
        twips(page.margin_footer),
        twips(page.margin_gutter),
    )
}

const CONTENT_TYPES: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Default Extension="jpeg" ContentType="image/jpeg"/><Default Extension="gif" ContentType="image/gif"/><Default Extension="bmp" ContentType="image/bmp"/><Default Extension="wmf" ContentType="image/x-wmf"/><Default Extension="emf" ContentType="image/x-emf"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>"#;

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
    for (index, link) in media.links.iter().enumerate() {
        let _ = write!(
            rels,
            r#"<Relationship Id="rIdLink{}" Type="{REL}/hyperlink" Target="{}" TargetMode="External"/>"#,
            index + 1,
            xml_escape(link)
        );
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

const NAMESPACES: &str = r#"xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape" xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:papy="urn:papy:docx" mc:Ignorable="papy""#;

/// 문단의 머리말·꼬리말 컨트롤. 한글(HWPX)은 표 칸 문단에도 머리말을 둘 수 있어 칸 안도 찾는다.
fn nested_header_footers<'a>(
    paragraph: &'a Paragraph,
    depth: usize,
    found: &mut impl FnMut(&'a Control),
) {
    for control in &paragraph.controls {
        match control {
            Control::Header(_) | Control::Footer(_) => found(control),
            Control::Table(table) if depth < 8 => {
                for cell in &table.cells {
                    for inner in &cell.paragraphs {
                        nested_header_footers(inner, depth + 1, found);
                    }
                }
            }
            _ => {}
        }
    }
}

/// 머리말·꼬리말을 부분으로 쓰고 영역([`column_regions`])마다 sectPr 참조를 돌려준다. 영역 안 본문 문단에 놓인
/// 머리말·꼬리말을 그 영역 것으로 쓰고, 영역 안에서 같은 종류·쪽은 처음 것만 쓴다. 머리말이 없는 영역은 참조를
/// 두지 않는다 — Word 는 앞 구역 것을 이어 쓴다(한글도 다음 구역에 앞 구역 머리말이 이어진다).
fn header_footer_parts(
    document: &Document,
    regions: &[(usize, usize, Option<&ColumnDef>)],
    media: &mut Media,
    parts: &mut Vec<Part>,
) -> Result<(Vec<String>, &'static str)> {
    let info = &document.doc_info;
    let mut seen: Vec<(usize, bool, &'static str)> = Vec::new();
    let mut per_region: Vec<(String, String)> = vec![Default::default(); regions.len()];
    let mut defaults: [Option<String>; 2] = [None, None];
    let Some(section) = document.sections.first() else {
        return Ok((vec![String::new(); regions.len()], ""));
    };
    let mut controls: Vec<(usize, &Control)> = Vec::new();
    let mut region = 0usize;
    for (s, body) in document.sections.iter().enumerate() {
        for (p, paragraph) in body.paragraphs.iter().enumerate() {
            while regions
                .get(region + 1)
                .is_some_and(|r| (r.0, r.1) <= (s, p))
            {
                region += 1;
            }
            nested_header_footers(paragraph, 0, &mut |c| controls.push((region, c)));
        }
    }
    for (region, control) in controls {
        let references = &mut per_region[region];
        let (is_header, apply, paragraphs) = match control {
            Control::Header(header) => (true, header.apply_to, &header.paragraphs),
            Control::Footer(footer) => (false, footer.apply_to, &footer.paragraphs),
            _ => continue,
        };
        let kind = match apply {
            HeaderFooterApply::Even => "even",
            _ => "default",
        };
        if seen.contains(&(region, is_header, kind)) {
            continue;
        }
        seen.push((region, is_header, kind));
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
        if kind == "default" && region == 0 {
            defaults[usize::from(!is_header)] = Some(rel.clone());
        }
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
    // 쪽 번호 위치(한글 "쪽 번호 매기기"): 머리말·꼬리말에 쪽 번호 필드 문단을 더한다(없으면 만든다).
    let page_number = section
        .paragraphs
        .iter()
        .take(4)
        .flat_map(|p| &p.controls)
        .find_map(|c| match c {
            Control::PageNumberPos(p) if p.position != 0 => Some(p.clone()),
            _ => None,
        });
    let references = &mut per_region[0];
    if let Some(pnp) = page_number {
        // 1·2·3·7·9 = 위(머리말), 그 밖 = 아래(꼬리말). 1·4 왼쪽, 3·6 오른쪽, 그 밖 가운데.
        let top = matches!(pnp.position, 1..=3 | 7 | 9);
        let jc = match pnp.position {
            1 | 4 => "left",
            3 | 6 => "right",
            _ => "center",
        };
        let deco = |c: char| {
            if c == '\0' {
                String::new()
            } else {
                c.to_string()
            }
        };
        let (dash, prefix, suffix) = (
            deco(pnp.dash_char),
            deco(pnp.prefix_char),
            deco(pnp.suffix_char),
        );
        let (before, after) = if dash.is_empty() {
            (prefix, suffix)
        } else {
            (format!("{dash} {prefix}"), format!("{suffix} {dash}"))
        };
        let run = |t: &str| {
            if t.is_empty() {
                String::new()
            } else {
                format!(
                    r#"<w:r><w:t xml:space="preserve">{}</w:t></w:r>"#,
                    xml_escape(t)
                )
            }
        };
        let paragraph = format!(
            r#"<w:p><w:pPr><w:jc w:val="{jc}"/></w:pPr>{}{}{}</w:p>"#,
            run(&before),
            page_field(false, pnp.format),
            run(&after)
        );
        let (tag, name, side) = if top {
            ("hdr", "header", 0)
        } else {
            ("ftr", "footer", 1)
        };
        match defaults[side]
            .as_ref()
            .and_then(|rel| parts.iter_mut().find(|p| p.rel.as_ref() == Some(rel)))
        {
            Some(part) => {
                let close = format!("</w:{tag}>");
                if let Some(at) = part.xml.rfind(&close) {
                    part.xml.insert_str(at, &paragraph);
                }
            }
            None => {
                let index = parts.len() + 1;
                let rel = format!("rIdPart{index}");
                let reference = format!(r#"<w:{name}Reference w:type="default" r:id="{rel}"/>"#);
                if top {
                    &mut references.0
                } else {
                    &mut references.1
                }
                .push_str(&reference);
                defaults[side] = Some(rel.clone());
                parts.push(Part {
                    name: format!("{name}{index}.xml"),
                    rel: Some(rel),
                    kind: name,
                    xml: format!(
                        r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:{tag} {NAMESPACES}>{paragraph}</w:{tag}>"#
                    ),
                });
            }
        }
    }
    // 기본 탭 간격(한글 기본 40pt, Word 기본 36pt)과 짝수·홀수 쪽 머리말.
    let tab = document
        .sections
        .first()
        .map(|s| s.section_def.default_tab_spacing / 2)
        .filter(|v| *v > 0)
        .unwrap_or(4000);
    let even = if seen.iter().any(|(_, _, kind)| *kind == "even") {
        "<w:evenAndOddHeaders/>"
    } else {
        ""
    };
    parts.push(Part {
        name: "settings.xml".into(),
        rel: Some("rIdSettings".into()),
        kind: "settings",
        xml: format!(
            r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings {NAMESPACES}><w:defaultTabStop w:val="{}"/>{even}</w:settings>"#,
            twips(tab)
        ),
    });
    // 첫 쪽 감추기: titlePg 로 첫 쪽 머리말·꼬리말을 비우고, 감추지 않은 쪽은 기본 것을 첫 쪽에도 건다.
    let hide = section
        .paragraphs
        .first()
        .into_iter()
        .flat_map(|p| &p.controls)
        .find_map(|c| match c {
            Control::PageHide(h) => Some([h.hide_header, h.hide_footer]),
            _ => None,
        })
        .unwrap_or_default();
    let mut title_page = "";
    if hide.iter().any(|&h| h) {
        title_page = "<w:titlePg/>";
        for (side, name) in [(0, "header"), (1, "footer")] {
            if let (false, Some(rel)) = (hide[side], &defaults[side]) {
                let reference = format!(r#"<w:{name}Reference w:type="first" r:id="{rel}"/>"#);
                if side == 0 {
                    &mut references.0
                } else {
                    &mut references.1
                }
                .push_str(&reference);
            }
        }
    }
    Ok((
        per_region.into_iter().map(|(h, f)| h + &f).collect(),
        title_page,
    ))
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
    docx_from_document_with(document, outline, None, true)
}

/// [`docx_from_document_with_outline`] 에 더해, 수식을 `render_equation` 이 그린 PNG 그림으로 넣는다.
///
/// `hangul_source` 면 한글 문서(HWP·HWPX)다: 다단 구역의 아래 여백을 한글 조판에 맞춘다([`body_leading`]).
/// DOCX·DOC 에서 연 문서는 Word 조판 그대로라 맞추지 않는다.
pub fn docx_from_document_with(
    document: &Document,
    outline: &HashMap<(usize, usize), u8>,
    render_equation: Option<EquationRenderer<'_>>,
    hangul_source: bool,
) -> Result<Vec<u8>> {
    let info = &document.doc_info;
    let column_leading = if hangul_source {
        body_leading(document)
    } else {
        0
    };
    let mut media = Media {
        contents: &document.bin_data_content,
        files: Vec::new(),
        render_equation,
        outline_numbering: document
            .sections
            .first()
            .map_or(0, |s| s.section_def.outline_numbering_id),
        lists: Vec::new(),
        note_marks: {
            let mark = |shape: &rhwp::model::footnote::FootnoteShape| {
                let text = |c: char| {
                    if c == '\0' {
                        String::new()
                    } else {
                        c.to_string()
                    }
                };
                (
                    text(shape.prefix_char),
                    text(shape.suffix_char),
                    shape.number_code_superscript,
                )
            };
            document.sections.first().map_or_else(
                || std::array::from_fn(|_| (String::new(), String::new(), true)),
                |s| {
                    [
                        mark(&s.section_def.footnote_shape),
                        mark(&s.section_def.endnote_shape),
                    ]
                },
            )
        },
        next_object: 0,
        footnotes: Vec::new(),
        endnotes: Vec::new(),
        comments: Vec::new(),
        links: Vec::new(),
        bookmarks: 0,
    };
    let mut body = String::new();
    let regions = column_regions(document);
    // 영역이 바뀌는 문단 앞에서 앞 영역을 끝낸다: 앞 문단 속성에 구역 속성을 넣는다(구역 속성은 머리말 관계를 만든 뒤
    // 채운다). 따로 문단을 더하면 쪽을 꽉 채운 영역 뒤에 빈 쪽이 생긴다.
    let mut region_ends = 0usize;
    let mut last_start = 0usize;
    for (section_index, section) in document.sections.iter().enumerate() {
        for (paragraph_index, paragraph) in section.paragraphs.iter().enumerate() {
            if regions
                .iter()
                .skip(1)
                .any(|r| (r.0, r.1) == (section_index, paragraph_index))
            {
                end_region(&mut body, last_start, region_ends);
                region_ends += 1;
            }
            let start = body.len();
            last_start = start;
            write_paragraph(&mut body, info, &mut media, paragraph, 0)?;
            if let Some(&level) = outline.get(&(section_index, paragraph_index)) {
                set_outline_level(&mut body, start, level);
            }
        }
    }
    let mut extra: Vec<Part> = Vec::new();
    let (references, title_page) = header_footer_parts(document, &regions, &mut media, &mut extra)?;
    for (notes, tag, kind, rel) in [
        (&media.footnotes, "footnote", "footnotes", "rIdFootnotes"),
        (&media.endnotes, "endnote", "endnotes", "rIdEndnotes"),
    ] {
        if notes.is_empty() {
            continue;
        }
        let mut xml = format!(
            r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:{kind} {NAMESPACES}><w:{tag} w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:{tag}><w:{tag} w:type="continuationSeparator" w:id="0"><w:p><w:r><w:continuationSeparator/></w:r></w:p></w:{tag}>"#
        );
        for (index, note) in notes.iter().enumerate() {
            let _ = write!(xml, r#"<w:{tag} w:id="{}">{note}</w:{tag}>"#, index + 1);
        }
        let _ = write!(xml, "</w:{kind}>");
        extra.push(Part {
            name: format!("{kind}.xml"),
            rel: Some(rel.into()),
            kind,
            xml,
        });
    }
    if !media.comments.is_empty() {
        let mut xml = format!(
            r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:comments {NAMESPACES}>"#
        );
        for (id, (author, comment)) in media.comments.iter().enumerate() {
            let author = xml_escape(author);
            let _ = write!(
                xml,
                r#"<w:comment w:id="{id}" w:author="{author}">{comment}</w:comment>"#
            );
        }
        xml.push_str("</w:comments>");
        extra.push(Part {
            name: "comments.xml".into(),
            rel: Some("rIdComments".into()),
            kind: "comments",
            xml,
        });
    }
    extra.push(Part {
        name: "fontTable.xml".into(),
        rel: Some("rIdFontTable".into()),
        kind: "fontTable",
        xml: font_table_xml(info),
    });
    if !media.lists.is_empty() {
        extra.push(Part {
            name: "numbering.xml".into(),
            rel: Some("rIdNumbering".into()),
            kind: "numbering",
            xml: numbering_xml(info, &media.lists),
        });
    }
    // 영역 k 의 구역 속성: 첫 영역만 "첫 쪽 다르게", 뒤 영역은 이어서(구역 첫 문단에서 바뀌면 새 쪽).
    let props = |k: usize| {
        let (s, p, columns) = regions[k];
        let start = match (k, p) {
            (0, _) => "",
            (_, 0) if s > 0 => r#"<w:type w:val="nextPage"/>"#,
            _ => r#"<w:type w:val="continuous"/>"#,
        };
        let title = if k == 0 { title_page } else { "" };
        region_props(
            document,
            &references[k],
            title,
            columns,
            start,
            column_leading,
        )
    };
    for k in 0..region_ends {
        body = body.replacen(&format!("<!--papy-region-{k}-->"), &props(k), 1);
    }
    let xml = format!(
        r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document {NAMESPACES}><w:body>{body}{}</w:body></w:document>"#,
        props(regions.len() - 1)
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
        .filter(|part| {
            !matches!(part.kind, "settings" | "numbering" | "fontTable") && !media.files.is_empty()
        })
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
