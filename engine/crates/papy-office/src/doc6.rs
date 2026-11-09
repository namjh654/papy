//! Word 6.0·95 문서(.doc, FIB `wIdent` 0xA5DC, nFib 101~104) 읽기 — 글과 문단만.
//!
//! Word 97 과 달리 표 스트림이 따로 없고(조각표·서식표가 WordDocument 스트림 안에 있다) 글은 한 바이트
//! 코드 페이지 글자다(FIB `lid` 언어로 고른다: 한국어 949, 러시아어 1251 …). 글자 서식·표 구조·그림은 읽지 않고
//! 문단(0x0D)·표 칸(0x07, 탭으로)·줄바꿈·쪽 나누기·필드 결과만 옮긴다. 열리지 않던 옛 문서의 글을 살리는 것이 목적이다.

use encoding_rs::Encoding;
use papy_model::{Error, ErrorCode, Result};

use crate::DocxReadReport;

fn invalid(message: &str) -> Error {
    Error::new(ErrorCode::InvalidDocument, message)
}

fn u16_at(b: &[u8], i: usize) -> Option<u16> {
    Some(u16::from_le_bytes(b.get(i..i + 2)?.try_into().ok()?))
}

fn u32_at(b: &[u8], i: usize) -> Option<u32> {
    Some(u32::from_le_bytes(b.get(i..i + 4)?.try_into().ok()?))
}

/// FIB `lid`(언어) → 글자 코드 페이지
fn encoding_for(lid: u16, mac: bool) -> &'static Encoding {
    use encoding_rs::*;
    if mac {
        return MACINTOSH;
    }
    match lid & 0x3FF {
        0x12 => EUC_KR,                                                        // 한국어(949)
        0x11 => SHIFT_JIS,                                                     // 일본어
        0x04 if lid == 0x0404 || lid == 0x0C04 => BIG5,                        // 중국어 번체
        0x04 => GBK,                                                           // 중국어 간체
        0x19 | 0x22 | 0x23 | 0x02 | 0x2F => WINDOWS_1251,                      // 키릴
        0x1A if lid == 0x0C1A => WINDOWS_1251,                                 // 세르비아어(키릴)
        0x05 | 0x0E | 0x15 | 0x18 | 0x1B | 0x1C | 0x24 | 0x1A => WINDOWS_1250, // 중부 유럽
        0x08 => WINDOWS_1253,                                                  // 그리스어
        0x1F => WINDOWS_1254,                                                  // 터키어
        0x0D => WINDOWS_1255,                                                  // 히브리어
        0x01 => WINDOWS_1256,                                                  // 아랍어
        0x25..=0x27 => WINDOWS_1257,                                           // 발트어
        0x2A => WINDOWS_1258,                                                  // 베트남어
        0x1E => WINDOWS_874,                                                   // 타이어
        _ => WINDOWS_1252,
    }
}

/// 글꼴 표(Sttbfffn, fc 0xD0·lcb 0xD4)의 글꼴 문자 집합(FFN `chs`) 가운데 ANSI·기호가 아닌 것으로 가장 많은 것.
/// 언어(`lid`)가 영어로 적혀 있어도 러시아어·체코어 글꼴로 쓴 문서가 많다.
fn font_charset(word: &[u8]) -> Option<u8> {
    let fc = u32_at(word, 0xD0)? as usize;
    let lcb = u32_at(word, 0xD4)? as usize;
    let table = word.get(fc..fc.checked_add(lcb)?)?;
    let mut counts = [0usize; 256];
    let mut at = 2;
    while at < table.len() {
        let size = usize::from(table[at]) + 1;
        if let Some(&chs) = table.get(at + 4) {
            if !matches!(chs, 0 | 1 | 2 | 77 | 255) {
                counts[usize::from(chs)] += 1;
            }
        }
        at += size;
    }
    let (chs, count) = counts.iter().enumerate().max_by_key(|(_, c)| **c)?;
    (*count > 0).then_some(chs as u8)
}

/// Windows 글꼴 문자 집합 → 코드 페이지
fn encoding_for_charset(chs: u8) -> Option<&'static Encoding> {
    use encoding_rs::*;
    Some(match chs {
        128 => SHIFT_JIS,
        129 => EUC_KR,
        134 => GBK,
        136 => BIG5,
        161 => WINDOWS_1253,
        162 => WINDOWS_1254,
        163 => WINDOWS_1258,
        177 => WINDOWS_1255,
        178 => WINDOWS_1256,
        186 => WINDOWS_1257,
        204 => WINDOWS_1251,
        222 => WINDOWS_874,
        238 => WINDOWS_1250,
        _ => return None,
    })
}

/// 글 위치(cp) 범위 → WordDocument 의 바이트 범위들(조각표, 없으면 fcMin 부터 이어서)
fn text_bytes(word: &[u8], ccp: u32) -> Result<Vec<u8>> {
    let flags = u16_at(word, 0x0A).unwrap_or(0);
    let fc_min = u32_at(word, 0x18).unwrap_or(0) as usize;
    let complex = flags & 0x0004 != 0;
    let ccp = ccp as usize;
    if !complex {
        let end = fc_min.checked_add(ccp).filter(|e| *e <= word.len());
        return end
            .map(|end| word[fc_min..end].to_vec())
            .ok_or_else(|| invalid("Word 6 text is out of range"));
    }
    // 조각표(Clx): fcClx 0x160, lcbClx 0x164. 앞의 grpprl(0x01) 묶음을 건너뛰고 0x02 Plcfpcd.
    let fc_clx = u32_at(word, 0x160).unwrap_or(0) as usize;
    let lcb_clx = u32_at(word, 0x164).unwrap_or(0) as usize;
    let clx = word
        .get(fc_clx..fc_clx.saturating_add(lcb_clx))
        .ok_or_else(|| invalid("Word 6 piece table is out of range"))?;
    let mut at = 0;
    while clx.get(at) == Some(&0x01) {
        let size = u16_at(clx, at + 1).ok_or_else(|| invalid("bad Word 6 piece table"))? as usize;
        at += 3 + size;
    }
    if clx.get(at) != Some(&0x02) {
        return Err(invalid("bad Word 6 piece table"));
    }
    let lcb = u32_at(clx, at + 1).ok_or_else(|| invalid("bad Word 6 piece table"))? as usize;
    let plc = clx
        .get(at + 5..at + 5 + lcb)
        .ok_or_else(|| invalid("bad Word 6 piece table"))?;
    // Plcfpcd: cp (n+1)개 + Pcd(8바이트) n개
    let n = lcb.saturating_sub(4) / 12;
    let mut out = Vec::with_capacity(ccp);
    for i in 0..n {
        let start = u32_at(plc, i * 4).unwrap_or(0) as usize;
        let end = u32_at(plc, (i + 1) * 4).unwrap_or(0) as usize;
        if start >= ccp {
            break;
        }
        let len = end.min(ccp).saturating_sub(start);
        let pcd = (n + 1) * 4 + i * 8;
        let fc = u32_at(plc, pcd + 2).unwrap_or(0) as usize;
        if let Some(bytes) = word.get(fc..fc.saturating_add(len)) {
            out.extend_from_slice(bytes);
        }
    }
    Ok(out)
}

fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}

/// Word 6·95 WordDocument 스트림 → 붙여넣을 HTML(문단만). 암호 문서는 `PasswordRequired`.
pub(crate) fn to_html(word: &[u8]) -> Result<(String, DocxReadReport)> {
    let flags = u16_at(word, 0x0A).unwrap_or(0);
    if flags & 0x0100 != 0 {
        return Err(Error::new(
            ErrorCode::PasswordRequired,
            "encrypted Word 6/95 documents are not supported",
        ));
    }
    let lid = u16_at(word, 0x06).unwrap_or(0x0409);
    // chse(0x14): 0 = Windows ANSI, 256 = Macintosh
    let mac = u16_at(word, 0x14) == Some(256);
    let ccp_text = u32_at(word, 0x34).unwrap_or(0);
    if ccp_text > super::doc::MAX_CHARS {
        return Err(Error::new(
            ErrorCode::LimitExceeded,
            "Word document is too large",
        ));
    }
    let bytes = text_bytes(word, ccp_text)?;
    // 언어가 서유럽(1252)이면 글꼴 문자 집합을 따른다.
    let mut encoding = encoding_for(lid, mac);
    if encoding == encoding_rs::WINDOWS_1252 {
        if let Some(by_font) = font_charset(word).and_then(encoding_for_charset) {
            encoding = by_font;
        }
    }
    let (text, _, _) = encoding.decode(&bytes);
    let mut html = String::from("<html><body>");
    let mut paragraph = String::new();
    // 필드: 0x13 명령 0x14 결과 0x15 — 명령은 빼고 결과만
    let mut in_instruction = Vec::<bool>::new();
    let mut page_break = false;
    for c in text.chars() {
        match c {
            '\u{13}' => {
                in_instruction.push(true);
            }
            '\u{14}' => {
                if let Some(top) = in_instruction.last_mut() {
                    *top = false;
                }
            }
            '\u{15}' => {
                in_instruction.pop();
            }
            _ if in_instruction.last() == Some(&true) => {}
            '\r' | '\u{07}' if c == '\r' || paragraph.ends_with('\t') => {
                // 문단 끝(표 행 끝 0x07 이 칸 끝 뒤에 오면 문단으로)
                let body = paragraph.trim_end_matches('\t');
                let mark = if std::mem::take(&mut page_break) {
                    crate::PAGE_BREAK_MARK.to_string()
                } else {
                    String::new()
                };
                html.push_str(&format!("<p>{mark}{}</p>", escape(body)));
                paragraph.clear();
            }
            '\u{07}' => paragraph.push('\t'),
            '\u{0B}' => paragraph.push('\n'),
            '\u{0C}' => {
                if !paragraph.is_empty() {
                    html.push_str(&format!("<p>{}</p>", escape(&paragraph)));
                    paragraph.clear();
                }
                page_break = true;
            }
            '\u{1E}' => paragraph.push('-'),
            '\u{1F}' => {}
            c if (c as u32) < 0x20 && c != '\t' => {}
            c => paragraph.push(c),
        }
    }
    if !paragraph.is_empty() {
        html.push_str(&format!("<p>{}</p>", escape(&paragraph)));
    }
    html = html.replace('\n', "<br>");
    html.push_str("</body></html>");
    Ok((html, DocxReadReport::default()))
}
