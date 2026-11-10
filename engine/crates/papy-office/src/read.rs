//! DOCX → HTML. rhwp 의 HTML 붙여넣기(`paste_html_native`)가 받는 범위의 태그만 만든다:
//! `<p style="text-align">`, `<span style="font-*;color;text-decoration">`, `<table>`/`<td colspan rowspan>`,
//! `<img src="data:…" width height>`(그림은 본문 관계·word/media 에서 꺼낸다).

use base64::Engine as _;
use std::collections::HashMap;
use std::io::{Cursor, Read};

use crate::notes::{
    BOOKMARK_MARK, CAPTION_MARKS, CAPTION_MORE_MARK, COLUMN_BREAK_MARK, COMMENT_MARK, ENDNOTE_MARK,
    FOOTNOTE_MARK, HeaderFooterText, LINK_END_MARK, LINK_START_MARK, Numbering, PAGE_BREAK_MARK,
    SECTION_MARK, header_footer_texts, note_parts, page_field_mark, parse_endnotes,
    parse_footnotes,
};
use crate::shapes::{CELL_SHAPE_MARK, DocxShape, Drawing, Pending, SHAPE_MARK, ShapeImage};
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
    /// 본문·표 칸의 책갈피 이름, [`crate::BOOKMARK_MARK`] 와 같은 차례.
    pub bookmarks: Vec<String>,
    /// 본문·표 칸의 하이퍼링크 주소, [`crate::LINK_START_MARK`] 와 같은 차례.
    pub hyperlinks: Vec<String>,
    /// 본문·표 칸의 누름틀(글 콘텐츠 컨트롤 `w:sdt`), [`crate::FIELD_START_MARK`] 와 같은 차례.
    pub click_here: Vec<crate::ClickHere>,
    /// 본문·표 칸의 [`crate::COMMENT_MARK`] 자리마다 메모 글(문단마다), 같은 차례.
    pub comment_marks: Vec<Vec<String>>,
    /// 각주 내용 HTML(서식·표·그림 포함, `footnotes` 와 같은 차례). 비었거나 None 이면 글자만 넣는다.
    pub footnote_html: Vec<Option<String>>,
    /// 미주 내용 HTML(`endnotes` 와 같은 차례).
    pub endnote_html: Vec<Option<String>>,
    /// 옮기지 못한 각주·미주 수(표 안 각주·미주, 첫 쪽 머리말 등).
    pub dropped_notes: usize,
    /// 본문 도형, 본문의 [`SHAPE_MARK`] 와 같은 순서.
    pub shapes: Vec<DocxShape>,
    /// 표 칸 안 도형, 칸 문단의 [`crate::CELL_SHAPE_MARK`] 와 같은 순서(바깥 표부터 문서 순서).
    pub cell_shapes: Vec<DocxShape>,
    /// 본문 제목 문단(스타일·문단의 개요 수준이 있는 것), 문서 순서. 목차로 쓴다. 표 안 문단은 넣지 않는다.
    pub headings: Vec<DocxHeading>,
    /// 첫 쪽에서 감출 (머리말, 꼬리말). 첫 구역이 "첫 쪽 다르게"(`w:titlePg`)일 때 켠다.
    /// rhwp 에는 첫 쪽 전용 머리말 자리가 없어, 첫 쪽에서는 기본 머리말을 감추고 첫 쪽 것의 글자는 옮기지 않는다.
    pub hide_first_page: (bool, bool),
    /// 메모(`word/comments.xml`), 본문에서 처음 가리킨 순서.
    pub comments: Vec<DocxComment>,
    /// 변경 추적: 넣은 내용(`w:ins`)·지운 내용(`w:del`) 수. 화면은 받아들인 상태(넣은 글은 보이고 지운 글은 빠진다).
    pub insertions: u32,
    /// 지운 내용 수
    pub deletions: u32,
    /// 본문 맨 바깥 표마다 왼쪽 자리(HWPUNIT, `w:tblInd`, 문서 순서). 없으면 0.
    pub table_indents: Vec<i32>,
    /// 본문 맨 바깥 표마다 쪽·종이 기준 자리(`w:tblpPr`): rhwp 표 속성 JSON 조각(`"vertRelTo":…`). 없으면 빈 글.
    pub table_places: Vec<String>,
    /// 본문 맨 바깥 표마다 Papy 가 쓴 한글 "글자처럼 취급" 표인가(`papy:inline`, 문서 순서).
    pub table_inline: Vec<bool>,
    /// 본문·표 칸 번호 목록 문단의 정의([`crate::LIST_MARK`] 뒤 글자가 가리킨다). 머리말·주석·글상자 안 목록은 번호 글자로 읽는다.
    pub lists: Vec<crate::ListDef>,
    /// 첫 구역의 쪽 크기·여백·다단(`w:pgSz`·`w:pgMar`·`w:cols`). 없거나 이상하면 None(빈 문서 기본값을 쓴다).
    pub page: Option<PageSetup>,
}

/// Word 메모 하나.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct DocxComment {
    /// 쓴 사람
    pub author: String,
    /// 쓴 때(ISO 8601 그대로)
    pub date: String,
    /// 메모 글(문단은 줄바꿈으로)
    pub text: String,
    /// 메모를 단 문단 글(앞 60자)
    pub anchor: String,
}

/// `word/comments.xml` → id 별 (쓴 사람, 때, 글).
fn parse_comments(xml: &str) -> HashMap<String, (String, String, String)> {
    let mut reader = Reader::from_str(xml);
    let mut out = HashMap::new();
    let mut current: Option<(String, String, String, Vec<String>)> = None;
    let mut in_text = false;
    loop {
        match reader.read_event() {
            Ok(Event::Eof) | Err(_) => break,
            Ok(Event::Start(e)) => match local(e.name().as_ref()) {
                "comment" => {
                    current = attr(&e, "id").map(|id| {
                        (
                            id,
                            attr(&e, "author").unwrap_or_default(),
                            attr(&e, "date").unwrap_or_default(),
                            Vec::new(),
                        )
                    })
                }
                "p" => {
                    if let Some(c) = current.as_mut() {
                        c.3.push(String::new());
                    }
                }
                "t" => in_text = true,
                _ => {}
            },
            Ok(Event::End(e)) => match local(e.name().as_ref()) {
                "t" => in_text = false,
                "comment" => {
                    if let Some((id, author, date, paragraphs)) = current.take() {
                        let text = paragraphs.join("\n").trim().to_owned();
                        out.insert(id, (author, date, text));
                    }
                }
                _ => {}
            },
            Ok(Event::Text(text)) if in_text => {
                if let Some(paragraph) = current.as_mut().and_then(|c| c.3.last_mut()) {
                    paragraph.push_str(&text.xml10_content());
                }
            }
            _ => {}
        }
    }
    out
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
/// 바깥 대상 관계(하이퍼링크 주소): 관계 id → 주소.
fn parse_links(xml: &str) -> HashMap<String, String> {
    let mut links = HashMap::new();
    let mut reader = Reader::from_str(xml);
    while let Ok(event) = reader.read_event() {
        match event {
            Event::Eof => break,
            Event::Start(e) | Event::Empty(e) if local(e.name().as_ref()) == "Relationship" => {
                let external = attr(&e, "TargetMode").as_deref() == Some("External");
                let hyperlink = attr(&e, "Type").is_some_and(|t| t.ends_with("/hyperlink"));
                if let (Some(id), Some(target), true) =
                    (attr(&e, "Id"), attr(&e, "Target"), external && hyperlink)
                {
                    links.insert(id, target);
                }
            }
            _ => {}
        }
    }
    links
}

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
/// 그림 형식: 비트맵([`image_mime`])과 메타파일(WMF·EMF). Word 와 rhwp 는 메타파일 그림을 그대로 그린다.
pub(crate) fn picture_mime(bytes: &[u8]) -> Option<&'static str> {
    image_mime(bytes).or_else(|| {
        if bytes.starts_with(&[0xD7, 0xCD, 0xC6, 0x9A])
            || bytes.starts_with(&[0x01, 0x00, 0x09, 0x00])
        {
            Some("image/x-wmf")
        } else if bytes.len() >= 44 && bytes.starts_with(&[1, 0, 0, 0]) && &bytes[40..44] == b" EMF"
        {
            Some("image/x-emf")
        } else {
            None
        }
    })
}

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
    /// 동아시아 글꼴(`w:eastAsia`, 없으면 라틴 글꼴)
    font: Option<String>,
    /// 라틴 글꼴(`w:ascii`·`w:hAnsi`). 한글 문서는 영문을 다른 글꼴(Times New Roman 등)로 쓰는 일이 흔해
    /// 하나로 합치면 영문 폭이 달라져 줄이 넘친다.
    latin: Option<String>,
    /// 자간(twip, `w:spacing`)
    spacing: Option<i32>,
    /// 장평(%, `w:w`)
    scale: Option<u32>,
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
            latin: self.latin.clone().or_else(|| base.latin.clone()),
            spacing: self.spacing.or(base.spacing),
            scale: self.scale.or(base.scale),
        }
    }

    fn css(&self) -> String {
        let mut css = String::new();
        // rhwp 붙여넣기: `font-family` 는 라틴(영문·기타·기호), `mso-fareast-font-family` 는 한글·한자·일어
        let family = |name: &str| escape(name).replace('\'', "");
        match (&self.font, &self.latin) {
            (Some(east), Some(latin)) if east != latin => css.push_str(&format!(
                "font-family:'{}';mso-fareast-font-family:'{}';",
                family(latin),
                family(east)
            )),
            (Some(font), _) | (None, Some(font)) => {
                css.push_str(&format!("font-family:'{}';", family(font)))
            }
            (None, None) => {}
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
        if let Some(scale) = self.scale.filter(|s| *s != 100) {
            css.push_str(&format!("font-stretch:{scale}%;"));
        }
        if let Some(spacing) = self.spacing.filter(|s| *s != 0) {
            css.push_str(&format!("letter-spacing:{:.2}pt;", spacing as f32 / 20.0));
        }
        css
    }
}

/// 문단 서식(twip): 앞뒤 간격, 줄 간격, 들여쓰기.
#[derive(Debug, Clone, Default)]
struct ParaProps {
    before: Option<i64>,
    after: Option<i64>,
    /// 줄 간격 값과 규칙(auto: 240 = 한 줄, exact·atLeast: twip)
    line: Option<(i64, String)>,
    left: Option<i64>,
    right: Option<i64>,
    /// 첫 줄 들여쓰기(+) 또는 내어쓰기(-)
    first_line: Option<i64>,
    /// 한글 최소 공백(%, Papy 가 쓴 `papy:condense`)
    condense: Option<i64>,
    /// 문단 테두리(`w:pBdr`) 왼쪽·오른쪽·위·아래: (굵기 pt, CSS 선 종류, 색, 글과의 간격 pt)
    borders: [Option<(f32, &'static str, String, f32)>; 4],
    /// 문단 배경색(`w:shd w:fill`)
    shading: Option<String>,
    /// 이어진 같은 테두리 문단을 한 상자로 묶지 않는다(Papy 가 쓴 `papy:connect="0"`)
    border_apart: bool,
}

impl ParaProps {
    fn over(&self, base: &ParaProps) -> ParaProps {
        ParaProps {
            before: self.before.or(base.before),
            after: self.after.or(base.after),
            line: self.line.clone().or_else(|| base.line.clone()),
            left: self.left.or(base.left),
            right: self.right.or(base.right),
            first_line: self.first_line.or(base.first_line),
            condense: self.condense.or(base.condense),
            borders: if self.borders.iter().any(Option::is_some) {
                self.borders.clone()
            } else {
                base.borders.clone()
            },
            shading: self.shading.clone().or_else(|| base.shading.clone()),
            border_apart: self.border_apart || base.border_apart,
        }
    }

    /// `w:pBdr` 안의 선 하나(`w:top` 등)를 반영한다.
    fn border(&mut self, side: &str, element: &BytesStart) {
        let index = match side {
            "left" | "start" => 0,
            "right" | "end" => 1,
            "top" => 2,
            "bottom" => 3,
            _ => return,
        };
        let value = attr(element, "val").unwrap_or_default();
        self.borders[index] = match value.as_str() {
            "nil" | "none" | "" => None,
            kind => {
                let width = attr(element, "sz")
                    .and_then(|v| v.parse::<f32>().ok())
                    .map_or(0.5, |sz| (sz / 8.0).max(0.25));
                let line = match kind {
                    "double" => "double",
                    "dashed" | "dashSmallGap" | "dotDash" | "dotDotDash" => "dashed",
                    "dotted" => "dotted",
                    _ => "solid",
                };
                let color = attr(element, "color")
                    .filter(|c| c.len() == 6 && c.chars().all(|c| c.is_ascii_hexdigit()))
                    .unwrap_or_else(|| "000000".into());
                let space = attr(element, "space")
                    .and_then(|v| v.parse::<f32>().ok())
                    .map_or(0.0, |v| v.clamp(0.0, 31.0));
                Some((width, line, color, space))
            }
        };
    }

    /// `w:spacing`·`w:ind` 하나를 반영한다.
    fn apply(&mut self, element: &BytesStart) {
        let number = |key: &str| {
            attr(element, key)
                .and_then(|v| v.parse::<f64>().ok())
                .map(|v| v as i64)
        };
        match local(element.name().as_ref()) {
            "spacing" => {
                self.before = number("before").or(self.before);
                self.after = number("after").or(self.after);
                self.condense = number("condense").or(self.condense);
                if let Some(percent) = number("linePercent") {
                    // Papy 가 쓴 원래 한글 배수 줄 간격(%) — 240 = 100% 기준으로 둔다.
                    self.line = Some(((percent * 240 + 50) / 100, "auto".into()));
                } else if let Some(line) = number("line") {
                    let rule = attr(element, "lineRule").unwrap_or_else(|| "auto".into());
                    self.line = Some((line, rule));
                }
            }
            "ind" => {
                self.left = number("left").or_else(|| number("start")).or(self.left);
                self.right = number("right").or_else(|| number("end")).or(self.right);
                if let Some(hanging) = number("hanging") {
                    self.first_line = Some(-hanging);
                } else if let Some(first) = number("firstLine") {
                    self.first_line = Some(first);
                }
            }
            _ => {}
        }
    }

    /// rhwp HTML 붙여넣기가 읽는 문단 CSS. 줄 간격은 Papy 저장과 같은 기준(240 = 100%)으로 옮긴다.
    fn css(&self) -> String {
        let pt = |twip: i64| twip as f32 / 20.0;
        let mut css = String::new();
        if let Some(before) = self.before.filter(|v| *v > 0) {
            css.push_str(&format!("margin-top:{:.1}pt;", pt(before)));
        }
        if let Some(after) = self.after.filter(|v| *v > 0) {
            css.push_str(&format!("margin-bottom:{:.1}pt;", pt(after)));
        }
        match &self.line {
            Some((line, rule)) if (rule == "exact" || rule == "atLeast") && *line > 0 => {
                // rhwp 는 px 를 고정 줄 간격으로 읽는다(글자보다 작으면 글자 높이 — "최소" 와 같다).
                css.push_str(&format!("line-height:{:.1}px;", pt(*line) * 96.0 / 72.0));
            }
            Some((line, rule)) if rule == "auto" && *line > 0 => {
                // 반올림한다(버리면 Papy 가 쓴 143% 가 343 → 142% 로 줄어 쪽마다 줄이 밀렸다).
                let percent = ((line * 100 + 120) / 240).clamp(50, 500);
                css.push_str(&format!("line-height:{percent}%;"));
            }
            _ => {}
        }
        if let Some(left) = self.left.filter(|v| *v != 0) {
            css.push_str(&format!("margin-left:{:.1}pt;", pt(left.max(0))));
        }
        if let Some(right) = self.right.filter(|v| *v > 0) {
            css.push_str(&format!("margin-right:{:.1}pt;", pt(right)));
        }
        if let Some(first) = self.first_line.filter(|v| *v != 0) {
            css.push_str(&format!("text-indent:{:.1}pt;", pt(first)));
        }
        if let Some(condense) = self.condense.filter(|v| (1..=75).contains(v)) {
            css.push_str(&format!("papy-condense:{condense};"));
        }
        // 문단 테두리·배경: rhwp 붙여넣기가 문단 모양 테두리/배경으로 읽는다(패치 0021). 간격은 pt.
        if self.borders.iter().any(Option::is_some) {
            for (side, border) in ["left", "right", "top", "bottom"].iter().zip(&self.borders) {
                if let Some((width, line, color, _)) = border {
                    css.push_str(&format!("border-{side}:{width:.2}pt {line} #{color};"));
                }
            }
            let space = |i: usize| self.borders[i].as_ref().map_or(0.0, |b| b.3);
            css.push_str(&format!(
                "papy-border-space:{}pt {}pt {}pt {}pt;",
                space(0),
                space(1),
                space(2),
                space(3)
            ));
            if self.border_apart {
                css.push_str("papy-border-connect:0;");
            }
        }
        if let Some(fill) = &self.shading {
            css.push_str(&format!("background-color:#{fill};"));
        }
        css
    }
}

#[derive(Debug, Clone, Default)]
struct ParaStyle {
    based_on: Option<String>,
    run: RunProps,
    para: ParaProps,
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
            let latin = attr(element, "ascii").or_else(|| attr(element, "hAnsi"));
            props.font = attr(element, "eastAsia").or_else(|| latin.clone());
            props.latin = latin;
        }
        "w" => {
            props.scale = attr(element, "val")
                .and_then(|v| v.trim_end_matches('%').parse::<f64>().ok())
                .filter(|v| (1.0..=600.0).contains(v))
                .map(|v| v.round() as u32)
        }
        "spacing" => {
            props.spacing = attr(element, "val")
                .and_then(|v| v.parse::<f64>().ok())
                .map(|v| v as i32)
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

/// styles.xml 에서 읽은 것: 기본 글자·문단 서식, 문단 스타일, 기본 문단 스타일 id.
#[derive(Default)]
struct Styles {
    run: RunProps,
    para: ParaProps,
    styles: HashMap<String, ParaStyle>,
    default_style: Option<String>,
}

/// styles.xml 에서 기본 글자·문단 서식과 문단 스타일을 읽는다.
fn parse_styles(xml: &str) -> Styles {
    let mut reader = Reader::from_str(xml);
    let mut defaults = RunProps::default();
    let mut para_defaults = ParaProps::default();
    let mut default_style = None;
    let mut in_ppr_defaults = false;
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
                "pPrDefault" => in_ppr_defaults = true,
                "style" => {
                    current = (attr(&e, "type").as_deref() == Some("paragraph"))
                        .then(|| attr(&e, "styleId"))
                        .flatten()
                        .map(|id| (id, ParaStyle::default()));
                    if let (Some((id, _)), true) = (&current, toggle_attr(&e, "default")) {
                        default_style = Some(id.clone());
                    }
                }
                "spacing" | "ind" if !in_rpr => {
                    if in_ppr_defaults {
                        para_defaults.apply(&e);
                    } else if let Some((_, style)) = current.as_mut() {
                        style.para.apply(&e);
                    }
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
                "pPrDefault" => in_ppr_defaults = false,
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
    Styles {
        run: defaults,
        para: para_defaults,
        styles,
        default_style,
    }
}

/// `w:tblpPr` → rhwp 표 속성 JSON 조각. 쪽(`margin`)·종이(`page`) 기준만 옮긴다(글 기준은 본문 흐름).
fn float_place(element: &BytesStart) -> String {
    // Papy 가 쓴 글 앞·뒤 표(`papy:wrap`)는 글 기준이어도 떠 있는 채로 되돌린다.
    let wrap = match attr(element, "wrap").as_deref() {
        Some("front") => Some("InFrontOfText"),
        Some("behind") => Some("BehindText"),
        _ => None,
    };
    let vert = match attr(element, "vertAnchor").as_deref() {
        Some("page") => "Paper",
        Some("margin") => "Page",
        _ if wrap.is_some() => "Para",
        _ => return String::new(),
    };
    let number = |key: &str| {
        attr(element, key)
            .and_then(|v| v.parse::<f64>().ok())
            .map(|v| (v * 5.0) as i64)
    };
    let mut json = format!(r#","vertRelTo":"{vert}""#);
    if let Some(wrap) = wrap {
        json.push_str(&format!(r#","textWrap":"{wrap}""#));
    }
    match attr(element, "tblpYSpec").as_deref() {
        Some("center") => json.push_str(r#","vertAlign":"Center""#),
        Some("bottom") => json.push_str(r#","vertAlign":"Bottom""#),
        _ => {
            json.push_str(&format!(
                r#","vertAlign":"Top","vertOffset":{}"#,
                number("tblpY").unwrap_or(0)
            ));
        }
    }
    let horz = match attr(element, "horzAnchor").as_deref() {
        Some("page") => "Paper",
        Some("margin") => "Page",
        _ if wrap.is_some() => "Para",
        _ => "Column",
    };
    json.push_str(&format!(r#","horzRelTo":"{horz}""#));
    match attr(element, "tblpXSpec").as_deref() {
        Some("center") => json.push_str(r#","horzAlign":"Center""#),
        Some("right") => json.push_str(r#","horzAlign":"Right""#),
        _ => json.push_str(&format!(
            r#","horzAlign":"Left","horzOffset":{}"#,
            number("tblpX").unwrap_or(0)
        )),
    }
    json
}

/// `w:default="1"` 같은 켜짐 속성.
fn toggle_attr(element: &BytesStart, key: &str) -> bool {
    attr(element, key).is_some_and(|v| matches!(v.as_str(), "1" | "true" | "on"))
}

/// 스타일 상속을 풀어 (글자 서식, 정렬, 개요 수준) 을 돌려준다.
fn resolve_style(
    styles: &HashMap<String, ParaStyle>,
    id: &str,
) -> (RunProps, Option<String>, Option<u8>, ParaProps) {
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
    let mut para = ParaProps::default();
    for style in chain.iter().rev() {
        run = style.run.over(&run);
        align = style.align.clone().or(align);
        outline = style.outline.or(outline);
        para = style.para.over(&para);
    }
    (run, align, outline, para)
}

/// 문단 안 콘텐츠 컨트롤(`w:sdt`) 읽는 중 상태
#[derive(Default)]
struct Sdt {
    alias: Option<String>,
    tag: Option<String>,
    in_pr: bool,
    placeholder: bool,
    /// 내용 잠금(`w:lock` contentLocked·sdtContentLocked)
    locked: bool,
    /// 누름틀 시작 표시를 넣었는가
    marked: bool,
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
    /// 칸 안쪽 여백(pt, 왼쪽·오른쪽·위·아래, `w:tblCellMar`). 없으면 Word 기본(좌우 5.4pt, 위아래 0).
    padding: [f32; 4],
    /// 왼쪽 들여쓰기(twip, `w:tblInd`)
    indent: i32,
    /// 쪽·종이 기준 자리(rhwp 표 속성 JSON 조각)
    place: String,
    /// 한글에서 글자처럼 취급한 표(`papy:inline`)
    inline: bool,
}

struct CellBuild {
    span: usize,
    /// 칸 너비(pt, `w:tcW` 의 dxa)
    width: Option<f32>,
    /// None: 병합 없음, Some(true): 병합 시작, Some(false): 위 칸에 이어짐
    vmerge: Option<bool>,
    /// 칸 테두리·배경·세로 정렬·행 높이 CSS(rhwp 표 붙여넣기가 읽는 것)
    style: String,
    html: String,
    /// 이 칸 마지막 표 뒤의 1pt 문단(Papy 가 Word 때문에 둔 것)을 뺐다: 그 뒤 빈 문단은 원본 문단이다.
    tiny_after_table: bool,
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
        // rhwp 표 붙여넣기는 표 CSS padding 을 칸 안쪽 여백으로 쓴다(0 은 기본값으로 읽으니 아주 작은 값으로).
        let [left, right, top, bottom] = self.padding.map(|v| v.max(0.02));
        let mut html = format!(
            "<table border=\"1\" style=\"padding:{top:.2}pt {right:.2}pt {bottom:.2}pt {left:.2}pt\">"
        );
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
                if let Some(width) = cell.width {
                    // rhwp 는 병합하지 않은 칸의 너비로 열 너비를 정하고, 병합 칸만 있는 열은 병합 칸 너비로 채운다(패치 0010).
                    style.push_str(&format!("width:{width:.1}pt;"));
                }
                style.push_str(&cell.style);
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

/// `papy:caption` 값(`bottom`, 이어지는 문단은 `bottom+`) → 캡션 문단 표시 글자.
fn caption_mark(value: &str) -> char {
    if value.ends_with('+') {
        return CAPTION_MORE_MARK;
    }
    let index = match value {
        "left" => 0,
        "right" => 1,
        "top" => 2,
        _ => 3,
    };
    CAPTION_MARKS[index]
}

/// DOCX 바이트를 rhwp 가 붙여넣을 수 있는 HTML 로 바꾼다.
pub fn docx_to_html(bytes: &[u8]) -> Result<(String, DocxReadReport)> {
    part_to_html(bytes, "word/document.xml", false)
}

/// 머리말·꼬리말 부분이 글자만으로 옮길 수 없는 것(표·글 속 그림)을 가졌는가.
fn rich_header_footer(xml: &str) -> bool {
    xml.contains("<w:tbl>") || xml.contains("<w:tbl ") || xml.contains("<wp:inline")
}

/// 문서 부분(`part`, 본문이나 머리말·꼬리말) 하나를 HTML 로 바꾼다. `header` 이면 머리말·꼬리말 부분이다:
/// 쪽 번호·전체 쪽 수 필드(표 밖)를 [`PAGE_NUMBER_MARK`]·[`PAGE_COUNT_MARK`] 로 두고, 본문 것(머리말 참조·쪽 설정)은 읽지 않는다.
fn part_to_html(bytes: &[u8], part: &str, header: bool) -> Result<(String, DocxReadReport)> {
    let mut archive =
        zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| invalid("not a ZIP container"))?;
    let document = read_part(&mut archive, part)?
        .ok_or_else(|| Error::new(ErrorCode::UnsupportedFormat, "not a DOCX document"))?;
    xml_to_html(bytes, &document, &rels_path_of(part), header, 0)
}

/// 부분의 관계: word/header1.xml → word/_rels/header1.xml.rels
fn rels_path_of(part: &str) -> String {
    match part.rsplit_once('/') {
        Some((dir, name)) => format!("{dir}/_rels/{name}.rels"),
        None => format!("_rels/{part}.rels"),
    }
}

/// 글상자·각주처럼 부분 안 조각(`w:p`·`w:tbl` 들)을 HTML 로 바꾼다. 관계는 그 조각이 든 부분의 것(`rels_path`).
/// 머리말처럼 읽는다(쪽 번호 필드는 표시 글자, 구역·쪽 설정은 읽지 않는다). 조각 안에 둘 수 없는 것
/// (떠 있는 도형·각주·나누기)은 표시 글자를 빼고 빠진 것으로 센다.
pub(crate) fn fragment_to_html(
    bytes: &[u8],
    fragment: &str,
    rels_path: &str,
    depth: usize,
) -> Result<(String, DocxReadReport)> {
    if depth >= MAX_FRAGMENT_DEPTH {
        return Err(Error::new(
            ErrorCode::LimitExceeded,
            "text boxes are nested too deeply",
        ));
    }
    let wrapped = format!("<w:body>{fragment}</w:body>");
    let (html, mut report) = xml_to_html(bytes, &wrapped, rels_path, true, depth + 1)?;
    report.dropped_drawings += report.shapes.len() + report.cell_shapes.len();
    report.cell_shapes.clear();
    report.dropped_notes += report.footnotes.len() + report.endnotes.len();
    report.shapes.clear();
    report.footnotes.clear();
    report.endnotes.clear();
    Ok((strip_marks(&html), report))
}

/// 본문 밖(머리말·글상자 등)에 둘 수 없는 자리 표시 글자(도형·각주·나누기·캡션)를 뺀다.
fn strip_marks(html: &str) -> String {
    html.chars()
        .filter(|c| !matches!(*c, SHAPE_MARK | CELL_SHAPE_MARK) && !outside_body_mark(*c))
        .collect()
}

/// [`strip_marks`] 처럼 빼되 도형 자리 표시는 남긴다(머리말 도형).
fn strip_marks_keeping_shapes(html: &str) -> String {
    html.chars().filter(|c| !outside_body_mark(*c)).collect()
}

/// 본문에서만 쓰는 자리 표시 글자(도형 것은 빼고)
fn outside_body_mark(c: char) -> bool {
    matches!(
        c,
        BOOKMARK_MARK
            | COMMENT_MARK
            | LINK_START_MARK
            | LINK_END_MARK
            | CAPTION_MORE_MARK
            | '\u{E0F9}'
            ..='\u{E0FC}' | FOOTNOTE_MARK | ENDNOTE_MARK | PAGE_BREAK_MARK | COLUMN_BREAK_MARK
    )
}

/// 글상자 안 글상자 조각 깊이 상한.
const MAX_FRAGMENT_DEPTH: usize = 4;

/// 부분 XML(`document`) 하나를 HTML 로 바꾼다. `rels_path` 는 그 부분의 관계, `depth` 는 조각 깊이(본문·머리말은 0).
fn xml_to_html(
    bytes: &[u8],
    document: &str,
    rels_path: &str,
    header: bool,
    depth: usize,
) -> Result<(String, DocxReadReport)> {
    let mut archive =
        zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| invalid("not a ZIP container"))?;
    let Styles {
        run: defaults,
        para: para_defaults,
        styles,
        default_style,
    } = read_part(&mut archive, "word/styles.xml")?
        .map(|xml| parse_styles(&xml))
        .unwrap_or_default();
    // pStyle 이 없는 문단은 기본 문단 스타일(보통 "Normal")을 따른다.
    let base_style = default_style
        .as_deref()
        .map(|id| resolve_style(&styles, id))
        .unwrap_or_default();

    let rels_xml = read_part(&mut archive, rels_path)?;
    let rels = rels_xml.as_deref().map(parse_rels).unwrap_or_default();
    let links = rels_xml.as_deref().map(parse_links).unwrap_or_default();
    // 열린 하이퍼링크(`w:hyperlink`) 깊이마다 표시를 넣었는가
    let mut link_stack: Vec<bool> = Vec::new();
    // 열린 콘텐츠 컨트롤(`w:sdt`)마다: 문단 안 것만 Some. 자리 표시 글(`w:showingPlcHdr`)은 읽지 않는다.
    let mut sdt_stack: Vec<Option<Sdt>> = Vec::new();
    let comments = read_part(&mut archive, "word/comments.xml")?
        .map(|xml| parse_comments(&xml))
        .unwrap_or_default();
    // 이 문단에서 가리킨 메모 id(문단이 끝나면 문단 글을 자리 글로 붙인다)
    let mut pending_comments: Vec<String> = Vec::new();
    let footnotes = read_part(&mut archive, "word/footnotes.xml")?
        .map(|xml| parse_footnotes(&xml))
        .unwrap_or_default();
    let endnotes = read_part(&mut archive, "word/endnotes.xml")?
        .map(|xml| parse_endnotes(&xml))
        .unwrap_or_default();
    // 주 내용 XML 조각(본문에서만, 다 읽은 뒤 서식째 HTML 로 바꾼다).
    let note_xml = |archive: &mut zip::ZipArchive<Cursor<&[u8]>>, part: &str, element: &str| {
        Ok::<_, Error>(if depth == 0 && !header {
            read_part(archive, part)?
                .map(|xml| note_parts(&xml, element))
                .unwrap_or_default()
        } else {
            HashMap::new()
        })
    };
    let footnote_parts = note_xml(&mut archive, "word/footnotes.xml", "footnote")?;
    let endnote_parts = note_xml(&mut archive, "word/endnotes.xml", "endnote")?;
    // 가리킨 차례대로 주 id(각주, 미주)
    let mut note_ids: [Vec<String>; 2] = [Vec::new(), Vec::new()];
    let mut numbering = read_part(&mut archive, "word/numbering.xml")?
        .map(|xml| Numbering::parse(&xml))
        .unwrap_or_default();
    // 머리말·꼬리말 참조: (구역, 머리말인가, 종류, 관계 id).
    let mut header_refs: Vec<(usize, bool, String, String)> = Vec::new();
    let mut title_page = false;
    // 구역마다 시작 방식(`w:type`, 없으면 새 쪽). 구역 속성은 구역 끝에 오므로 다음 구역 첫 자리에 자리 표시를 두고
    // 다 읽은 뒤 나누기 표시로 바꾼다.
    let mut section_types: Vec<String> = Vec::new();
    let mut in_sect = false;
    let mut section_ends_here = false;
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
    // 그리기 안 글상자 내용(`w:txbxContent`) 깊이와 맨 바깥 것이 시작한 자리(XML 바이트)
    let mut textbox_depth = 0usize;
    let mut textbox_start = 0usize;
    // 그리기 안 그리기(글상자 안 그림 등) 깊이: 안쪽 `</w:drawing>` 에서 바깥 그리기를 끝내지 않는다.
    let mut inner_drawings = 0usize;

    let mut report = DocxReadReport::default();
    let mut reader = Reader::from_str(document);
    // 출력 대상: 본문 또는 열린 표 칸들(중첩 표 지원).
    let mut body = String::new();
    let mut tables: Vec<TableBuild> = Vec::new();
    let mut cell: Vec<Option<CellBuild>> = Vec::new();

    let mut para_html = String::new();
    let mut in_para = false;
    let mut para_style: (RunProps, Option<String>, Option<u8>, ParaProps) = base_style.clone();
    let mut para_props = ParaProps::default();
    let mut para_align: Option<String> = None;
    // 문단에 직접 준 개요 수준(스타일 것보다 앞선다)과 문단 글자(목차용, 이스케이프 전).
    let mut para_outline: Option<u8> = None;
    let mut para_plain = String::new();
    let mut page_break_before = false;
    // Papy 가 쓴 캡션 문단(`papy:caption`): 문단 맨 앞 표시 글자. 표 뒤 캡션 다음의 1pt 문단도 표 뒤처럼 뺀다.
    let mut para_caption: Option<char> = None;
    // 문단 탭(`w:tabs`): rhwp 붙여넣기 CSS `tab-stops` 로 옮긴다(패치 0017).
    let mut in_tabs = false;
    let mut para_tabs: Vec<String> = Vec::new();
    let mut caption_after_table = false;
    let mut in_ppr = false;
    // 문단 테두리(`w:pBdr`) 안
    let mut in_pbdr = false;
    let mut in_tbl_borders = false;
    let mut in_tc_borders = false;
    let mut in_tbl_cell_mar = false;
    let mut row_height: Option<f32> = None;
    // 이 칸 위·아래 테두리 가운데 굵은 것(pt). Word 의 행 높이는 테두리를 빼고 잰다.
    let mut cell_border: f32 = 0.0;
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
    // 머리말·꼬리말 필드: 복합 필드 명령 글자, 결과(캐시된 쪽 번호) 건너뛰기, 단순 필드 결과 건너뛰기
    let mut field_instr = String::new();
    let mut in_instr = false;
    let mut field_marker: Option<char> = None;
    let mut skip_field_result = false;
    let mut skip_simple = false;
    // Papy 가 쓴 주 번호 꾸밈 런(`papy:noteMark`): 글로 읽지 않는다.
    let mut note_mark_run = false;
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
                    if name == "drawing" && !empty {
                        inner_drawings += 1;
                    }
                    if name == "txbxContent" && !empty {
                        if textbox_depth == 0 {
                            textbox_start = reader.buffer_position() as usize;
                        }
                        textbox_depth += 1;
                    }
                    current.start(name, &e, empty);
                    continue;
                }
                match name {
                    "ins" if !empty => report.insertions += 1,
                    "del" if !empty => report.deletions += 1,
                    // 책갈피(Word 가 스스로 두는 `_GoBack`·`_Toc…` 같은 숨은 책갈피는 뺀다)
                    "bookmarkStart" if in_para && !header && depth == 0 => {
                        if let Some(name) = attr(&e, "name").filter(|n| !n.starts_with('_')) {
                            flush(
                                &mut para_html,
                                &mut run_text,
                                &run,
                                &para_style.0,
                                &defaults,
                            );
                            para_html.push(BOOKMARK_MARK);
                            report.bookmarks.push(name);
                        }
                    }
                    "sdt" if !empty => sdt_stack.push(in_para.then(Sdt::default)),
                    "sdtPr" | "alias" | "tag" | "showingPlcHdr" | "lock" => {
                        if let Some(Some(sdt)) = sdt_stack.last_mut() {
                            match name {
                                "sdtPr" => sdt.in_pr = !empty,
                                "alias" if sdt.in_pr => sdt.alias = attr(&e, "val"),
                                "tag" if sdt.in_pr => sdt.tag = attr(&e, "val"),
                                "showingPlcHdr" if sdt.in_pr => sdt.placeholder = true,
                                "lock" if sdt.in_pr => {
                                    sdt.locked = attr(&e, "val").is_some_and(|v| {
                                        v == "contentLocked" || v == "sdtContentLocked"
                                    })
                                }
                                _ => {}
                            }
                        }
                    }
                    // 본문·표 칸·글상자 안(조각은 depth > 0) 것만. 머리말 것은 글만 남긴다.
                    "sdtContent"
                        if !empty
                            && (!header || depth > 0)
                            && report.click_here.len() < crate::MAX_FIELDS =>
                    {
                        if let Some(Some(sdt)) = sdt_stack.last_mut() {
                            flush(
                                &mut para_html,
                                &mut run_text,
                                &run,
                                &para_style.0,
                                &defaults,
                            );
                            // 자리 표시 글은 건너뛴다. 단 내용을 고칠 수 없게 잠근 것은 채울 자리가 아니라
                            // 보이는 글이다(showingPlcHdr 이어도 글을 넣어 둔다, POI 55966).
                            sdt.placeholder &= !sdt.locked;
                            para_html.push_str(&crate::field_start(report.click_here.len()));
                            report.click_here.push(crate::ClickHere::from_word(
                                sdt.alias.take(),
                                sdt.tag.take(),
                            ));
                            sdt.marked = true;
                        }
                    }
                    "hyperlink" if !empty && in_para => {
                        let uri = attr(&e, "id")
                            .and_then(|id| links.get(&id))
                            .filter(|_| !header && depth == 0);
                        if let Some(uri) = uri {
                            flush(
                                &mut para_html,
                                &mut run_text,
                                &run,
                                &para_style.0,
                                &defaults,
                            );
                            para_html.push(LINK_START_MARK);
                            report.hyperlinks.push(uri.clone());
                        }
                        link_stack.push(uri.is_some());
                    }
                    "commentReference" => {
                        if let Some(id) = attr(&e, "id") {
                            // 본문·칸 메모는 그 자리에 숨은 설명으로 넣는다(머리말·글상자 안 것은 빼고).
                            if let (Some((_, _, text)), true, true) =
                                (comments.get(&id), in_para, !header && depth == 0)
                            {
                                flush(
                                    &mut para_html,
                                    &mut run_text,
                                    &run,
                                    &para_style.0,
                                    &defaults,
                                );
                                para_html.push(COMMENT_MARK);
                                report
                                    .comment_marks
                                    .push(text.split('\n').map(str::to_owned).collect());
                            }
                            if comments.contains_key(&id) && !pending_comments.contains(&id) {
                                pending_comments.push(id);
                            }
                        }
                    }
                    "p" if empty => {
                        paragraphs += 1;
                        out(&mut body, &mut cell, "<p></p>");
                    }
                    "p" => {
                        paragraphs += 1;
                        in_para = true;
                        para_html.clear();
                        para_style = base_style.clone();
                        para_props = ParaProps::default();
                        para_align = None;
                        para_outline = None;
                        page_break_before = false;
                        para_caption = attr(&e, "caption").map(|value| caption_mark(&value));
                        para_tabs.clear();
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
                    "tabs" if in_ppr && !empty => in_tabs = true,
                    "tab" if in_tabs => {
                        let kind = match attr(&e, "val").as_deref() {
                            Some("right" | "end") => Some(1),
                            Some("center") => Some(2),
                            Some("decimal") => Some(3),
                            Some("clear" | "bar") => None,
                            _ => Some(0),
                        };
                        // 채움: Papy 쓰기의 반대(점선 3, 실선 1, 파선 2, 가운뎃점 7)
                        let fill = match attr(&e, "leader").as_deref() {
                            Some("dot") => 3,
                            Some("underscore" | "heavy") => 1,
                            Some("hyphen") => 2,
                            Some("middleDot") => 7,
                            _ => 0,
                        };
                        let pos = attr(&e, "pos").and_then(|v| v.parse::<f32>().ok());
                        if let (Some(kind), Some(pos)) = (kind, pos.filter(|p| *p > 0.0)) {
                            para_tabs.push(format!("{:.2}pt/{kind}/{fill}", pos / 20.0));
                        }
                    }
                    "pStyle" if in_ppr => {
                        if let Some(id) = attr(&e, "val") {
                            para_style = resolve_style(&styles, &id);
                        }
                    }
                    "jc" if in_ppr && !in_rpr => para_align = attr(&e, "val"),
                    "spacing" | "ind" if in_ppr && !in_rpr => para_props.apply(&e),
                    "pBdr" if in_ppr && !in_rpr => {
                        in_pbdr = !empty;
                        para_props.border_apart = attr(&e, "connect").as_deref() == Some("0");
                    }
                    side @ ("top" | "left" | "bottom" | "right" | "start" | "end") if in_pbdr => {
                        para_props.border(side, &e)
                    }
                    "shd" if in_ppr && !in_rpr && !in_pbdr => {
                        para_props.shading = attr(&e, "fill").filter(|c| {
                            c.len() == 6
                                && c.chars().all(|c| c.is_ascii_hexdigit())
                                && !c.eq_ignore_ascii_case("FFFFFF")
                        })
                    }
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
                        section_types.push("nextPage".into());
                        if in_ppr {
                            section_ends_here = true;
                        }
                        if empty {
                            section_pages.push((page, paragraphs - section_start));
                            section_start = paragraphs;
                        } else {
                            in_sect = true;
                        }
                    }
                    "titlePg" if in_sect && section_pages.is_empty() => title_page = toggle(&e),
                    "type" if in_sect => {
                        if let (Some(kind), Some(last)) =
                            (attr(&e, "val"), section_types.last_mut())
                        {
                            *last = kind;
                        }
                    }
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
                        // Papy 가 Word 다단 때문에 줄인 아래 여백의 원래 값
                        if let Some(value) = v("hwpBottom") {
                            page.bottom = value as i64;
                        }
                    }
                    "cols" => {
                        let v = |k| attr(&e, k).and_then(|v| v.parse::<f64>().ok());
                        page.columns = v("num").map_or(1, |n| n as i64);
                        if let Some(gap) = v("space") {
                            page.column_gap = gap as i64;
                        }
                    }
                    kind @ ("headerReference" | "footerReference") if in_sect => {
                        if let (Some(kind_attr), Some(id)) = (attr(&e, "type"), attr(&e, "id")) {
                            header_refs.push((
                                section_pages.len(),
                                kind == "headerReference",
                                kind_attr,
                                id,
                            ));
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
                        let (notes, mark, out, ids) = if kind == "footnoteReference" {
                            (
                                &footnotes,
                                FOOTNOTE_MARK,
                                &mut report.footnotes,
                                &mut note_ids[0],
                            )
                        } else {
                            (
                                &endnotes,
                                ENDNOTE_MARK,
                                &mut report.endnotes,
                                &mut note_ids[1],
                            )
                        };
                        let id = attr(&e, "id");
                        match id.as_ref().and_then(|id| notes.get(id)) {
                            // 표 안 각주·미주는 papy-hwp 가 본문에서 만들어 칸으로 옮긴다(겹친 표 안은 빼고).
                            Some(note) if tables.len() <= 1 => {
                                para_html.push(mark);
                                out.push(note.clone());
                                ids.push(id.unwrap_or_default());
                            }
                            _ => report.dropped_notes += 1,
                        }
                    }
                    "r" if in_para => {
                        in_run = true;
                        run = RunProps::default();
                        note_mark_run = attr(&e, "noteMark").is_some();
                    }
                    "rPr" => in_rpr = true,
                    _ if in_rpr && in_run => apply_run_prop(&mut run, &e),
                    "t" => in_text = true,
                    "instrText" if header && !empty => in_instr = true,
                    "fldChar" if header => match attr(&e, "fldCharType").as_deref() {
                        Some("begin") => {
                            field_instr.clear();
                            field_marker = None;
                        }
                        Some("separate") | Some("end") => {
                            if field_marker.is_none() && cell.is_empty() {
                                field_marker = page_field_mark(&field_instr);
                                if let Some(mark) = field_marker {
                                    run_text.push(mark);
                                }
                            }
                            field_instr.clear();
                            skip_field_result = attr(&e, "fldCharType").as_deref()
                                == Some("separate")
                                && field_marker.is_some();
                            if !skip_field_result {
                                field_marker = None;
                            }
                        }
                        _ => {}
                    },
                    "fldSimple" if header && cell.is_empty() => {
                        if let Some(mark) = attr(&e, "instr").as_deref().and_then(page_field_mark) {
                            run_text.push(mark);
                            skip_simple = !empty;
                        }
                    }
                    // 위치 탭(ptab, 머리말의 왼쪽·가운데·오른쪽 칸 나누기)도 탭으로
                    // 탭은 런 글자에 넣어 런 서식을 따르게 한다(밖에 두면 기본 글꼴 10pt 로 빠진다).
                    "tab" | "ptab" if in_run => run_text.push('\t'),
                    "br" if in_run => {
                        flush(
                            &mut para_html,
                            &mut run_text,
                            &run,
                            &para_style.0,
                            &defaults,
                        );
                        let kind = attr(&e, "type");
                        if kind.as_deref() == Some("page") && cell.is_empty() {
                            // 쪽 나누기: 문단을 끊고 뒤 문단 앞에서 쪽을 나눈다.
                            para_html.push_str("</p><p>");
                            para_html.push(PAGE_BREAK_MARK);
                        } else if kind.as_deref() == Some("column") && cell.is_empty() {
                            // 단 나누기: 런이 문단 맨 앞이면 이 문단 앞에서, 아니면 끊고 뒤 문단 앞에서.
                            if !para_html.is_empty() {
                                para_html.push_str("</p><p>");
                            }
                            para_html.push(COLUMN_BREAK_MARK);
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
                            padding: [5.4, 5.4, 0.0, 0.0],
                            indent: 0,
                            place: String::new(),
                            inline: false,
                        });
                    }
                    "tr" => {
                        row_height = None;
                        if let Some(table) = tables.last_mut() {
                            table.rows.push(Vec::new());
                        }
                    }
                    "trHeight" => {
                        row_height = attr(&e, "val")
                            .and_then(|v| v.parse::<f32>().ok())
                            .filter(|h| *h > 0.0)
                            .map(|h| h / 20.0);
                    }
                    "tc" => {
                        // 테두리 굵기는 아래 tcBorders 에서 높이에 더한다.
                        cell_border = 0.0;
                        cell.push(Some(CellBuild {
                            span: 1,
                            width: None,
                            vmerge: None,
                            style: row_height
                                .map(|h| format!("height:{h:.1}pt;"))
                                .unwrap_or_default(),
                            html: String::new(),
                            tiny_after_table: false,
                        }));
                    }
                    "tblPr" => {
                        if let (Some(table), true) = (
                            tables.last_mut(),
                            attr(&e, "inline").as_deref() == Some("1"),
                        ) {
                            table.inline = true;
                        }
                    }
                    "tblpPr" => {
                        if let Some(table) = tables.last_mut() {
                            table.place = float_place(&e);
                        }
                    }
                    "tblInd" => {
                        if let (Some(table), Some(w)) = (
                            tables.last_mut(),
                            attr(&e, "w").and_then(|v| v.parse::<f64>().ok()),
                        ) {
                            if attr(&e, "type").as_deref() != Some("pct") {
                                table.indent = w as i32;
                            }
                        }
                    }
                    "tcBorders" if !empty => in_tc_borders = true,
                    "tblCellMar" if !empty => in_tbl_cell_mar = true,
                    side @ ("top" | "left" | "bottom" | "right" | "start" | "end")
                        if in_tbl_cell_mar =>
                    {
                        let value = (attr(&e, "type").as_deref() != Some("pct"))
                            .then(|| attr(&e, "w").and_then(|v| v.parse::<f32>().ok()))
                            .flatten();
                        if let (Some(value), Some(table)) = (value, tables.last_mut()) {
                            let index = match side {
                                "left" | "start" => 0,
                                "right" | "end" => 1,
                                "top" => 2,
                                _ => 3,
                            };
                            table.padding[index] = value.max(0.0) / 20.0;
                        }
                    }
                    side @ ("top" | "left" | "bottom" | "right" | "start" | "end")
                        if in_tc_borders =>
                    {
                        if let Some(Some(current)) = cell.last_mut() {
                            let side = match side {
                                "start" => "left",
                                "end" => "right",
                                other => other,
                            };
                            let value = attr(&e, "val").unwrap_or_default();
                            let css = match value.as_str() {
                                "nil" | "none" | "" => "none".to_owned(),
                                kind => {
                                    let width = attr(&e, "sz")
                                        .and_then(|v| v.parse::<f32>().ok())
                                        .map_or(0.5, |sz| (sz / 8.0).max(0.25));
                                    let line = match kind {
                                        "double" => "double",
                                        "dashed" | "dashSmallGap" => "dashed",
                                        "dotted" => "dotted",
                                        _ => "solid",
                                    };
                                    let color = attr(&e, "color")
                                        .filter(|c| c.len() == 6 && c != "auto")
                                        .unwrap_or_else(|| "000000".into());
                                    format!("{width:.2}pt {line} #{color}")
                                }
                            };
                            current.style.push_str(&format!("border-{side}:{css};"));
                            // 한글 칸 높이는 테두리를 품는다: 위·아래 테두리 가운데 굵은 것을 행 높이에 더한다.
                            let width = attr(&e, "sz")
                                .and_then(|v| v.parse::<f32>().ok())
                                .filter(|_| css != "none")
                                .map_or(0.0, |sz| sz / 8.0);
                            if let (Some(h), true) = (row_height, matches!(side, "top" | "bottom"))
                            {
                                if width > cell_border {
                                    let old = format!("height:{:.1}pt;", h + cell_border);
                                    let new = format!("height:{:.1}pt;", h + width);
                                    current.style = current.style.replacen(&old, &new, 1);
                                    cell_border = width;
                                }
                            }
                        }
                    }
                    "shd" if !cell.is_empty() && !in_para => {
                        if let (Some(Some(current)), Some(fill)) = (
                            cell.last_mut(),
                            attr(&e, "fill").filter(|f| f.len() == 6 && f != "auto"),
                        ) {
                            current
                                .style
                                .push_str(&format!("background-color:#{fill};"));
                        }
                    }
                    "vAlign" => {
                        if let Some(Some(current)) = cell.last_mut() {
                            let value = match attr(&e, "val").as_deref() {
                                Some("center") => "middle",
                                Some("bottom") => "bottom",
                                _ => "top",
                            };
                            current.style.push_str(&format!("vertical-align:{value};"));
                        }
                    }
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
            Event::End(e)
                if drawing.is_some()
                    && (local(e.name().as_ref()) != "drawing" || inner_drawings > 0) =>
            {
                let qname = e.name();
                let name = local(qname.as_ref());
                if name == "txbxContent" && textbox_depth > 0 {
                    textbox_depth -= 1;
                    let end = document[..(reader.buffer_position() as usize).min(document.len())]
                        .rfind("</")
                        .unwrap_or(textbox_start);
                    // 글상자 내용을 본문처럼 HTML 로(서식·그림·수식 그림·표). 못 하면 글자만 쓴다.
                    if textbox_depth == 0 && end > textbox_start {
                        if let Ok((html, part)) =
                            fragment_to_html(bytes, &document[textbox_start..end], rels_path, depth)
                        {
                            report.images += part.images;
                            report.dropped_drawings += part.dropped_drawings;
                            report.dropped_notes += part.dropped_notes;
                            // 글상자 안 누름틀: 차례를 바깥 것 뒤로 옮겨 합친다.
                            let html =
                                crate::notes::shift_field_marks(&html, report.click_here.len());
                            report.click_here.extend(part.click_here);
                            if let Some(current) = drawing.as_mut() {
                                current.set_textbox_html(html);
                            }
                        }
                    }
                }
                if name == "drawing" {
                    inner_drawings -= 1;
                }
                if let Some(current) = drawing.as_mut() {
                    current.end(name);
                }
            }
            Event::Text(text) if in_instr => field_instr.push_str(&text.xml10_content()),
            Event::Text(_) | Event::GeneralRef(_)
                if skip_field_result
                    || skip_simple
                    || (note_mark_run && in_run)
                    || sdt_stack
                        .iter()
                        .flatten()
                        .any(|s| s.marked && s.placeholder) => {}
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
                    in_sect = false;
                    section_pages.push((page, paragraphs - section_start));
                    section_start = paragraphs;
                }
                "drawing" => {
                    let Some(done) = drawing.take() else { continue };
                    // 본문·표 칸의 떠 있는 그림(글 뒤 배경 그림 등)은 도형 자리로 넣어 위치·배치를 지킨다.
                    // 전에는 글자처럼 놓인 그림으로 읽어 자리를 차지했다(시험지 편지 배경 그림이 단을 넘침).
                    let floating = done.is_floating_picture() && !header && depth == 0;
                    if !done.is_picture() || floating {
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
                            if depth > 0 && in_para {
                                // 글상자 안 도형(글상자 안 글상자 등): 넣을 자리가 없어 그 글자를 이 자리에 둔다.
                                for text in shape.text.iter().filter(|t| !t.trim().is_empty()) {
                                    para_html.push_str(&escape(text));
                                    para_html.push(' ');
                                }
                                report.dropped_drawings += 1;
                            } else if cell.is_empty() && in_para {
                                para_html.push(SHAPE_MARK);
                                report.shapes.push(shape);
                            } else if in_para {
                                // 표 칸 안 도형: papy-hwp 가 본문에서 만들어 칸 문단으로 옮긴다.
                                para_html.push(CELL_SHAPE_MARK);
                                report.cell_shapes.push(shape);
                            } else {
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
                        .and_then(|bytes| picture_mime(&bytes).map(|mime| (mime, bytes)));
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
                "sdtPr" => {
                    if let Some(Some(sdt)) = sdt_stack.last_mut() {
                        sdt.in_pr = false;
                    }
                }
                "sdt" => {
                    if let Some(Some(sdt)) = sdt_stack.pop() {
                        if sdt.marked {
                            flush(
                                &mut para_html,
                                &mut run_text,
                                &run,
                                &para_style.0,
                                &defaults,
                            );
                            para_html.push(crate::FIELD_END_MARK);
                        }
                    }
                }
                "hyperlink" => {
                    if link_stack.pop() == Some(true) {
                        flush(
                            &mut para_html,
                            &mut run_text,
                            &run,
                            &para_style.0,
                            &defaults,
                        );
                        para_html.push(LINK_END_MARK);
                    }
                }
                "instrText" => in_instr = false,
                "fldSimple" => skip_simple = false,
                "rPr" => in_rpr = false,
                "pPr" => {
                    in_ppr = false;
                    in_pbdr = false;
                }
                "pBdr" => in_pbdr = false,
                "tabs" => in_tabs = false,
                "tblBorders" => in_tbl_borders = false,
                "tcBorders" => in_tc_borders = false,
                "tblCellMar" => in_tbl_cell_mar = false,
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
                    for id in pending_comments.drain(..) {
                        if let Some((author, date, text)) = comments.get(&id) {
                            report.comments.push(DocxComment {
                                author: author.clone(),
                                date: date.clone(),
                                text: text.clone(),
                                anchor: para_plain.trim().chars().take(60).collect(),
                            });
                        }
                    }
                    let align = para_align.clone().or_else(|| para_style.1.clone());
                    let mut css = align
                        .map(|a| format!("text-align:{};", align_css(&a)))
                        .unwrap_or_default();
                    // 들여쓰기 차례: 문단 직접 > 번호 목록 수준 > 스타일
                    let level_props = match &num_id {
                        Some(id) => {
                            let (left, first_line) = numbering.level_indent(id, ilvl);
                            ParaProps {
                                left,
                                first_line,
                                ..ParaProps::default()
                            }
                        }
                        None => ParaProps::default(),
                    };
                    css.push_str(
                        &para_props
                            .over(&level_props)
                            .over(&para_style.3)
                            .over(&para_defaults)
                            .css(),
                    );
                    if !para_tabs.is_empty() {
                        css.push_str(&format!("tab-stops:{};", para_tabs.join(" ")));
                    }
                    let style = if css.is_empty() {
                        String::new()
                    } else {
                        format!(" style=\"{css}\"")
                    };
                    // 본문·칸의 번호 목록은 한글 문단 번호로(표시 글자), 그 밖(머리말·주석·글상자)이나 한글로 옮길 수
                    // 없는 형식은 번호 글자로.
                    let list_mark = match (&num_id, list_item) {
                        (Some(_), _) | (None, true) if !header && depth == 0 => {
                            numbering.list_mark(num_id.as_deref(), ilvl)
                        }
                        _ => None,
                    };
                    let label = match (&num_id, list_item) {
                        _ if list_mark.is_some() => None,
                        (Some(id), _) => numbering.label(id, ilvl),
                        (None, true) => Some("•".into()),
                        (None, false) => None,
                    };
                    let label = list_mark.unwrap_or_default()
                        + &label
                            .map(|label| format!("{} ", escape(&label)))
                            .unwrap_or_default();
                    let mark = if page_break_before && cell.is_empty() {
                        PAGE_BREAK_MARK.to_string()
                    } else {
                        String::new()
                    };
                    // 표 뒤에 Word 가 요구해서 둔 1pt 빈 문단(Papy 저장)은 rhwp 에 필요 없고 한 줄을 차지하니 뺀다.
                    let tiny = para_html.is_empty()
                        && label.is_empty()
                        && mark.is_empty()
                        && matches!(&para_props.line, Some((line, rule)) if rule == "exact" && *line <= 40);
                    let after_table = match cell.last() {
                        Some(Some(current)) => current.html.ends_with("</table>"),
                        _ => body.ends_with("</table>"),
                    };
                    let caption = para_caption.take();
                    if tiny && (after_table || caption_after_table) {
                        if let Some(Some(current)) = cell.last_mut() {
                            current.tiny_after_table = true;
                        }
                    } else {
                        let caption_text = caption.map(String::from).unwrap_or_default();
                        let html = format!("<p{style}>{mark}{caption_text}{label}{para_html}</p>");
                        out(&mut body, &mut cell, &html);
                    }
                    caption_after_table = caption.is_some() && (after_table || caption_after_table);
                    if std::mem::take(&mut section_ends_here) && cell.is_empty() {
                        body.push_str(&format!("<!--papy-section-{}-->", section_pages.len()));
                    }
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
                    if let (Some(Some(mut done)), Some(table)) = (cell.pop(), tables.last_mut()) {
                        // Word 는 칸이 문단으로 끝나야 해서 칸 안 표 뒤에 빈 문단을 둔다. rhwp 칸은 그럴 필요가 없고
                        // 빈 줄만큼 칸이 높아지니 뺀다. Papy 가 쓴 문서는 그 자리에 1pt 문단을 두므로(이미 뺐다)
                        // 뒤 빈 문단은 원본 것이라 남긴다.
                        if let Some(end) = done
                            .html
                            .rfind("</table>")
                            .map(|i| i + "</table>".len())
                            .filter(|_| !done.tiny_after_table)
                        {
                            // 빈 문단(`<p…></p>`)만 뺀다. 글이 든 문단(`<p><span>F</span></p>`)도 `></p>` 로 끝나
                            // 전에는 칸 안 표 뒤 마지막 문단 글이 빠졌다.
                            let tail = &done.html[end..];
                            let empty = tail.starts_with("<p")
                                && tail.find('>').is_some_and(|i| &tail[i + 1..] == "</p>");
                            if empty {
                                done.html.truncate(end);
                            }
                        }
                        if let Some(row) = table.rows.last_mut() {
                            row.push(done);
                        }
                    }
                }
                "tbl" => {
                    if let Some(table) = tables.pop() {
                        if tables.is_empty() && cell.is_empty() {
                            report.table_indents.push(table.indent.saturating_mul(5));
                            report.table_places.push(table.place.clone());
                            report.table_inline.push(table.inline);
                        }
                        let html = table.to_html();
                        out(&mut body, &mut cell, &html);
                        caption_after_table = false;
                        if let Some(Some(current)) = cell.last_mut() {
                            current.tiny_after_table = false;
                        }
                    }
                }
                _ => {}
            },
            _ => {}
        }
    }
    report.lists = std::mem::take(&mut numbering.lists);
    // 문단이 가장 많은 구역(같으면 앞 구역).
    report.page = section_pages
        .iter()
        .rev()
        .max_by_key(|(_, count)| *count)
        .and_then(|(page, _)| page.to_setup());
    // 머리말·꼬리말: 짝수 쪽 것이 있으면 기본(default)은 홀수 쪽이다. 첫 쪽 것은 rhwp 에 맞는 자리가 없어
    // 첫 쪽에서 기본 것을 감추고(Word 도 첫 쪽 것이 없으면 아무것도 안 보인다), 글자가 있으면 빠진 것으로 센다.
    // 짝수 쪽 것은 설정(evenAndOddHeaders)이 켜졌을 때만 쓴다. 꺼져 있으면 Word 는 모든 쪽에 기본 것을 쓴다.
    if header {
        return Ok((format!("<html><body>{body}</body></html>"), report));
    }
    // 주 내용: 글자만이 아니라 서식·표·그림째 옮긴다. 못 바꾸면 None(글자만).
    for (parts, rels, ids, out) in [
        (
            &footnote_parts,
            "word/_rels/footnotes.xml.rels",
            &note_ids[0],
            &mut report.footnote_html,
        ),
        (
            &endnote_parts,
            "word/_rels/endnotes.xml.rels",
            &note_ids[1],
            &mut report.endnote_html,
        ),
    ] {
        for id in ids {
            let html = parts
                .get(id)
                .and_then(|xml| fragment_to_html(bytes, xml, rels, depth).ok());
            if let Some((_, part)) = &html {
                report.images += part.images;
                report.dropped_drawings += part.dropped_drawings;
            }
            out.push(html.map(|(html, _)| crate::notes::strip_field_marks(&html)));
        }
    }
    // 구역이 시작하는 자리: 구역 시작 방식대로 나누기 표시와 구역 표시를 첫 문단 앞에 둔다.
    // 구역이 표로 시작하면 표시만 담은 문단을 앞에 둔다.
    for section in 1..section_pages.len() {
        let placeholder = format!("<!--papy-section-{section}-->");
        let Some(at) = body.find(&placeholder) else {
            continue;
        };
        body.replace_range(at..at + placeholder.len(), "");
        let mut marks = String::new();
        match section_types.get(section).map(String::as_str) {
            Some("continuous") => {}
            Some("nextColumn") => marks.push(COLUMN_BREAK_MARK),
            _ => marks.push(PAGE_BREAK_MARK),
        }
        // 구역 표시는 모든 구역에 둔다(papy-hwp 가 표시 차례로 구역을 찾는다).
        marks.push(SECTION_MARK);
        let rest = &body[at..];
        if rest.starts_with("<p>") || rest.starts_with("<p ") {
            let close = at + rest.find('>').unwrap_or(0) + 1;
            body.insert_str(close, &marks);
        } else {
            body.insert_str(at, &format!("<p>{marks}</p>"));
        }
    }
    let even_and_odd = read_part(&mut archive, "word/settings.xml")?.is_some_and(|xml| {
        xml.split("<w:evenAndOddHeaders")
            .nth(1)
            .is_some_and(|rest| {
                let tag = &rest[..rest.find('>').unwrap_or(rest.len())];
                !(tag.contains("\"0\"") || tag.contains("\"false\"") || tag.contains("\"off\""))
            })
    });
    if !even_and_odd {
        header_refs.retain(|(_, _, kind, _)| kind != "even");
    }
    let has_even = header_refs.iter().any(|r| r.2 == "even");
    for (section, is_header, kind, id) in header_refs {
        let Some(path) = rels.get(&id).filter(|path| !path.is_empty()) else {
            continue;
        };
        let Some(xml) = read_part(&mut archive, path)? else {
            continue;
        };
        let paragraphs = header_footer_texts(&xml);
        let rich = rich_header_footer(&xml);
        let blank = !rich && paragraphs.iter().all(|p| p.trim().is_empty());
        let apply_to = match kind.as_str() {
            "default" if has_even => 2,
            "default" => 0,
            "even" => 1,
            "first" => {
                if section == 0 && title_page && !blank {
                    report.dropped_notes += 1;
                }
                continue;
            }
            _ => continue,
        };
        // 뒤 구역의 빈 머리말은 앞 구역 것을 끊으므로 남긴다.
        if blank && section == 0 {
            continue;
        }
        let target = if is_header {
            &mut report.headers
        } else {
            &mut report.footers
        };
        if !target
            .iter()
            .any(|existing| (existing.section, existing.apply_to) == (section, apply_to))
        {
            // 본문처럼 HTML 로도 바꾼다: 표·그림과 문단 서식(가운데 정렬 쪽 번호 등)·글자 모양이 남는다.
            // 못 하면 글자만 옮긴다.
            let (mut shapes, mut cell_shapes) = (Vec::new(), Vec::new());
            let html = if !blank {
                part_to_html(bytes, path, true).ok().map(|(html, part)| {
                    // 머리말에 둘 수 없는 것(각주·나누기)은 표시 글자를 빼고 빠진 것으로 센다. 도형은 머리말에 넣는다.
                    report.dropped_drawings += part.dropped_drawings;
                    report.dropped_notes += part.footnotes.len() + part.endnotes.len();
                    report.images += part.images;
                    shapes = part.shapes;
                    cell_shapes = part.cell_shapes;
                    crate::notes::strip_field_marks(&strip_marks_keeping_shapes(&html))
                })
            } else {
                None
            };
            target.push(HeaderFooterText {
                section,
                apply_to,
                paragraphs,
                html,
                shapes,
                cell_shapes,
            });
        }
    }
    if title_page {
        let first = |items: &[HeaderFooterText]| items.iter().any(|item| item.section == 0);
        report.hide_first_page = (first(&report.headers), first(&report.footers));
    }
    Ok((format!("<html><body>{body}</body></html>"), report))
}
