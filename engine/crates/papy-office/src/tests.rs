use super::*;
use crate::notes::{ENDNOTE_MARK, FOOTNOTE_MARK, HeaderFooterText};
use std::io::{Cursor, Write};

/// 주어진 document.xml(과 styles.xml)로 최소 DOCX 를 만든다.
fn docx(document: &str, styles: Option<&str>) -> Vec<u8> {
    let mut buffer = Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    let options = zip::write::SimpleFileOptions::default();
    zip.start_file("[Content_Types].xml", options).unwrap();
    zip.write_all(b"<Types/>").unwrap();
    zip.start_file("word/document.xml", options).unwrap();
    zip.write_all(document.as_bytes()).unwrap();
    if let Some(styles) = styles {
        zip.start_file("word/styles.xml", options).unwrap();
        zip.write_all(styles.as_bytes()).unwrap();
    }
    zip.finish().unwrap();
    buffer.into_inner()
}

const NS: &str = r#"xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main""#;

#[test]
fn detects_docx() {
    assert!(is_docx(&docx(
        &format!("<w:document {NS}><w:body/></w:document>"),
        None
    )));
    assert!(!is_docx(b"PK\x03\x04 not a zip"));
}

#[test]
fn paragraphs_runs_styles_and_entities() {
    let styles = format!(
        r#"<w:styles {NS}><w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>
        <w:style w:type="paragraph" w:styleId="Heading1"><w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style></w:styles>"#
    );
    let document = format!(
        r#"<w:document {NS}><w:body>
        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>제목</w:t></w:r></w:p>
        <w:p><w:r><w:rPr><w:i/><w:color w:val="FF0000"/></w:rPr><w:t xml:space="preserve">빨강 기울임 </w:t></w:r><w:r><w:rPr><w:b w:val="0"/><w:u w:val="single"/></w:rPr><w:t>A &amp; B &lt;C&gt;</w:t></w:r></w:p>
        <w:p/>
        <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/></w:numPr></w:pPr><w:r><w:t>항목</w:t><w:tab/><w:t>끝</w:t></w:r></w:p>
        <w:p><w:r><w:drawing/></w:r></w:p>
        </w:body></w:document>"#
    );
    let (html, report) = docx_to_html(&docx(&document, Some(&styles))).unwrap();
    assert!(html.contains(r#"<p style="text-align:center"><span style="font-size:16pt;font-weight:bold;">제목</span></p>"#), "{html}");
    assert!(
        html.contains("font-style:italic;color:#FF0000;\">빨강 기울임 </span>"),
        "{html}"
    );
    assert!(
        html.contains("font-size:11pt;text-decoration:underline;\">A &amp; B &lt;C&gt;</span>"),
        "{html}"
    );
    assert!(html.contains("<p></p>"));
    assert!(
        html.contains("<p>• <span style=\"font-size:11pt;\">항목</span>\t"),
        "{html}"
    );
    assert_eq!(report.dropped_drawings, 1);
}

#[test]
fn tables_with_merged_cells() {
    let cell = |text: &str, extra: &str| {
        format!("<w:tc><w:tcPr>{extra}</w:tcPr><w:p><w:r><w:t>{text}</w:t></w:r></w:p></w:tc>")
    };
    let document = format!(
        r#"<w:document {NS}><w:body><w:tbl>
        <w:tr>{}{}</w:tr>
        <w:tr>{}{}{}</w:tr>
        <w:tr>{}{}{}</w:tr>
        </w:tbl></w:body></w:document>"#,
        cell("가로 병합", r#"<w:gridSpan w:val="2"/>"#),
        cell("C1", ""),
        cell("세로 병합", r#"<w:vMerge w:val="restart"/>"#),
        cell("B2", ""),
        cell("C2", ""),
        cell("", "<w:vMerge/>"),
        cell("B3", ""),
        cell("C3", ""),
    );
    let (html, _) = docx_to_html(&docx(&document, None)).unwrap();
    assert!(
        html.contains(r#"<td colspan="2"><p>가로 병합</p></td>"#),
        "{html}"
    );
    assert!(
        html.contains(r#"<td rowspan="2"><p>세로 병합</p></td>"#),
        "{html}"
    );
    // 이어지는 칸은 내보내지 않는다: 셋째 줄은 두 칸.
    assert!(
        html.contains("<tr><td><p>B3</p></td><td><p>C3</p></td></tr>"),
        "{html}"
    );
}

#[test]
fn rejects_non_docx_and_malformed_xml() {
    let mut buffer = Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    zip.start_file("xl/workbook.xml", zip::write::SimpleFileOptions::default())
        .unwrap();
    zip.finish().unwrap();
    let error = docx_to_html(&buffer.into_inner()).unwrap_err();
    assert_eq!(error.code, papy_model::ErrorCode::UnsupportedFormat);
    let error = docx_to_html(&docx("<w:document><w:body><w:p></w:x>", None)).unwrap_err();
    assert_eq!(error.code, papy_model::ErrorCode::InvalidDocument);
}

#[test]
fn writes_a_valid_package() {
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "Word <안녕> & 문서")
        .unwrap();
    let bytes = docx_from_document(core.document()).unwrap();
    assert!(is_docx(&bytes));
    let mut archive = zip::ZipArchive::new(Cursor::new(&bytes[..])).unwrap();
    for part in [
        "[Content_Types].xml",
        "_rels/.rels",
        "word/_rels/document.xml.rels",
        "word/styles.xml",
    ] {
        assert!(archive.by_name(part).is_ok(), "{part}");
    }
    let (html, _) = docx_to_html(&bytes).unwrap();
    assert!(html.contains("Word &lt;안녕&gt; &amp; 문서"), "{html}");
}

/// 1×1 빨간 점 PNG
pub(crate) const PNG_1X1: &[u8] = &[
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
    0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, 0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00,
    0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD, 0x8D, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
    0x44, 0xAE, 0x42, 0x60, 0x82,
];

/// document.xml 과 추가 파일로 DOCX 를 만든다.
pub(crate) fn docx_with(document: &str, parts: &[(&str, &[u8])]) -> Vec<u8> {
    let mut buffer = Cursor::new(Vec::new());
    let mut zip = zip::ZipWriter::new(&mut buffer);
    let options = zip::write::SimpleFileOptions::default();
    zip.start_file("[Content_Types].xml", options).unwrap();
    zip.write_all(b"<Types/>").unwrap();
    zip.start_file("word/document.xml", options).unwrap();
    zip.write_all(document.as_bytes()).unwrap();
    for (name, bytes) in parts {
        zip.start_file(*name, options).unwrap();
        zip.write_all(bytes).unwrap();
    }
    zip.finish().unwrap();
    buffer.into_inner()
}

pub(crate) fn drawing(embed: &str) -> String {
    format!(
        r#"<w:r><w:drawing><wp:inline><wp:extent cx="914400" cy="457200"/><a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed="{embed}"/></pic:blipFill></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"#
    )
}

pub(crate) const RELS: &str = r#"<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/><Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../../outside.png"/><Relationship Id="rId7" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/notes.txt"/><Relationship Id="rId8" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="https://example.com/a.png" TargetMode="External"/></Relationships>"#;

#[test]
fn images_become_data_uris() {
    let document = format!(
        r#"<w:document {NS} xmlns:wp="wp" xmlns:a="a" xmlns:pic="pic" xmlns:r="r"><w:body><w:p><w:r><w:t>앞</w:t></w:r>{}<w:r><w:t>뒤</w:t></w:r></w:p><w:p>{}{}{}</w:p><w:p><w:r><w:drawing/></w:r></w:p></w:body></w:document>"#,
        drawing("rId5"),
        drawing("rId6"),
        drawing("rId7"),
        drawing("rId8"),
    );
    let bytes = docx_with(
        &document,
        &[
            ("word/_rels/document.xml.rels", RELS.as_bytes()),
            ("word/media/image1.png", PNG_1X1),
            ("word/media/notes.txt", b"not an image"),
        ],
    );
    let (html, report) = docx_to_html(&bytes).unwrap();
    assert_eq!(report.images, 1, "{html}");
    // 패키지 밖 경로, 그림이 아닌 파일, 외부 링크, 빈 drawing 은 옮기지 않는다.
    assert_eq!(report.dropped_drawings, 4, "{html}");
    assert!(
        html.contains(r#"<img src="data:image/png;base64,iVBORw0KGgo"#),
        "{html}"
    );
    assert!(html.contains(r#"width="96" height="48""#), "{html}");
    // 그림 앞뒤 글자가 순서대로 남는다.
    let (front, back) = (html.find('앞').unwrap(), html.find('뒤').unwrap());
    let image = html.find("<img").unwrap();
    assert!(front < image && image < back, "{html}");
}

#[test]
fn normalizes_package_paths() {
    assert_eq!(
        read::normalize_path("word/media/../media/a.png"),
        "word/media/a.png"
    );
    assert_eq!(read::normalize_path("word/../../x"), "");
}

pub(crate) const NOTES_DOCUMENT: &str = r#"<w:document xmlns:w="w" xmlns:r="r"><w:body>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>첫째</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>하위</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>둘째</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr><w:r><w:t>점</w:t></w:r></w:p>
<w:p><w:r><w:t>본문</w:t></w:r><w:r><w:footnoteReference w:id="2"/></w:r><w:r><w:t xml:space="preserve"> 계속</w:t></w:r></w:p>
<w:tbl><w:tr><w:tc><w:p><w:r><w:t>칸</w:t></w:r><w:r><w:footnoteReference w:id="3"/></w:r></w:p></w:tc></w:tr></w:tbl>
<w:p><w:r><w:t>끝</w:t></w:r><w:r><w:endnoteReference w:id="1"/></w:r></w:p>
<w:sectPr><w:headerReference w:type="default" r:id="rIdH"/><w:footerReference w:type="default" r:id="rIdF"/><w:headerReference w:type="first" r:id="rIdH"/><w:titlePg/></w:sectPr>
</w:body></w:document>"#;

pub(crate) const NOTES_PARTS: [(&str, &str); 6] = [
    (
        "word/endnotes.xml",
        r#"<w:endnotes xmlns:w="w"><w:endnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:endnote><w:endnote w:id="1"><w:p><w:r><w:endnoteRef/></w:r><w:r><w:t xml:space="preserve"> 미주 글</w:t></w:r></w:p></w:endnote></w:endnotes>"#,
    ),
    (
        "word/_rels/document.xml.rels",
        r#"<Relationships xmlns="r"><Relationship Id="rIdH" Type="header" Target="header1.xml"/><Relationship Id="rIdF" Type="footer" Target="footer1.xml"/></Relationships>"#,
    ),
    (
        "word/header1.xml",
        r#"<w:hdr xmlns:w="w"><w:p><w:r><w:t>머리 글</w:t></w:r></w:p></w:hdr>"#,
    ),
    (
        "word/footer1.xml",
        r#"<w:ftr xmlns:w="w"><w:p><w:r><w:t>꼬리</w:t><w:tab/><w:t>글</w:t></w:r></w:p></w:ftr>"#,
    ),
    (
        "word/footnotes.xml",
        r#"<w:footnotes xmlns:w="w"><w:footnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:footnote><w:footnote w:id="2"><w:p><w:r><w:footnoteRef/></w:r><w:r><w:t xml:space="preserve"> 각주 &amp; 하나</w:t></w:r></w:p><w:p><w:r><w:t>둘째 줄</w:t></w:r></w:p></w:footnote><w:footnote w:id="3"><w:p><w:r><w:t>표 각주</w:t></w:r></w:p></w:footnote></w:footnotes>"#,
    ),
    (
        "word/numbering.xml",
        r#"<w:numbering xmlns:w="w"><w:abstractNum w:abstractNumId="7"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl><w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="ganada"/><w:lvlText w:val="%1-%2)"/></w:lvl></w:abstractNum><w:abstractNum w:abstractNumId="8"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/><w:lvlText w:val="&#61623;"/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="7"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="8"/></w:num></w:numbering>"#,
    ),
];

pub(crate) fn notes_docx() -> Vec<u8> {
    let parts: Vec<(&str, &[u8])> = NOTES_PARTS
        .iter()
        .map(|(name, xml)| (*name, xml.as_bytes()))
        .collect();
    docx_with(NOTES_DOCUMENT, &parts)
}

#[test]
fn numbering_footnotes_headers_and_footers() {
    let (html, report) = docx_to_html(&notes_docx()).unwrap();
    assert!(html.contains("<p>1. 첫째</p>"), "{html}");
    assert!(html.contains("<p>1-가) 하위</p>"), "{html}");
    assert!(html.contains("<p>2. 둘째</p>"), "{html}");
    assert!(html.contains("<p>• 점</p>"), "{html}");
    assert!(
        html.contains(&format!("본문{FOOTNOTE_MARK} 계속")),
        "{html}"
    );
    assert_eq!(
        report.footnotes[0],
        vec![" 각주 & 하나".to_string(), "둘째 줄".to_string()]
    );
    assert!(html.contains(&format!("끝{ENDNOTE_MARK}")), "{html}");
    assert_eq!(report.endnotes, vec![vec![" 미주 글".to_string()]]);
    // 표 안 각주는 표시와 함께 넘기고(papy-hwp 가 칸으로 옮긴다), 글자가 있는 첫 쪽 머리말만 옮기지 못한다.
    assert!(html.contains(&format!("칸{FOOTNOTE_MARK}")), "{html}");
    assert_eq!(report.footnotes.len(), 2);
    assert_eq!(report.dropped_notes, 1);
    assert_eq!(report.hide_first_page, (true, true));
    assert_eq!(
        report.headers,
        vec![HeaderFooterText {
            apply_to: 0,
            paragraphs: vec!["머리 글".into()]
        }]
    );
    assert_eq!(report.footers[0].paragraphs, vec!["꼬리\t글".to_string()]);
}

#[test]
fn number_formats() {
    use crate::notes::format_number;
    assert_eq!(format_number(4, "lowerRoman"), "iv");
    assert_eq!(format_number(1994, "upperRoman"), "MCMXCIV");
    assert_eq!(format_number(27, "lowerLetter"), "aa");
    assert_eq!(format_number(15, "ganada"), "가");
    assert_eq!(format_number(3, "decimalEnclosedCircle"), "③");
    assert_eq!(format_number(7, "decimal"), "7");
}

/// 묶음 도형: 묶음 좌표(200×100)를 2,000,000×1,000,000 EMU 로 늘린 묶음 안에 사각형·글상자 타원,
/// 안쪽 묶음(좌표를 반으로 줄임)의 선, 그림 하나.
const GROUP_DOCUMENT: &str = r#"<w:document xmlns:w="w" xmlns:wp="wp" xmlns:a="a" xmlns:wpg="wpg" xmlns:wps="wps" xmlns:pic="pic"><w:body><w:p><w:r><w:drawing><wp:anchor behindDoc="0"><wp:positionH relativeFrom="page"><wp:posOffset>500000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>100000</wp:posOffset></wp:positionV><wp:extent cx="2000000" cy="1000000"/><wp:wrapSquare/><a:graphic><a:graphicData><wpg:wgp>
<wpg:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="2000000" cy="1000000"/><a:chOff x="0" y="0"/><a:chExt cx="200" cy="100"/></a:xfrm></wpg:grpSpPr>
<wps:wsp><wps:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="50"/></a:xfrm><a:prstGeom prst="rect"/><a:solidFill><a:srgbClr val="FF0000"/></a:solidFill></wps:spPr><wps:txbx><w:txbxContent><w:p><w:r><w:t>묶음 글</w:t></w:r></w:p></w:txbxContent></wps:txbx></wps:wsp>
<wps:wsp><wps:spPr><a:xfrm flipH="1"><a:off x="100" y="50"/><a:ext cx="100" cy="50"/></a:xfrm><a:prstGeom prst="ellipse"/></wps:spPr></wps:wsp>
<wpg:grpSp><wpg:grpSpPr><a:xfrm><a:off x="0" y="50"/><a:ext cx="100" cy="50"/><a:chOff x="0" y="0"/><a:chExt cx="200" cy="100"/></a:xfrm></wpg:grpSpPr>
<wps:wsp><wps:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="200" cy="0"/></a:xfrm><a:prstGeom prst="line"/><a:ln w="12700"><a:solidFill><a:srgbClr val="00FF00"/></a:solidFill></a:ln></wps:spPr></wps:wsp>
</wpg:grpSp>
<pic:pic><pic:blipFill><a:blip r:embed="rId1" xmlns:r="r"/></pic:blipFill></pic:pic>
</wpg:wgp></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r><w:r><w:t>뒤 글</w:t></w:r></w:p></w:body></w:document>"#;

#[test]
fn group_shapes_are_unpacked() {
    let (html, report) = docx_to_html(&docx(GROUP_DOCUMENT, None)).unwrap();
    assert_eq!(html.matches(SHAPE_MARK).count(), 3, "{html}");
    assert!(html.contains("뒤 글"));
    // 묶음 안 그림만 빠진다
    assert_eq!(report.dropped_drawings, 1);
    let shapes = &report.shapes;
    assert_eq!(shapes.len(), 3);
    let rect = &shapes[0];
    assert_eq!(rect.kind, ShapeKind::Rectangle);
    assert_eq!((rect.width, rect.height), (1_000_000, 500_000));
    assert_eq!(rect.horizontal, (Relative::Page, 500_000));
    assert_eq!(rect.vertical, (Relative::Paragraph, 100_000));
    assert_eq!(rect.wrap, Wrap::Square);
    assert!(!rect.inline);
    assert_eq!(rect.fill, Some(0xFF0000));
    assert_eq!(rect.text, vec!["묶음 글".to_owned()]);
    let ellipse = &shapes[1];
    assert_eq!(ellipse.kind, ShapeKind::Ellipse);
    assert_eq!(ellipse.horizontal, (Relative::Page, 1_500_000));
    assert_eq!(ellipse.vertical, (Relative::Paragraph, 600_000));
    assert!(ellipse.flip_h);
    // 안쪽 묶음: (0,0)-(200,0) → 바깥 (0,50)-(100,50) → (0,500000)-(1000000,500000)
    let line = &shapes[2];
    assert_eq!(line.kind, ShapeKind::Line);
    assert_eq!((line.width, line.height), (1_000_000, 0));
    assert_eq!(line.horizontal, (Relative::Page, 500_000));
    assert_eq!(line.vertical, (Relative::Paragraph, 600_000));
    assert_eq!(line.line, Some(0x00FF00));
}

/// 문서 XML 부분을 글로 꺼낸다.
fn part_text(bytes: &[u8], name: &str) -> String {
    let mut archive = zip::ZipArchive::new(Cursor::new(bytes)).unwrap();
    let mut xml = String::new();
    std::io::Read::read_to_string(&mut archive.by_name(name).unwrap(), &mut xml).unwrap();
    xml
}

#[test]
fn forms_and_equations_are_written() {
    use rhwp::model::control::{Control, Equation, FormObject, FormType};
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "양식").unwrap();
    let mut document = core.document().clone();
    let paragraph = &mut document.sections[0].paragraphs[0];
    let check = FormObject {
        form_type: FormType::CheckBox,
        caption: "동의함".into(),
        value: 1,
        ..FormObject::default()
    };
    let mut equation = Equation {
        script: "1 over 2".into(),
        ..Equation::default()
    };
    equation.common.width = 2000;
    equation.common.height = 3000;
    equation.baseline = 70;
    for control in [
        Control::Form(Box::new(check)),
        Control::Equation(Box::new(equation)),
    ] {
        paragraph.controls.push(control);
    }
    // 그림을 못 그리면 수식 스크립트를 글로 넣는다.
    let plain = docx_from_document(&document).unwrap();
    let xml = part_text(&plain, "word/document.xml");
    assert!(xml.contains("☑ 동의함"), "{xml}");
    assert!(xml.contains("1 over 2"), "{xml}");
    // 그리는 함수가 있으면 그림으로 넣고, 기준선 아래 부분(30%)만큼 내린다.
    let render = |_: &Equation| Some(PNG_1X1.to_vec());
    let pictured =
        docx_from_document_with(&document, &std::collections::HashMap::new(), Some(&render))
            .unwrap();
    let xml = part_text(&pictured, "word/document.xml");
    assert!(
        !xml.contains("1 over 2") && xml.contains("<wp:inline"),
        "{xml}"
    );
    assert!(xml.contains(r#"<w:position w:val="-9"/>"#), "{xml}");
    assert!(part_text(&pictured, "word/_rels/document.xml.rels").contains("media/image1.png"));
}

#[test]
fn page_setup_is_read_from_the_longest_section() {
    // 첫 구역(문단 하나)은 아래 여백이 큰 표지, 둘째 구역(문단 둘)이 본문: 가로 A4, 2단.
    let document = format!(
        r#"<w:document {NS}><w:body><w:p><w:pPr><w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="10000" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr></w:pPr></w:p><w:p/><w:p/><w:sectPr><w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/><w:pgMar w:top="1000" w:right="800" w:bottom="900" w:left="700" w:header="400" w:footer="300" w:gutter="0"/><w:cols w:num="2" w:space="425"/></w:sectPr></w:body></w:document>"#
    );
    let (_, report) = docx_to_html(&docx(&document, None)).unwrap();
    let page = report.page.expect("쪽 설정");
    assert_eq!((page.width, page.height), (16838 * 5, 11906 * 5));
    assert_eq!((page.margin_left, page.margin_right), (700 * 5, 800 * 5));
    // Word 위 여백 1000 = 머리말 자리 400 + 본문 여백 600
    assert_eq!((page.margin_header, page.margin_top), (400 * 5, 600 * 5));
    assert_eq!((page.margin_footer, page.margin_bottom), (300 * 5, 600 * 5));
    assert_eq!((page.columns, page.column_gap), (2, 425 * 5));
    // 이상한 값(본문이 남지 않는 여백)은 버린다.
    let broken = format!(
        r#"<w:document {NS}><w:body><w:p/><w:sectPr><w:pgSz w:w="2000" w:h="2000"/><w:pgMar w:top="900" w:right="900" w:bottom="900" w:left="900"/></w:sectPr></w:body></w:document>"#
    );
    assert_eq!(docx_to_html(&docx(&broken, None)).unwrap().1.page, None);
}

#[test]
fn bmp_pictures_become_png() {
    // 2×1 BMP(24비트)
    let mut bmp = Vec::new();
    bmp.extend_from_slice(b"BM");
    bmp.extend_from_slice(&(54u32 + 8).to_le_bytes());
    bmp.extend_from_slice(&[0; 4]);
    bmp.extend_from_slice(&54u32.to_le_bytes());
    bmp.extend_from_slice(&40u32.to_le_bytes());
    bmp.extend_from_slice(&2i32.to_le_bytes());
    bmp.extend_from_slice(&1i32.to_le_bytes());
    bmp.extend_from_slice(&1u16.to_le_bytes());
    bmp.extend_from_slice(&24u16.to_le_bytes());
    bmp.extend_from_slice(&[0; 24]);
    bmp.extend_from_slice(&[0, 0, 255, 255, 0, 0, 0, 0]);
    let png = super::write::bmp_to_png_for_test(&bmp).expect("PNG");
    assert!(png.starts_with(b"\x89PNG"));
}
