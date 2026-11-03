//! rhwp 문서 → DOCX. 문단·글자 서식(글꼴·크기·굵게·기울임·밑줄·취소선·색)·정렬·줄 간격·표(병합 포함)·
//! 쪽 크기와 여백을 옮긴다. 단위: HWPUNIT(1/7200 인치) → twip(1/1440 인치) 은 5 로 나눈다.

use std::fmt::Write as _;
use std::io::{Cursor, Write};

use papy_model::{Error, ErrorCode, Result};
use rhwp::model::control::Control;
use rhwp::model::document::{DocInfo, Document};
use rhwp::model::paragraph::Paragraph;
use rhwp::model::style::{Alignment, LineSpacingType, UnderlineType};
use rhwp::model::table::Table;

/// 표 중첩 상한(순환·과도한 중첩 방어).
const MAX_DEPTH: usize = 16;

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
    props.push_str("</w:pPr>");
    props
}

/// 문단 하나와 그 안의 표.
fn write_paragraph(
    out: &mut String,
    info: &DocInfo,
    paragraph: &Paragraph,
    depth: usize,
) -> Result<()> {
    out.push_str("<w:p>");
    out.push_str(&para_props(info, paragraph));
    let chars: Vec<char> = paragraph.text.chars().collect();
    let mut start = 0;
    while start < chars.len() {
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
        while end < chars.len() && shape_of(end) == shape {
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
    out.push_str("</w:p>");
    for control in &paragraph.controls {
        if let Control::Table(table) = control {
            write_table(out, info, table, depth + 1)?;
            // Word 는 표 바로 뒤에 문단이 있어야 한다.
            out.push_str("<w:p/>");
        }
    }
    Ok(())
}

fn write_table(out: &mut String, info: &DocInfo, table: &Table, depth: usize) -> Result<()> {
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
                    write_paragraph(out, info, paragraph, depth)?;
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

fn section_props(document: &Document) -> String {
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
        r#"<w:sectPr><w:pgSz w:w="{width}" w:h="{height}"{orient}/><w:pgMar w:top="{}" w:right="{}" w:bottom="{}" w:left="{}" w:header="{}" w:footer="{}" w:gutter="{}"/></w:sectPr>"#,
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
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"#;

const ROOT_RELS: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"#;

const DOCUMENT_RELS: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"#;

const STYLES: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="20"/><w:lang w:val="ko-KR" w:eastAsia="ko-KR"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style></w:styles>"#;

/// rhwp 문서를 DOCX 바이트로 쓴다.
pub fn docx_from_document(document: &Document) -> Result<Vec<u8>> {
    let info = &document.doc_info;
    let mut body = String::new();
    for section in &document.sections {
        for paragraph in &section.paragraphs {
            write_paragraph(&mut body, info, paragraph, 0)?;
        }
    }
    let xml = format!(
        r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>{body}{}</w:body></w:document>"#,
        section_props(document)
    );

    let mut buffer = Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated);
    let io = |_| Error::new(ErrorCode::Internal, "could not write DOCX");
    for (name, content) in [
        ("[Content_Types].xml", CONTENT_TYPES),
        ("_rels/.rels", ROOT_RELS),
        ("word/_rels/document.xml.rels", DOCUMENT_RELS),
        ("word/styles.xml", STYLES),
        ("word/document.xml", xml.as_str()),
    ] {
        zip.start_file(name, options).map_err(io)?;
        zip.write_all(content.as_bytes())
            .map_err(|_| Error::new(ErrorCode::Internal, "could not write DOCX"))?;
    }
    zip.finish().map_err(io)?;
    Ok(buffer.into_inner())
}
