//! DOCX → HTML. rhwp 의 HTML 붙여넣기(`paste_html_native`)가 받는 범위의 태그만 만든다:
//! `<p style="text-align">`, `<span style="font-*;color;text-decoration">`, `<table>`/`<td colspan rowspan>`,
//! `<img src="data:…" width height>`(그림은 본문 관계·word/media 에서 꺼낸다).

use base64::Engine as _;
use std::collections::HashMap;
use std::io::{Cursor, Read};

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
fn image_mime(bytes: &[u8]) -> Option<&'static str> {
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
#[derive(Default)]
struct Drawing {
    cx: Option<f64>,
    cy: Option<f64>,
    embed: Option<String>,
}

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
}

fn local(name: &str) -> &str {
    name.rsplit(':').next().unwrap_or(name)
}

fn attr(element: &BytesStart, key: &str) -> Option<String> {
    element.attributes().flatten().find_map(|attribute| {
        (local(attribute.key.as_ref()) == key).then(|| attribute.value.to_string())
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

/// 스타일 상속을 풀어 (글자 서식, 정렬) 을 돌려준다.
fn resolve_style(styles: &HashMap<String, ParaStyle>, id: &str) -> (RunProps, Option<String>) {
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
    for style in chain.iter().rev() {
        run = style.run.over(&run);
        align = style.align.clone().or(align);
    }
    (run, align)
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
}

struct CellBuild {
    span: usize,
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
    let mut image_budget = MAX_TOTAL_IMAGE_BYTES;
    let mut drawing: Option<Drawing> = None;

    let mut report = DocxReadReport::default();
    let mut reader = Reader::from_str(&document);
    // 출력 대상: 본문 또는 열린 표 칸들(중첩 표 지원).
    let mut body = String::new();
    let mut tables: Vec<TableBuild> = Vec::new();
    let mut cell: Vec<Option<CellBuild>> = Vec::new();

    let mut para_html = String::new();
    let mut in_para = false;
    let mut para_style: (RunProps, Option<String>) = (RunProps::default(), None);
    let mut para_align: Option<String> = None;
    let mut in_ppr = false;
    let mut run = RunProps::default();
    let mut in_run = false;
    let mut in_rpr = false;
    let mut in_text = false;
    let mut list_item = false;
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
                match local(e.name().as_ref()) {
                    "p" if empty => out(&mut body, &mut cell, "<p></p>"),
                    "p" => {
                        in_para = true;
                        para_html.clear();
                        para_style = (RunProps::default(), None);
                        para_align = None;
                        list_item = false;
                    }
                    "pPr" | "rPr" | "r" | "t" | "tc" | "tbl" | "tr" if empty => {}
                    "pPr" => in_ppr = true,
                    "pStyle" if in_ppr => {
                        if let Some(id) = attr(&e, "val") {
                            para_style = resolve_style(&styles, &id);
                        }
                    }
                    "jc" if in_ppr && !in_rpr => para_align = attr(&e, "val"),
                    "numPr" if in_ppr => list_item = true,
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
                        para_html.push_str("<br>");
                    }
                    "drawing" if !empty => drawing = Some(Drawing::default()),
                    "extent" if drawing.is_some() => {
                        if let Some(current) = drawing.as_mut() {
                            current.cx = attr(&e, "cx").and_then(|v| v.parse().ok());
                            current.cy = attr(&e, "cy").and_then(|v| v.parse().ok());
                        }
                    }
                    "blip" if drawing.is_some() => {
                        if let Some(current) = drawing.as_mut() {
                            current.embed = current.embed.take().or_else(|| attr(&e, "embed"));
                        }
                    }
                    "drawing" | "pict" | "object" => report.dropped_drawings += 1,
                    "tbl" => {
                        if tables.len() >= MAX_TABLE_DEPTH {
                            return Err(Error::new(
                                ErrorCode::LimitExceeded,
                                "tables are nested too deeply",
                            ));
                        }
                        tables.push(TableBuild { rows: Vec::new() });
                    }
                    "tr" => {
                        if let Some(table) = tables.last_mut() {
                            table.rows.push(Vec::new());
                        }
                    }
                    "tc" => cell.push(Some(CellBuild {
                        span: 1,
                        vmerge: None,
                        html: String::new(),
                    })),
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
            Event::Text(text) if in_text => {
                run_text.push_str(&escape(&text.xml10_content()));
            }
            Event::GeneralRef(reference) if in_text => {
                // &amp; 등 문자 참조
                if let Ok(Some(ch)) = reference.resolve_char_ref() {
                    run_text.push_str(&escape(&ch.to_string()));
                } else {
                    let ch = match reference.into_inner().as_ref() {
                        "amp" => "&amp;",
                        "lt" => "&lt;",
                        "gt" => "&gt;",
                        "quot" => "&quot;",
                        "apos" => "'",
                        _ => "",
                    };
                    run_text.push_str(ch);
                }
            }
            Event::End(e) => match local(e.name().as_ref()) {
                "drawing" => {
                    let Some(done) = drawing.take() else { continue };
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
                    let bullet = if list_item { "• " } else { "" };
                    let html = format!("<p{style}>{bullet}{para_html}</p>");
                    out(&mut body, &mut cell, &html);
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
    Ok((format!("<html><body>{body}</body></html>"), report))
}
