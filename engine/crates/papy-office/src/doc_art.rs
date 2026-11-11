//! Word 97–2003 그리기 개체(OfficeArt): 떠 있는 도형·글상자·그림, 메타파일(EMF·WMF) 그림.
//!
//! - 본문의 떠 있는 개체 자리(0x08)는 `PlcfSpa`(본문 cp → FSPA: 개체 id·자리·배치)로 찾고,
//!   개체 모양·채우기·선·글상자·그림은 Table 스트림의 OfficeArt 내용(`fcDggInfo`)에서 읽는다.
//! - 그림 저장소(BStore)의 그림은 FBSE 안에 있거나 WordDocument 스트림의 `foDelay` 자리에 있다.
//! - EMF·WMF 는 rhwp 변환기로 SVG 를 만들어 넣는다(OLE 개체의 미리보기 그림도 대개 EMF 다).

use std::collections::HashMap;
use std::io::Read;

use crate::shapes::{DocxShape, Relative, ShapeImage, ShapeKind, Wrap};

/// 그림 하나(압축을 푼 메타파일 포함) 최대 크기
const MAX_PICTURE_BYTES: usize = 32 * 1024 * 1024;
/// 묶음 도형 안으로 들어가는 최대 깊이
const MAX_DEPTH: usize = 8;
/// twip → EMU
const EMU_PER_TWIP: i64 = 635;

fn u16_at(b: &[u8], i: usize) -> Option<u16> {
    Some(u16::from_le_bytes(b.get(i..i + 2)?.try_into().ok()?))
}
fn u32_at(b: &[u8], i: usize) -> Option<u32> {
    Some(u32::from_le_bytes(b.get(i..i + 4)?.try_into().ok()?))
}
fn i32_at(b: &[u8], i: usize) -> Option<i32> {
    Some(i32::from_le_bytes(b.get(i..i + 4)?.try_into().ok()?))
}

/// 그림 내용
#[derive(Debug, Clone)]
pub(crate) enum Picture {
    /// PNG·JPEG·GIF·BMP 파일 바이트
    Raster(Vec<u8>),
    /// 메타파일을 바꾼 SVG
    Svg(String),
}

impl Picture {
    pub(crate) fn len(&self) -> usize {
        match self {
            Picture::Raster(bytes) => bytes.len(),
            Picture::Svg(svg) => svg.len(),
        }
    }

    pub(crate) fn into_image(self) -> ShapeImage {
        match self {
            Picture::Raster(bytes) => ShapeImage::Raster(bytes),
            Picture::Svg(svg) => ShapeImage::Svg(svg),
        }
    }
}

/// OfficeArt 레코드 머리: (버전·인스턴스, 종류, 몸 범위)
fn record(data: &[u8], at: usize) -> Option<(u16, u16, std::ops::Range<usize>)> {
    let ver_inst = u16_at(data, at)?;
    let kind = u16_at(data, at + 2)?;
    let len = u32_at(data, at + 4)? as usize;
    let start = at + 8;
    let end = start.saturating_add(len).min(data.len());
    Some((ver_inst, kind, start..end))
}

/// 레코드들을 차례로 돈다.
fn records(data: &[u8]) -> impl Iterator<Item = (u16, u16, &[u8])> {
    let mut at = 0usize;
    std::iter::from_fn(move || {
        let (ver_inst, kind, range) = record(data, at)?;
        at = range.end.max(at + 8);
        Some((ver_inst, kind, &data[range]))
    })
}

/// OfficeArt BLIP 레코드(또는 그것을 품은 묶음·FBSE)에서 그림을 꺼낸다.
pub(crate) fn find_picture(data: &[u8]) -> Option<Picture> {
    find_picture_at(data, 0)
}

fn find_picture_at(data: &[u8], depth: usize) -> Option<Picture> {
    if depth > MAX_DEPTH {
        return None;
    }
    for (ver_inst, kind, body) in records(data) {
        if ver_inst & 0x000F == 0x000F {
            if let Some(found) = find_picture_at(body, depth + 1) {
                return Some(found);
            }
        } else if kind == 0xF007 {
            // OfficeArtFBSE: 36바이트 머리 + 이름 + 안에 BLIP 레코드
            let name_len = *body.get(33)? as usize;
            if let Some(found) = body
                .get(36 + name_len..)
                .and_then(|b| find_picture_at(b, depth + 1))
            {
                return Some(found);
            }
        } else if (0xF018..=0xF117).contains(&kind) {
            if let Some(found) = blip(ver_inst >> 4, kind, body) {
                return Some(found);
            }
        }
    }
    None
}

/// BLIP 레코드 몸 하나.
fn blip(instance: u16, kind: u16, body: &[u8]) -> Option<Picture> {
    // UID 16바이트(인스턴스에 따라 두 개)
    let double = matches!(
        instance,
        0x3D5 | 0x217 | 0x543 | 0x46B | 0x6E1 | 0x6E3 | 0x6E5 | 0x7A9 | 0x6E9 | 0x7A7
    );
    let uid = if double { 32 } else { 16 };
    match kind {
        // PNG·JPEG·TIFF: UID + 태그 1바이트
        0xF01D | 0xF02A | 0xF01E | 0xF029 => {
            let payload = body.get(uid + 1..)?;
            crate::read::image_mime(payload).map(|_| Picture::Raster(payload.to_vec()))
        }
        0xF01F => Some(Picture::Raster(crate::doc::dib_to_bmp(
            body.get(uid + 1..)?,
        ))),
        // EMF·WMF: UID + 메타파일 머리 34바이트(원래 크기·경계·저장 크기·압축) + 내용
        0xF01A | 0xF01B => {
            let header = body.get(uid..uid + 34)?;
            let size = u32_at(header, 0)? as usize;
            let saved = u32_at(header, 28)? as usize;
            let compression = *header.get(32)?;
            if size > MAX_PICTURE_BYTES {
                return None;
            }
            let stored = body.get(uid + 34..(uid + 34).saturating_add(saved).min(body.len()))?;
            let bytes = match compression {
                0 => {
                    let mut out = Vec::with_capacity(size.min(MAX_PICTURE_BYTES));
                    flate2::read::ZlibDecoder::new(stored)
                        .take(MAX_PICTURE_BYTES as u64 + 1)
                        .read_to_end(&mut out)
                        .ok()?;
                    if out.len() > MAX_PICTURE_BYTES {
                        return None;
                    }
                    out
                }
                0xFE => stored.to_vec(),
                _ => return None,
            };
            let svg = if kind == 0xF01A {
                rhwp::emf::convert_to_standalone_svg(&bytes)?
            } else {
                wmf_to_svg(&bytes, header)?
            };
            String::from_utf8(svg).ok().map(Picture::Svg)
        }
        _ => None,
    }
}

/// WMF → SVG. 저장된 WMF 에 쪽 맞춤 머리(placeable)가 없으면 메타파일 머리의 경계로 붙인다.
fn wmf_to_svg(bytes: &[u8], header: &[u8]) -> Option<Vec<u8>> {
    let convert = |data: &[u8]| {
        std::panic::catch_unwind(|| {
            rhwp::wmf::converter::WMFConverter::new(data, rhwp::wmf::converter::SVGPlayer::new())
                .run()
                .ok()
        })
        .ok()
        .flatten()
    };
    if u32_at(bytes, 0) == Some(0x9AC6_CDD7) {
        return convert(bytes);
    }
    // rcBounds(왼·위·오른·아래)
    let (l, t, r, b) = (
        i32_at(header, 4)?,
        i32_at(header, 8)?,
        i32_at(header, 12)?,
        i32_at(header, 16)?,
    );
    let clamp = |v: i32| v.clamp(i16::MIN as i32, i16::MAX as i32) as i16;
    let mut placeable = Vec::with_capacity(22 + bytes.len());
    placeable.extend_from_slice(&0x9AC6_CDD7u32.to_le_bytes());
    placeable.extend_from_slice(&0u16.to_le_bytes());
    for v in [l, t, r, b] {
        placeable.extend_from_slice(&clamp(v).to_le_bytes());
    }
    placeable.extend_from_slice(&1440u16.to_le_bytes());
    placeable.extend_from_slice(&0u32.to_le_bytes());
    let mut checksum = 0u16;
    for pair in placeable.chunks_exact(2) {
        checksum ^= u16::from_le_bytes([pair[0], pair[1]]);
    }
    placeable.extend_from_slice(&checksum.to_le_bytes());
    placeable.extend_from_slice(bytes);
    convert(&placeable).or_else(|| convert(bytes))
}

/// FSPA: 본문 cp 의 떠 있는 개체 자리
#[derive(Debug, Clone, Copy)]
struct Fspa {
    spid: u32,
    rect: [i32; 4],
    flags: u16,
}

/// OfficeArt 도형 하나
#[derive(Debug, Default, Clone)]
struct Sp {
    shape_type: u16,
    flags: u32,
    props: HashMap<u16, u32>,
    /// 묶음 안 자리(묶음 좌표)
    child_anchor: Option<[i32; 4]>,
    /// 묶음이면: 묶음 좌표계와 안의 도형들
    group: Option<([i32; 4], Vec<Sp>)>,
}

impl Sp {
    fn prop(&self, id: u16) -> Option<u32> {
        self.props.get(&id).copied()
    }

    /// 불 속성 묶음에서 한 비트: 쓰기(fUse) 비트가 켜졌을 때만 값을 본다.
    fn flag(&self, id: u16, bit: u32) -> Option<bool> {
        let value = self.prop(id)?;
        (value & (1 << (bit + 16)) != 0).then_some(value & (1 << bit) != 0)
    }
}

/// 문서의 그리기 개체
#[derive(Debug, Default)]
pub(crate) struct Art {
    anchors: HashMap<u32, Fspa>,
    shapes: HashMap<u32, Sp>,
    blips: Vec<Option<Picture>>,
}

impl Art {
    /// `plcspa`: 본문 FSPA 목록, `dgg`: OfficeArt 내용 자리(Table 스트림).
    pub(crate) fn parse(word: &[u8], table: &[u8], plcspa: (u32, u32), dgg: (u32, u32)) -> Art {
        let mut art = Art::default();
        let slice = |(fc, lcb): (u32, u32)| {
            table.get(fc as usize..(fc as usize).saturating_add(lcb as usize))
        };
        if let Some(plc) = slice(plcspa).filter(|p| p.len() >= 4) {
            let n = (plc.len() - 4) / 30;
            for k in 0..n {
                let (Some(cp), Some(spid)) =
                    (u32_at(plc, k * 4), u32_at(plc, (n + 1) * 4 + k * 26))
                else {
                    continue;
                };
                let at = (n + 1) * 4 + k * 26;
                let rect = [
                    i32_at(plc, at + 4).unwrap_or(0),
                    i32_at(plc, at + 8).unwrap_or(0),
                    i32_at(plc, at + 12).unwrap_or(0),
                    i32_at(plc, at + 16).unwrap_or(0),
                ];
                let flags = u16_at(plc, at + 20).unwrap_or(0);
                art.anchors.insert(cp, Fspa { spid, rect, flags });
            }
        }
        let Some(content) = slice(dgg) else {
            return art;
        };
        // OfficeArtDggContainer, 그다음 (dgglbl 1바이트 + OfficeArtDgContainer) 되풀이
        let Some((_, kind, range)) = record(content, 0) else {
            return art;
        };
        if kind == 0xF000 {
            for (_, kind, body) in records(&content[range.clone()]) {
                if kind == 0xF001 {
                    art.blips = records(body)
                        .map(|(_, kind, fbse)| {
                            (kind == 0xF007).then(|| fbse_picture(word, fbse)).flatten()
                        })
                        .collect();
                }
            }
        }
        let mut at = range.end;
        while at < content.len() {
            let label = content[at];
            let Some((_, kind, range)) = record(content, at + 1) else {
                break;
            };
            if kind == 0xF002 && label == 0 {
                collect_shapes(&content[range.clone()], &mut art.shapes, 0);
            }
            at = range.end.max(at + 9);
        }
        art
    }

    /// 본문 cp 의 떠 있는 개체를 도형으로. 글상자 글은 `textbox(번호)` 로 얻는다.
    /// 옮길 수 없으면 빈 목록.
    pub(crate) fn shapes_at(
        &self,
        cp: u32,
        textbox: &mut dyn FnMut(usize) -> Vec<String>,
    ) -> Vec<DocxShape> {
        let Some(fspa) = self.anchors.get(&cp) else {
            return Vec::new();
        };
        let Some(sp) = self.shapes.get(&fspa.spid) else {
            return Vec::new();
        };
        let [l, t, r, b] = fspa.rect;
        let mut out = Vec::new();
        self.place(sp, fspa, [l, t, r, b], textbox, &mut out, 0);
        out
    }

    fn place(
        &self,
        sp: &Sp,
        fspa: &Fspa,
        rect: [i32; 4],
        textbox: &mut dyn FnMut(usize) -> Vec<String>,
        out: &mut Vec<DocxShape>,
        depth: usize,
    ) {
        if depth > MAX_DEPTH {
            return;
        }
        if let Some((frame, children)) = &sp.group {
            // 묶음 좌표 → 둘러싼 자리(twip)
            let fw = f64::from((frame[2] - frame[0]).max(1));
            let fh = f64::from((frame[3] - frame[1]).max(1));
            let sx = f64::from(rect[2] - rect[0]) / fw;
            let sy = f64::from(rect[3] - rect[1]) / fh;
            for child in children {
                let Some(a) = child.child_anchor else {
                    continue;
                };
                let map_x = |x: i32| rect[0] + (f64::from(x - frame[0]) * sx).round() as i32;
                let map_y = |y: i32| rect[1] + (f64::from(y - frame[1]) * sy).round() as i32;
                let child_rect = [map_x(a[0]), map_y(a[1]), map_x(a[2]), map_y(a[3])];
                self.place(child, fspa, child_rect, textbox, out, depth + 1);
            }
            return;
        }
        if let Some(shape) = self.shape(sp, fspa, rect, textbox) {
            out.push(shape);
        }
    }

    fn shape(
        &self,
        sp: &Sp,
        fspa: &Fspa,
        rect: [i32; 4],
        textbox: &mut dyn FnMut(usize) -> Vec<String>,
    ) -> Option<DocxShape> {
        let (x0, x1) = (rect[0].min(rect[2]), rect[0].max(rect[2]));
        let (y0, y1) = (rect[1].min(rect[3]), rect[1].max(rect[3]));
        let width = i64::from(x1 - x0) * EMU_PER_TWIP;
        let height = i64::from(y1 - y0) * EMU_PER_TWIP;
        if width <= 0 && height <= 0 {
            return None;
        }
        // FSPA: bx(1–2)·by(3–4)·wr(5–8)·fBelowText(14)
        let bx = (fspa.flags >> 1) & 0x3;
        let by = (fspa.flags >> 3) & 0x3;
        let wr = (fspa.flags >> 5) & 0xF;
        let below = fspa.flags & 0x4000 != 0;
        let horizontal = match sp.prop(0x038F) {
            Some(1) => Relative::Margin,
            Some(2) => Relative::Page,
            Some(3) => Relative::Column,
            Some(4) => Relative::Paragraph,
            _ => match bx {
                1 => Relative::Page,
                2 => Relative::Column,
                _ => Relative::Margin,
            },
        };
        let vertical = match sp.prop(0x0391) {
            Some(1) => Relative::Margin,
            Some(2) => Relative::Page,
            Some(3 | 4) => Relative::Paragraph,
            _ => match by {
                1 => Relative::Page,
                2 => Relative::Paragraph,
                _ => Relative::Margin,
            },
        };
        let wrap = match wr {
            1 => Wrap::TopAndBottom,
            0 | 2 => Wrap::Square,
            4 | 5 => Wrap::Tight,
            _ if below => Wrap::Behind,
            _ => Wrap::InFront,
        };
        let flip_h = sp.flags & 0x40 != 0;
        let flip_v = sp.flags & 0x80 != 0;
        let base = DocxShape {
            kind: ShapeKind::Rectangle,
            width: width.max(0),
            height: height.max(0),
            inline: false,
            horizontal: (horizontal, i64::from(x0) * EMU_PER_TWIP),
            vertical: (vertical, i64::from(y0) * EMU_PER_TWIP),
            wrap,
            fill: None,
            line: None,
            line_width: 0,
            flip_h,
            flip_v,
            text: Vec::new(),
            html: None,
            image: None,
            insets: crate::shapes::DEFAULT_INSETS,
            text_anchor: 0,
            align: (0, 0),
            crop: [0; 4],
            pending: None,
        };
        // 그림: pib(0x0104, 그림 저장소 1부터)
        if let Some(pib) = sp.prop(0x0104).filter(|&p| p > 0) {
            let picture = self.blips.get(pib as usize - 1).cloned().flatten();
            if width <= 0 || height <= 0 {
                return None;
            }
            return picture.map(|picture| DocxShape {
                image: Some(picture.into_image()),
                ..base
            });
        }
        let kind = match sp.shape_type {
            2 => ShapeKind::RoundRectangle,
            3 => ShapeKind::Ellipse,
            20 | 32 | 33 | 34 | 38 => ShapeKind::Line,
            // 그림 틀인데 그림이 없다
            75 => return None,
            _ => ShapeKind::Rectangle,
        };
        let line_kind = kind == ShapeKind::Line;
        let filled = sp.flag(0x01BF, 4).unwrap_or(true) && !line_kind;
        let lined = sp.flag(0x01FF, 3).unwrap_or(true);
        let fill = filled.then(|| color(sp.prop(0x0181), 0xFFFFFF));
        let line = lined.then(|| color(sp.prop(0x01C0), 0x000000));
        let line_width = i64::from(sp.prop(0x01CB).unwrap_or(9525)).clamp(0, 20 * 12700);
        // 글상자: lTxid(0x0080) 위 16비트 = 글상자 이야기 번호(1부터)
        let text = match sp.prop(0x0080) {
            Some(txid) if !line_kind && txid >> 16 > 0 => textbox((txid >> 16) as usize - 1),
            _ => Vec::new(),
        };
        let has_text = text.iter().any(|t| !t.trim().is_empty());
        if fill.is_none() && line.is_none() && !has_text {
            return None;
        }
        // 글상자 안 여백 dxTextLeft·dyTextTop·dxTextRight·dyTextBottom(0x0081~0x0084, EMU), 글 세로 자리 anchorText(0x0087)
        let [l, t, r, b] = crate::shapes::DEFAULT_INSETS;
        let inset =
            |id: u16, default: i64| sp.prop(id).map_or(default, |v| i64::from(v.min(1 << 24)));
        let insets = [
            inset(0x0081, l),
            inset(0x0082, t),
            inset(0x0083, r),
            inset(0x0084, b),
        ];
        let text_anchor = match sp.prop(0x0087) {
            Some(1 | 4) => 1,
            Some(2 | 5 | 7 | 9) => 2,
            _ => 0,
        };
        Some(DocxShape {
            kind,
            fill,
            line,
            line_width,
            text: if has_text { text } else { Vec::new() },
            insets,
            text_anchor,
            ..base
        })
    }
}

/// OfficeArtCOLORREF(0xFFBBGGRR, FF = 색표·체계 색 표시) → 0xRRGGBB. 풀지 못하면 `default`.
fn color(value: Option<u32>, default: u32) -> u32 {
    match value {
        Some(v) if v >> 24 & 0x1C == 0 => {
            let (r, g, b) = (v & 0xFF, (v >> 8) & 0xFF, (v >> 16) & 0xFF);
            (r << 16) | (g << 8) | b
        }
        _ => default,
    }
}

/// FBSE 하나의 그림: 안에 있거나 WordDocument 스트림 `foDelay` 자리에 있다.
fn fbse_picture(word: &[u8], fbse: &[u8]) -> Option<Picture> {
    let size = u32_at(fbse, 20)? as usize;
    let delay = u32_at(fbse, 28)? as usize;
    let name_len = *fbse.get(33)? as usize;
    if let Some(found) = fbse
        .get(36 + name_len..)
        .filter(|b| b.len() >= 8)
        .and_then(find_picture)
    {
        return Some(found);
    }
    if size == 0 || size > MAX_PICTURE_BYTES {
        return None;
    }
    let blip = word.get(delay..delay.checked_add(size)?)?;
    find_picture(blip)
}

/// OfficeArtDgContainer 안의 도형(묶음 포함)을 spid 로 모은다. 맨 바깥 묶음(바탕)의 첫 도형은 바탕 자신이라 뺀다.
fn collect_shapes(data: &[u8], out: &mut HashMap<u32, Sp>, depth: usize) {
    if depth > MAX_DEPTH {
        return;
    }
    for (_, kind, body) in records(data) {
        if kind != 0xF003 {
            continue;
        }
        let mut first = true;
        for (_, kind, inner) in records(body) {
            match kind {
                0xF004 if first => first = false,
                0xF004 => {
                    if let Some((spid, sp)) = read_sp(inner) {
                        out.insert(spid, sp);
                    }
                }
                0xF003 => {
                    if let Some((spid, group)) = read_group(inner, depth + 1) {
                        out.insert(spid, group);
                    }
                }
                _ => {}
            }
        }
    }
}

/// SpgrContainer: (묶음 spid, 묶음 도형)
fn read_group(data: &[u8], depth: usize) -> Option<(u32, Sp)> {
    if depth > MAX_DEPTH {
        return None;
    }
    let mut head: Option<(u32, Sp)> = None;
    let mut children = Vec::new();
    let mut frame = [0, 0, 1, 1];
    for (_, kind, body) in records(data) {
        match kind {
            0xF004 if head.is_none() => {
                // 묶음 자신: FSPGR(0xF009)에 묶음 좌표
                for (_, k, b) in records(body) {
                    if k == 0xF009 {
                        frame = [
                            i32_at(b, 0).unwrap_or(0),
                            i32_at(b, 4).unwrap_or(0),
                            i32_at(b, 8).unwrap_or(1),
                            i32_at(b, 12).unwrap_or(1),
                        ];
                    }
                }
                head = read_sp(body);
            }
            0xF004 => {
                if let Some((_, sp)) = read_sp(body) {
                    children.push(sp);
                }
            }
            0xF003 => {
                if let Some((_, group)) = read_group(body, depth + 1) {
                    children.push(group);
                }
            }
            _ => {}
        }
    }
    let (spid, mut sp) = head?;
    sp.group = Some((frame, children));
    Some((spid, sp))
}

/// SpContainer → (spid, 도형)
fn read_sp(data: &[u8]) -> Option<(u32, Sp)> {
    let mut sp = Sp::default();
    let mut spid = None;
    for (ver_inst, kind, body) in records(data) {
        match kind {
            0xF00A => {
                sp.shape_type = ver_inst >> 4;
                spid = u32_at(body, 0);
                sp.flags = u32_at(body, 4).unwrap_or(0);
            }
            // 주·둘째·셋째 속성표
            0xF00B | 0xF121 | 0xF122 => {
                let count = (ver_inst >> 4) as usize;
                for k in 0..count {
                    let (Some(id), Some(value)) = (u16_at(body, k * 6), u32_at(body, k * 6 + 2))
                    else {
                        break;
                    };
                    // 복합 속성(fComplex)은 값이 길이라 쓰지 않는다.
                    if id & 0x8000 == 0 {
                        sp.props.insert(id & 0x3FFF, value);
                    }
                }
            }
            0xF00F => {
                sp.child_anchor = Some([
                    i32_at(body, 0)?,
                    i32_at(body, 4)?,
                    i32_at(body, 8)?,
                    i32_at(body, 12)?,
                ]);
            }
            _ => {}
        }
    }
    // 지운 도형(fDeleted)은 넣지 않는다.
    if sp.flags & 0x08 != 0 {
        return None;
    }
    Some((spid?, sp))
}
