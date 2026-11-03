use super::*;
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
