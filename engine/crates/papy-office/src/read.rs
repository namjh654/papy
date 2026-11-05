//! DOCX → HTML. rhwp 의 HTML 붙여넣기(`paste_html_native`)가 받는 범위의 태그만 만든다:
//! `<p style="text-align">`, `<span style="font-*;color;text-decoration">`, `<table>`/`<td colspan rowspan>`,
//! `<img src="data:…" width height>`(그림은 본문 관계·word/media 에서 꺼낸다).

use base64::Engine as _;
use std::collections::HashMap;
use std::io::{Cursor, Read};

use crate::notes::{
    ENDNOTE_MARK, FOOTNOTE_MARK, HeaderFooterText, Numbering, PAGE_BREAK_MARK, paragraph_texts,
    parse_endnotes, parse_footnotes,
};
use crate::shapes::{DocxShape, Drawing, Pending, SHAPE_MARK, ShapeImage};
use papy_model::{Error, ErrorCode, Result};
use quick_xml::Reader;
use quick_xml::events::{BytesStart, Event};

/// 압축을 푼 XML 한 개의 상한(악성 압축 폭탄 방어).
const MAX_PART_BYTES: u64 = 64 * 1024 * 1024;
/// 표 중첩 상한.
const MAX_TABLE_DEPTH: usize = 16;

/// 변환하면서 옮기지 못한 것.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct DocxReadReport {
    /// 옮긴 그림 수(PNG·JPEG·GIF·BMP).
    pub images: usize,
    /// 옮기지 못한 그림·도형·개체 수(도형, 지원하지 않는 그림 형식, 크기 제한 초과 등).
    pub dropped_drawings: usize,
    /// 머리말(글자만). 본문 HTML 을 붙여넣은 뒤 문서에 넣는다.
    pub headers: Vec<HeaderFooterText>,
    /// 꼬리말(글자만)
    pub footers: Vec<HeaderFooterText>,
    /// 본문 미주(글자만), 본문의 [`ENDNOTE_MARK`] 와 같은 순서.
    pub endnotes: Vec<Vec<String>>,
    /// 본문 각주(글자만), 본문의 [`FOOTNOTE_MARK`] 와 같은 순서.
    pub footnotes: Vec<Vec<String>>,
    /// 옮기지 못한 각주·미주 수(표 안 각주·미주, 첫 쪽 머리말 등).
    pub dropped_notes: usize,
    /// 본문 도형, 본문의 [`SHAPE_MARK`] 와 같은 순서.
    pub shapes: Vec<DocxShape>,
    /// 본문 제목 문단(스타일·문단의 개요 수준이 있는 것), 문서 순서. 목차로 쓴다. 표 안 문단은 넣지 않는다.
    pub headings: Vec<DocxHeading>,
    /// 첫 쪽에서 감출 (머리말, 꼬리말). 첫 구역이 "첫 쪽 다르게"(`w:titlePg`)일 때 켠다.
    /// rhwp 에는 첫 쪽 전용 머리말 자리가 없어, 첫 쪽에서는 기본 머리말을 감추고 첫 쪽 것의 글자는 옮기지 않는다.
    pub hide_first_page: (bool, bool),
    /// 첫 구역의 쪽 크기·여백·다단(`w:pgSz`·`w:pgMar`·`w:cols`). 없거나 이상하면 None(빈 문서 기본값을 쓴다).
    pub page: Option<PageSetup>,
}

/// 쪽 설정(HWPUNIT). 머리말·꼬리말 여백은 HWP 방식이다(본문 위·아래 여백에 더해지는 머리말·꼬리말 자리).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PageSetup {
    /// 쪽 너비
    pub width: u32,
    /// 쪽 높이
    pub height: u32,
    /// 왼쪽 여백
    pub margin_left: u32,
    /// 오른쪽 여백
    pub margin_right: u32,
    /// 위 여백(머리말 자리 뺀 것)
    pub margin_top: u32,
    /// 아래 여백(꼬리말 자리 뺀 것)
    pub margin_bottom: u32,
    /// 머리말 자리
    pub margin_header: u32,
    /// 꼬리말 자리
    pub margin_footer: u32,
    /// 제본 여백
    pub margin_gutter: u32,
    /// 단 수(1 이면 다단 아님)
    pub columns: u16,
    /// 단 사이 간격
    pub column_gap: u32,
}

/// Word 쪽 설정(twip, 1/1440 인치).
#[derive(Debug, Clone, Copy, Default)]
pub(crate) struct WordPage {
    pub width: i64,
    pub height: i64,
    pub top: i64,
    pub bottom: i64,
    pub left: i64,
    pub right: i64,
    pub header: i64,
    pub footer: i64,
    pub gutter: i64,
    pub columns: i64,
    pub column_gap: i64,
}

impl WordPage {
    /// HWP 쪽 설정으로. Word 는 쪽 끝에서 머리말·본문까지를 재고, HWP 는 본문 여백 + 머리말 자리로 잰다.
    pub(crate) fn to_setup(self) -> Option<PageSetup> {
        let hwp = |twip: i64| u32::try_from(twip.max(0) * 5).ok();
        let (top, bottom) = (self.top.abs(), self.bottom.abs());
        let header = self.header.clamp(0, top);
        let footer = self.footer.clamp(0, bottom);
        let usable_width = self.width - self.left - self.right - self.gutter;
        let usable_height = self.height - top - bottom;
        // 1 인치보다 작은 쪽, 본문이 반 인치도 안 남는 여백, 22 인치(Word 상한)보다 큰 쪽은 버린다.
        if !(1440..=31680).contains(&self.width)
            || !(1440..=31680).contains(&self.height)
            || usable_width < 720
            || usable_height < 720
            || self.left < 0
            || self.right < 0
        {
            return None;
        }
        let columns = self.columns.clamp(1, 16) as u16;
        Some(PageSetup {
            width: hwp(self.width)?,
            height: hwp(self.height)?,
            margin_left: hwp(self.left)?,
            margin_right: hwp(self.right)?,
            margin_top: hwp(top - header)?,
            margin_bottom: hwp(bottom - footer)?,
            margin_header: hwp(header)?,
            margin_footer: hwp(footer)?,
            margin_gutter: hwp(self.gutter)?,
            columns,
            column_gap: hwp(self.column_gap.clamp(0, usable_width / 2))?,
        })
    }
}

/// 제목 문단 하나.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DocxHeading {
    /// 개요 수준(1부터). Word 의 "제목 1" 이 1 이다.
    pub level: u8,
    /// 문단 글자(번호 글자는 빼고, 앞뒤 공백 없이).
    pub text: String,
}

/// 그림 하나의 최대 크기, 문서 전체 그림 크기 합의 최대치.
const MAX_IMAGE_BYTES: u64 = 32 * 1024 * 1024;
const MAX_TOTAL_IMAGE_BYTES: u64 = 256 * 1024 * 1024;
/// EMU(English Metric Unit) → CSS px(96 dpi)
const EMU_PER_PX: f64 = 9525.0;

/// 본문 관계(rId → 파일 경로, word/ 기준으로 푼 값).
fn parse_rels(xml: &str) -> HashMap<String, String> {
    let mut rels = HashMap::new();
    let mut reader = Reader::from_str(xml);
    while let Ok(event) = reader.read_event() {
        match event {
            Event::Eof => break,
            Event::Start(e) | Event::Empty(e) if local(e.name().as_ref()) == "Relationship" => {
                let external = attr(&e, "TargetMode").as_deref() == Some("External");
                if let (Some(id), Some(target), false) =
                    (attr(&e, "Id"), attr(&e, "Target"), external)
                {
                    let path = match target.strip_prefix('/') {
                        Some(absolute) => absolute.to_owned(),
                        None => format!("word/{target}"),
                    };
                    rels.insert(id, normalize_path(&path));
                }
            }
            _ => {}
        }
    }
    rels
}

/// "word/media/../media/a.png" → "word/media/a.png". 패키지 밖(../ 가 넘침)이면 빈 문자열.
pub(crate) fn normalize_path(path: &str) -> String {
    let mut parts: Vec<&str> = Vec::new();
    for part in path.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                if parts.pop().is_none() {
                    return String::new();
                }
            }
            other => parts.push(other),
        }
    }
    parts.join("/")
}

/// 그림 파일 형식. rhwp 가 넣을 수 있는 것만.
pub(crate) fn image_mime(bytes: &[u8]) -> Option<&'static str> {
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        Some("image/png")
    } else if bytes.starts_with(&[0xFF, 0xD8, 0xFF]) {
        Some("image/jpeg")
    } else if bytes.starts_with(b"GIF8") {
        Some("image/gif")
    } else if bytes.starts_with(b"BM") {
        Some("image/bmp")
    } else {
        None
    }
}

fn read_binary(
    archive: &mut zip::ZipArchive<Cursor<&[u8]>>,
    name: &str,
    limit: u64,
) -> Option<Vec<u8>> {
    let file = archive.by_name(name).ok()?;
    let mut bytes = Vec::new();
    file.take(limit + 1).read_to_end(&mut bytes).ok()?;
    (bytes.len() as u64 <= limit).then_some(bytes)
}

/// 본문 안 그림(`<w:drawing>`) 하나: 크기(EMU)와 그림 관계 id.
fn invalid(message: &str) -> Error {
    Error::new(ErrorCode::InvalidDocument, message)
}

/// ZIP 안에 `word/document.xml` 이 있으면 DOCX 로 본다.
pub fn is_docx(bytes: &[u8]) -> bool {
    zip::ZipArchive::new(Cursor::new(bytes))
        .is_ok_and(|archive| archive.index_for_name("word/document.xml").is_some())
}

fn read_part(archive: &mut zip::ZipArchive<Cursor<&[u8]>>, name: &str) -> Result<Option<String>> {
    let Ok(file) = archive.by_name(name) else {
        return Ok(None);
    };
    let mut text = String::new();
    file.take(MAX_PART_BYTES + 1)
        .read_to_string(&mut text)
        .map_err(|_| invalid("unreadable DOCX part"))?;
    if text.len() as u64 > MAX_PART_BYTES {
        return Err(Error::new(
            ErrorCode::LimitExceeded,
            "DOCX part is too large",
        ));
    }
    Ok(Some(text))
}

/// 글자 서식. `None` 은 상위(스타일)에서 물려받는다.
#[derive(Debug, Clone, Default, PartialEq)]
struct RunProps {
    bold: Option<bool>,
    italic: Option<bool>,
    underline: Option<bool>,
    strike: Option<bool>,
    /// 반 포인트
    size: Option<u32>,
    color: Option<String>,
    font: Option<String>,
}

impl RunProps {
    fn over(&self, base: &RunProps) -> RunProps {
        RunProps {
            bold: self.bold.or(base.bold),
            italic: self.italic.or(base.italic),
            underline: self.underline.or(base.underline),
            strike: self.strike.or(base.strike),
            size: self.size.or(base.size),
            color: self.color.clone().or_else(|| base.color.clone()),
            font: self.font.clone().or_else(|| base.font.clone()),
        }
    }

    fn css(&self) -> String {
        let mut css = String::new();
        if let Some(font) = &self.font {
            css.push_str(&format!(
                "font-family:'{}';",
                escape(font).replace('\'', "")
            ));
        }
        if let Some(size) = self.size {
            css.push_str(&format!("font-size:{}pt;", size as f32 / 2.0));
        }
        if self.bold == Some(true) {
            css.push_str("font-weight:bold;");
        }
        if self.italic == Some(true) {
            css.push_str("font-style:italic;");
        }
        if let Some(color) = &self.color {
            css.push_str(&format!("color:#{color};"));
        }
        let mut decorations = Vec::new();
        if self.underline == Some(true) {
            decorations.push("underline");
        }
        if self.strike == Some(true) {
            decorations.push("line-through");
        }
        if !decorations.is_empty() {
            css.push_str(&format!("text-decoration:{};", decorations.join(" ")));
        }
        css
    }
}

#[derive(Debug, Clone, Default)]
struct ParaStyle {
    based_on: Option<String>,
    run: RunProps,
    align: Option<String>,
    /// 개요 수준(0부터, `w:outlineLvl` 또는 이름이 "heading N"). 9 는 본문이다.
    outline: Option<u8>,
}

/// `w:outlineLvl` 값. 0~8 만 제목이고 9 는 본문이다.
fn outline_level(element: &BytesStart) -> Option<u8> {
    attr(element, "val")
        .and_then(|v| v.parse::<u8>().ok())
        .filter(|&level| level <= 9)
}

/// 기본 스타일 이름("heading 1" 등)의 개요 수준.
fn heading_name_level(name: &str) -> Option<u8> {
    let rest = name.trim().to_ascii_lowercase();
    let level: u8 = rest.strip_prefix("heading")?.trim().parse().ok()?;
    (1..=9).contains(&level).then(|| level - 1)
}

pub(crate) fn local(name: &str) -> &str {
    name.rsplit(':').next().unwrap_or(name)
}

pub(crate) fn attr(element: &BytesStart, key: &str) -> Option<String> {
    element.attributes().flatten().find_map(|attribute| {
        // 값의 문자 참조(&#61623; 등)는 풀고, 풀 수 없으면 원래 값을 쓴다.
        (local(attribute.key.as_ref()) == key).then(|| {
            attribute
                .normalized_value(quick_xml::XmlVersion::default())
                .map(|value| value.into_owned())
                .unwrap_or_else(|_| attribute.value.to_string())
        })
    })
}

/// `<w:b/>`, `<w:b w:val="0"/>` 같은 켜고 끄기 값.
fn toggle(element: &BytesStart) -> bool {
    !matches!(
        attr(element, "val").as_deref(),
        Some("0" | "false" | "off" | "none")
    )
}

/// rPr 안의 요소 하나를 반영한다.
fn apply_run_prop(props: &mut RunProps, element: &BytesStart) {
    match local(element.name().as_ref()) {
        "b" => props.bold = Some(toggle(element)),
        "i" => props.italic = Some(toggle(element)),
        "strike" | "dstrike" => props.strike = Some(toggle(element)),
        "u" => props.underline = Some(attr(element, "val").as_deref() != Some("none")),
        "sz" => props.size = attr(element, "val").and_then(|v| v.parse().ok()),
        "color" => {
            props.color = attr(element, "val")
                .filter(|v| v.len() == 6 && v.chars().all(|c| c.is_ascii_hexdigit()))
        }
        "rFonts" => {
            props.font = attr(element, "eastAsia")
                .or_else(|| attr(element, "ascii"))
                .or_else(|| attr(element, "hAnsi"))
        }
        _ => {}
    }
}

fn align_css(value: &str) -> &'static str {
    match value {
        "center" => "center",
        "right" | "end" => "right",
        "both" | "distribute" => "justify",
        _ => "left",
    }
}

/// styles.xml 에서 기본 글자 서식과 문단 스타일을 읽는다.
fn parse_styles(xml: &str) -> (RunProps, HashMap<String, ParaStyle>) {
    let mut reader = Reader::from_str(xml);
    let mut defaults = RunProps::default();
    let mut styles = HashMap::new();
    let mut current: Option<(String, ParaStyle)> = None;
    let mut in_defaults = false;
    let mut in_rpr = false;
    loop {
        let event = match reader.read_event() {
            Ok(Event::Eof) | Err(_) => break,
            Ok(event) => event,
        };
        match event {
            Event::Start(e) | Event::Empty(e) => match local(e.name().as_ref()) {
                "rPrDefault" => in_defaults = true,
                "style" => {
                    current = (attr(&e, "type").as_deref() == Some("paragraph"))
                        .then(|| attr(&e, "styleId"))
                        .flatten()
                        .map(|id| (id, ParaStyle::default()));
                }
                "basedOn" => {
                    if let Some((_, style)) = current.as_mut() {
                        style.based_on = attr(&e, "val");
                    }
                }
                "jc" => {
                    if let Some((_, style)) = current.as_mut() {
                        style.align = attr(&e, "val");
                    }
                }
                "name" if !in_rpr => {
                    if let Some((_, style)) = current.as_mut() {
                        let level = attr(&e, "val").and_then(|n| heading_name_level(&n));
                        style.outline = style.outline.or(level);
                    }
                }
                "outlineLvl" if !in_rpr => {
                    if let Some((_, style)) = current.as_mut() {
                        style.outline = outline_level(&e);
                    }
                }
                "rPr" => in_rpr = true,
                _ if in_rpr => {
                    if in_defaults {
                        apply_run_prop(&mut defaults, &e);
                    } else if let Some((_, style)) = current.as_mut() {
                        apply_run_prop(&mut style.run, &e);
                    }
                }
                _ => {}
            },
            Event::End(e) => match local(e.name().as_ref()) {
                "rPrDefault" => in_defaults = false,
                "rPr" => in_rpr = false,
                "style" => {
                    if let Some((id, style)) = current.take() {
                        styles.insert(id, style);
                    }
                }
                _ => {}
            },
            _ => {}
        }
    }
    (defaults, styles)
}

/// 스타일 상속을 풀어 (글자 서식, 정렬, 개요 수준) 을 돌려준다.
fn resolve_style(
    styles: &HashMap<String, ParaStyle>,
    id: &str,
) -> (RunProps, Option<String>, Option<u8>) {
    let mut chain = Vec::new();
    let mut next = Some(id.to_string());
    while let Some(id) = next {
        if chain.len() > 16 {
            break;
        }
        let Some(style) = styles.get(&id) else { break };
        chain.push(style);
        next = style.based_on.clone();
    }
    let mut run = RunProps::default();
    let mut align = None;
    let mut outline = None;
    for style in chain.iter().rev() {
        run = style.run.over(&run);
        align = style.align.clone().or(align);
        outline = style.outline.or(outline);
    }
    (run, align, outline)
}

fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
}

/// 표 하나: 행마다 (열 너비 칸 수, 세로 병합 상태, 내용 HTML).
struct TableBuild {
    rows: Vec<Vec<CellBuild>>,
    /// 표 테두리(`w:tblBorders` 의 선, 또는 Word 기본 "표 구분선" 스타일)
    bordered: bool,
}

struct CellBuild {
    span: usize,
    /// 칸 너비(pt, `w:tcW` 의 dxa)
    width: Option<f32>,
    /// None: 병합 없음, Some(true): 병합 시작, Some(false): 위 칸에 이어짐
    vmerge: Option<bool>,
    html: String,
}

impl TableBuild {
    fn to_html(&self) -> String {
        // 각 칸의 시작 열 번호를 구해 같은 열에서 이어지는 칸 수로 rowspan 을 센다.
        let starts: Vec<Vec<usize>> = self
            .rows
            .iter()
            .map(|row| {
                let mut col = 0;
                row.iter()
                    .map(|cell| {
                        let start = col;
                        col += cell.span;
                        start
                    })
                    .collect()
            })
            .collect();
        let continues = |row: usize, col: usize| {
            self.rows.get(row).is_some_and(|cells| {
                cells
                    .iter()
                    .zip(&starts[row])
                    .any(|(cell, start)| *start == col && cell.vmerge == Some(false))
            })
        };
        let mut html = String::from("<table border=\"1\">");
        for (r, row) in self.rows.iter().enumerate() {
            html.push_str("<tr>");
            for (cell, col) in row.iter().zip(&starts[r]) {
                if cell.vmerge == Some(false) {
                    continue;
                }
                let mut rowspan = 1;
                if cell.vmerge == Some(true) {
                    while continues(r + rowspan, *col) {
                        rowspan += 1;
                    }
                }
                html.push_str("<td");
                let mut style = String::new();
                if self.bordered {
                    // rhwp 붙여넣기는 칸 CSS 로만 테두리를 읽는다(Word 기본 0.5pt 검정).
                    style.push_str("border:0.5pt solid #000000;");
                }
                if let (Some(width), 1) = (cell.width, cell.span) {
                    // rhwp 는 병합하지 않은 칸의 너비로 열 너비를 정한다.
                    style.push_str(&format!("width:{width:.1}pt;"));
                }
                if !style.is_empty() {
                    html.push_str(&format!(" style=\"{style}\""));
                }
                if cell.span > 1 {
                    html.push_str(&format!(" colspan=\"{}\"", cell.span));
                }
                if rowspan > 1 {
                    html.push_str(&format!(" rowspan=\"{rowspan}\""));
                }
                html.push('>');
                html.push_str(if cell.html.is_empty() {
                    "<p></p>"
                } else {
                    &cell.html
                });
                html.push_str("</td>");
            }
            html.push_str("</tr>");
        }
        html.push_str("</table>");
        html
    }
}

/// 도형 자리 그림(묶음 안 그림, 차트)의 내용. 그림 형식을 모르거나 차트에 그릴 값이 없으면 None.
fn resolve_image(
    archive: &mut zip::ZipArchive<Cursor<&[u8]>>,
    rels: &HashMap<String, String>,
    pending: &Pending,
    shape: &DocxShape,
    image_budget: &mut u64,
) -> Result<Option<ShapeImage>> {
    let (Pending::Image(id) | Pending::Chart(id)) = pending;
    let Some(path) = rels.get(id).filter(|path| !path.is_empty()) else {
        return Ok(None);
    };
    match pending {
        Pending::Image(_) => {
            let image = read_binary(archive, path, MAX_IMAGE_BYTES.min(*image_budget))
                .filter(|bytes| image_mime(bytes).is_some());
            if let Some(bytes) = &image {
                *image_budget = image_budget.saturating_sub(bytes.len() as u64);
            }
            Ok(image.map(ShapeImage::Raster))
        }
        Pending::Chart(_) => {
            let Some(xml) = read_part(archive, path)? else {
                return Ok(None);
            };
            Ok(crate::chart::parse(&xml).map(|chart| {
                ShapeImage::Svg(chart.to_svg(
                    shape.width as f64 / EMU_PER_PX,
                    shape.height as f64 / EMU_PER_PX,
                ))
            }))
        }
    }
}

/// DOCX 바이트를 rhwp 가 붙여넣을 수 있는 HTML 로 바꾼다.
pub fn docx_to_html(bytes: &[u8]) -> Result<(String, DocxReadReport)> {
    let mut archive =
        zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| invalid("not a ZIP container"))?;
    let document = read_part(&mut archive, "word/document.xml")?
        .ok_or_else(|| Error::new(ErrorCode::UnsupportedFormat, "not a DOCX document"))?;
    let (defaults, styles) = read_part(&mut archive, "word/styles.xml")?
        .map(|xml| parse_styles(&xml))
        .unwrap_or_default();

    let rels = read_part(&mut archive, "word/_rels/document.xml.rels")?
        .map(|xml| parse_rels(&xml))
        .unwrap_or_default();
    let footnotes = read_part(&mut archive, "word/footnotes.xml")?
        .map(|xml| parse_footnotes(&xml))
        .unwrap_or_default();
    let endnotes = read_part(&mut archive, "word/endnotes.xml")?
        .map(|xml| parse_endnotes(&xml))
        .unwrap_or_default();
    let mut numbering = read_part(&mut archive, "word/numbering.xml")?
        .map(|xml| Numbering::parse(&xml))
        .unwrap_or_default();
    // 머리말·꼬리말 참조: (머리말인가, 종류, 관계 id). 첫 구역 것을 쓴다.
    let mut header_refs: Vec<(bool, String, String)> = Vec::new();
    let mut sections_seen = 0usize;
    let mut title_page = false;
    // 구역마다 쪽 설정과 문단 수. rhwp 문서는 구역 하나라서 문단이 가장 많은 구역의 쪽 설정을 쓴다
    // (표지 구역만 여백이 큰 문서 등). 정하지 않은 값은 Word 기본값(Letter, 여백 1 인치, 머리말 0.5 인치).
    const WORD_PAGE: WordPage = WordPage {
        width: 12240,
        height: 15840,
        top: 1440,
        bottom: 1440,
        left: 1440,
        right: 1440,
        header: 720,
        footer: 720,
        gutter: 0,
        columns: 1,
        column_gap: 720,
    };
    let mut page = WORD_PAGE;
    let mut section_pages: Vec<(WordPage, usize)> = Vec::new();
    let (mut paragraphs, mut section_start) = (0usize, 0usize);
    let mut image_budget = MAX_TOTAL_IMAGE_BYTES;
    let mut drawing: Option<Drawing> = None;
    // 건너뛰는 요소 깊이(mc:Fallback — 같은 내용의 옛 형식, VML)
    let mut skip = 0usize;

    let mut report = DocxReadReport::default();
    let mut reader = Reader::from_str(&document);
    // 출력 대상: 본문 또는 열린 표 칸들(중첩 표 지원).
    let mut body = String::new();
    let mut tables: Vec<TableBuild> = Vec::new();
    let mut cell: Vec<Option<CellBuild>> = Vec::new();

    let mut para_html = String::new();
    let mut in_para = false;
    let mut para_style: (RunProps, Option<String>, Option<u8>) = (RunProps::default(), None, None);
    let mut para_align: Option<String> = None;
    // 문단에 직접 준 개요 수준(스타일 것보다 앞선다)과 문단 글자(목차용, 이스케이프 전).
    let mut para_outline: Option<u8> = None;
    let mut para_plain = String::new();
    let mut page_break_before = false;
    let mut in_ppr = false;
    let mut in_tbl_borders = false;
    let mut run = RunProps::default();
    let mut in_run = false;
    let mut in_rpr = false;
    let mut in_text = false;
    let mut list_item = false;
    let mut in_numpr = false;
    let mut num_id: Option<String> = None;
    let mut ilvl = 0usize;
    // 지금 런의 글자(이미 HTML 이스케이프됨). 런이 끝나거나 탭·줄바꿈을 만나면 서식 span 으로 내보낸다.
    let mut run_text = String::new();
    let flush = |para_html: &mut String,
                 run_text: &mut String,
                 run: &RunProps,
                 style: &RunProps,
                 defaults: &RunProps| {
        if run_text.is_empty() {
            return;
        }
        let css = run.over(style).over(defaults).css();
        if css.is_empty() {
            para_html.push_str(run_text);
        } else {
            para_html.push_str(&format!("<span style=\"{css}\">{run_text}</span>"));
        }
        run_text.clear();
    };

    let out = |body: &mut String, cell: &mut [Option<CellBuild>], html: &str| match cell.last_mut()
    {
        Some(Some(cell)) => cell.html.push_str(html),
        _ => body.push_str(html),
    };

    loop {
        let event = match reader.read_event() {
            Ok(Event::Eof) => break,
            Ok(event) => event,
            Err(_) => return Err(invalid("malformed DOCX XML")),
        };
        match event {
            event @ (Event::Start(_) | Event::Empty(_)) => {
                // 빈 요소(<w:p/>, <w:t/>, <w:rPr/>)는 End 가 오지 않으므로 상태를 켜지 않는다.
                let empty = matches!(event, Event::Empty(_));
                let (Event::Start(e) | Event::Empty(e)) = event else {
                    unreachable!()
                };
                let qname = e.name();
                let name = local(qname.as_ref());
                if skip > 0 {
                    if !empty {
                        skip += 1;
                    }
                    continue;
                }
                // mc:AlternateContent 는 Choice(새 형식)만 읽는다. VML 개체(pict·object)는 옮기지 않는다.
                if matches!(name, "Fallback" | "pict" | "object") {
                    if name != "Fallback" {
                        report.dropped_drawings += 1;
                    }
                    if !empty {
                        skip = 1;
                    }
                    continue;
                }
                if let Some(current) = drawing.as_mut() {
                    current.start(name, &e, empty);
                    continue;
                }
                match name {
                    "p" if empty => {
                        paragraphs += 1;
                        out(&mut body, &mut cell, "<p></p>");
                    }
                    "p" => {
                        paragraphs += 1;
                        in_para = true;
                        para_html.clear();
                        para_style = (RunProps::default(), None, None);
                        para_align = None;
                        para_outline = None;
                        page_break_before = false;
                        para_plain.clear();
                        list_item = false;
                        num_id = None;
                        ilvl = 0;
                    }
                    "pPr" | "rPr" | "r" | "t" | "tc" | "tbl" | "tr" if empty => {}
                    "pPr" => in_ppr = true,
                    "tblBorders" if !empty => in_tbl_borders = true,
                    "top" | "left" | "bottom" | "right" | "start" | "end" | "insideH"
                    | "insideV"
                        if in_tbl_borders =>
                    {
                        let line =
                            attr(&e, "val").is_some_and(|v| !matches!(v.as_str(), "nil" | "none"));
                        if let (true, Some(table)) = (line, tables.last_mut()) {
                            table.bordered = true;
                        }
                    }
                    // Word 기본 "표 구분선"(TableGrid) 스타일은 테두리가 있다.
                    "tblStyle" => {
                        let grid = attr(&e, "val")
                            .is_some_and(|v| v.to_ascii_lowercase().contains("grid"));
                        if let (true, Some(table)) = (grid, tables.last_mut()) {
                            table.bordered = true;
                        }
                    }
                    "pStyle" if in_ppr => {
                        if let Some(id) = attr(&e, "val") {
                            para_style = resolve_style(&styles, &id);
                        }
                    }
                    "jc" if in_ppr && !in_rpr => para_align = attr(&e, "val"),
                    "outlineLvl" if in_ppr && !in_rpr => para_outline = outline_level(&e),
                    "numPr" if in_ppr => {
                        list_item = true;
                        in_numpr = !empty;
                    }
                    "numId" if in_numpr => num_id = attr(&e, "val"),
                    "ilvl" if in_numpr => {
                        ilvl = attr(&e, "val").and_then(|v| v.parse().ok()).unwrap_or(0)
                    }
                    "sectPr" => {
                        page = WORD_PAGE;
                        if empty {
                            section_pages.push((page, paragraphs - section_start));
                            section_start = paragraphs;
                        } else {
                            sections_seen += 1;
                        }
                    }
                    "titlePg" if sections_seen == 1 => title_page = toggle(&e),
                    "pgSz" => {
                        let v = |k| attr(&e, k).and_then(|v| v.parse::<f64>().ok());
                        if let (Some(w), Some(h)) = (v("w"), v("h")) {
                            (page.width, page.height) = (w as i64, h as i64);
                        }
                    }
                    "pgMar" => {
                        let v = |k| attr(&e, k).and_then(|v| v.parse::<f64>().ok());
                        for (key, field) in [
                            ("top", &mut page.top),
                            ("bottom", &mut page.bottom),
                            ("left", &mut page.left),
                            ("right", &mut page.right),
                            ("header", &mut page.header),
                            ("footer", &mut page.footer),
                            ("gutter", &mut page.gutter),
                        ] {
                            if let Some(value) = v(key) {
                                *field = value as i64;
                            }
                        }
                    }
                    "cols" => {
                        let v = |k| attr(&e, k).and_then(|v| v.parse::<f64>().ok());
                        page.columns = v("num").map_or(1, |n| n as i64);
                        if let Some(gap) = v("space") {
                            page.column_gap = gap as i64;
                        }
                    }
                    kind @ ("headerReference" | "footerReference") if sections_seen == 1 => {
                        if let (Some(kind_attr), Some(id)) = (attr(&e, "type"), attr(&e, "id")) {
                            header_refs.push((kind == "headerReference", kind_attr, id));
                        }
                    }
                    kind @ ("footnoteReference" | "endnoteReference") if in_run => {
                        flush(
                            &mut para_html,
                            &mut run_text,
                            &run,
                            &para_style.0,
                            &defaults,
                        );
                        let (notes, mark, out) = if kind == "footnoteReference" {
                            (&footnotes, FOOTNOTE_MARK, &mut report.footnotes)
                        } else {
                            (&endnotes, ENDNOTE_MARK, &mut report.endnotes)
                        };
                        match attr(&e, "id").and_then(|id| notes.get(&id)) {
                            // 표 안 각주·미주는 papy-hwp 가 본문에서 만들어 칸으로 옮긴다(겹친 표 안은 빼고).
                            Some(note) if tables.len() <= 1 => {
                                para_html.push(mark);
                                out.push(note.clone());
                            }
                            _ => report.dropped_notes += 1,
                        }
                    }
                    "r" if in_para => {
                        in_run = true;
                        run = RunProps::default();
                    }
                    "rPr" => in_rpr = true,
                    _ if in_rpr && in_run => apply_run_prop(&mut run, &e),
                    "t" => in_text = true,
                    "tab" if in_run => {
                        flush(
                            &mut para_html,
                            &mut run_text,
                            &run,
                            &para_style.0,
                            &defaults,
                        );
                        para_html.push('\t');
                    }
                    "br" if in_run => {
                        flush(
                            &mut para_html,
                            &mut run_text,
                            &run,
                            &para_style.0,
                            &defaults,
                        );
                        if attr(&e, "type").as_deref() == Some("page") && cell.is_empty() {
                            // 쪽 나누기: 문단을 끊고 뒤 문단 앞에서 쪽을 나눈다.
                            para_html.push_str("</p><p>");
                            para_html.push(PAGE_BREAK_MARK);
                        } else {
                            para_html.push_str("<br>");
                        }
                    }
                    "pageBreakBefore" if in_ppr && toggle(&e) => page_break_before = true,
                    "drawing" if !empty => drawing = Some(Drawing::default()),
                    "drawing" => report.dropped_drawings += 1,
                    "tbl" => {
                        if tables.len() >= MAX_TABLE_DEPTH {
                            return Err(Error::new(
                                ErrorCode::LimitExceeded,
                                "tables are nested too deeply",
                            ));
                        }
                        tables.push(TableBuild {
                            rows: Vec::new(),
                            bordered: false,
                        });
                    }
                    "tr" => {
                        if let Some(table) = tables.last_mut() {
                            table.rows.push(Vec::new());
                        }
                    }
                    "tc" => cell.push(Some(CellBuild {
                        span: 1,
                        width: None,
                        vmerge: None,
                        html: String::new(),
                    })),
                    "tcW" => {
                        if let Some(Some(current)) = cell.last_mut() {
                            current.width = (attr(&e, "type").as_deref() == Some("dxa"))
                                .then(|| attr(&e, "w").and_then(|v| v.parse::<f32>().ok()))
                                .flatten()
                                .filter(|w| *w > 0.0)
                                .map(|w| w / 20.0);
                        }
                    }
                    "gridSpan" => {
                        if let Some(Some(current)) = cell.last_mut() {
                            current.span = attr(&e, "val")
                                .and_then(|v| v.parse().ok())
                                .unwrap_or(1)
                                .clamp(1, 64);
                        }
                    }
                    "vMerge" => {
                        if let Some(Some(current)) = cell.last_mut() {
                            current.vmerge = Some(attr(&e, "val").as_deref() == Some("restart"));
                        }
                    }
                    _ => {}
                }
            }
            Event::Text(_) | Event::GeneralRef(_) if skip > 0 => {}
            Event::Text(text) if drawing.is_some() => {
                if let Some(current) = drawing.as_mut() {
                    current.text(&text.xml10_content());
                }
            }
            Event::GeneralRef(reference) if drawing.is_some() => {
                let ch = match reference.resolve_char_ref() {
                    Ok(Some(ch)) => Some(ch),
                    _ => match reference.into_inner().as_ref() {
                        "amp" => Some('&'),
                        "lt" => Some('<'),
                        "gt" => Some('>'),
                        "quot" => Some('"'),
                        "apos" => Some('\''),
                        _ => None,
                    },
                };
                if let (Some(current), Some(ch)) = (drawing.as_mut(), ch) {
                    current.text(&ch.to_string());
                }
            }
            Event::End(_) if skip > 0 => skip -= 1,
            Event::End(e) if drawing.is_some() && local(e.name().as_ref()) != "drawing" => {
                if let Some(current) = drawing.as_mut() {
                    current.end(local(e.name().as_ref()));
                }
            }
            Event::Text(text) if in_text => {
                let text = text.xml10_content();
                para_plain.push_str(&text);
                run_text.push_str(&escape(&text));
            }
            Event::GeneralRef(reference) if in_text => {
                // &amp; 등 문자 참조
                if let Ok(Some(ch)) = reference.resolve_char_ref() {
                    para_plain.push(ch);
                    run_text.push_str(&escape(&ch.to_string()));
                } else {
                    let (plain, ch) = match reference.into_inner().as_ref() {
                        "amp" => ("&", "&amp;"),
                        "lt" => ("<", "&lt;"),
                        "gt" => (">", "&gt;"),
                        "quot" => ("\"", "&quot;"),
                        "apos" => ("'", "'"),
                        _ => ("", ""),
                    };
                    para_plain.push_str(plain);
                    run_text.push_str(ch);
                }
            }
            Event::End(e) => match local(e.name().as_ref()) {
                "sectPr" => {
                    section_pages.push((page, paragraphs - section_start));
                    section_start = paragraphs;
                }
                "drawing" => {
                    let Some(done) = drawing.take() else { continue };
                    if !done.is_picture() {
                        flush(
                            &mut para_html,
                            &mut run_text,
                            &run,
                            &para_style.0,
                            &defaults,
                        );
                        let (shapes, dropped) = done.into_shapes();
                        report.dropped_drawings += dropped;
                        for mut shape in shapes {
                            // 묶음 안 그림·차트: 관계를 풀어 그림 내용을 채운다. 못 풀면 빠진 것으로 센다.
                            if let Some(pending) = shape.pending.take() {
                                shape.image = resolve_image(
                                    &mut archive,
                                    &rels,
                                    &pending,
                                    &shape,
                                    &mut image_budget,
                                )?;
                                if shape.image.is_none() {
                                    report.dropped_drawings += 1;
                                    continue;
                                }
                            }
                            if cell.is_empty() && in_para {
                                para_html.push(SHAPE_MARK);
                                report.shapes.push(shape);
                            } else {
                                // 표 안 도형은 rhwp 에 넣을 자리가 없어 글상자 글자만 남긴다.
                                for text in shape.text.iter().filter(|t| !t.is_empty()) {
                                    para_html.push_str(&escape(text));
                                    para_html.push(' ');
                                }
                                report.dropped_drawings += 1;
                            }
                        }
                        continue;
                    }
                    let image = done
                        .embed
                        .as_ref()
                        .and_then(|id| rels.get(id))
                        .filter(|path| !path.is_empty())
                        .and_then(|path| {
                            read_binary(&mut archive, path, MAX_IMAGE_BYTES.min(image_budget))
                        })
                        .and_then(|bytes| image_mime(&bytes).map(|mime| (mime, bytes)));
                    match image {
                        Some((mime, bytes)) => {
                            image_budget = image_budget.saturating_sub(bytes.len() as u64);
                            // 크기를 모르면 rhwp 기본값(200×150px)을 쓴다.
                            let size = match (done.cx, done.cy) {
                                (Some(cx), Some(cy)) if cx > 0.0 && cy > 0.0 => format!(
                                    " width=\"{}\" height=\"{}\"",
                                    (cx / EMU_PER_PX).round().clamp(1.0, 20000.0),
                                    (cy / EMU_PER_PX).round().clamp(1.0, 20000.0)
                                ),
                                _ => String::new(),
                            };
                            flush(
                                &mut para_html,
                                &mut run_text,
                                &run,
                                &para_style.0,
                                &defaults,
                            );
                            para_html.push_str(&format!(
                                "<img src=\"data:{mime};base64,{}\"{size}>",
                                base64::engine::general_purpose::STANDARD.encode(&bytes)
                            ));
                            report.images += 1;
                        }
                        None => report.dropped_drawings += 1,
                    }
                }
                "t" => in_text = false,
                "rPr" => in_rpr = false,
                "pPr" => in_ppr = false,
                "tblBorders" => in_tbl_borders = false,
                "numPr" => in_numpr = false,
                "r" => {
                    flush(
                        &mut para_html,
                        &mut run_text,
                        &run,
                        &para_style.0,
                        &defaults,
                    );
                    in_run = false;
                }
                "p" => {
                    in_para = false;
                    let align = para_align.clone().or_else(|| para_style.1.clone());
                    let style = align
                        .map(|a| format!(" style=\"text-align:{}\"", align_css(&a)))
                        .unwrap_or_default();
                    let label = match (&num_id, list_item) {
                        (Some(id), _) => numbering.label(id, ilvl),
                        (None, true) => Some("•".into()),
                        (None, false) => None,
                    };
                    let label = label
                        .map(|label| format!("{} ", escape(&label)))
                        .unwrap_or_default();
                    let mark = if page_break_before && cell.is_empty() {
                        PAGE_BREAK_MARK.to_string()
                    } else {
                        String::new()
                    };
                    let html = format!("<p{style}>{mark}{label}{para_html}</p>");
                    out(&mut body, &mut cell, &html);
                    let level = para_outline.or(para_style.2).filter(|&level| level < 9);
                    let text = para_plain.trim();
                    if let (Some(level), true, false) = (level, cell.is_empty(), text.is_empty()) {
                        report.headings.push(DocxHeading {
                            level: level + 1,
                            text: text.to_owned(),
                        });
                    }
                }
                "tc" => {
                    if let (Some(Some(done)), Some(table)) = (cell.pop(), tables.last_mut()) {
                        if let Some(row) = table.rows.last_mut() {
                            row.push(done);
                        }
                    }
                }
                "tbl" => {
                    if let Some(table) = tables.pop() {
                        let html = table.to_html();
                        out(&mut body, &mut cell, &html);
                    }
                }
                _ => {}
            },
            _ => {}
        }
    }
    // 문단이 가장 많은 구역(같으면 앞 구역).
    report.page = section_pages
        .iter()
        .rev()
        .max_by_key(|(_, count)| *count)
        .and_then(|(page, _)| page.to_setup());
    // 머리말·꼬리말: 짝수 쪽 것이 있으면 기본(default)은 홀수 쪽이다. 첫 쪽 것은 rhwp 에 맞는 자리가 없어
    // 첫 쪽에서 기본 것을 감추고(Word 도 첫 쪽 것이 없으면 아무것도 안 보인다), 글자가 있으면 빠진 것으로 센다.
    // 짝수 쪽 것은 설정(evenAndOddHeaders)이 켜졌을 때만 쓴다. 꺼져 있으면 Word 는 모든 쪽에 기본 것을 쓴다.
    let even_and_odd = read_part(&mut archive, "word/settings.xml")?.is_some_and(|xml| {
        xml.split("<w:evenAndOddHeaders")
            .nth(1)
            .is_some_and(|rest| {
                let tag = &rest[..rest.find('>').unwrap_or(rest.len())];
                !(tag.contains("\"0\"") || tag.contains("\"false\"") || tag.contains("\"off\""))
            })
    });
    if !even_and_odd {
        header_refs.retain(|(_, kind, _)| kind != "even");
    }
    let has_even = header_refs.iter().any(|(_, kind, _)| kind == "even");
    for (is_header, kind, id) in header_refs {
        let Some(path) = rels.get(&id).filter(|path| !path.is_empty()) else {
            continue;
        };
        let Some(xml) = read_part(&mut archive, path)? else {
            continue;
        };
        let paragraphs = paragraph_texts(&xml);
        let blank = paragraphs.iter().all(|p| p.trim().is_empty());
        let apply_to = match kind.as_str() {
            "default" if has_even => 2,
            "default" => 0,
            "even" => 1,
            "first" => {
                if title_page && !blank {
                    report.dropped_notes += 1;
                }
                continue;
            }
            _ => continue,
        };
        if blank {
            continue;
        }
        let target = if is_header {
            &mut report.headers
        } else {
            &mut report.footers
        };
        if !target.iter().any(|existing| existing.apply_to == apply_to) {
            target.push(HeaderFooterText {
                apply_to,
                paragraphs,
            });
        }
    }
    if title_page {
        report.hide_first_page = (!report.headers.is_empty(), !report.footers.is_empty());
    }
    Ok((format!("<html><body>{body}</body></html>"), report))
}
