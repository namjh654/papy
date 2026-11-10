//! DOCX 의 본문 밖 부분: 번호 목록(`word/numbering.xml`), 각주(`word/footnotes.xml`), 머리말·꼬리말(`word/header*.xml`).
//!
//! 머리말·꼬리말·각주는 글자만 옮긴다(문단 단위). rhwp 문서 모델에 넣는 일은 papy-hwp 가 한다.

use std::collections::HashMap;

use quick_xml::Reader;
use quick_xml::events::Event;

use crate::read::{attr, local};

/// 본문 문단 안 각주 자리 표시(사용자 영역 글자). rhwp 에 붙여넣은 뒤 이 자리에 각주를 넣고 지운다.
pub const FOOTNOTE_MARK: char = '\u{E021}';
/// 본문 문단 안 미주 자리 표시(각주와 같은 방식).
pub const ENDNOTE_MARK: char = '\u{E023}';
/// 문단 맨 앞의 쪽 나누기 표시(사용자 영역 글자). papy-hwp 가 지우고 그 문단 앞에서 쪽을 나눈다.
pub const PAGE_BREAK_MARK: char = '\u{E024}';
/// 단 나누기 표시 글자(문단 앞). papy-hwp 가 붙여넣은 뒤 단 나누기로 바꾼다.
pub const COLUMN_BREAK_MARK: char = '\u{E025}';

/// 캡션 문단 표시(문단 맨 앞, 사용자 영역 글자). 차례로 왼쪽·오른쪽·위·아래 캡션의 첫 문단이다. papy-hwp 가
/// 지우고 그 문단을 앞(위 캡션이면 뒤) 문단의 표·그림 캡션으로 옮긴다.
pub const CAPTION_MARKS: [char; 4] = ['\u{E029}', '\u{E02A}', '\u{E02B}', '\u{E02C}'];
/// 캡션의 둘째 문단부터 붙는 표시(앞 캡션 문단에 이어진다).
pub const CAPTION_MORE_MARK: char = '\u{E02D}';

/// 메모 자리 표시(사용자 영역 글자). papy-hwp 가 지우고 그 자리에 한글 숨은 설명을 넣는다.
pub const COMMENT_MARK: char = '\u{E020}';

/// 책갈피 자리 표시(사용자 영역 글자). papy-hwp 가 지우고 그 자리에 책갈피를 넣는다.
pub const BOOKMARK_MARK: char = '\u{E01D}';
/// 번호 목록 문단 맨 앞 표시: 바로 뒤 두 글자가 `정의 번호 × 8 + 수준` 이다([`code_chars`])
/// ([`DocxReadReport::lists`](crate::DocxReadReport::lists) 의 정의를 한글 문단 번호·글머리표로 바꾼다).
pub const LIST_MARK: char = '\u{E01C}';
/// [`LIST_MARK`] 로 나타낼 수 있는 정의 수.
pub const MAX_LIST_DEFS: usize = 224;

/// 하이퍼링크 시작·끝 표시(사용자 영역 글자). papy-hwp 가 지우고 그 사이 글에 하이퍼링크를 건다.
pub const LINK_START_MARK: char = '\u{E01E}';
/// 하이퍼링크 끝 표시.
pub const LINK_END_MARK: char = '\u{E01F}';

/// 누름틀 시작·끝 표시(사용자 영역 글자). papy-hwp 가 지우고 그 사이 글을 누름틀(ClickHere)로 감싼다
/// (시작 표시 차례 = [`DocxReadReport::click_here`](crate::DocxReadReport::click_here) 차례).
pub const FIELD_START_MARK: char = '\u{E01A}';
/// 누름틀 끝 표시.
pub const FIELD_END_MARK: char = '\u{E01B}';
/// 표시로 나타낼 수 있는 누름틀 수
pub const MAX_FIELDS: usize = CODE_RADIX * CODE_RADIX;

/// 표시 뒤 차례 글자의 시작. 한 글자가 0~63 을 나타낸다(U+E040~U+E07F).
///
/// 자리 표시 글자는 모두 사용자 영역 U+E000~U+E0BB 에 둔다. rhwp 0.9 는 그 위(U+E0BC~U+F8F7)를 옛한글(KTUG 표)로
/// 바꿔 그리는데, 붙여넣은 뒤 표시가 남은 문단은 조판과 그리기가 글자 수를 달리 세어 멈췄다. 원문 옛한글을 표시로
/// 잘못 읽지도 않는다.
const CODE_BASE: u32 = 0xE040;
const CODE_RADIX: usize = 64;

/// 차례(`0..4096`)를 표시 뒤 두 글자로
pub fn code_chars(value: usize) -> [char; 2] {
    let digit =
        |n: usize| char::from_u32(CODE_BASE + (n % CODE_RADIX) as u32).unwrap_or('\u{E040}');
    [digit(value / CODE_RADIX), digit(value)]
}

/// 표시 뒤 두 글자 → 차례
pub fn code_value(high: char, low: char) -> Option<usize> {
    let digit = |c: char| {
        u32::from(c)
            .checked_sub(CODE_BASE)
            .map(|n| n as usize)
            .filter(|n| *n < CODE_RADIX)
    };
    Some(digit(high)? * CODE_RADIX + digit(low)?)
}

/// 누름틀 시작 표시(차례 포함)
pub fn field_start(index: usize) -> String {
    let [high, low] = code_chars(index);
    [FIELD_START_MARK, high, low].into_iter().collect()
}

/// [`FIELD_START_MARK`] 뒤 두 글자 → 차례
pub fn field_index(high: char, low: char) -> Option<usize> {
    code_value(high, low)
}

/// 조각 HTML 의 누름틀 차례를 `offset` 만큼 옮긴다(글상자 조각을 바깥 차례에 합칠 때).
pub(crate) fn shift_field_marks(html: &str, offset: usize) -> String {
    let chars: Vec<char> = html.chars().collect();
    let mut out = String::with_capacity(html.len());
    let mut i = 0;
    while i < chars.len() {
        if chars[i] == FIELD_START_MARK && i + 2 < chars.len() {
            if let Some(index) = field_index(chars[i + 1], chars[i + 2]) {
                out.push_str(&field_start(index + offset));
                i += 3;
                continue;
            }
        }
        out.push(chars[i]);
        i += 1;
    }
    out
}

/// 누름틀 표시를 모두 뺀다(누름틀을 넣지 않는 조각: 각주·미주).
pub(crate) fn strip_field_marks(html: &str) -> String {
    let chars: Vec<char> = html.chars().collect();
    let mut out = String::with_capacity(html.len());
    let mut i = 0;
    while i < chars.len() {
        match chars[i] {
            FIELD_START_MARK => {
                i += 1;
                if i + 1 < chars.len() && field_index(chars[i], chars[i + 1]).is_some() {
                    i += 2;
                }
            }
            FIELD_END_MARK => i += 1,
            c => {
                out.push(c);
                i += 1;
            }
        }
    }
    out
}
/// Papy 가 누름틀을 Word 콘텐츠 컨트롤(`w:sdt`)로 쓸 때 `w:tag` 앞머리: `papy-clickhere|<1·0 편집>|<이름>|<메모>`.
pub const CLICK_HERE_TAG: &str = "papy-clickhere";
/// 한글 계산식 필드를 `w:sdt` 로 쓸 때 `w:tag` 앞머리: `papy-formula|<1·0 편집>|<한글 명령>`(내용은 계산 결과 글자).
pub const FORMULA_TAG: &str = "papy-formula";

/// 누름틀 하나(한글 ClickHere 필드 ↔ Word 글 콘텐츠 컨트롤).
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ClickHere {
    /// 안내문(비었을 때 보이는 글). Word `w:alias`.
    pub guide: String,
    /// 메모(도움말)
    pub memo: String,
    /// 필드 이름. Word `w:tag`(Papy 것이 아니면 그대로).
    pub name: String,
    /// 양식 모드에서 고칠 수 있는가
    pub editable: bool,
    /// 누름틀이 아니라 한글 계산식 필드면 그 명령(`=SUM(B?:D?)??%g,;;3,520` 같은 것)
    pub formula: Option<String>,
}

impl ClickHere {
    /// `w:tag` 값
    pub fn tag(&self) -> String {
        if let Some(command) = &self.formula {
            return format!("{FORMULA_TAG}|{}|{command}", u8::from(self.editable));
        }
        format!(
            "{CLICK_HERE_TAG}|{}|{}|{}",
            u8::from(self.editable),
            self.name.replace('|', "/"),
            self.memo
        )
    }

    /// `w:alias`·`w:tag` 값에서
    pub fn from_word(alias: Option<String>, tag: Option<String>) -> Self {
        let guide = alias.unwrap_or_default();
        if let Some(rest) = tag.as_deref().and_then(|t| t.strip_prefix(FORMULA_TAG)) {
            let mut parts = rest.trim_start_matches('|').splitn(2, '|');
            let editable = parts.next() == Some("1");
            let command = parts.next().unwrap_or("").to_owned();
            return Self {
                editable,
                formula: Some(command),
                ..Self::default()
            };
        }
        match tag.as_deref().and_then(|t| t.strip_prefix(CLICK_HERE_TAG)) {
            Some(rest) => {
                let mut parts = rest.trim_start_matches('|').splitn(3, '|');
                let editable = parts.next() != Some("0");
                let name = parts.next().unwrap_or("").to_owned();
                let memo = parts.next().unwrap_or("").to_owned();
                Self {
                    guide,
                    memo,
                    name,
                    editable,
                    formula: None,
                }
            }
            None => Self {
                guide,
                memo: String::new(),
                name: tag.unwrap_or_default(),
                editable: true,
                formula: None,
            },
        }
    }
}

/// 머리말·꼬리말 하나.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct HeaderFooterText {
    /// Word 구역(0 부터). 0 이 아니면 본문의 그 차례 [`SECTION_MARK`] 문단부터 쓴다.
    pub section: usize,
    /// 적용 쪽: 0 = 모든 쪽, 1 = 짝수 쪽, 2 = 홀수 쪽(rhwp 의 apply_to 값과 같다).
    pub apply_to: u8,
    /// 문단별 글자
    pub paragraphs: Vec<String>,
    /// 머리말·꼬리말의 HTML(본문과 같은 꼴, 서식·표·그림 포함). 쪽 번호·전체 쪽 수 필드(표 밖)는
    /// [`PAGE_NUMBER_MARK`]·[`PAGE_COUNT_MARK`] 글자다. 있으면 `paragraphs` 대신 이것을 붙여넣는다.
    pub html: Option<String>,
    /// `html` 의 도형: 문단의 [`crate::SHAPE_MARK`] 자리마다(같은 차례). 칸 안 것은 `cell_shapes`.
    pub shapes: Vec<crate::DocxShape>,
    /// `html` 의 표 칸 안 도형([`crate::CELL_SHAPE_MARK`] 자리, 같은 차례)
    pub cell_shapes: Vec<crate::DocxShape>,
    /// `html` 의 맨 바깥 표마다 쪽·종이 기준 자리(`w:tblpPr`, [`crate::DocxReadReport::table_places`] 와 같은 꼴).
    /// 바탕쪽만 쓴다.
    pub table_places: Vec<String>,
}

/// Word 구역(첫 구역 뒤)이 시작하는 본문 문단 표시. 구역마다 하나씩 문서 순서로 둔다.
pub const SECTION_MARK: char = '\u{E028}';

/// HTML 머리말·꼬리말의 쪽 번호 필드 자리 표시(붙여넣은 뒤 [`PAGE_NUMBER_FIELD`] 로 바꾼다).
pub const PAGE_NUMBER_MARK: char = '\u{E026}';
/// HTML 머리말·꼬리말의 전체 쪽 수 필드 자리 표시(붙여넣은 뒤 [`PAGE_COUNT_FIELD`] 로 바꾼다).
pub const PAGE_COUNT_MARK: char = '\u{E027}';

/// 필드 명령이 쪽 번호·전체 쪽 수이면 그 자리 표시 글자.
pub fn page_field_mark(instr: &str) -> Option<char> {
    match field_char(instr)? {
        PAGE_NUMBER_FIELD => Some(PAGE_NUMBER_MARK),
        _ => Some(PAGE_COUNT_MARK),
    }
}

/// 머리말·꼬리말의 쪽 번호 필드 글자: rhwp 머리말·꼬리말은 이 글자를 지금 쪽 번호·전체 쪽 수로 그린다.
pub const PAGE_NUMBER_FIELD: char = '\u{0015}';
/// 전체 쪽 수 필드 글자
pub const PAGE_COUNT_FIELD: char = '\u{0016}';

/// XML 조각의 문단별 글자. 표 안 문단도 차례대로 펼친다. 각주 번호 표시(`footnoteRef`)는 뺀다.
pub fn paragraph_texts(xml: &str) -> Vec<String> {
    texts(xml, false)
}

/// [`paragraph_texts`] 와 같되 쪽 번호(`PAGE`)·전체 쪽 수(`NUMPAGES`) 필드를 필드 글자로 바꾼다(머리말·꼬리말).
pub fn header_footer_texts(xml: &str) -> Vec<String> {
    texts(xml, true)
}

/// 필드 명령의 첫 낱말로 고른 필드 글자.
fn field_char(instr: &str) -> Option<char> {
    match instr
        .split_whitespace()
        .next()?
        .to_ascii_uppercase()
        .as_str()
    {
        "PAGE" => Some(PAGE_NUMBER_FIELD),
        "NUMPAGES" | "SECTIONPAGES" => Some(PAGE_COUNT_FIELD),
        _ => None,
    }
}

fn texts(xml: &str, fields: bool) -> Vec<String> {
    let mut reader = Reader::from_str(xml);
    let mut paragraphs = Vec::new();
    let mut current: Option<String> = None;
    let mut in_text = false;
    // 필드: 복합 필드(fldChar)의 명령 글자, 결과(캐시된 쪽 번호) 건너뛰기
    let mut instr: Option<String> = None;
    let mut in_instr = false;
    let mut skip_result = false;
    let mut simple_depth = 0usize;
    // 주 번호 꾸밈 런(Papy 가 쓴 `papy:noteMark`)
    let mut note_mark = false;
    loop {
        match reader.read_event() {
            Ok(Event::Eof) | Err(_) => break,
            Ok(Event::Start(e)) => match local(e.name().as_ref()) {
                "p" => current = Some(String::new()),
                "r" => note_mark = attr(&e, "noteMark").is_some(),
                "t" => in_text = true,
                "instrText" if fields => in_instr = true,
                "fldSimple" if fields => {
                    if let Some(ch) = attr(&e, "instr").as_deref().and_then(field_char) {
                        current.get_or_insert_default().push(ch);
                        simple_depth += 1;
                    }
                }
                _ => {}
            },
            Ok(Event::Empty(e)) => match local(e.name().as_ref()) {
                "p" => paragraphs.push(String::new()),
                "tab" | "ptab" => current.get_or_insert_default().push('\t'),
                "br" | "cr" => current.get_or_insert_default().push(' '),
                "fldSimple" if fields => {
                    if let Some(ch) = attr(&e, "instr").as_deref().and_then(field_char) {
                        current.get_or_insert_default().push(ch);
                    }
                }
                "fldChar" if fields => match attr(&e, "fldCharType").as_deref() {
                    Some("begin") => instr = Some(String::new()),
                    Some("separate") => {
                        if let Some(ch) = instr.as_deref().and_then(field_char) {
                            current.get_or_insert_default().push(ch);
                            skip_result = true;
                        }
                        instr = None;
                    }
                    Some("end") => {
                        // 결과 없는 필드
                        if let Some(ch) = instr.take().as_deref().and_then(field_char) {
                            current.get_or_insert_default().push(ch);
                        }
                        skip_result = false;
                    }
                    _ => {}
                },
                _ => {}
            },
            Ok(Event::End(e)) => match local(e.name().as_ref()) {
                "p" => paragraphs.extend(current.take()),
                "r" => note_mark = false,
                "t" => in_text = false,
                "instrText" => in_instr = false,
                "fldSimple" if simple_depth > 0 => simple_depth -= 1,
                _ => {}
            },
            Ok(Event::Text(text)) if in_instr => {
                if let Some(instr) = instr.as_mut() {
                    instr.push_str(&text.xml10_content());
                }
            }
            Ok(Event::Text(_)) if in_text && (skip_result || simple_depth > 0 || note_mark) => {}
            Ok(Event::Text(text)) if in_text => {
                if let Some(current) = current.as_mut() {
                    current.push_str(&text.xml10_content());
                }
            }
            Ok(Event::GeneralRef(reference)) if in_text => {
                let Some(current) = current.as_mut() else {
                    continue;
                };
                if let Ok(Some(ch)) = reference.resolve_char_ref() {
                    current.push(ch);
                } else {
                    current.push_str(match reference.into_inner().as_ref() {
                        "amp" => "&",
                        "lt" => "<",
                        "gt" => ">",
                        "quot" => "\"",
                        "apos" => "'",
                        _ => "",
                    });
                }
            }
            _ => {}
        }
    }
    // 글자 안의 표시 문자는 지운다(우리 자리 표시와 섞이지 않게).
    for paragraph in &mut paragraphs {
        paragraph.retain(|c| c != FOOTNOTE_MARK);
    }
    paragraphs
}

/// `word/footnotes.xml` → 각주 id 별 문단 글자. 구분선(separator) 각주는 뺀다.
pub fn parse_footnotes(xml: &str) -> HashMap<String, Vec<String>> {
    parse_notes(xml, "footnote")
}

/// `word/endnotes.xml` 의 미주(id → 문단 글자).
pub fn parse_endnotes(xml: &str) -> HashMap<String, Vec<String>> {
    parse_notes(xml, "endnote")
}

/// 각주(`footnote`)·미주(`endnote`) 부분의 주마다 XML 조각(그 요소 전체). 구분선 등 특수 주는 뺀다.
pub(crate) fn note_parts(xml: &str, element: &str) -> HashMap<String, String> {
    let mut notes = HashMap::new();
    let mut reader = Reader::from_str(xml);
    let mut start = None;
    let mut id = None;
    loop {
        let position = reader.buffer_position() as usize;
        match reader.read_event() {
            Ok(Event::Eof) | Err(_) => break,
            Ok(Event::Start(e)) if local_name(&e) == element => {
                let separator = attr(&e, "type").is_some_and(|t| t != "normal");
                id = attr(&e, "id").filter(|_| !separator);
                start = Some(position);
            }
            Ok(Event::End(e)) if local(e.name().as_ref()) == element => {
                let end = reader.buffer_position() as usize;
                if let (Some(id), Some(start)) = (id.take(), start.take()) {
                    if let Some(part) = xml.get(start..end) {
                        notes.insert(id, part.to_owned());
                    }
                }
            }
            _ => {}
        }
    }
    notes
}

fn parse_notes(xml: &str, element: &str) -> HashMap<String, Vec<String>> {
    let mut notes = HashMap::new();
    let mut reader = Reader::from_str(xml);
    let mut start = None;
    let mut id = None;
    loop {
        let position = reader.buffer_position() as usize;
        match reader.read_event() {
            Ok(Event::Eof) | Err(_) => break,
            Ok(Event::Start(e)) if local_name(&e) == element => {
                let separator = attr(&e, "type").is_some_and(|t| t != "normal");
                id = attr(&e, "id").filter(|_| !separator);
                start = Some(position);
            }
            Ok(Event::End(e)) if local(e.name().as_ref()) == element => {
                let end = reader.buffer_position() as usize;
                if let (Some(id), Some(start)) = (id.take(), start.take()) {
                    if let Some(part) = xml.get(start..end) {
                        notes.insert(id, paragraph_texts(part));
                    }
                }
            }
            _ => {}
        }
    }
    notes
}

fn local_name(e: &quick_xml::events::BytesStart) -> String {
    local(e.name().as_ref()).to_owned()
}

/// 번호 목록 한 수준.
#[derive(Debug, Clone)]
struct Level {
    format: String,
    text: String,
    start: u32,
    /// 수준 들여쓰기(twip, `w:lvl/w:pPr/w:ind`): 왼쪽, 첫 줄(+ 들여쓰기, - 내어쓰기)
    left: Option<i64>,
    first_line: Option<i64>,
}

/// 한글 문단 번호·글머리표로 옮길 번호 목록 정의 하나(Word `w:num` 하나, 글머리표는 글자 하나).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ListDef {
    /// 글머리표면 그 글자(수준과 무관)
    pub bullet: Option<char>,
    /// 번호면 수준(0~6)마다 (한글 번호 형식 코드(표 43), 한글 번호 모양(`^1.`), 시작 번호)
    pub levels: Vec<(u8, String, u32)>,
}

/// Word `w:numFmt` → 한글 번호 형식 코드(표 43). 한글에 없는 형식은 None(번호 글자로 읽는다).
fn hwp_num_format(format: &str) -> Option<u8> {
    Some(match format {
        "decimal" => 0,
        "decimalEnclosedCircle" | "decimalEnclosedCircleChinese" => 1,
        "upperRoman" => 2,
        "lowerRoman" => 3,
        "upperLetter" => 4,
        "lowerLetter" => 5,
        "ganada" => 8,
        "chosung" => 10,
        "koreanCounting" => 12,
        "chineseCounting"
        | "japaneseCounting"
        | "chineseCountingThousand"
        | "ideographTraditional" => 13,
        _ => return None,
    })
}

/// Word 번호 모양(`%1.%2.`) → 한글 번호 모양(`^1.^2.`). 8·9 수준을 가리키면 None.
fn hwp_level_text(text: &str) -> Option<String> {
    let mut out = String::new();
    let mut chars = text.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '%' {
            match chars.next() {
                Some(d @ '1'..='7') => {
                    out.push('^');
                    out.push(d);
                }
                _ => return None,
            }
        } else if c == '^' {
            return None;
        } else {
            out.push(c);
        }
    }
    Some(out)
}

/// `word/numbering.xml` 과 지금까지 센 번호.
#[derive(Debug, Default)]
pub struct Numbering {
    /// [`Numbering::list_mark`] 가 만든 정의(표시 글자의 정의 번호 차례). 글머리표는 글자마다, 번호는 numId 마다.
    pub lists: Vec<ListDef>,
    /// 정의 번호: numId(번호) 또는 "•글자"(글머리표)
    list_keys: Vec<String>,
    /// numId → abstractNumId
    nums: HashMap<String, String>,
    /// abstractNumId → 수준(ilvl 순)
    abstracts: HashMap<String, Vec<Option<Level>>>,
    /// numId → 수준별 현재 번호
    counters: HashMap<String, [u32; 9]>,
}

impl Numbering {
    pub fn parse(xml: &str) -> Numbering {
        let mut numbering = Numbering::default();
        let mut reader = Reader::from_str(xml);
        let mut abstract_id: Option<String> = None;
        let mut level: Option<(usize, Level)> = None;
        let mut num_id: Option<String> = None;
        loop {
            let (e, empty) = match reader.read_event() {
                Ok(Event::Eof) | Err(_) => break,
                Ok(Event::Start(e)) => (e, false),
                Ok(Event::Empty(e)) => (e, true),
                Ok(Event::End(e)) => {
                    match local(e.name().as_ref()) {
                        "lvl" => {
                            if let (Some(id), Some((index, done))) = (&abstract_id, level.take()) {
                                let levels = numbering.abstracts.entry(id.clone()).or_default();
                                if levels.len() <= index {
                                    levels.resize(index + 1, None);
                                }
                                levels[index] = Some(done);
                            }
                        }
                        "abstractNum" => abstract_id = None,
                        "num" => num_id = None,
                        _ => {}
                    }
                    continue;
                }
                _ => continue,
            };
            match local_name(&e).as_str() {
                "abstractNum" if !empty => abstract_id = attr(&e, "abstractNumId"),
                "lvl" if !empty && abstract_id.is_some() => {
                    let index = attr(&e, "ilvl")
                        .and_then(|v| v.parse().ok())
                        .unwrap_or(0usize);
                    if index < 9 {
                        level = Some((
                            index,
                            Level {
                                format: "decimal".into(),
                                text: format!("%{}.", index + 1),
                                start: 1,
                                left: None,
                                first_line: None,
                            },
                        ));
                    }
                }
                "numFmt" => {
                    if let (Some((_, level)), Some(value)) = (level.as_mut(), attr(&e, "val")) {
                        level.format = value;
                    }
                }
                "lvlText" => {
                    if let (Some((_, level)), Some(value)) = (level.as_mut(), attr(&e, "val")) {
                        level.text = value;
                    }
                }
                "start" => {
                    if let (Some((_, level)), Some(value)) =
                        (level.as_mut(), attr(&e, "val").and_then(|v| v.parse().ok()))
                    {
                        level.start = value;
                    }
                }
                "ind" => {
                    if let Some((_, level)) = level.as_mut() {
                        let number = |key: &str| attr(&e, key).and_then(|v| v.parse::<i64>().ok());
                        level.left = number("left").or_else(|| number("start"));
                        level.first_line = number("hanging")
                            .map(|h| -h)
                            .or_else(|| number("firstLine"));
                    }
                }
                "num" if !empty => num_id = attr(&e, "numId"),
                "abstractNumId" => {
                    if let (Some(id), Some(value)) = (&num_id, attr(&e, "val")) {
                        numbering.nums.insert(id.clone(), value);
                    }
                }
                _ => {}
            }
        }
        numbering
    }

    /// 번호 목록 문단을 한글 문단 번호로 옮길 표시 글자([`LIST_MARK`] + 정의·수준). 한글로 옮길 수 없는
    /// 형식(번호 없음, 8·9 수준, 한글에 없는 번호 형식, 정의가 너무 많음)이면 None — 그때는 [`Numbering::label`] 로
    /// 번호 글자를 넣는다. `num_id` 가 None 이면(정의 없는 목록 문단) 글머리표 "•".
    pub fn list_mark(&mut self, num_id: Option<&str>, ilvl: usize) -> Option<String> {
        if ilvl > 6 || num_id == Some("0") {
            return None;
        }
        let level_of =
            |levels: &Vec<Option<Level>>, index: usize| levels.get(index).cloned().flatten();
        let (key, def) = match num_id.and_then(|id| {
            self.nums
                .get(id)
                .and_then(|a| self.abstracts.get(a))
                .map(|l| (id, l))
        }) {
            None => (
                "•".to_string(),
                ListDef {
                    bullet: Some('•'),
                    levels: Vec::new(),
                },
            ),
            Some((id, levels)) => {
                let level = level_of(levels, ilvl)?;
                if level.format == "bullet" {
                    let ch = bullet(&level.text).chars().next().unwrap_or('•');
                    (
                        format!("•{ch}"),
                        ListDef {
                            bullet: Some(ch),
                            levels: Vec::new(),
                        },
                    )
                } else {
                    let mut hwp_levels = Vec::new();
                    for index in 0..7 {
                        let entry = match level_of(levels, index) {
                            Some(l) if l.format != "bullet" && l.format != "none" => {
                                match (hwp_num_format(&l.format), hwp_level_text(&l.text)) {
                                    (Some(code), Some(text)) => (code, text, l.start),
                                    _ if index == ilvl => return None,
                                    _ => (0, format!("^{}.", index + 1), 1),
                                }
                            }
                            _ if index == ilvl => return None,
                            _ => (0, format!("^{}.", index + 1), 1),
                        };
                        hwp_levels.push(entry);
                    }
                    (
                        id.to_string(),
                        ListDef {
                            bullet: None,
                            levels: hwp_levels,
                        },
                    )
                }
            }
        };
        let index = match self.list_keys.iter().position(|k| *k == key) {
            Some(index) => index,
            None if self.lists.len() < MAX_LIST_DEFS => {
                self.list_keys.push(key);
                self.lists.push(def);
                self.lists.len() - 1
            }
            None => return None,
        };
        let [high, low] = code_chars(index * 8 + ilvl);
        Some(format!("{LIST_MARK}{high}{low}"))
    }

    /// 번호 목록 수준의 들여쓰기(twip): (왼쪽, 첫 줄). 문단에 직접 준 들여쓰기가 없을 때 쓴다(Word 와 같은 차례).
    pub fn level_indent(&self, num_id: &str, ilvl: usize) -> (Option<i64>, Option<i64>) {
        self.nums
            .get(num_id)
            .and_then(|id| self.abstracts.get(id))
            .and_then(|levels| levels.get(ilvl.min(8)).cloned().flatten())
            .map_or((None, None), |level| (level.left, level.first_line))
    }

    /// 문단 번호 글자("1.", "가.", "•" 등). numId 가 0 이면 번호 없음. 정의를 못 찾으면 "•".
    pub fn label(&mut self, num_id: &str, ilvl: usize) -> Option<String> {
        if num_id == "0" {
            return None;
        }
        let ilvl = ilvl.min(8);
        let levels = self.nums.get(num_id).and_then(|id| self.abstracts.get(id));
        let Some(levels) = levels else {
            return Some("•".into());
        };
        let level_of = |index: usize| levels.get(index).cloned().flatten();
        let Some(level) = level_of(ilvl) else {
            return Some("•".into());
        };
        if level.format == "bullet" {
            return Some(bullet(&level.text));
        }
        if level.format == "none" {
            return (!level.text.contains('%'))
                .then_some(level.text)
                .filter(|t| !t.is_empty());
        }
        let counters = self.counters.entry(num_id.to_owned()).or_insert([0; 9]);
        counters[ilvl] = if counters[ilvl] == 0 {
            level.start
        } else {
            counters[ilvl] + 1
        };
        for deeper in counters.iter_mut().skip(ilvl + 1) {
            *deeper = 0;
        }
        let mut text = level.text.clone();
        for index in (0..=ilvl).rev() {
            let placeholder = format!("%{}", index + 1);
            if !text.contains(&placeholder) {
                continue;
            }
            let (value, format) = match level_of(index) {
                Some(upper) => (
                    if counters[index] == 0 {
                        upper.start
                    } else {
                        counters[index]
                    },
                    upper.format,
                ),
                None => (counters[index].max(1), "decimal".into()),
            };
            // 이 수준은 자기 형식, "%1.%2." 처럼 함께 쓰는 상위 번호는 상위 수준 형식.
            let format = if index == ilvl {
                level.format.clone()
            } else {
                format
            };
            text = text.replace(&placeholder, &format_number(value, &format));
        }
        Some(text)
    }
}

/// 글머리표 글자. Symbol·Wingdings 글꼴의 사용자 영역 글자는 일반 글머리표로 바꾼다.
fn bullet(text: &str) -> String {
    let mapped: String = text
        .chars()
        .map(|c| match c {
            '\u{F0A7}' | '\u{F0A8}' | '\u{F06E}' => '▪',
            '\u{F0D8}' | '\u{F0E0}' => '➢',
            '\u{F0FC}' => '✓',
            c if ('\u{E000}'..='\u{F8FF}').contains(&c) => '•',
            c => c,
        })
        .collect();
    if mapped.trim().is_empty() {
        "•".into()
    } else {
        mapped
    }
}

fn roman(mut value: u32) -> String {
    const TABLE: [(u32, &str); 13] = [
        (1000, "m"),
        (900, "cm"),
        (500, "d"),
        (400, "cd"),
        (100, "c"),
        (90, "xc"),
        (50, "l"),
        (40, "xl"),
        (10, "x"),
        (9, "ix"),
        (5, "v"),
        (4, "iv"),
        (1, "i"),
    ];
    let mut out = String::new();
    for (unit, text) in TABLE {
        while value >= unit {
            out.push_str(text);
            value -= unit;
        }
    }
    out
}

fn cycle(value: u32, items: &[char]) -> String {
    let index = (value.max(1) - 1) as usize;
    items[index % items.len()].to_string()
}

/// 번호 하나를 형식에 맞춘 글자로.
pub(crate) fn format_number(value: u32, format: &str) -> String {
    const GANADA: [char; 14] = [
        '가', '나', '다', '라', '마', '바', '사', '아', '자', '차', '카', '타', '파', '하',
    ];
    const CHOSUNG: [char; 14] = [
        'ㄱ', 'ㄴ', 'ㄷ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅅ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    ];
    match format {
        "lowerLetter" | "upperLetter" => {
            let index = value.max(1) - 1;
            let letter = (b'a' + (index % 26) as u8) as char;
            let text = letter.to_string().repeat((index / 26 + 1).min(10) as usize);
            if format == "upperLetter" {
                text.to_uppercase()
            } else {
                text
            }
        }
        "lowerRoman" => roman(value),
        "upperRoman" => roman(value).to_uppercase(),
        "ganada" => cycle(value, &GANADA),
        "chosung" => cycle(value, &CHOSUNG),
        "decimalEnclosedCircle" | "decimalEnclosedCircleChinese" if (1..=20).contains(&value) => {
            char::from_u32(0x2460 + value - 1)
                .map(String::from)
                .unwrap_or_default()
        }
        "decimalZero" if value < 10 => format!("0{value}"),
        _ => value.to_string(),
    }
}
