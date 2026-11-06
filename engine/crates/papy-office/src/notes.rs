//! DOCX 의 본문 밖 부분: 번호 목록(`word/numbering.xml`), 각주(`word/footnotes.xml`), 머리말·꼬리말(`word/header*.xml`).
//!
//! 머리말·꼬리말·각주는 글자만 옮긴다(문단 단위). rhwp 문서 모델에 넣는 일은 papy-hwp 가 한다.

use std::collections::HashMap;

use quick_xml::Reader;
use quick_xml::events::Event;

use crate::read::{attr, local};

/// 본문 문단 안 각주 자리 표시(사용자 영역 글자). rhwp 에 붙여넣은 뒤 이 자리에 각주를 넣고 지운다.
pub const FOOTNOTE_MARK: char = '\u{E0F1}';
/// 본문 문단 안 미주 자리 표시(각주와 같은 방식).
pub const ENDNOTE_MARK: char = '\u{E0F3}';
/// 문단 맨 앞의 쪽 나누기 표시(사용자 영역 글자). papy-hwp 가 지우고 그 문단 앞에서 쪽을 나눈다.
pub const PAGE_BREAK_MARK: char = '\u{E0F4}';
/// 단 나누기 표시 글자(문단 앞). papy-hwp 가 붙여넣은 뒤 단 나누기로 바꾼다.
pub const COLUMN_BREAK_MARK: char = '\u{E0F5}';

/// 머리말·꼬리말 하나.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct HeaderFooterText {
    /// 적용 쪽: 0 = 모든 쪽, 1 = 짝수 쪽, 2 = 홀수 쪽(rhwp 의 apply_to 값과 같다).
    pub apply_to: u8,
    /// 문단별 글자
    pub paragraphs: Vec<String>,
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
    loop {
        match reader.read_event() {
            Ok(Event::Eof) | Err(_) => break,
            Ok(Event::Start(e)) => match local(e.name().as_ref()) {
                "p" => current = Some(String::new()),
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
                "tab" => current.get_or_insert_default().push('\t'),
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
            Ok(Event::Text(_)) if in_text && (skip_result || simple_depth > 0) => {}
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
}

/// `word/numbering.xml` 과 지금까지 센 번호.
#[derive(Debug, Default)]
pub struct Numbering {
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
