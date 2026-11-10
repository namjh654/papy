//! 구형 Word 문서(.doc, Word 97~2003 바이너리, [MS-DOC]) → HTML.
//!
//! DOCX 와 같은 길을 탄다: rhwp 의 HTML 붙여넣기가 받는 HTML 과 [`DocxReadReport`](머리말·꼬리말·각주·미주·제목)를
//! 만들고, papy-hwp 가 붙여넣은 뒤 나머지를 넣는다.
//!
//! 옮기는 것: 본문 글자(조각 표), 글자 서식(굵게·기울임·밑줄·취소선·크기·색·글꼴), 문단 정렬, 제목 스타일(목차),
//! 표(겹친 표, 가로·세로 병합, 테두리), 글자처럼 놓은 그림(PNG·JPEG·DIB·GIF, EMF·WMF 는 SVG 로), OLE 개체의
//! 미리보기 그림, 떠 있는 그림·도형·글상자·묶음 도형([`crate::doc_art`]), 필드 결과 글자, 각주·미주(글자),
//! 첫 구역 머리말·꼬리말(글자). 암호 문서(RC4·RC4 CryptoAPI·XOR 난독화)는 암호로 푼다([`crate::doc_crypt`]).
//! 옮기지 않는 것(빠진 개체로 센다): 표 안의 도형·메타파일 그림, 머리말 안 도형. 번호 목록은 "•" 로 둔다.

use std::collections::HashMap;
use std::io::{Cursor, Read};

use base64::Engine as _;
use papy_model::{Error, ErrorCode, Result};

use crate::doc_art::{Art, Picture, find_picture};
use crate::notes::HeaderFooterText;
use crate::read::{DocxHeading, DocxReadReport, image_mime};
use crate::shapes::{DocxShape, Relative, SHAPE_MARK, ShapeImage, ShapeKind, Wrap};
use crate::{COLUMN_BREAK_MARK, ENDNOTE_MARK, FOOTNOTE_MARK, PAGE_BREAK_MARK};

/// 스트림 하나의 상한
const MAX_STREAM_BYTES: u64 = 256 * 1024 * 1024;
/// 본문 글자 수 상한
pub(crate) const MAX_CHARS: u32 = 64 * 1024 * 1024;
/// 그림 하나·전체 상한
const MAX_IMAGE_BYTES: usize = 32 * 1024 * 1024;
const MAX_TOTAL_IMAGE_BYTES: usize = 256 * 1024 * 1024;
/// 표 겹침 상한
const MAX_TABLE_DEPTH: usize = 16;

fn invalid(message: &str) -> Error {
    Error::new(ErrorCode::InvalidDocument, message)
}

/// CFB 안에 `WordDocument` 스트림이 있으면 Word 문서로 본다.
pub fn is_doc(bytes: &[u8]) -> bool {
    cfb::CompoundFile::open(Cursor::new(bytes)).is_ok_and(|file| file.is_stream("/WordDocument"))
}

// ---- 바이트 읽기 ----

fn u16_at(b: &[u8], i: usize) -> Option<u16> {
    Some(u16::from_le_bytes(b.get(i..i + 2)?.try_into().ok()?))
}
fn u32_at(b: &[u8], i: usize) -> Option<u32> {
    Some(u32::from_le_bytes(b.get(i..i + 4)?.try_into().ok()?))
}
fn i16_at(b: &[u8], i: usize) -> Option<i16> {
    Some(i16::from_le_bytes(b.get(i..i + 2)?.try_into().ok()?))
}

fn read_stream(file: &mut cfb::CompoundFile<Cursor<&[u8]>>, name: &str) -> Result<Option<Vec<u8>>> {
    if !file.is_stream(name) {
        return Ok(None);
    }
    let stream = file
        .open_stream(name)
        .map_err(|_| invalid("unreadable Word stream"))?;
    let mut bytes = Vec::new();
    stream
        .take(MAX_STREAM_BYTES + 1)
        .read_to_end(&mut bytes)
        .map_err(|_| invalid("unreadable Word stream"))?;
    if bytes.len() as u64 > MAX_STREAM_BYTES {
        return Err(Error::new(
            ErrorCode::LimitExceeded,
            "Word stream is too large",
        ));
    }
    Ok(Some(bytes))
}

// ---- 글자 서식·문단 속성(sprm) ----

/// 글자 서식. `None` 은 스타일에서 물려받는다.
#[derive(Debug, Clone, Default, PartialEq)]
struct Chp {
    bold: Option<bool>,
    italic: Option<bool>,
    underline: Option<bool>,
    strike: Option<bool>,
    hidden: Option<bool>,
    deleted: Option<bool>,
    special: Option<bool>,
    ole: Option<bool>,
    /// 반 포인트
    size: Option<u16>,
    /// 0xRRGGBB
    color: Option<u32>,
    /// 글꼴 번호(ascii, 동아시아)
    font: Option<u16>,
    font_east: Option<u16>,
    /// 그림 위치(Data 스트림)
    pic: Option<u32>,
    /// 문자 스타일
    istd: Option<u16>,
}

impl Chp {
    fn over(&self, base: &Chp) -> Chp {
        Chp {
            bold: self.bold.or(base.bold),
            italic: self.italic.or(base.italic),
            underline: self.underline.or(base.underline),
            strike: self.strike.or(base.strike),
            hidden: self.hidden.or(base.hidden),
            deleted: self.deleted.or(base.deleted),
            special: self.special.or(base.special),
            ole: self.ole.or(base.ole),
            size: self.size.or(base.size),
            color: self.color.or(base.color),
            font: self.font.or(base.font),
            font_east: self.font_east.or(base.font_east),
            pic: self.pic.or(base.pic),
            istd: self.istd.or(base.istd),
        }
    }
}

/// 문단 속성.
#[derive(Debug, Clone, Default)]
struct Pap {
    istd: u16,
    align: Option<u8>,
    outline: Option<u8>,
    in_table: bool,
    /// 표 깊이(`sprmPItap`)
    depth: u32,
    /// 행 끝 문단(바깥 표 `sprmPFTtp`, 안쪽 표 `sprmPFInnerTtp`)
    row_end: bool,
    inner_row_end: bool,
    inner_cell: bool,
    list: bool,
    /// 문단 앞에서 쪽 나누기(`sprmPFPageBreakBefore`)
    page_break: bool,
    /// 행 정의(행 끝 문단에만)
    row: Option<RowDef>,
}

/// 표 한 행의 정의(`sprmTDefTable`).
#[derive(Debug, Clone, Default)]
struct RowDef {
    /// 칸 경계(twip), 칸 수 + 1
    edges: Vec<i16>,
    /// 칸마다 (가로 병합: 0 없음 / 1 시작 / 2 이어짐, 세로 병합: 0 / 1 시작 / 2 이어짐)
    merges: Vec<(u8, u8)>,
    bordered: bool,
}

/// sprm 하나의 피연산자 길이.
fn operand_len(sprm: u16, data: &[u8], at: usize) -> Option<usize> {
    Some(match (sprm >> 13) & 7 {
        0 | 1 => 1,
        2 | 4 | 5 => 2,
        3 => 4,
        7 => 3,
        _ => {
            // 가변 길이: sprmTDefTable·sprmTDefTable10 은 2바이트 길이(+1 을 뺀다), 그 밖에는 1바이트 길이.
            if sprm == 0xD608 || sprm == 0xD606 {
                let cb = u16_at(data, at)? as usize;
                return Some(2 + cb.saturating_sub(1));
            }
            if sprm == 0xC615 {
                // sprmPChgTabs: cb 가 255 면 따로 센다(드물다). 그대로 1바이트 길이로 본다.
                let cb = *data.get(at)? as usize;
                return Some(1 + cb);
            }
            1 + *data.get(at)? as usize
        }
    })
}

/// grpprl 을 (sprm, 피연산자) 로 나눈다.
fn sprms(data: &[u8]) -> Vec<(u16, &[u8])> {
    let mut out = Vec::new();
    let mut i = 0;
    while i + 2 <= data.len() {
        let Some(sprm) = u16_at(data, i) else { break };
        let Some(len) = operand_len(sprm, data, i + 2) else {
            break;
        };
        let Some(operand) = data.get(i + 2..i + 2 + len) else {
            break;
        };
        out.push((sprm, operand));
        i += 2 + len;
    }
    out
}

/// 켜고 끄기 피연산자: 0 끔, 1 켬, 0x80 스타일 값, 0x81 스타일 값의 반대.
fn toggle(value: u8, base: Option<bool>) -> Option<bool> {
    match value {
        0 => Some(false),
        1 => Some(true),
        0x80 => base,
        0x81 => Some(!base.unwrap_or(false)),
        _ => None,
    }
}

/// Word 기본 16색(ico).
fn ico_color(ico: u8) -> Option<u32> {
    const ICO: [u32; 17] = [
        0, 0x000000, 0x0000FF, 0x00FFFF, 0x00FF00, 0xFF00FF, 0xFF0000, 0xFFFF00, 0xFFFFFF,
        0x000080, 0x008080, 0x008000, 0x800080, 0x800000, 0x808000, 0x808080, 0xC0C0C0,
    ];
    (1..=16).contains(&ico).then(|| ICO[ico as usize])
}

/// 글자 sprm 을 서식에 반영한다. `base` 는 켜고 끄기 0x80·0x81 의 기준(스타일 값).
fn apply_chp(chp: &mut Chp, grpprl: &[u8], base: &Chp) {
    for (sprm, op) in sprms(grpprl) {
        let b = op.first().copied().unwrap_or(0);
        match sprm {
            0x0835 => chp.bold = toggle(b, base.bold),
            0x0836 => chp.italic = toggle(b, base.italic),
            0x0837 | 0x2A53 => chp.strike = toggle(b, base.strike),
            0x083C => chp.hidden = toggle(b, base.hidden),
            0x0800 => chp.deleted = Some(b != 0),
            0x0855 => chp.special = Some(b != 0),
            0x080A => chp.ole = Some(b != 0),
            0x2A3E => chp.underline = Some(b != 0),
            0x4A43 => chp.size = u16_at(op, 0),
            0x2A42 => {
                if let Some(color) = ico_color(b) {
                    chp.color = Some(color);
                } else if b == 0 {
                    chp.color = None;
                }
            }
            0x6870 => {
                // COLORREF 0x00BBGGRR. 맨 윗바이트 0xFF 는 "자동".
                if let Some(cv) = u32_at(op, 0) {
                    chp.color = (cv >> 24 != 0xFF)
                        .then_some(((cv & 0xFF) << 16) | (cv & 0xFF00) | ((cv >> 16) & 0xFF))
                }
            }
            0x4A4F | 0x4A3D => chp.font = u16_at(op, 0),
            0x4A50 => chp.font_east = u16_at(op, 0),
            0x6A03 => chp.pic = u32_at(op, 0),
            0x4A30 => chp.istd = u16_at(op, 0),
            _ => {}
        }
    }
}

/// 문단 sprm 을 문단 속성에 반영한다.
fn apply_pap(pap: &mut Pap, grpprl: &[u8]) {
    for (sprm, op) in sprms(grpprl) {
        let b = op.first().copied().unwrap_or(0);
        match sprm {
            0x2403 | 0x2461 => pap.align = Some(b),
            0x2640 => pap.outline = (b < 9).then_some(b),
            0x2416 => pap.in_table = b != 0,
            0x6649 => pap.depth = u32_at(op, 0).unwrap_or(0),
            0x2417 => pap.row_end = b != 0,
            0x244C => pap.inner_row_end = b != 0,
            0x244B => pap.inner_cell = b != 0,
            0x460B => pap.list = u16_at(op, 0).is_some_and(|v| v != 0),
            0x2407 => pap.page_break = b != 0,
            0xD608 => pap.row = parse_tdef(op),
            0xD605 | 0xD613 => {
                // 표 테두리(sprmTTableBorders80 / sprmTTableBorders): 선 종류가 하나라도 있으면.
                if let Some(row) = pap.row.as_mut() {
                    row.bordered |= op.iter().skip(1).any(|&v| v != 0 && v != 0xFF);
                } else {
                    pap.row = Some(RowDef {
                        bordered: op.iter().skip(1).any(|&v| v != 0 && v != 0xFF),
                        ..RowDef::default()
                    });
                }
            }
            0xD62B => {
                // sprmTVertMerge: itc, 병합(0 없음, 1 이어짐, 3 시작)
                if let (Some(row), Some(&itc), Some(&kind)) =
                    (pap.row.as_mut(), op.get(1), op.get(2))
                {
                    if let Some(merge) = row.merges.get_mut(itc as usize) {
                        merge.1 = match kind {
                            3 => 1,
                            1 => 2,
                            _ => 0,
                        };
                    }
                }
            }
            _ => {}
        }
    }
    if pap.depth == 0 && pap.in_table {
        pap.depth = 1;
    }
}

/// sprmTDefTable 피연산자: cb(u16), itcMac(u8), rgdxaCenter(i16 × itcMac+1), rgTc80(20바이트 × itcMac).
fn parse_tdef(op: &[u8]) -> Option<RowDef> {
    let count = *op.get(2)? as usize;
    let mut edges = Vec::with_capacity(count + 1);
    for i in 0..=count {
        edges.push(i16_at(op, 3 + i * 2)?);
    }
    let tc_start = 3 + (count + 1) * 2;
    let mut merges = Vec::with_capacity(count);
    let mut bordered = false;
    for i in 0..count {
        let at = tc_start + i * 20;
        let flags = u16_at(op, at).unwrap_or(0);
        let horizontal = if flags & 0x0001 != 0 {
            1
        } else if flags & 0x0002 != 0 {
            2
        } else {
            0
        };
        let vertical = if flags & 0x0040 != 0 {
            1
        } else if flags & 0x0020 != 0 {
            2
        } else {
            0
        };
        merges.push((horizontal, vertical));
        // BRC80 4개(위·왼쪽·아래·오른쪽): 둘째 바이트가 선 종류
        for side in 0..4 {
            if let Some(&kind) = op.get(at + 4 + side * 4 + 1) {
                bordered |= kind != 0 && kind != 0xFF;
            }
        }
    }
    Some(RowDef {
        edges,
        merges,
        bordered,
    })
}

// ---- 스타일 ----

#[derive(Debug, Clone, Default)]
struct Style {
    base: Option<u16>,
    /// 기본 제공 스타일 번호(sti). 1~9 가 "제목 1~9".
    sti: u16,
    chp: Vec<u8>,
    pap: Vec<u8>,
}

/// STSH(스타일 표)를 읽는다.
fn parse_styles(table: &[u8], fc: u32, lcb: u32) -> Vec<Option<Style>> {
    let Some(data) = table.get(fc as usize..(fc as usize).saturating_add(lcb as usize)) else {
        return Vec::new();
    };
    let Some(cb_stshi) = u16_at(data, 0).map(usize::from) else {
        return Vec::new();
    };
    let stshi = &data[2.min(data.len())..(2 + cb_stshi).min(data.len())];
    let count = u16_at(stshi, 0).unwrap_or(0) as usize;
    let cb_base = u16_at(stshi, 2).unwrap_or(10) as usize;
    let mut styles = Vec::with_capacity(count);
    let mut i = 2 + cb_stshi;
    for _ in 0..count.min(4096) {
        let Some(cb) = u16_at(data, i).map(usize::from) else {
            break;
        };
        i += 2;
        if cb == 0 {
            styles.push(None);
            continue;
        }
        let Some(std) = data.get(i..i + cb) else {
            break;
        };
        i += cb;
        styles.push(parse_std(std, cb_base));
    }
    styles
}

fn parse_std(std: &[u8], cb_base: usize) -> Option<Style> {
    let w0 = u16_at(std, 0)?;
    let w1 = u16_at(std, 2)?;
    let w2 = u16_at(std, 4)?;
    let sti = w0 & 0x0FFF;
    let kind = w1 & 0x000F; // 1 문단, 2 문자
    let base = w1 >> 4;
    let cupx = w2 & 0x000F;
    // 이름(xstzName): 길이(u16) + UTF-16 + 끝 0
    let mut at = cb_base;
    let name_len = u16_at(std, at)? as usize;
    at += 2 + name_len * 2 + 2;
    let mut upx = Vec::new();
    for _ in 0..cupx {
        let cb = u16_at(std, at)? as usize;
        let body = std.get(at + 2..at + 2 + cb)?;
        upx.push(body);
        at += 2 + cb;
        if at % 2 == 1 {
            at += 1;
        }
    }
    let (pap, chp) = match kind {
        1 => (
            upx.first()
                .map(|p| p.get(2..).unwrap_or(&[]).to_vec())
                .unwrap_or_default(),
            upx.get(1).map(|c| c.to_vec()).unwrap_or_default(),
        ),
        2 => (
            Vec::new(),
            upx.first().map(|c| c.to_vec()).unwrap_or_default(),
        ),
        _ => (Vec::new(), Vec::new()),
    };
    Some(Style {
        base: (base != 0x0FFF).then_some(base),
        sti,
        chp,
        pap,
    })
}

/// 스타일 상속을 푼 (글자 서식, 문단 속성).
fn resolve_style(styles: &[Option<Style>], istd: u16) -> (Chp, Pap) {
    let mut chain = Vec::new();
    let mut next = Some(istd);
    while let Some(id) = next {
        if chain.len() > 16 {
            break;
        }
        let Some(Some(style)) = styles.get(id as usize) else {
            break;
        };
        chain.push(style);
        next = style.base;
    }
    let mut chp = Chp::default();
    let mut pap = Pap::default();
    for style in chain.iter().rev() {
        let base = chp.clone();
        apply_chp(&mut chp, &style.chp, &base);
        apply_pap(&mut pap, &style.pap);
        if (1..=9).contains(&style.sti) {
            pap.outline = Some((style.sti - 1) as u8);
        }
    }
    // 스타일의 표 표시는 문단 자신의 것으로만 본다.
    pap.in_table = false;
    pap.depth = 0;
    pap.row_end = false;
    pap.row = None;
    (chp, pap)
}

// ---- 글꼴 ----

fn parse_fonts(table: &[u8], fc: u32, lcb: u32) -> Vec<String> {
    let Some(data) = table.get(fc as usize..(fc as usize).saturating_add(lcb as usize)) else {
        return Vec::new();
    };
    let count = u16_at(data, 0).unwrap_or(0) as usize;
    let mut fonts = Vec::new();
    let mut i = 4;
    for _ in 0..count.min(4096) {
        let Some(&cb) = data.get(i) else { break };
        let Some(ffn) = data.get(i + 1..i + 1 + cb as usize) else {
            break;
        };
        let name: Vec<u16> = ffn
            .get(39..)
            .unwrap_or(&[])
            .chunks_exact(2)
            .map(|c| u16::from_le_bytes([c[0], c[1]]))
            .take_while(|&c| c != 0)
            .collect();
        fonts.push(String::from_utf16_lossy(&name));
        i += 1 + cb as usize;
    }
    fonts
}

// ---- FKP(서식 페이지) ----

/// (FC 시작, FC 끝, sprm 묶음) 을 FC 순으로.
struct Runs {
    runs: Vec<(u32, u32, Vec<u8>)>,
}

impl Runs {
    fn find(&self, fc: u32) -> Option<&[u8]> {
        let index = self.runs.partition_point(|(start, _, _)| *start <= fc);
        let (start, end, grpprl) = self.runs.get(index.checked_sub(1)?)?;
        (*start <= fc && fc < *end).then_some(grpprl.as_slice())
    }
}

/// PlcBteChpx / PlcBtePapx 를 따라 FKP 를 모두 읽는다.
fn parse_fkps(word: &[u8], table: &[u8], fc: u32, lcb: u32, paragraph: bool) -> Runs {
    let mut runs = Vec::new();
    let plc = table
        .get(fc as usize..(fc as usize).saturating_add(lcb as usize))
        .unwrap_or(&[]);
    // PLC: (n+1) 개 FC, n 개 PnFkp(4바이트)
    let n = plc.len().saturating_sub(4) / 8;
    for k in 0..n {
        let Some(pn) = u32_at(plc, (n + 1) * 4 + k * 4).map(|v| v & 0x003F_FFFF) else {
            continue;
        };
        let Some(page) = word.get(pn as usize * 512..pn as usize * 512 + 512) else {
            continue;
        };
        let count = page[511] as usize;
        for j in 0..count {
            let (Some(start), Some(end)) = (u32_at(page, j * 4), u32_at(page, (j + 1) * 4)) else {
                break;
            };
            let grpprl: Vec<u8> = if paragraph {
                let bx = (count + 1) * 4 + j * 13;
                let offset = page.get(bx).copied().unwrap_or(0) as usize * 2;
                if offset == 0 {
                    Vec::new()
                } else {
                    let cb = page.get(offset).copied().unwrap_or(0) as usize;
                    let (len, data) = if cb == 0 {
                        (
                            page.get(offset + 1).copied().unwrap_or(0) as usize * 2,
                            offset + 2,
                        )
                    } else {
                        (cb * 2 - 1, offset + 1)
                    };
                    // istd(2) + grpprl
                    page.get(data..(data + len).min(512))
                        .unwrap_or(&[])
                        .to_vec()
                }
            } else {
                let offset = page.get((count + 1) * 4 + j).copied().unwrap_or(0) as usize * 2;
                if offset == 0 {
                    Vec::new()
                } else {
                    let cb = page.get(offset).copied().unwrap_or(0) as usize;
                    page.get(offset + 1..(offset + 1 + cb).min(512))
                        .unwrap_or(&[])
                        .to_vec()
                }
            };
            runs.push((start, end, grpprl));
        }
    }
    runs.sort_by_key(|(start, _, _)| *start);
    Runs { runs }
}

// ---- 조각 표(글자) ----

#[derive(Debug, Clone)]
struct Piece {
    cp_start: u32,
    cp_end: u32,
    /// 스트림 안 바이트 위치
    fc: u32,
    compressed: bool,
    /// 조각 sprm(Prc 의 grpprl)
    grpprl: Vec<u8>,
}

/// cp1252 의 0x80~0x9F
fn cp1252(byte: u8) -> char {
    const HIGH: [u16; 32] = [
        0x20AC, 0x81, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021, 0x02C6, 0x2030, 0x0160,
        0x2039, 0x0152, 0x8D, 0x017D, 0x8F, 0x90, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013,
        0x2014, 0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x9D, 0x017E, 0x0178,
    ];
    match byte {
        0x80..=0x9F => char::from_u32(u32::from(HIGH[(byte - 0x80) as usize])).unwrap_or('?'),
        _ => char::from(byte),
    }
}

fn parse_pieces(table: &[u8], fc: u32, lcb: u32) -> Result<Vec<Piece>> {
    let clx = table
        .get(fc as usize..(fc as usize).saturating_add(lcb as usize))
        .ok_or_else(|| invalid("Word piece table out of range"))?;
    let mut prcs: Vec<&[u8]> = Vec::new();
    let mut i = 0;
    while clx.get(i) == Some(&0x01) {
        let cb = u16_at(clx, i + 1).ok_or_else(|| invalid("bad Word piece table"))? as usize;
        prcs.push(clx.get(i + 3..i + 3 + cb).unwrap_or(&[]));
        i += 3 + cb;
    }
    if clx.get(i) != Some(&0x02) {
        return Err(invalid("bad Word piece table"));
    }
    let lcb_pcd = u32_at(clx, i + 1).ok_or_else(|| invalid("bad Word piece table"))? as usize;
    let plc = clx
        .get(i + 5..i + 5 + lcb_pcd)
        .ok_or_else(|| invalid("bad Word piece table"))?;
    let n = plc.len().saturating_sub(4) / 12;
    let mut pieces = Vec::with_capacity(n);
    for k in 0..n {
        let cp_start = u32_at(plc, k * 4).unwrap_or(0);
        let cp_end = u32_at(plc, (k + 1) * 4).unwrap_or(0);
        let pcd = (n + 1) * 4 + k * 8;
        let raw = u32_at(plc, pcd + 2).unwrap_or(0);
        let prm = u16_at(plc, pcd + 6).unwrap_or(0);
        let compressed = raw & 0x4000_0000 != 0;
        let fc = raw & 0x3FFF_FFFF;
        let grpprl = if prm & 1 == 1 {
            prcs.get((prm >> 1) as usize)
                .map(|g| g.to_vec())
                .unwrap_or_default()
        } else {
            Vec::new()
        };
        pieces.push(Piece {
            cp_start,
            cp_end,
            fc: if compressed { fc / 2 } else { fc },
            compressed,
            grpprl,
        });
    }
    Ok(pieces)
}

/// 글자 하나와 그 FC(스트림 위치), 조각 sprm.
struct Text<'a> {
    word: &'a [u8],
    pieces: Vec<Piece>,
}

impl Text<'_> {
    /// cp 범위의 (글자, fc, 조각 번호).
    fn chars(&self, from: u32, to: u32) -> Vec<(char, u32, usize)> {
        let mut out: Vec<(u16, u32, usize)> = Vec::new();
        for (index, piece) in self.pieces.iter().enumerate() {
            let start = piece.cp_start.max(from);
            let end = piece.cp_end.min(to);
            if start >= end {
                continue;
            }
            for cp in start..end {
                let k = cp - piece.cp_start;
                if piece.compressed {
                    let fc = piece.fc + k;
                    if let Some(&b) = self.word.get(fc as usize) {
                        out.push((cp1252(b) as u16, fc, index));
                    }
                } else {
                    let fc = piece.fc + k * 2;
                    if let Some(unit) = u16_at(self.word, fc as usize) {
                        out.push((unit, fc, index));
                    }
                }
            }
        }
        // UTF-16 → 글자. 대리 쌍은 앞 자리에 합친 글자, 뒤 자리에 0(건너뛰는 제어 글자)을 두어 cp 수를 지킨다.
        let mut chars = Vec::with_capacity(out.len());
        let mut i = 0;
        while i < out.len() {
            let (unit, fc, piece) = out[i];
            let next = out.get(i + 1).map(|n| n.0);
            match (unit, next) {
                (0xD800..=0xDBFF, Some(low @ 0xDC00..=0xDFFF)) => {
                    let code =
                        0x10000 + ((u32::from(unit) - 0xD800) << 10) + (u32::from(low) - 0xDC00);
                    chars.push((char::from_u32(code).unwrap_or('\u{FFFD}'), fc, piece));
                    chars.push(('\u{0}', out[i + 1].1, out[i + 1].2));
                    i += 2;
                }
                _ => {
                    chars.push((
                        char::from_u32(u32::from(unit)).unwrap_or('\u{FFFD}'),
                        fc,
                        piece,
                    ));
                    i += 1;
                }
            }
        }
        chars
    }

    /// 대리 쌍(UTF-16)을 고려한 글자들. 짝이 맞지 않으면 대체 글자.
    fn plain(&self, from: u32, to: u32) -> String {
        self.chars(from, to)
            .into_iter()
            .map(|(c, _, _)| c)
            .collect()
    }
}

// ---- 표 만들기 ----

#[derive(Default)]
struct TableBuild {
    rows: Vec<Vec<String>>,
    defs: Vec<Option<RowDef>>,
    /// 지금 행의 칸들
    cells: Vec<String>,
    /// 지금 칸 내용
    cell: String,
}

impl TableBuild {
    /// 행 끝 표시 없이 끝난 칸·행을 닫는다(손상·특이한 문서).
    fn close_open_row(&mut self) {
        if !self.cell.is_empty() {
            let cell = std::mem::take(&mut self.cell);
            self.cells.push(cell);
        }
        if !self.cells.is_empty() {
            let cells = std::mem::take(&mut self.cells);
            self.rows.push(cells);
            self.defs.push(None);
        }
    }

    fn to_html(&self) -> String {
        // 모든 행의 칸 경계를 모아 공통 격자를 만들고, 칸마다 걸치는 격자 칸 수를 colspan 으로 쓴다.
        let mut grid: Vec<i16> = self
            .defs
            .iter()
            .flatten()
            .flat_map(|d| d.edges.iter().copied())
            .collect();
        grid.sort_unstable();
        grid.dedup_by(|a, b| (*a - *b).abs() < 20);
        let bordered =
            self.defs.iter().flatten().any(|d| d.bordered) || self.defs.iter().all(Option::is_none);
        // 칸마다 (시작 격자, 격자 칸 수, 세로 병합)
        let layout: Vec<Vec<(usize, usize, u8, bool)>> = self
            .rows
            .iter()
            .zip(&self.defs)
            .map(|(cells, def)| {
                let mut out = Vec::new();
                let mut hidden_by_merge = vec![false; cells.len()];
                for (i, _) in cells.iter().enumerate() {
                    let (horizontal, vertical) = def
                        .as_ref()
                        .and_then(|d| d.merges.get(i).copied())
                        .unwrap_or((0, 0));
                    let span_of = |i: usize| -> (usize, usize) {
                        match def.as_ref() {
                            Some(d) if d.edges.len() > i + 1 && grid.len() > 1 => {
                                let find = |x: i16| {
                                    grid.iter().position(|g| (g - x).abs() < 20).unwrap_or(0)
                                };
                                let (a, b) = (find(d.edges[i]), find(d.edges[i + 1]));
                                (a.min(b), b.max(a + 1) - a.min(b))
                            }
                            _ => (i, 1),
                        }
                    };
                    let (start, mut span) = span_of(i);
                    if horizontal == 2 {
                        hidden_by_merge[i] = true;
                    } else if horizontal == 1 {
                        // 이어지는 칸들의 폭을 더한다.
                        let mut j = i + 1;
                        while def
                            .as_ref()
                            .and_then(|d| d.merges.get(j))
                            .is_some_and(|m| m.0 == 2)
                        {
                            span += span_of(j).1;
                            j += 1;
                        }
                    }
                    out.push((start, span, vertical, hidden_by_merge[i]));
                }
                out
            })
            .collect();
        let mut html = String::from("<table border=\"1\">");
        for (r, cells) in self.rows.iter().enumerate() {
            html.push_str("<tr>");
            for (i, content) in cells.iter().enumerate() {
                let (start, span, vertical, hidden) = layout[r][i];
                if hidden || vertical == 2 {
                    continue;
                }
                let mut rowspan = 1;
                if vertical == 1 {
                    while layout
                        .get(r + rowspan)
                        .is_some_and(|row| row.iter().any(|(s, _, v, _)| *s == start && *v == 2))
                    {
                        rowspan += 1;
                    }
                }
                html.push_str("<td");
                // 칸 너비(twip → pt). rhwp 는 병합하지 않은 칸의 너비로 열 너비를 정한다.
                let width = self.defs[r]
                    .as_ref()
                    .and_then(|d| Some(f32::from(*d.edges.get(i + 1)? - *d.edges.get(i)?) / 20.0))
                    .filter(|w| *w > 0.0);
                let mut style = String::new();
                if bordered {
                    style.push_str("border:0.5pt solid #000000;");
                }
                if let (Some(width), 1) = (width, span) {
                    style.push_str(&format!("width:{width:.1}pt;"));
                }
                if !style.is_empty() {
                    html.push_str(&format!(" style=\"{style}\""));
                }
                if span > 1 {
                    html.push_str(&format!(" colspan=\"{}\"", span.min(64)));
                }
                if rowspan > 1 {
                    html.push_str(&format!(" rowspan=\"{rowspan}\""));
                }
                html.push('>');
                html.push_str(if content.is_empty() {
                    "<p></p>"
                } else {
                    content
                });
                html.push_str("</td>");
            }
            html.push_str("</tr>");
        }
        html.push_str("</table>");
        html
    }
}

// ---- 본문 ----

fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
}

fn css(chp: &Chp, fonts: &[String]) -> String {
    let mut css = String::new();
    let font = chp
        .font_east
        .or(chp.font)
        .and_then(|i| fonts.get(i as usize))
        .filter(|name| !name.is_empty());
    if let Some(font) = font {
        css.push_str(&format!(
            "font-family:'{}';",
            escape(font).replace('\'', "")
        ));
    }
    if let Some(size) = chp.size.filter(|&s| s > 0) {
        css.push_str(&format!("font-size:{}pt;", f32::from(size) / 2.0));
    }
    if chp.bold == Some(true) {
        css.push_str("font-weight:bold;");
    }
    if chp.italic == Some(true) {
        css.push_str("font-style:italic;");
    }
    if let Some(color) = chp.color.filter(|&c| c != 0) {
        css.push_str(&format!("color:#{color:06X};"));
    }
    let mut decorations = Vec::new();
    if chp.underline == Some(true) {
        decorations.push("underline");
    }
    if chp.strike == Some(true) {
        decorations.push("line-through");
    }
    if !decorations.is_empty() {
        css.push_str(&format!("text-decoration:{};", decorations.join(" ")));
    }
    css
}

/// 읽는 데 필요한 것을 모은 것.
struct Reader<'a> {
    text: Text<'a>,
    data: Option<Vec<u8>>,
    chpx: Runs,
    papx: Runs,
    styles: Vec<Option<Style>>,
    fonts: Vec<String>,
    style_cache: HashMap<u16, (Chp, Pap)>,
    image_budget: usize,
    /// 떠 있는 그리기 개체
    art: Art,
    /// 글상자 이야기마다 cp 범위
    textboxes: Vec<(u32, u32)>,
    /// 구역 나누기 글자 cp → 뒤 구역의 시작 방식(bkc: 0 이어서, 1 새 단, 그 밖은 새 쪽)
    section_breaks: HashMap<u32, u8>,
}

/// 글자처럼 놓은 그림
enum InlinePicture {
    /// `<img>` 로 넣는 비트맵
    Html(String),
    /// 도형 자리로 넣는 그림(메타파일 → SVG)
    Shape(DocxShape),
}

/// 본문(또는 주 이야기) 한 부분을 HTML 로.
struct Output {
    html: String,
    headings: Vec<DocxHeading>,
    images: usize,
    dropped: usize,
    /// 본문에서 만난 각주·미주 표시(cp 순서)
    notes: Vec<(u32, char)>,
    /// 본문 도형([`SHAPE_MARK`] 순서)
    shapes: Vec<DocxShape>,
}

impl Reader<'_> {
    fn style(&mut self, istd: u16) -> (Chp, Pap) {
        if let Some(found) = self.style_cache.get(&istd) {
            return found.clone();
        }
        let resolved = resolve_style(&self.styles, istd);
        self.style_cache.insert(istd, resolved.clone());
        resolved
    }

    fn pap_at(&mut self, fc: u32) -> (Pap, Chp) {
        let grpprl = self.papx.find(fc).map(<[u8]>::to_vec).unwrap_or_default();
        let istd = u16_at(&grpprl, 0).unwrap_or(0);
        let (style_chp, mut pap) = self.style(istd);
        pap.istd = istd;
        let own = grpprl.get(2..).unwrap_or(&[]);
        apply_pap(&mut pap, own);
        // sprmPHugePapx: 속성이 커서(큰 표 행 정의 등) Data 스트림에 따로 있다. 길이(u16) + grpprl.
        for (sprm, op) in sprms(own) {
            if sprm != 0x6646 {
                continue;
            }
            let huge = u32_at(op, 0).and_then(|at| {
                let data = self.data.as_ref()?;
                let len = u16_at(data, at as usize)? as usize;
                data.get(at as usize + 2..at as usize + 2 + len)
                    .map(<[u8]>::to_vec)
            });
            if let Some(huge) = huge {
                apply_pap(&mut pap, &huge);
            }
        }
        (pap, style_chp)
    }

    fn chp_at(&mut self, fc: u32, piece: usize, style: &Chp) -> Chp {
        let mut chp = style.clone();
        if let Some(grpprl) = self.chpx.find(fc).map(<[u8]>::to_vec) {
            // 문자 스타일을 먼저 입힌다.
            let mut direct = Chp::default();
            apply_chp(&mut direct, &grpprl, style);
            if let Some(istd) = direct.istd.filter(|&i| i != 10) {
                let (char_style, _) = self.style(istd);
                chp = char_style.over(&chp);
            }
            let base = chp.clone();
            apply_chp(&mut chp, &grpprl, &base);
        }
        if let Some(grpprl) = self.text.pieces.get(piece).map(|p| p.grpprl.clone()) {
            if !grpprl.is_empty() {
                let base = chp.clone();
                apply_chp(&mut chp, &grpprl, &base);
            }
        }
        chp
    }

    /// 글자처럼 놓은 그림(PICF + OfficeArt BLIP)을 `<img>` 로.
    fn picture(&mut self, offset: u32) -> Option<InlinePicture> {
        let data = self.data.as_ref()?;
        let at = offset as usize;
        let lcb = u32_at(data, at)? as usize;
        let cb_header = u16_at(data, at + 4)? as usize;
        let block = data.get(at..at.checked_add(lcb)?)?;
        let mm = u16_at(block, 6)?;
        let dxa_goal = u16_at(block, 28)? as f64;
        let dya_goal = u16_at(block, 30)? as f64;
        let mx = u16_at(block, 32).filter(|&v| v > 0).unwrap_or(1000) as f64;
        let my = u16_at(block, 34).filter(|&v| v > 0).unwrap_or(1000) as f64;
        let mut start = cb_header;
        if mm == 0x66 {
            // 그림 이름(cchPicName + 이름)
            let len = *block.get(start)? as usize;
            start += 1 + len;
        }
        let picture = find_picture(block.get(start..)?)?;
        if picture.len() > MAX_IMAGE_BYTES || picture.len() > self.image_budget {
            return None;
        }
        self.image_budget -= picture.len();
        let (width_twip, height_twip) = (dxa_goal * mx / 1000.0, dya_goal * my / 1000.0);
        match picture {
            Picture::Raster(image) => {
                let mime = image_mime(&image)?;
                // twip → px(96 DPI): 1px = 15 twip
                let width = (width_twip / 15.0).round().clamp(1.0, 20000.0);
                let height = (height_twip / 15.0).round().clamp(1.0, 20000.0);
                Some(InlinePicture::Html(format!(
                    "<img src=\"data:{mime};base64,{}\" width=\"{width}\" height=\"{height}\">",
                    base64::engine::general_purpose::STANDARD.encode(&image)
                )))
            }
            Picture::Svg(svg) => {
                let emu = |twip: f64| (twip.clamp(15.0, 20000.0 * 15.0) * 635.0).round() as i64;
                Some(InlinePicture::Shape(DocxShape {
                    kind: ShapeKind::Rectangle,
                    width: emu(width_twip),
                    height: emu(height_twip),
                    inline: true,
                    horizontal: (Relative::Column, 0),
                    vertical: (Relative::Paragraph, 0),
                    wrap: Wrap::InFront,
                    fill: None,
                    line: None,
                    line_width: 0,
                    flip_h: false,
                    flip_v: false,
                    text: Vec::new(),
                    html: None,
                    image: Some(ShapeImage::Svg(svg)),
                    insets: crate::shapes::DEFAULT_INSETS,
                    text_anchor: 0,
                    pending: None,
                }))
            }
        }
    }

    /// 글상자 이야기 하나의 문단
    fn textbox(&self, index: usize) -> Vec<String> {
        match self.textboxes.get(index) {
            Some(&(a, b)) => self.story_paragraphs(a, b),
            None => Vec::new(),
        }
    }

    /// cp 범위를 HTML 로. `notes`: 이 범위 안의 각주·미주 참조 cp → 표시 글자.
    fn story(&mut self, from: u32, to: u32, refs: &HashMap<u32, char>) -> Output {
        let mut out = Output {
            html: String::new(),
            headings: Vec::new(),
            images: 0,
            dropped: 0,
            notes: Vec::new(),
            shapes: Vec::new(),
        };
        let chars = self.text.chars(from, to);
        let mut tables: Vec<TableBuild> = Vec::new();
        let mut para = String::new();
        let mut plain = String::new();
        let mut run = String::new();
        let mut run_css = String::new();
        // 필드: 단계마다 (지시문 안인가)
        let mut fields: Vec<bool> = Vec::new();
        // 앞 문단이 쪽·구역 나누기(0x0C)로 끝나 뒤 문단 앞에 넣을 표시(쪽 또는 단 나누기)
        let mut break_pending: Option<char> = None;
        let flush = |para: &mut String, run: &mut String, run_css: &str| {
            if run.is_empty() {
                return;
            }
            if run_css.is_empty() {
                para.push_str(run);
            } else {
                para.push_str(&format!("<span style=\"{run_css}\">{run}</span>"));
            }
            run.clear();
        };
        let mut cp = from;
        let mut i = 0;
        // 문단마다: 끝 글자 FC 로 문단 속성을 찾는다. 먼저 문단 경계를 찾아 스타일을 미리 안다.
        while i < chars.len() {
            // 이 문단의 끝(0x0D·0x07·0x0C)을 찾는다.
            let mut end = i;
            while end < chars.len() && !matches!(chars[end].0, '\r' | '\u{7}' | '\u{C}') {
                end += 1;
            }
            let end_fc = chars
                .get(end)
                .map(|c| c.1)
                .unwrap_or_else(|| chars[end - 1].1);
            let (pap, style_chp) = self.pap_at(end_fc);
            for &(ch, fc, piece) in &chars[i..end.min(chars.len())] {
                let this_cp = cp;
                cp += 1;
                match ch {
                    '\u{13}' => {
                        fields.push(true);
                        continue;
                    }
                    '\u{14}' => {
                        if let Some(top) = fields.last_mut() {
                            *top = false;
                        }
                        continue;
                    }
                    '\u{15}' => {
                        fields.pop();
                        continue;
                    }
                    _ => {}
                }
                if fields.iter().any(|&instruction| instruction) {
                    continue;
                }
                let chp = self.chp_at(fc, piece, &style_chp);
                if chp.hidden == Some(true) || chp.deleted == Some(true) {
                    continue;
                }
                if let Some(mark) = refs.get(&this_cp) {
                    flush(&mut para, &mut run, &run_css);
                    para.push(*mark);
                    out.notes.push((this_cp, *mark));
                    continue;
                }
                let special = chp.special == Some(true);
                match ch {
                    '\u{1}' if special => {
                        flush(&mut para, &mut run, &run_css);
                        // OLE 개체도 Data 스트림에 미리보기 그림(대개 EMF)이 있다.
                        match chp.pic.and_then(|offset| self.picture(offset)) {
                            Some(InlinePicture::Html(img)) => {
                                para.push_str(&img);
                                out.images += 1;
                            }
                            Some(InlinePicture::Shape(shape)) if pap.depth == 0 => {
                                para.push(SHAPE_MARK);
                                out.shapes.push(shape);
                                out.images += 1;
                            }
                            // 표 안 도형 자리는 rhwp 에 넣을 곳이 없다.
                            _ => out.dropped += 1,
                        }
                        continue;
                    }
                    // 떠 있는 도형·그림 자리
                    '\u{8}' if special => {
                        flush(&mut para, &mut run, &run_css);
                        let shapes = self.art.shapes_at(this_cp, &mut |k| self.textbox(k));
                        if shapes.is_empty() {
                            out.dropped += 1;
                        } else if pap.depth == 0 {
                            for shape in shapes {
                                para.push(SHAPE_MARK);
                                out.shapes.push(shape);
                            }
                        } else {
                            // 표 안: 글상자 글자만 남긴다.
                            for shape in shapes {
                                for text in shape.text.iter().filter(|t| !t.trim().is_empty()) {
                                    para.push_str(&escape(text));
                                    para.push(' ');
                                }
                                out.dropped += 1;
                            }
                        }
                        continue;
                    }
                    '\t' => {
                        flush(&mut para, &mut run, &run_css);
                        para.push('\t');
                        plain.push('\t');
                        continue;
                    }
                    '\u{B}' => {
                        flush(&mut para, &mut run, &run_css);
                        para.push_str("<br>");
                        plain.push(' ');
                        continue;
                    }
                    '\u{1E}' => {
                        // 줄 안 나눔 하이픈
                        let style = css(&chp, &self.fonts);
                        if style != run_css {
                            flush(&mut para, &mut run, &run_css);
                            run_css = style;
                        }
                        run.push('-');
                        plain.push('-');
                        continue;
                    }
                    '\u{1F}' => continue,
                    '\u{E}' if pap.depth == 0 && tables.is_empty() => {
                        // 단 나누기: 문단 맨 앞이면 이 문단 앞에서, 아니면 끊고 뒤 문단 앞에서.
                        flush(&mut para, &mut run, &run_css);
                        if !para.is_empty() {
                            para.push_str("</p><p>");
                        }
                        para.push(COLUMN_BREAK_MARK);
                        continue;
                    }
                    c if (c as u32) < 0x20 => continue,
                    _ => {}
                }
                let style = css(&chp, &self.fonts);
                if style != run_css {
                    flush(&mut para, &mut run, &run_css);
                    run_css = style;
                }
                run.push_str(&escape(&ch.to_string()));
                plain.push(ch);
            }
            flush(&mut para, &mut run, &run_css);
            run_css.clear();
            // 끝 글자
            let terminator = chars.get(end).map(|c| c.0);
            if end < chars.len() {
                cp += 1;
            }
            let align = match pap.align {
                Some(1) => " style=\"text-align:center\"",
                Some(2) => " style=\"text-align:right\"",
                Some(3..=5) => " style=\"text-align:justify\"",
                _ => "",
            };
            let label = if pap.list { "• " } else { "" };
            let depth_now = pap.depth as usize;
            let at_top = depth_now == 0 && tables.is_empty();
            let mark = match break_pending {
                _ if !at_top => String::new(),
                _ if pap.page_break => PAGE_BREAK_MARK.to_string(),
                Some(mark) => mark.to_string(),
                None => String::new(),
            };
            if at_top {
                break_pending = None;
            }
            if terminator == Some('\u{C}') {
                // 구역 나누기면 뒤 구역의 시작 방식을 따른다(이어서는 나누지 않는다).
                break_pending = match self.section_breaks.get(&(cp - 1)) {
                    Some(0) => None,
                    Some(1) => Some(COLUMN_BREAK_MARK),
                    _ => Some(PAGE_BREAK_MARK),
                };
            }
            let paragraph = format!("<p{align}>{mark}{label}{para}</p>");
            let depth = (pap.depth as usize).min(MAX_TABLE_DEPTH);
            // 표 깊이를 맞춘다(새 표를 열거나 끝난 표를 닫는다).
            while tables.len() < depth {
                tables.push(TableBuild::default());
            }
            while tables.len() > depth {
                if let Some(mut done) = tables.pop() {
                    done.close_open_row();
                    let html = done.to_html();
                    match tables.last_mut() {
                        Some(outer) => outer.cell.push_str(&html),
                        None => out.html.push_str(&html),
                    }
                }
            }
            let row_end = if depth > 1 {
                pap.inner_row_end
            } else {
                pap.row_end
            };
            let cell_end = if depth > 1 {
                pap.inner_cell
            } else {
                terminator == Some('\u{7}') && !pap.row_end
            };
            match tables.last_mut() {
                Some(table) if row_end => {
                    // 행 끝 표시 문단: 칸들을 행으로 닫는다.
                    let cells = std::mem::take(&mut table.cells);
                    table.rows.push(cells);
                    table.defs.push(pap.row.clone());
                }
                Some(table) => {
                    table.cell.push_str(&paragraph);
                    if cell_end {
                        let cell = std::mem::take(&mut table.cell);
                        table.cells.push(cell);
                    }
                }
                None => {
                    out.html.push_str(&paragraph);
                    let text = plain.trim();
                    if let (Some(level), false) = (pap.outline, text.is_empty()) {
                        out.headings.push(DocxHeading {
                            level: level + 1,
                            text: text.to_owned(),
                        });
                    }
                }
            }
            para.clear();
            plain.clear();
            i = end + 1;
        }
        while let Some(mut done) = tables.pop() {
            done.close_open_row();
            let html = done.to_html();
            match tables.last_mut() {
                Some(outer) => outer.cell.push_str(&html),
                None => out.html.push_str(&html),
            }
        }
        out
    }

    /// 주·머리말 이야기 하나의 문단 글자들(끝 문단 표시는 뺀다).
    fn story_paragraphs(&self, from: u32, to: u32) -> Vec<String> {
        let text = self.text.plain(from, to);
        let mut paragraphs: Vec<String> = text
            .split(['\r', '\u{7}', '\u{C}'])
            .map(|p| {
                let mut out = String::new();
                let mut depth = 0usize;
                let mut instruction = false;
                for c in p.chars() {
                    match c {
                        '\u{13}' => {
                            depth += 1;
                            instruction = true;
                        }
                        '\u{14}' => instruction = false,
                        '\u{15}' => {
                            depth = depth.saturating_sub(1);
                            instruction = false;
                        }
                        _ if instruction && depth > 0 => {}
                        '\t' => out.push('\t'),
                        '\u{B}' => out.push(' '),
                        c if (c as u32) < 0x20 => {}
                        c => out.push(c),
                    }
                }
                out
            })
            .collect();
        while paragraphs.last().is_some_and(|p| p.trim().is_empty()) {
            paragraphs.pop();
        }
        paragraphs
    }
}

/// PLC 의 cp 들(n+1 개)과 n 개의 자료(크기 `size`).
fn plc_cps(table: &[u8], fc: u32, lcb: u32, size: usize) -> Vec<u32> {
    let Some(data) = table.get(fc as usize..(fc as usize).saturating_add(lcb as usize)) else {
        return Vec::new();
    };
    if data.len() < 4 {
        return Vec::new();
    }
    let n = (data.len() - 4) / (4 + size);
    (0..=n).filter_map(|i| u32_at(data, i * 4)).collect()
}

pub(crate) fn dib_to_bmp(dib: &[u8]) -> Vec<u8> {
    let header = u32_at(dib, 0).unwrap_or(40);
    let bits = u16_at(dib, 14).unwrap_or(24);
    let colors = u32_at(dib, 32).unwrap_or(0);
    let palette = if bits <= 8 {
        if colors == 0 { 1u32 << bits } else { colors }
    } else {
        0
    } * 4;
    let offset = 14 + header + palette;
    let mut bmp = Vec::with_capacity(dib.len() + 14);
    bmp.extend_from_slice(b"BM");
    bmp.extend_from_slice(&((dib.len() + 14) as u32).to_le_bytes());
    bmp.extend_from_slice(&[0, 0, 0, 0]);
    bmp.extend_from_slice(&offset.to_le_bytes());
    bmp.extend_from_slice(dib);
    bmp
}

/// .doc 바이트를 rhwp 가 붙여넣을 수 있는 HTML 과 머리말·각주 정보로 바꾼다.
/// 암호 걸린 문서는 `PasswordRequired` — [`doc_to_html_with_password`] 를 쓴다.
pub fn doc_to_html(bytes: &[u8]) -> Result<(String, DocxReadReport)> {
    doc_to_html_with_password(bytes, None)
}

/// [`doc_to_html`] 과 같고, 암호 걸린 문서(RC4·RC4 CryptoAPI·XOR 난독화)는 `password` 로 푼다.
/// 암호가 없거나 틀리면 `PasswordRequired`.
pub fn doc_to_html_with_password(
    bytes: &[u8],
    password: Option<&str>,
) -> Result<(String, DocxReadReport)> {
    let mut file =
        cfb::CompoundFile::open(Cursor::new(bytes)).map_err(|_| invalid("not a compound file"))?;
    let mut word = read_stream(&mut file, "/WordDocument")?
        .ok_or_else(|| Error::new(ErrorCode::UnsupportedFormat, "not a Word document"))?;
    if matches!(u16_at(&word, 0), Some(0xA5DC | 0xA699))
        && matches!(u16_at(&word, 2), Some(0x65..=0x68))
    {
        // Word 6.0·95: 글과 문단만 읽는다([`crate::doc6`])
        return crate::doc6::to_html(&word);
    }
    if u16_at(&word, 0) != Some(0xA5EC) {
        return Err(Error::new(
            ErrorCode::UnsupportedFormat,
            "not a Word 97-2003 document",
        ));
    }
    let nfib = u16_at(&word, 2).unwrap_or(0);
    if nfib < 0x00C1 {
        // Word 6·95 이전 형식
        return Err(Error::new(
            ErrorCode::UnsupportedFormat,
            "Word 95 and older documents are not supported",
        ));
    }
    let flags = u16_at(&word, 0x0A).unwrap_or(0);
    let table_name = if flags & 0x0200 != 0 {
        "/1Table"
    } else {
        "/0Table"
    };
    let mut table = read_stream(&mut file, table_name)?
        .ok_or_else(|| invalid("Word table stream is missing"))?;
    let mut data = read_stream(&mut file, "/Data")?;
    if flags & 0x0100 != 0 {
        if flags & 0x8000 != 0 {
            // XOR 난독화(옛 Word 방식 암호): FibBase lKey(0x0E)가 암호 검증값
            let l_key = u32_at(&word, 0x0E).unwrap_or(0);
            crate::doc_crypt::deobfuscate(password, l_key, &mut word, &mut table, data.as_mut())?;
        } else {
            crate::doc_crypt::decrypt(password, &mut word, &mut table, data.as_mut())?;
        }
    }
    let word = word;
    let table = table;

    // FIB: FibBase(32) + csw + FibRgW97 + cslw + FibRgLw97 + cbRgFcLcb + FibRgFcLcb
    let csw = u16_at(&word, 32).unwrap_or(0) as usize;
    let lw = 34 + csw * 2;
    let cslw = u16_at(&word, lw).unwrap_or(0) as usize;
    let lw_start = lw + 2;
    let lw_value = |i: usize| -> u32 {
        if i < cslw {
            u32_at(&word, lw_start + i * 4).unwrap_or(0)
        } else {
            0
        }
    };
    let fc_start = lw_start + cslw * 4;
    let count = u16_at(&word, fc_start).unwrap_or(0) as usize;
    let pair = |i: usize| -> (u32, u32) {
        if i < count {
            let at = fc_start + 2 + i * 8;
            (
                u32_at(&word, at).unwrap_or(0),
                u32_at(&word, at + 4).unwrap_or(0),
            )
        } else {
            (0, 0)
        }
    };
    let ccp_text = lw_value(3);
    let ccp_ftn = lw_value(4);
    let ccp_hdd = lw_value(5);
    let ccp_mcr = lw_value(6);
    let ccp_atn = lw_value(7);
    if ccp_text > MAX_CHARS {
        return Err(Error::new(
            ErrorCode::LimitExceeded,
            "Word document is too large",
        ));
    }

    let (fc_clx, lcb_clx) = pair(33);
    let pieces = parse_pieces(&table, fc_clx, lcb_clx)?;
    let (fc_sty, lcb_sty) = pair(1);
    let (fc_chp, lcb_chp) = pair(12);
    let (fc_pap, lcb_pap) = pair(13);
    let (fc_ffn, lcb_ffn) = pair(15);
    let mut reader = Reader {
        chpx: parse_fkps(&word, &table, fc_chp, lcb_chp, false),
        papx: parse_fkps(&word, &table, fc_pap, lcb_pap, true),
        styles: parse_styles(&table, fc_sty, lcb_sty),
        fonts: parse_fonts(&table, fc_ffn, lcb_ffn),
        text: Text {
            word: &word,
            pieces,
        },
        data,
        style_cache: HashMap::new(),
        image_budget: MAX_TOTAL_IMAGE_BYTES,
        art: Art::parse(&word, &table, pair(40), pair(50)),
        textboxes: Vec::new(),
        section_breaks: section_breaks(&word, &table, pair(6)),
    };

    let mut report = DocxReadReport::default();
    // 각주·미주: 본문 참조 cp 와 주 글자 범위
    let ftn_start = ccp_text;
    let hdd_start = ftn_start + ccp_ftn;
    let atn_start = hdd_start + ccp_hdd + ccp_mcr;
    let edn_start = atn_start + ccp_atn;
    let mut refs: HashMap<u32, char> = HashMap::new();
    let mut note_text: HashMap<u32, Vec<String>> = HashMap::new();
    for (ref_index, txt_index, start, mark) in [
        (2usize, 3usize, ftn_start, FOOTNOTE_MARK),
        (46, 47, edn_start, ENDNOTE_MARK),
    ] {
        let (fc_ref, lcb_ref) = pair(ref_index);
        let (fc_txt, lcb_txt) = pair(txt_index);
        let ref_cps = plc_cps(&table, fc_ref, lcb_ref, 2);
        let txt_cps = plc_cps(&table, fc_txt, lcb_txt, 0);
        for (k, cp) in ref_cps
            .iter()
            .take(ref_cps.len().saturating_sub(1))
            .enumerate()
        {
            let (Some(&a), Some(&b)) = (txt_cps.get(k), txt_cps.get(k + 1)) else {
                continue;
            };
            refs.insert(*cp, mark);
            note_text.insert(*cp, reader.story_paragraphs(start + a, start + b));
        }
    }

    // 글상자 이야기: 미주 다음. PlcftxbxTxt = cp 목록 + FTXBXS(22바이트)
    let txbx_start = edn_start + lw_value(8);
    let (fc_txbx, lcb_txbx) = pair(56);
    let txbx_cps = plc_cps(&table, fc_txbx, lcb_txbx, 22);
    reader.textboxes = txbx_cps
        .windows(2)
        .map(|w| (txbx_start + w[0], txbx_start + w[1].max(w[0])))
        .collect();

    let body = reader.story(0, ccp_text, &refs);
    report.images = body.images;
    report.shapes = body.shapes;
    report.dropped_drawings = body.dropped;
    report.headings = body.headings;
    for (cp, mark) in &body.notes {
        let text = note_text.remove(cp).unwrap_or_default();
        if *mark == FOOTNOTE_MARK {
            report.footnotes.push(text);
        } else {
            report.endnotes.push(text);
        }
    }

    // 머리말·꼬리말: 첫 구역 이야기(짝수 머리말, 홀수 머리말, 짝수 꼬리말, 홀수 꼬리말, 첫 쪽 머리말, 첫 쪽 꼬리말)
    let (fc_hdd, lcb_hdd) = pair(11);
    let hdd = plc_cps(&table, fc_hdd, lcb_hdd, 0);
    let story = |k: usize| -> Option<(u32, u32)> {
        let (a, b) = (*hdd.get(k)?, *hdd.get(k + 1)?);
        (b > a).then_some((hdd_start + a, hdd_start + b))
    };
    let title_page = title_page(&section_sprms(&word, &table, pair(6), 0));
    // rhwp 문서는 구역 하나라서 글이 가장 긴 구역의 쪽 설정을 쓴다(표지 구역만 여백이 큰 문서 등).
    let longest = longest_section(&table, pair(6));
    report.page = page_setup(&section_sprms(&word, &table, pair(6), longest));
    let even = story(6).map(|(a, b)| reader.story_paragraphs(a, b));
    let has_even = even.as_ref().is_some_and(|p| !p.is_empty());
    for (k, is_header, kind) in [
        (6usize, true, 1u8),
        (7, true, 0),
        (8, false, 1),
        (9, false, 0),
        (10, true, 9),
        (11, false, 9),
    ] {
        let Some((a, b)) = story(k) else { continue };
        let paragraphs = reader.story_paragraphs(a, b);
        if paragraphs.iter().all(|p| p.trim().is_empty()) {
            continue;
        }
        let apply_to = match kind {
            1 => 1,
            0 if has_even => 2,
            0 => 0,
            _ => {
                if title_page {
                    report.dropped_notes += 1;
                }
                continue;
            }
        };
        let target = if is_header {
            &mut report.headers
        } else {
            &mut report.footers
        };
        target.push(HeaderFooterText {
            section: 0,
            apply_to,
            paragraphs,
            html: None,
            ..HeaderFooterText::default()
        });
    }
    if title_page {
        report.hide_first_page = (!report.headers.is_empty(), !report.footers.is_empty());
    }
    Ok((format!("<html><body>{}</body></html>", body.html), report))
}

/// 글자 범위(CP)가 가장 긴 구역 번호(같으면 앞 구역).
fn longest_section(table: &[u8], (fc, lcb): (u32, u32)) -> usize {
    let Some(plc) = table.get(fc as usize..(fc as usize).saturating_add(lcb as usize)) else {
        return 0;
    };
    if plc.len() < 4 + 4 + 12 {
        return 0;
    }
    let n = (plc.len() - 4) / 16;
    (0..n)
        .map(|i| {
            let (a, b) = (u32_at(plc, i * 4), u32_at(plc, (i + 1) * 4));
            (i, b.zip(a).map_or(0, |(b, a)| b.saturating_sub(a)))
        })
        .rev()
        .max_by_key(|&(_, len)| len)
        .map_or(0, |(i, _)| i)
}

/// 구역 나누기 글자(앞 구역의 마지막 cp) → 뒤 구역의 시작 방식(sprmSBkc, 기본값 2 = 새 쪽).
fn section_breaks(word: &[u8], table: &[u8], (fc, lcb): (u32, u32)) -> HashMap<u32, u8> {
    let cps = plc_cps(table, fc, lcb, 12);
    let mut breaks = HashMap::new();
    let last = cps.len().saturating_sub(1);
    for (index, &start) in cps.iter().enumerate().take(last).skip(1) {
        let bkc = section_sprms(word, table, (fc, lcb), index)
            .iter()
            .rev()
            .find(|(sprm, _)| *sprm == 0x3009)
            .and_then(|(_, op)| op.first().copied())
            .unwrap_or(2);
        if let Some(cp) = start.checked_sub(1) {
            breaks.insert(cp, bkc);
        }
    }
    breaks
}

/// `index` 번째 구역의 구역 속성(SEPX) sprm 목록. 없으면 빈 목록(모두 기본값).
fn section_sprms(
    word: &[u8],
    table: &[u8],
    (fc, lcb): (u32, u32),
    index: usize,
) -> Vec<(u16, Vec<u8>)> {
    let Some(plc) = table.get(fc as usize..(fc as usize).saturating_add(lcb as usize)) else {
        return Vec::new();
    };
    if plc.len() < 4 + 4 + 12 {
        return Vec::new();
    }
    let n = (plc.len() - 4) / 16;
    if index >= n {
        return Vec::new();
    }
    // SED: fn(2) fcSepx(4) fnMpr(2) fcMpr(4)
    let Some(fc_sepx) = u32_at(plc, (n + 1) * 4 + index * 12 + 2) else {
        return Vec::new();
    };
    if fc_sepx == 0xFFFF_FFFF {
        return Vec::new();
    }
    let Some(cb) = u16_at(word, fc_sepx as usize).map(usize::from) else {
        return Vec::new();
    };
    let Some(grpprl) = word.get(fc_sepx as usize + 2..fc_sepx as usize + 2 + cb) else {
        return Vec::new();
    };
    sprms(grpprl)
        .into_iter()
        .map(|(sprm, op)| (sprm, op.to_vec()))
        .collect()
}

/// 첫 구역이 "첫 쪽 다르게"(sprmSFTitlePage)인가.
fn title_page(sep: &[(u16, Vec<u8>)]) -> bool {
    sep.iter()
        .any(|(sprm, op)| *sprm == 0x300A && op.first().is_some_and(|&b| b != 0))
}

/// 구역의 쪽 크기·여백·다단. 정하지 않은 값은 Word 97 기본값(Letter, 좌우 1.25 인치, 위아래 1 인치).
fn page_setup(sep: &[(u16, Vec<u8>)]) -> Option<crate::read::PageSetup> {
    let mut page = crate::read::WordPage {
        width: 12240,
        height: 15840,
        top: 1440,
        bottom: 1440,
        left: 1800,
        right: 1800,
        header: 720,
        footer: 720,
        gutter: 0,
        columns: 1,
        column_gap: 720,
    };
    for (sprm, op) in sep {
        let unsigned = || {
            op.get(..2)
                .map(|b| i64::from(u16::from_le_bytes([b[0], b[1]])))
        };
        let signed = || {
            op.get(..2)
                .map(|b| i64::from(i16::from_le_bytes([b[0], b[1]])))
        };
        let value = match sprm {
            0xB01F | 0xB020 | 0xB021 | 0xB022 | 0xB017 | 0xB018 | 0xB025 | 0x500B => unsigned(),
            0x9023 | 0x9024 | 0x900C => signed(),
            _ => None,
        };
        let Some(value) = value else { continue };
        match sprm {
            0xB01F => page.width = value,
            0xB020 => page.height = value,
            0xB021 => page.left = value,
            0xB022 => page.right = value,
            0x9023 => page.top = value,
            0x9024 => page.bottom = value,
            0xB017 => page.header = value,
            0xB018 => page.footer = value,
            0xB025 => page.gutter = value,
            // 단 수 - 1
            0x500B => page.columns = value + 1,
            0x900C => page.column_gap = value,
            _ => {}
        }
    }
    page.to_setup()
}
