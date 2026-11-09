use super::*;
use crate::notes::{COLUMN_BREAK_MARK, ENDNOTE_MARK, FOOTNOTE_MARK, HeaderFooterText};
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
    assert!(html.contains(r#"<p style="text-align:center;"><span style="font-size:16pt;font-weight:bold;">제목</span></p>"#), "{html}");
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
        // 정의 없는 목록 문단은 글머리표 "•" 로(한글 문단 머리 표시). 탭은 런 서식 안에 둔다(밖에 두면 기본 글꼴로 빠진다).
        html.contains(&format!(
            "<p>{}<span style=\"font-size:11pt;\">항목\t끝</span>",
            list_mark(0, 0)
        )),
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
    // 본문 번호 목록은 한글 문단 번호로 옮길 표시(정의·수준)와 정의로 읽는다.
    assert!(
        html.contains(&format!("<p>{}첫째</p>", list_mark(0, 0))),
        "{html}"
    );
    assert!(
        html.contains(&format!("<p>{}하위</p>", list_mark(0, 1))),
        "{html}"
    );
    assert!(
        html.contains(&format!("<p>{}둘째</p>", list_mark(0, 0))),
        "{html}"
    );
    assert!(
        html.contains(&format!("<p>{}점</p>", list_mark(1, 0))),
        "{html}"
    );
    assert_eq!(report.lists[0].bullet, None);
    assert_eq!(report.lists[0].levels[0], (0, "^1.".to_string(), 1));
    assert_eq!(report.lists[0].levels[1], (8, "^1-^2)".to_string(), 1));
    assert_eq!(report.lists[1].bullet, Some('•'));
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
            section: 0,
            apply_to: 0,
            paragraphs: vec!["머리 글".into()],
            html: Some("<html><body><p>머리 글</p></body></html>".into()),
            ..HeaderFooterText::default()
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

fn part_text_opt(bytes: &[u8], name: &str) -> Option<String> {
    let mut archive = zip::ZipArchive::new(Cursor::new(bytes)).ok()?;
    let mut xml = String::new();
    std::io::Read::read_to_string(&mut archive.by_name(name).ok()?, &mut xml).ok()?;
    Some(xml)
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
    let pictured = docx_from_document_with(
        &document,
        &std::collections::HashMap::new(),
        Some(&render),
        true,
    )
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

#[test]
fn groups_polygons_and_floating_pictures_are_written() {
    use rhwp::model::Point;
    use rhwp::model::control::Control;
    use rhwp::model::shape::{GroupShape, PolygonShape, RectangleShape, ShapeObject};
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "도형").unwrap();
    let mut document = core.document().clone();
    // 묶음: 사각형(0,0 1000×500)과 삼각형(1000,0 1000×1000)
    let mut rect = RectangleShape::default();
    rect.drawing.shape_attr.original_width = 1000;
    rect.drawing.shape_attr.original_height = 500;
    rect.drawing.shape_attr.render_sx = 1.0;
    rect.drawing.shape_attr.render_sy = 1.0;
    let mut triangle = PolygonShape {
        points: vec![
            Point { x: 0, y: 1000 },
            Point { x: 500, y: 0 },
            Point { x: 1000, y: 1000 },
            Point { x: 0, y: 1000 },
        ],
        ..PolygonShape::default()
    };
    let attr = &mut triangle.drawing.shape_attr;
    (attr.original_width, attr.original_height) = (1000, 1000);
    (attr.render_sx, attr.render_sy, attr.render_tx) = (1.0, 1.0, 1000.0);
    let mut group = GroupShape {
        children: vec![
            ShapeObject::Rectangle(rect),
            ShapeObject::Polygon(triangle.clone()),
        ],
        ..GroupShape::default()
    };
    (group.common.width, group.common.height) = (2000, 1000);
    group.common.treat_as_char = true;
    triangle.common.width = 1000;
    triangle.common.height = 1000;
    let paragraph = &mut document.sections[0].paragraphs[0];
    paragraph
        .controls
        .push(Control::Shape(Box::new(ShapeObject::Group(group))));
    paragraph
        .controls
        .push(Control::Shape(Box::new(ShapeObject::Polygon(triangle))));
    let xml = part_text(&docx_from_document(&document).unwrap(), "word/document.xml");
    // 묶음 안 두 도형이 묶음 좌표(EMU = HWPUNIT × 127)로 들어간다.
    assert!(xml.contains("<wpg:wgp>"), "{xml}");
    assert!(
        xml.contains(r#"<a:chExt cx="254000" cy="127000"/>"#),
        "{xml}"
    );
    assert!(xml.contains(r#"<a:off x="127000" y="0"/>"#), "{xml}");
    // 다각형: 꼭짓점 경로(처음 = 끝이면 닫는다), 떠 있는 개체는 wp:anchor.
    assert!(
        xml.contains(r#"<a:path w="1000" h="1000"><a:moveTo><a:pt x="0" y="1000"/></a:moveTo><a:lnTo><a:pt x="500" y="0"/></a:lnTo>"#),
        "{xml}"
    );
    assert!(
        xml.contains("<a:close/>") && xml.contains("<wp:anchor"),
        "{xml}"
    );
}

#[test]
fn table_cells_keep_borders_fill_alignment_and_height() {
    use rhwp::model::control::Control;
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.create_table_native(0, 0, 0, 2, 2).unwrap();
    let mut document = core.document().clone();
    // 칸 테두리·배경: 왼쪽만 없음, 노란 배경
    let mut fill = rhwp::model::style::BorderFill::default();
    fill.borders[0].line_type = rhwp::model::style::BorderLineType::None;
    fill.fill.fill_type = rhwp::model::style::FillType::Solid;
    fill.fill.solid = Some(rhwp::model::style::SolidFill {
        background_color: 0x0000_FFFF,
        ..Default::default()
    });
    document.doc_info.border_fills.push(fill);
    let fill_id = document.doc_info.border_fills.len() as u16;
    let table = document.sections[0]
        .paragraphs
        .iter_mut()
        .flat_map(|p| p.controls.iter_mut())
        .find_map(|c| match c {
            Control::Table(t) => Some(t),
            _ => None,
        })
        .expect("표");
    // 둘째 열은 병합 칸에서만 너비를 알 수 있다: 첫 행 = 2000 + 3000, 둘째 행 = 5000(가로 병합).
    table.cells[0].width = 2000;
    table.cells[1].width = 3000;
    table.cells[0].border_fill_id = fill_id;
    table.cells[0].height = 1500;
    table.cells[0].vertical_align = rhwp::model::table::VerticalAlign::Center;
    table.cells[2].col_span = 2;
    table.cells[2].width = 5000;
    table.cells.remove(3);
    let bytes = docx_from_document(&document).unwrap();
    let xml = part_text(&bytes, "word/document.xml");
    assert!(
        xml.contains(r#"<w:gridCol w:w="400"/><w:gridCol w:w="600"/>"#),
        "{xml}"
    );
    assert!(xml.contains(r#"<w:left w:val="nil"/>"#), "{xml}");
    assert!(
        xml.contains(r#"<w:shd w:val="clear" w:color="auto" w:fill="FFFF00"/>"#),
        "{xml}"
    );
    assert!(xml.contains(r#"<w:vAlign w:val="center"/>"#), "{xml}");
    // 행 높이는 칸 테두리 굵기(0.35pt ≈ 7 twip)를 뺀다(Word 는 행 높이에 테두리를 더한다). 다시 읽으면 더한다.
    assert!(
        xml.contains(r#"<w:trHeight w:val="293" w:hRule="atLeast"/>"#),
        "{xml}"
    );
    // 다시 읽으면 rhwp 표 붙여넣기가 읽는 CSS 로 옮긴다.
    let (html, _) = docx_to_html(&bytes).unwrap();
    for css in [
        "border-left:none;",
        "background-color:#FFFF00;",
        "vertical-align:middle;",
        "height:15.0pt;",
    ] {
        assert!(html.contains(css), "{css}: {html}");
    }
}

/// 구역 안에서 단 정의가 바뀌면 이어지는 구역으로 나누고, 문단 탭 정의·기본 탭 간격을 쓴다.
#[test]
fn column_changes_and_tab_stops_are_written() {
    use rhwp::model::control::Control;
    use rhwp::model::page::ColumnDef;
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "하나둘셋").unwrap();
    core.split_paragraph_native(0, 0, 2, None).unwrap();
    core.split_paragraph_native(0, 1, 1, None).unwrap();
    let mut document = core.document().clone();
    let two = ColumnDef {
        column_count: 2,
        spacing: 1000,
        ..Default::default()
    };
    let paragraphs = &mut document.sections[0].paragraphs;
    assert_eq!(paragraphs.len(), 3);
    paragraphs[1].controls.push(Control::ColumnDef(two));
    paragraphs[2].controls.push(Control::ColumnDef(ColumnDef {
        column_count: 1,
        ..Default::default()
    }));
    // 탭: 2배 단위 14500 → 7250 HWPUNIT = 1450 twip, 오른쪽 탭·점선 채움
    let mut tabs = rhwp::model::style::TabDef::default();
    tabs.tabs.push(rhwp::model::style::TabItem {
        position: 14500,
        tab_type: 1,
        fill_type: 3,
    });
    // 음수 위치(u32 로 넘친 값)·Word 범위 밖 탭은 뺀다(전에는 w:pos="429470700" 같은 값이 나갔다).
    for position in [(-1600i32) as u32, 70_000_000] {
        tabs.tabs.push(rhwp::model::style::TabItem {
            position,
            tab_type: 0,
            fill_type: 0,
        });
    }
    document.doc_info.tab_defs.push(tabs);
    let tab_id = (document.doc_info.tab_defs.len() - 1) as u16;
    let shape = paragraphs[0].para_shape_id as usize;
    document.doc_info.para_shapes[shape].tab_def_id = tab_id;
    let bytes = docx_from_document(&document).unwrap();
    let xml = part_text(&bytes, "word/document.xml");
    // 영역 셋: 1단(첫 문단) · 2단(이어서) · 1단(이어서). 구역 속성은 따로 문단을 더하지 않고 영역 끝 문단에 든다.
    assert_eq!(xml.matches("<w:sectPr>").count(), 3, "{xml}");
    assert_eq!(
        xml.matches(r#"<w:type w:val="continuous"/>"#).count(),
        2,
        "{xml}"
    );
    assert_eq!(xml.matches(r#"<w:cols w:num="2""#).count(), 1, "{xml}");
    assert_eq!(xml.matches("<w:p>").count(), 3, "{xml}");
    let second = xml.split("<w:p>").nth(2).unwrap();
    assert!(
        second.contains(r#"<w:cols w:num="2" w:space="200"/>"#),
        "{xml}"
    );
    // 다단 영역만 아래 여백을 줄 간격 여분(10pt 160% → 6pt = 120 twip)만큼 줄이고 원래 값을 남긴다.
    // Word 는 단 마지막 줄의 줄 간격까지 단 안에 넣어야 해서 한글 단 나누기가 한 단씩 밀렸다.
    assert_eq!(xml.matches("papy:hwpBottom=").count(), 1, "{xml}");
    let margin = |sect: &str| {
        let at = sect.find(r#"w:bottom=""#).unwrap() + r#"w:bottom=""#.len();
        sect[at..sect[at..].find('"').unwrap() + at]
            .parse::<i32>()
            .unwrap()
    };
    let sections: Vec<&str> = xml.split("<w:sectPr>").skip(1).collect();
    assert_eq!(margin(sections[0]) - margin(sections[1]), 120, "{xml}");
    assert!(
        xml.contains(r#"<w:tabs><w:tab w:val="right" w:leader="dot" w:pos="1450"/></w:tabs>"#),
        "{xml}"
    );
    let settings = part_text(&bytes, "word/settings.xml");
    assert!(
        settings.contains(r#"<w:defaultTabStop w:val="800"/>"#),
        "{settings}"
    );
}

#[test]
fn paragraph_spacing_indent_and_letter_spacing_round_trip() {
    // 기본 문단 스타일(Normal)의 뒤 간격·줄 간격이 pStyle 없는 문단에 들어간다.
    let styles = format!(
        r#"<w:styles {NS}><w:docDefaults><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:pPr><w:spacing w:after="200"/></w:pPr></w:style></w:styles>"#
    );
    let document = format!(
        r#"<w:document {NS}><w:body><w:p><w:r><w:t>기본</w:t></w:r></w:p><w:p><w:pPr><w:spacing w:before="240" w:line="360" w:lineRule="exact"/><w:ind w:left="720" w:hanging="360"/></w:pPr><w:r><w:rPr><w:spacing w:val="-10"/></w:rPr><w:t>내어쓰기</w:t></w:r></w:p></w:body></w:document>"#
    );
    let (html, _) = docx_to_html(&docx(&document, Some(&styles))).unwrap();
    assert!(
        html.contains(r#"<p style="margin-bottom:10.0pt;line-height:107%;">"#),
        "{html}"
    );
    assert!(
        html.contains(r#"margin-top:12.0pt;margin-bottom:10.0pt;line-height:24.0px;margin-left:36.0pt;text-indent:-18.0pt;"#),
        "{html}"
    );
    assert!(html.contains("letter-spacing:-0.50pt;"), "{html}");

    // 쓰기: HWP 문단 서식 → w:spacing·w:ind, 자간·장평 → w:spacing·w:w
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "문단").unwrap();
    let mut document = core.document().clone();
    let id = document.sections[0].paragraphs[0].para_shape_id as usize;
    let shape = &mut document.doc_info.para_shapes[id];
    // 2배 스케일: 10pt = 1000 HWPUNIT → 2000
    shape.spacing_before = 2000;
    shape.spacing_after = 1000;
    shape.margin_left = 4000;
    shape.indent = -2000;
    shape.line_spacing_type = rhwp::model::style::LineSpacingType::Fixed;
    shape.line_spacing = 1800;
    let char_id = document.sections[0].paragraphs[0].char_shapes[0].char_shape_id as usize;
    let char_shape = &mut document.doc_info.char_shapes[char_id];
    char_shape.base_size = 1000;
    char_shape.spacings = [-10; 7];
    char_shape.ratios = [90; 7];
    let xml = part_text(&docx_from_document(&document).unwrap(), "word/document.xml");
    assert!(
        xml.contains(r#"<w:spacing w:before="200" w:after="100" w:line="360" w:lineRule="exact"/><w:ind w:left="600" w:hanging="200"/>"#),
        "{xml}"
    );
    assert!(
        xml.contains(r#"<w:spacing w:val="-20"/><w:w w:val="90"/>"#),
        "{xml}"
    );
}

#[test]
fn table_layout_round_trip() {
    use rhwp::model::control::Control;
    // 글자처럼 놓은 표 둘이 한 문단에 → 테두리 없는 한 줄 표의 칸에 하나씩, 뒤 문단은 1pt
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.create_table_native(0, 0, 0, 1, 1).unwrap();
    let mut document = core.document().clone();
    let (para, table) = document.sections[0]
        .paragraphs
        .iter()
        .enumerate()
        .find_map(|(i, p)| {
            p.controls.iter().find_map(|c| match c {
                Control::Table(t) => Some((i, t.clone())),
                _ => None,
            })
        })
        .expect("표");
    let mut left = table.clone();
    left.common.treat_as_char = true;
    left.common.width = 10_000;
    let right = left.clone();
    let host = &mut document.sections[0].paragraphs[para];
    host.controls.retain(|c| !matches!(c, Control::Table(_)));
    host.controls.push(Control::Table(left));
    host.controls.push(Control::Table(right));
    host.text = "  ".into();
    let xml = part_text(&docx_from_document(&document).unwrap(), "word/document.xml");
    // 바깥 표 칸 안에 표, 칸 끝·표 뒤 문단은 1pt
    assert!(xml.contains(r#"<w:tc><w:tcPr><w:tcW w:w="#), "{xml}");
    assert!(xml.matches("<w:tbl>").count() >= 3, "{xml}");
    assert!(xml.contains(r#"w:line="20" w:lineRule="exact""#), "{xml}");

    // 쪽 기준 떠 있는 표(쪽 아래 가운데) → w:tblpPr, 왼쪽 자리 → w:tblInd
    let mut document = core.document().clone();
    let table = document.sections[0]
        .paragraphs
        .iter_mut()
        .flat_map(|p| p.controls.iter_mut())
        .find_map(|c| match c {
            Control::Table(t) => Some(t),
            _ => None,
        })
        .unwrap();
    table.common.treat_as_char = false;
    table.common.vert_rel_to = rhwp::model::shape::VertRelTo::Page;
    table.common.vert_align = rhwp::model::shape::VertAlign::Bottom;
    table.common.horz_align = rhwp::model::shape::HorzAlign::Center;
    table.common.margin.left = 500;
    let bytes = docx_from_document(&document).unwrap();
    let xml = part_text(&bytes, "word/document.xml");
    assert!(
        xml.contains(
            r#"w:vertAnchor="margin" w:horzAnchor="text" w:tblpXSpec="center" w:tblpYSpec="bottom""#
        ),
        "{xml}"
    );
    assert!(
        xml.contains(r#"<w:tblInd w:w="100" w:type="dxa"/>"#),
        "{xml}"
    );
    let (html, report) = docx_to_html(&bytes).unwrap();
    assert_eq!(report.table_indents, vec![500]);
    assert!(
        report.table_places[0].contains(r#""vertRelTo":"Page","vertAlign":"Bottom""#)
            && report.table_places[0].contains(r#""horzAlign":"Center""#),
        "{:?}",
        report.table_places
    );
    // 1pt 문단은 다시 읽을 때 뺀다.
    assert!(!html.contains("line-height:1.3px"), "{html}");
}

#[test]
fn table_cell_margins_ratio_and_thin_grid_columns() {
    // 칸 안쪽 여백(tblCellMar) → 표 padding, 장평(w:w) → font-stretch
    let document = format!(
        r#"<w:document {NS}><w:body><w:tbl><w:tblPr><w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="28" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/><w:right w:w="28" w:type="dxa"/></w:tblCellMar></w:tblPr><w:tr><w:tc><w:p><w:r><w:rPr><w:w w:val="90"/></w:rPr><w:t>칸</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p/></w:body></w:document>"#
    );
    let (html, _) = docx_to_html(&docx(&document, None)).unwrap();
    assert!(
        html.contains(r#"style="padding:0.02pt 1.40pt 0.02pt 1.40pt""#),
        "{html}"
    );
    assert!(html.contains("font-stretch:90%;"), "{html}");

    // 칸 하나가 가는 격자 열 여럿에 걸친 표: 열 너비를 칸 너비 합에 맞춘다(최소 너비로 부풀리지 않는다).
    use rhwp::model::control::Control;
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.create_table_native(0, 0, 0, 2, 4).unwrap();
    let mut document = core.document().clone();
    let table = document.sections[0]
        .paragraphs
        .iter_mut()
        .flat_map(|p| p.controls.iter_mut())
        .find_map(|c| match c {
            Control::Table(t) => Some(t),
            _ => None,
        })
        .unwrap();
    // 1행: [0..3) 600 + [3] 5000, 2행: [0] 200 + [1..4) 5400
    table
        .cells
        .retain(|c| !(c.row == 0 && (c.col == 1 || c.col == 2)) && !(c.row == 1 && c.col >= 2));
    for cell in &mut table.cells {
        match (cell.row, cell.col) {
            (0, 0) => (cell.col_span, cell.width) = (3, 600),
            (0, 3) => cell.width = 5000,
            (1, 0) => cell.width = 200,
            (1, 1) => (cell.col_span, cell.width) = (3, 5400),
            _ => {}
        }
    }
    let xml = part_text(&docx_from_document(&document).unwrap(), "word/document.xml");
    // 경계 0·200·(400)·600·5600 HWPUNIT → 40·40·40·1000 twip
    assert!(
        xml.contains(r#"<w:gridCol w:w="40"/><w:gridCol w:w="40"/><w:gridCol w:w="40"/><w:gridCol w:w="1000"/>"#),
        "{xml}"
    );
}

#[test]
fn column_breaks_and_diagonals_are_written() {
    use rhwp::model::control::Control;
    // 단 나누기: 쓰기 w:br type=column, 읽기 표시 글자
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "첫 단").unwrap();
    let mut document = core.document().clone();
    let mut second = document.sections[0].paragraphs[0].clone();
    second.text = "둘째 단".into();
    second.column_type = rhwp::model::paragraph::ColumnBreakType::Column;
    document.sections[0].paragraphs.push(second);
    let bytes = docx_from_document(&document).unwrap();
    let xml = part_text(&bytes, "word/document.xml");
    assert!(xml.contains(r#"<w:br w:type="column"/>"#), "{xml}");
    let (html, _) = docx_to_html(&bytes).unwrap();
    assert!(html.contains(COLUMN_BREAK_MARK), "{html}");

    // 칸 대각선(속성 비트 5~7 = 왼쪽 위 → 오른쪽 아래) → w:tl2br
    core.create_table_native(0, 0, 0, 1, 1).unwrap();
    let mut document = core.document().clone();
    let mut fill = rhwp::model::style::BorderFill {
        attr: 0b010_0000,
        ..Default::default()
    };
    fill.diagonal.diagonal_type = 1;
    document.doc_info.border_fills.push(fill);
    let id = document.doc_info.border_fills.len() as u16;
    for control in document.sections[0]
        .paragraphs
        .iter_mut()
        .flat_map(|p| p.controls.iter_mut())
    {
        if let Control::Table(table) = control {
            table.cells[0].border_fill_id = id;
        }
    }
    let xml = part_text(&docx_from_document(&document).unwrap(), "word/document.xml");
    assert!(xml.contains("<w:tl2br w:val=\"single\""), "{xml}");
    assert!(!xml.contains("<w:tr2bl"), "{xml}");
}

#[test]
fn numbering_note_marks_and_page_fields_are_written() {
    use rhwp::model::control::{AutoNumber, AutoNumberType, Control, PageNumberPos};
    use rhwp::model::paragraph::{MarkerHead, NumberingMarker};
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "항목").unwrap();
    let mut document = core.document().clone();
    // 번호 정의 1: 수준 1 "^1." (가나다), 수준 2 "^1.^2)"
    let mut numbering = rhwp::model::style::Numbering::default();
    numbering.level_formats[0] = "^1.".into();
    numbering.level_formats[1] = "^1.^2)".into();
    numbering.heads[0].number_format = 8;
    numbering.level_start_numbers = [1; 7];
    document.doc_info.numberings.push(numbering);
    let numbering_id = document.doc_info.numberings.len() as u16;
    let mut shape = document.doc_info.para_shapes[0].clone();
    shape.head_type = rhwp::model::style::HeadType::Number;
    shape.numbering_id = numbering_id;
    shape.para_level = 1;
    document.doc_info.para_shapes.push(shape);
    let shape_id = (document.doc_info.para_shapes.len() - 1) as u16;
    let section = &mut document.sections[0];
    let paragraph = &mut section.paragraphs[0];
    paragraph.para_shape_id = shape_id;
    paragraph.numbering_marker = NumberingMarker::Text("가.1)".into(), MarkerHead::default());
    // 쪽 번호 위치: 아래 가운데 "- 1 -", 글 안 전체 쪽 수
    paragraph
        .controls
        .push(Control::PageNumberPos(PageNumberPos {
            position: 5,
            dash_char: '-',
            ..PageNumberPos::default()
        }));
    paragraph.controls.push(Control::AutoNumber(AutoNumber {
        number_type: AutoNumberType::TotalPage,
        ..AutoNumber::default()
    }));
    // 미주 번호 모양: "문1)" 처럼 본문 크기
    section.section_def.endnote_shape.prefix_char = '문';
    section.section_def.endnote_shape.suffix_char = ')';
    section.section_def.endnote_shape.number_code_superscript = false;
    section.section_def.endnote_shape.number_format =
        rhwp::model::footnote::NumberFormat::HangulSyllable;
    section.paragraphs[0]
        .controls
        .push(Control::Endnote(Box::default()));
    let bytes = docx_from_document(&document).unwrap();
    let xml = part_text(&bytes, "word/document.xml");
    assert!(
        xml.contains(r#"<w:numPr><w:ilvl w:val="1"/><w:numId w:val="1"/></w:numPr>"#),
        "{xml}"
    );
    let numbering = part_text(&bytes, "word/numbering.xml");
    assert!(
        numbering.contains(
            r#"<w:numFmt w:val="ganada"/><w:suff w:val="space"/><w:lvlText w:val="%1."/>"#
        ),
        "{numbering}"
    );
    assert!(
        numbering.contains(r#"<w:lvlText w:val="%1.%2)"/>"#),
        "{numbering}"
    );
    assert!(
        xml.contains(r#"<w:fldSimple w:instr=" NUMPAGES ">"#),
        "{xml}"
    );
    assert!(
        xml.contains(r#"<w:r papy:noteMark="1"><w:t xml:space="preserve">문</w:t></w:r><w:r><w:endnoteReference w:id="1"/></w:r><w:r papy:noteMark="1"><w:t xml:space="preserve">)</w:t></w:r>"#),
        "{xml}"
    );
    assert!(
        xml.contains(r#"<w:endnotePr><w:numFmt w:val="ganada"/>"#),
        "{xml}"
    );
    let footer = part_text(&bytes, "word/footer1.xml");
    assert!(
        footer.contains(r#"<w:jc w:val="center"/></w:pPr><w:r><w:t xml:space="preserve">- </w:t></w:r><w:fldSimple w:instr=" PAGE ">"#),
        "{footer}"
    );
    // 다시 읽기: 머리말·꼬리말의 쪽 번호 필드는 rhwp 필드 글자로, 본문 번호 목록은 한글 문단 번호 정의로.
    assert_eq!(
        crate::notes::header_footer_texts(&footer),
        vec![format!("- {} -", crate::notes::PAGE_NUMBER_FIELD)]
    );
    let complex = r#"<w:ftr xmlns:w="w"><w:p><w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText> NUMPAGES </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>9</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r><w:r><w:t>쪽</w:t></w:r></w:p></w:ftr>"#;
    assert_eq!(
        crate::notes::header_footer_texts(complex),
        vec![format!("{}쪽", crate::notes::PAGE_COUNT_FIELD)]
    );
    // 다시 읽으면 같은 번호 모양의 한글 문단 번호 정의(가나다 → 표 43 코드 8)
    let (html, report) = docx_to_html(&bytes).unwrap();
    assert!(html.contains(LIST_MARK), "{html}");
    assert!(
        report.lists[0]
            .levels
            .iter()
            .any(|(_, text, _)| text == "^1.^2)"),
        "{:?}",
        report.lists
    );
}

#[test]
fn comments_tracked_changes_font_table_and_line_spacing() {
    // 읽기: Word 메모(쓴 사람·때·글·단 자리)와 변경 추적 수
    let document = format!(
        r#"<w:document {NS}><w:body><w:p><w:r><w:t>검토할 문장</w:t></w:r><w:r><w:commentReference w:id="3"/></w:r><w:ins w:id="1"><w:r><w:t>넣음</w:t></w:r></w:ins><w:del w:id="2"><w:r><w:delText>지움</w:delText></w:r></w:del></w:p></w:body></w:document>"#
    );
    let comments = format!(
        r#"<w:comments {NS}><w:comment w:id="3" w:author="홍길동" w:date="2026-11-06T10:00:00Z"><w:p><w:r><w:t>확인 필요</w:t></w:r></w:p><w:p><w:r><w:t>둘째 줄</w:t></w:r></w:p></w:comment></w:comments>"#
    );
    let bytes = docx_with(&document, &[("word/comments.xml", comments.as_bytes())]);
    let (html, report) = docx_to_html(&bytes).unwrap();
    assert_eq!(
        report.comments,
        vec![DocxComment {
            author: "홍길동".into(),
            date: "2026-11-06T10:00:00Z".into(),
            text: "확인 필요\n둘째 줄".into(),
            anchor: "검토할 문장넣음".into(),
        }]
    );
    assert_eq!((report.insertions, report.deletions), (1, 1));
    assert!(html.contains("넣음") && !html.contains("지움"), "{html}");

    // 쓰기: 글꼴 표(계열·한글 문자 집합·대체 글꼴), 배수 줄 간격은 글자 크기 × % 를 "최소"로
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "본문").unwrap();
    let document = core.document().clone();
    let bytes = docx_from_document(&document).unwrap();
    let fonts = part_text(&bytes, "word/fontTable.xml");
    assert!(fonts.contains(r#"<w:charset w:val="81"/>"#), "{fonts}");
    assert!(
        fonts.contains(r#"<w:family w:val="roman"/>"#)
            || fonts.contains(r#"<w:family w:val="swiss"/>"#),
        "{fonts}"
    );
    assert!(part_text(&bytes, "word/_rels/document.xml.rels").contains("fontTable.xml"));
    let xml = part_text(&bytes, "word/document.xml");
    // 빈 문서 본문: 10pt 160% → 16pt = 320 twip
    assert!(
        xml.contains(r#"w:line="320" w:lineRule="atLeast" papy:linePercent="160""#),
        "{xml}"
    );
    // Papy 로 다시 열면 원래 % 로
    let (html, _) = docx_to_html(&bytes).unwrap();
    assert!(html.contains("line-height:160%;"), "{html}");
}

/// 머리말·꼬리말을 영역(Word 구역)마다 쓴다: 둘째 한글 구역의 머리말은 둘째 구역 참조로(첫 구역 것으로 덮지 않는다),
/// 단 정의가 없는 한글 구역도 새 쪽 구역으로 시작하고, 표 칸 문단에 놓인 머리말(HWPX)도 쓴다.
#[test]
fn header_footers_are_written_per_section() {
    use rhwp::model::control::Control;
    use rhwp::model::header_footer::{Header, HeaderFooterApply};
    use rhwp::model::paragraph::Paragraph;
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "첫 구역").unwrap();
    let mut document = core.document().clone();
    let header = |text: &str| {
        let mut paragraph = Paragraph::new_empty();
        paragraph.text = text.into();
        paragraph.char_offsets = (0..text.chars().count() as u32).collect();
        paragraph.char_count = text.chars().count() as u32 + 1;
        Control::Header(Box::new(Header {
            apply_to: HeaderFooterApply::Both,
            paragraphs: vec![paragraph],
            ..Default::default()
        }))
    };
    // 첫 구역: 표 칸 안에 머리말을 둔다.
    let mut cell_paragraph = Paragraph::new_empty();
    cell_paragraph.controls.push(header("칸 속 머리말"));
    let table = rhwp::model::table::Table {
        row_count: 1,
        col_count: 1,
        cells: vec![rhwp::model::table::Cell {
            paragraphs: vec![cell_paragraph],
            ..Default::default()
        }],
        ..Default::default()
    };
    document.sections[0].paragraphs[0]
        .controls
        .push(Control::Table(Box::new(table)));
    // 둘째 구역: 단 정의 없이 머리말만.
    let mut second = document.sections[0].clone();
    let mut first = Paragraph::new_empty();
    first.text = "둘째 구역".into();
    first.char_offsets = (0..5).collect();
    first.char_count = 6;
    first.controls.push(header("둘째 머리말"));
    second.paragraphs = vec![first];
    document.sections.push(second);
    let bytes = docx_from_document(&document).unwrap();
    let xml = part_text(&bytes, "word/document.xml");
    let sections: Vec<&str> = xml.split("<w:sectPr>").skip(1).collect();
    assert_eq!(sections.len(), 2, "{xml}");
    assert!(
        sections[1].contains(r#"<w:type w:val="nextPage"/>"#),
        "{xml}"
    );
    let reference = |section: &str| {
        section
            .split(r#"<w:headerReference w:type="default" r:id=""#)
            .nth(1)
            .and_then(|rest| rest.split('"').next())
            .map(str::to_owned)
    };
    let (one, two) = (reference(sections[0]), reference(sections[1]));
    assert!(one.is_some() && two.is_some() && one != two, "{xml}");
    let headers: String = (1..=4)
        .filter_map(|i| part_text_opt(&bytes, &format!("word/header{i}.xml")))
        .collect();
    assert!(
        headers.contains("칸 속 머리말") && headers.contains("둘째 머리말"),
        "{headers}"
    );
}

#[test]
fn pictures_captions_inline_tables_links_and_memos_are_written() {
    use rhwp::model::bin_data::BinDataContent;
    use rhwp::model::control::{Control, Field, FieldType};
    use rhwp::model::image::Picture;
    use rhwp::model::paragraph::{FieldRange, Paragraph};
    use rhwp::model::shape::{Caption, CaptionDirection};
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.insert_text_native(0, 0, 0, "링크 글").unwrap();
    core.create_table_native(0, 0, 4, 1, 1).unwrap();
    let mut document = core.document().clone();
    // 그림 자료: 차례(1부터)와 저장 id 가 다르다. 그림은 차례 1(= id 4, WMF)을 가리킨다.
    // 전에는 id 로 찾아 그림이 빠졌고, WMF 는 Word 가 못 읽는 형식으로 보고 뺐다.
    let mut wmf = vec![0x01, 0x00, 0x09, 0x00, 0x00, 0x03];
    wmf.resize(64, 0);
    document.bin_data_content = vec![
        BinDataContent {
            id: 4,
            data: wmf.into(),
            extension: "wmf".into(),
        },
        BinDataContent {
            id: 3,
            data: PNG_1X1.to_vec().into(),
            extension: "png".into(),
        },
    ];
    let mut caption_paragraph = Paragraph::new_empty();
    caption_paragraph.insert_text_at(0, "그림 캡션");
    let mut picture = Picture::default();
    picture.image_attr.bin_data_id = 1;
    picture.common.treat_as_char = true;
    (picture.common.width, picture.common.height) = (1000, 1000);
    picture.caption = Some(Caption {
        direction: CaptionDirection::Bottom,
        paragraphs: vec![caption_paragraph.clone()],
        ..Caption::default()
    });
    let paragraphs = &mut document.sections[0].paragraphs;
    // 하이퍼링크(0..2)와 한글 메모(3..4) 필드
    let link = Field {
        field_type: FieldType::Hyperlink,
        command: r"https\://example.com/;1;0;0;".into(),
        ..Field::default()
    };
    let memo = Field {
        field_type: FieldType::Unknown,
        command: "MEMO/65535/1/1/1/검토자/".into(),
        memo_paragraphs: vec![caption_paragraph.clone()],
        ..Field::default()
    };
    let first = &mut paragraphs[0];
    let base = first.controls.len();
    first.controls.push(Control::Field(link));
    first.controls.push(Control::Field(memo));
    first.field_ranges.push(FieldRange {
        start_char_idx: 0,
        end_char_idx: 2,
        control_idx: base,
        ..FieldRange::default()
    });
    first.field_ranges.push(FieldRange {
        start_char_idx: 3,
        end_char_idx: 4,
        control_idx: base + 1,
        ..FieldRange::default()
    });
    first.controls.push(Control::Picture(Box::new(picture)));
    // 표: 글자처럼 취급, 위 캡션
    for paragraph in paragraphs.iter_mut() {
        for control in &mut paragraph.controls {
            if let Control::Table(table) = control {
                table.common.treat_as_char = true;
                table.caption = Some(Caption {
                    direction: CaptionDirection::Top,
                    paragraphs: vec![caption_paragraph.clone()],
                    ..Caption::default()
                });
            }
        }
    }
    let bytes = docx_from_document(&document).unwrap();
    let xml = part_text(&bytes, "word/document.xml");
    let rels = part_text(&bytes, "word/_rels/document.xml.rels");
    assert!(rels.contains(r#"Target="media/image1.wmf""#), "{rels}");
    assert!(
        part_text(&bytes, "[Content_Types].xml").contains(r#"Extension="wmf""#),
        "content types"
    );
    // 캡션 문단: 그림 아래(문단 뒤), 표 위(표 앞)
    assert!(xml.contains(r#"<w:p papy:caption="bottom">"#), "{xml}");
    let top = xml
        .find(r#"<w:p papy:caption="top">"#)
        .expect("top caption");
    assert!(top < xml.find("<w:tbl>").unwrap_or(0), "{xml}");
    assert!(xml.contains(r#"<w:tblPr papy:inline="1">"#), "{xml}");
    // 하이퍼링크·메모
    assert!(xml.contains(r#"<w:hyperlink r:id="rIdLink1">"#), "{xml}");
    assert!(
        rels.contains(r#"Target="https://example.com/" TargetMode="External""#),
        "{rels}"
    );
    assert!(
        xml.contains(r#"<w:commentRangeStart w:id="0"/>"#)
            && xml.contains(r#"<w:commentReference w:id="0"/>"#),
        "{xml}"
    );
    let comments = part_text(&bytes, "word/comments.xml");
    assert!(
        comments.contains(r#"w:author="검토자""#) && comments.contains("그림 캡션"),
        "{comments}"
    );
}

#[test]
fn tab_stops_short_rows_and_cell_tails_are_read_and_written() {
    // 문단 탭 → 붙여넣기 CSS tab-stops(위치 pt/종류/채움). 지우기 탭은 뺀다.
    let document = format!(
        r#"<w:document {NS}><w:body><w:p><w:pPr><w:tabs><w:tab w:val="right" w:leader="dot" w:pos="1450"/><w:tab w:val="clear" w:pos="900"/><w:tab w:val="left" w:pos="3000"/></w:tabs></w:pPr><w:r><w:t>탭</w:t></w:r></w:p>
        <w:tbl><w:tr><w:tc><w:tbl><w:tr><w:tc><w:p><w:r><w:t>안</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p><w:r><w:t>칸 끝 글</w:t></w:r></w:p></w:tc>
        <w:tc><w:tbl><w:tr><w:tc><w:p/></w:tc></w:tr></w:tbl><w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="20" w:lineRule="exact"/></w:pPr></w:p><w:p/></w:tc>
        <w:tc><w:tbl><w:tr><w:tc><w:p/></w:tc></w:tr></w:tbl><w:p/></w:tc></w:tr></w:tbl><w:p/></w:body></w:document>"#
    );
    let (html, _) = docx_to_html(&docx(&document, None)).unwrap();
    assert!(
        html.contains("tab-stops:72.50pt/1/3 150.00pt/0/0;"),
        "{html}"
    );
    // 칸 안 표 뒤 글이 든 문단은 남는다(전에는 `></p>` 로 끝나 Word 가 요구한 빈 문단으로 보고 뺐다).
    assert!(html.contains("칸 끝 글"), "{html}");
    // Papy 가 쓴 칸(표 → 1pt 문단 → 빈 문단): 빈 문단은 원본 것이라 남는다.
    assert!(html.contains("</table><p></p></td><td>"), "{html}");
    // Word 칸(표 → 빈 문단): Word 가 요구한 빈 문단이라 뺀다.
    assert!(
        html.contains("</table></td></tr></table><p></p></body>"),
        "{html}"
    );

    // 짧은 행(끝 격자 칸이 비었다): 빈 칸을 만들지 않고 w:gridAfter 로 쓴다.
    let mut core = rhwp::document_core::DocumentCore::new_empty();
    core.create_blank_document_native().unwrap();
    core.create_table_native(0, 0, 0, 2, 2).unwrap();
    let mut document = core.document().clone();
    for paragraph in &mut document.sections[0].paragraphs {
        for control in &mut paragraph.controls {
            if let rhwp::model::control::Control::Table(table) = control {
                table.cells.retain(|c| !(c.row == 1 && c.col == 1));
            }
        }
    }
    let xml = part_text(&docx_from_document(&document).unwrap(), "word/document.xml");
    assert!(xml.contains(r#"<w:gridAfter w:val="1"/>"#), "{xml}");
    assert_eq!(xml.matches("<w:tc>").count(), 3, "{xml}");
}

#[test]
fn latin_and_east_asian_fonts_are_kept_apart() {
    // 한글 문서의 흔한 꼴: 한글은 명조, 영문은 Times New Roman. 하나로 합치면 영문 폭이 바뀌어 줄이 넘친다.
    let document = format!(
        r#"<w:document {NS}><w:body>
        <w:p><w:r><w:rPr><w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="바탕"/></w:rPr><w:t>① $100</w:t><w:tab/><w:t>② $150</w:t></w:r></w:p>
        <w:p><w:r><w:rPr><w:rFonts w:ascii="바탕" w:eastAsia="바탕"/></w:rPr><w:t>같음</w:t></w:r></w:p>
        </w:body></w:document>"#
    );
    let (html, _) = docx_to_html(&docx(&document, None)).unwrap();
    assert!(
        html.contains(
            "font-family:'Times New Roman';mso-fareast-font-family:'바탕';\">① $100\t② $150</span>"
        ),
        "{html}"
    );
    assert!(html.contains("font-family:'바탕';\">같음"), "{html}");
}

/// 번호 목록 표시 글자(정의 번호, 수준)
fn list_mark(def: u32, level: u32) -> String {
    format!(
        "{LIST_MARK}{}",
        char::from_u32(crate::LIST_CODE_BASE + def * 8 + level).unwrap()
    )
}
