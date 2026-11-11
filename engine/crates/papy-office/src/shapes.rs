//! DOCX 그리기 개체(`w:drawing`) 읽기: 그림과 도형(사각형·둥근 사각형·타원·선·글상자).
//!
//! 도형은 rhwp HTML 붙여넣기로 넣을 수 없어, 본문에는 [`SHAPE_MARK`] 만 두고 도형 정보는
//! [`crate::DocxReadReport::shapes`] 로 넘긴다. papy-hwp 가 붙여넣은 뒤 그 자리에 도형을 만든다.
//! 묶음 도형(`wpg:wgp`)은 안의 도형·그림·차트를 하나씩 풀어 같은 자리에 놓는다.
//! 차트는 저장된 값으로 그림을 그려 넣는다([`crate::chart`]). SmartArt·잉크는 아직 옮기지 않는다.

use quick_xml::events::BytesStart;

use crate::read::attr;

/// 본문 문단 안 도형 자리 표시(사용자 영역 글자).
pub const SHAPE_MARK: char = '\u{E022}';
/// 표 칸 문단 안 도형 자리 표시. 칸 도형은 [`crate::DocxReadReport::cell_shapes`] 에 같은 순서로 둔다.
pub const CELL_SHAPE_MARK: char = '\u{E02E}';

/// 도형 모양.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ShapeKind {
    /// 사각형
    Rectangle,
    /// 둥근 사각형
    RoundRectangle,
    /// 타원
    Ellipse,
    /// 직선
    Line,
}

/// 도형 위치의 기준.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Relative {
    /// 쪽(종이)
    Page,
    /// 쪽 여백 안
    Margin,
    /// 단
    Column,
    /// 문단(세로) 또는 글자(가로)
    Paragraph,
}

/// 본문과의 배치.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Wrap {
    /// 글 앞
    InFront,
    /// 글 뒤
    Behind,
    /// 사각형 둘레
    Square,
    /// 위아래
    TopAndBottom,
    /// 빽빽하게
    Tight,
}

/// DOCX 도형 하나. 길이는 EMU(1/914400 인치), 색은 `0xRRGGBB`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DocxShape {
    /// 모양
    pub kind: ShapeKind,
    /// 너비
    pub width: i64,
    /// 높이
    pub height: i64,
    /// 글자처럼 놓기(`wp:inline`)
    pub inline: bool,
    /// 가로 위치(기준, 거리)
    pub horizontal: (Relative, i64),
    /// 세로 위치(기준, 거리)
    pub vertical: (Relative, i64),
    /// 배치
    pub wrap: Wrap,
    /// 채우기 색(없으면 채우지 않음)
    pub fill: Option<u32>,
    /// 선 색(없으면 선 없음)
    pub line: Option<u32>,
    /// 선 굵기(EMU)
    pub line_width: i64,
    /// 선을 좌우로 뒤집음(오른쪽 위 → 왼쪽 아래)
    pub flip_h: bool,
    /// 선을 위아래로 뒤집음
    pub flip_v: bool,
    /// 글상자 문단(없으면 글상자가 아님)
    pub text: Vec<String>,
    /// 글상자 내용의 HTML(본문과 같은 꼴, 서식·그림·표 포함). 있으면 `text` 대신 이것을 넣는다.
    pub html: Option<String>,
    /// 그림으로 넣을 내용(묶음 안 그림, 차트). 있으면 도형 대신 이 그림을 넣는다.
    pub image: Option<ShapeImage>,
    /// 글상자 안 여백(왼쪽, 위, 오른쪽, 아래, EMU, `wps:bodyPr` 의 `lIns`…). 없으면 Word 기본값.
    pub insets: [i64; 4],
    /// 글상자 글의 세로 자리(`wps:bodyPr anchor`): 0 위, 1 가운데, 2 아래
    pub text_anchor: u8,
    /// 위치 정렬(`wp:align`) (가로, 세로): 0 값(posOffset), 1 가운데, 2 오른쪽·아래, 3 안쪽, 4 바깥쪽
    pub align: (u8, u8),
    /// 그림 자르기(`a:srcRect`): 왼쪽·위·오른쪽·아래에서 잘라 낸 양(1/1000 %). 자르지 않으면 모두 0.
    pub crop: [u32; 4],
    /// 읽는 중에 찾은 그림·차트 관계 id. [`crate::docx_to_html`] 이 [`DocxShape::image`] 로 푼다.
    pub(crate) pending: Option<Pending>,
}

/// Word 글상자 기본 안 여백(왼쪽·오른쪽 0.1인치, 위·아래 0.05인치, EMU).
pub const DEFAULT_INSETS: [i64; 4] = [91_440, 45_720, 91_440, 45_720];

/// 도형 자리에 넣을 그림.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ShapeImage {
    /// PNG·JPEG·GIF·BMP 파일 바이트
    Raster(Vec<u8>),
    /// SVG 문서(차트). 넣는 쪽이 비트맵으로 그린다.
    Svg(String),
}

/// 아직 풀지 않은 관계.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum Pending {
    Image(String),
    Chart(String),
}

/// Office 기본 테마의 도형 색(테마 색을 풀지 않을 때 쓴다): accent1 채우기, 그보다 어두운 선.
const THEME_FILL: u32 = 0x4472C4;
const THEME_LINE: u32 = 0x2F528F;
/// 기본 선 굵기 0.75pt
const DEFAULT_LINE_WIDTH: i64 = 9525;

/// 색 지정: 아직 못 봄 / 없음(noFill) / 색 / 테마 색(풀지 않음).
#[derive(Debug, Clone, Copy, Default, PartialEq)]
enum Paint {
    #[default]
    Unset,
    None,
    Color(u32),
    Theme,
}

/// `w:drawing` 하나를 읽는 동안의 상태.
#[derive(Debug, Default)]
pub(crate) struct Drawing {
    pub cx: Option<f64>,
    pub cy: Option<f64>,
    pub embed: Option<String>,
    /// 그림 자르기(`a:srcRect`, 1/1000 %): 왼쪽·위·오른쪽·아래
    crop: [u32; 4],
    inline: bool,
    picture: bool,
    shape: bool,
    unsupported: bool,
    geometry: Option<String>,
    behind: bool,
    wrap: Option<Wrap>,
    horizontal: Option<(Relative, i64)>,
    vertical: Option<(Relative, i64)>,
    /// 지금 읽는 위치 요소: (가로인가, 기준)
    position: Option<(bool, Relative)>,
    in_offset: bool,
    offset_text: String,
    /// `wp:align` 안(글을 `offset_text` 에 모은다)
    in_align: bool,
    align: (u8, u8),
    in_shape_props: bool,
    in_line: bool,
    in_solid: bool,
    has_style: bool,
    fill: Paint,
    line: Paint,
    line_width: Option<i64>,
    flip_h: bool,
    flip_v: bool,
    /// 글상자 내용(`w:txbxContent`) 깊이. 안의 그림·도형(수식 그림 등)은 이 도형 것이 아니다.
    textbox_level: usize,
    in_text: bool,
    text: Vec<String>,
    /// 글상자 내용 HTML([`Drawing::set_textbox_html`])
    html: Option<String>,
    /// `wps:bodyPr` 안 여백·세로 자리
    insets: Option<[i64; 4]>,
    text_anchor: u8,
    /// `a:xfrm` 안의 `a:off`·`a:ext`(묶음 안 도형의 자리, 묶음 좌표)
    in_xfrm: bool,
    xfrm_off: Option<(i64, i64)>,
    xfrm_ext: Option<(i64, i64)>,
    /// 묶음 도형(읽는 중이거나 다 읽은 것)
    group: Option<Group>,
    /// 맨 바깥 묶음을 다 읽었다
    group_done: bool,
    /// 차트 관계 id(`c:chart r:id`)
    chart: Option<String>,
    /// 묶음 안 `graphicFrame`(변환이 `spPr` 밖에 있다)
    frame: bool,
}

/// 묶음의 좌표 변환: 자식 좌표 `ch_off..ch_off+ch_ext` 를 부모 좌표 `off..off+ext` 로 옮긴다.
#[derive(Debug, Clone, Copy, Default)]
struct GroupXfrm {
    off: (i64, i64),
    ext: (i64, i64),
    ch_off: (i64, i64),
    ch_ext: (i64, i64),
}

impl GroupXfrm {
    fn map(&self, (x, y): (f64, f64)) -> (f64, f64) {
        let scale = |ext: i64, ch: i64| if ch > 0 { ext as f64 / ch as f64 } else { 1.0 };
        (
            self.off.0 as f64 + (x - self.ch_off.0 as f64) * scale(self.ext.0, self.ch_ext.0),
            self.off.1 as f64 + (y - self.ch_off.1 as f64) * scale(self.ext.1, self.ch_ext.1),
        )
    }
}

/// 묶음 안 도형 하나와 그 자리(맨 바깥 묶음 좌표, EMU): 두 모서리.
#[derive(Debug)]
struct Placed {
    drawing: Drawing,
    a: (f64, f64),
    b: (f64, f64),
}

/// 묶음 도형을 읽는 동안의 상태.
#[derive(Debug, Default)]
struct Group {
    /// 바깥 묶음부터 지금 묶음까지의 변환
    stack: Vec<GroupXfrm>,
    /// `grpSpPr` 안(지금 묶음의 변환을 읽는 중)
    in_props: bool,
    in_xfrm: bool,
    /// 읽는 중인 안쪽 도형과 그 요소 이름(wsp·pic·graphicFrame)
    child: Option<Box<Drawing>>,
    child_tag: &'static str,
    /// 다 읽은 안쪽 도형과 그 자리
    done: Vec<Placed>,
    /// 옮기지 못한 안쪽 개체(그림·차트 등)
    dropped: usize,
    /// 옮기지 못하는 안쪽 개체를 건너뛰는 깊이
    skip: usize,
}

impl Group {
    fn start(&mut self, name: &str, e: &BytesStart<'_>, empty: bool) {
        if self.skip > 0 {
            if !empty {
                self.skip += 1;
            }
            return;
        }
        if let Some(child) = self.child.as_mut() {
            child.start(name, e, empty);
            return;
        }
        let point = |x: &str, y: &str| {
            let v = |k| attr(e, k).and_then(|v| v.parse::<i64>().ok()).unwrap_or(0);
            (v(x), v(y))
        };
        match name {
            "grpSp" if !empty => self.stack.push(GroupXfrm::default()),
            "grpSpPr" if !empty => self.in_props = true,
            "xfrm" if self.in_props && !empty => self.in_xfrm = true,
            "off" | "ext" | "chOff" | "chExt" if self.in_xfrm => {
                let value = if name.ends_with("Off") || name == "off" {
                    point("x", "y")
                } else {
                    point("cx", "cy")
                };
                if let Some(top) = self.stack.last_mut() {
                    match name {
                        "off" => top.off = value,
                        "ext" => top.ext = value,
                        "chOff" => top.ch_off = value,
                        _ => top.ch_ext = value,
                    }
                }
            }
            "wsp" | "pic" | "graphicFrame" if !empty => {
                let (tag, child) = match name {
                    "wsp" => (
                        "wsp",
                        Drawing {
                            shape: true,
                            ..Drawing::default()
                        },
                    ),
                    "pic" => (
                        "pic",
                        Drawing {
                            picture: true,
                            ..Drawing::default()
                        },
                    ),
                    _ => (
                        "graphicFrame",
                        Drawing {
                            frame: true,
                            ..Drawing::default()
                        },
                    ),
                };
                self.child_tag = tag;
                self.child = Some(Box::new(child));
            }
            // 묶음 안 잉크·빈 그림 등은 옮기지 않는다.
            "pic" | "graphicFrame" | "contentPart" => {
                self.dropped += 1;
                if !empty {
                    self.skip = 1;
                }
            }
            _ => {}
        }
    }

    fn end(&mut self, name: &str) {
        if self.skip > 0 {
            self.skip -= 1;
            return;
        }
        if self.child.is_some() && name == self.child_tag {
            if let Some(child) = self.child.take() {
                let off = child.xfrm_off.unwrap_or((0, 0));
                let ext = child.xfrm_ext.unwrap_or((0, 0));
                let mut a = (off.0 as f64, off.1 as f64);
                let mut b = ((off.0 + ext.0) as f64, (off.1 + ext.1) as f64);
                for xfrm in self.stack.iter().rev() {
                    a = xfrm.map(a);
                    b = xfrm.map(b);
                }
                self.done.push(Placed {
                    drawing: *child,
                    a,
                    b,
                });
            }
            return;
        }
        if let Some(child) = self.child.as_mut() {
            child.end(name);
            return;
        }
        match name {
            "grpSp" => {
                self.stack.pop();
            }
            "grpSpPr" => self.in_props = false,
            "xfrm" => self.in_xfrm = false,
            _ => {}
        }
    }

    fn text(&mut self, text: &str) {
        if self.skip == 0 {
            if let Some(child) = self.child.as_mut() {
                child.text(text);
            }
        }
    }

    fn set_textbox_html(&mut self, html: String) {
        if let (0, Some(child)) = (self.skip, self.child.as_mut()) {
            child.set_textbox_html(html);
        }
    }
}

fn relative(value: Option<String>, horizontal: bool) -> Relative {
    match value.as_deref() {
        Some("page") => Relative::Page,
        Some("margin" | "leftMargin" | "rightMargin" | "topMargin" | "bottomMargin") => {
            Relative::Margin
        }
        Some("column") => Relative::Column,
        Some("paragraph" | "line" | "character") => Relative::Paragraph,
        _ if horizontal => Relative::Column,
        _ => Relative::Paragraph,
    }
}

fn hex(value: Option<String>) -> Option<u32> {
    value
        .filter(|v| v.len() == 6)
        .and_then(|v| u32::from_str_radix(&v, 16).ok())
}

impl Drawing {
    /// 그림으로 넣을 것인가(그림 개체이거나, 도형이 아닌데 그림 참조가 있다).
    pub fn is_picture(&self) -> bool {
        self.embed.is_some() && (self.picture || !self.shape)
    }

    /// 잘라 낸 그림 하나인가(묶음 아님).
    pub fn is_cropped_picture(&self) -> bool {
        self.is_picture()
            && self.picture
            && self.group.is_none()
            && self.crop.iter().any(|v| *v > 0)
    }

    /// 문단에 떠 있는(`wp:anchor`) 그림인가. 글자처럼 놓인 그림(`wp:inline`)은 아니다.
    pub fn is_floating_picture(&self) -> bool {
        self.is_picture() && !self.inline && self.picture && self.group.is_none()
    }

    /// 요소 시작(빈 요소 포함).
    pub fn start(&mut self, name: &str, e: &BytesStart<'_>, empty: bool) {
        if let (Some(group), false) = (self.group.as_mut(), self.group_done) {
            group.start(name, e, empty);
            return;
        }
        // 글상자 안: 글자만 모은다(안쪽 그림의 `pic`·`blip`·`wsp` 를 이 도형 것으로 읽으면 글상자가 그림이 된다).
        if self.textbox_level > 0 {
            match name {
                "txbxContent" if !empty => self.textbox_level += 1,
                "p" => self.text.push(String::new()),
                "t" if !empty => self.in_text = true,
                "tab" => self.push_text("\t"),
                "br" => self.push_text(" "),
                _ => {}
            }
            return;
        }
        match name {
            "inline" => self.inline = true,
            "anchor" => self.behind = attr(e, "behindDoc").as_deref() == Some("1"),
            "extent" => {
                self.cx = attr(e, "cx").and_then(|v| v.parse().ok());
                self.cy = attr(e, "cy").and_then(|v| v.parse().ok());
            }
            "positionH" | "positionV" => {
                let horizontal = name == "positionH";
                self.position = Some((horizontal, relative(attr(e, "relativeFrom"), horizontal)));
            }
            "posOffset" if !empty => {
                self.in_offset = true;
                self.offset_text.clear();
            }
            "align" if !empty && self.position.is_some() => {
                self.in_offset = true;
                self.in_align = true;
                self.offset_text.clear();
            }
            "wrapNone" => {
                self.wrap = Some(if self.behind {
                    Wrap::Behind
                } else {
                    Wrap::InFront
                })
            }
            "wrapSquare" => self.wrap = Some(Wrap::Square),
            "wrapTopAndBottom" => self.wrap = Some(Wrap::TopAndBottom),
            "wrapTight" | "wrapThrough" => self.wrap = Some(Wrap::Tight),
            "pic" => self.picture = true,
            "blip" => self.embed = self.embed.take().or_else(|| attr(e, "embed")),
            // 그림 자르기. 음수(그림 밖으로 넓힘)는 0 으로 본다.
            "srcRect" if self.picture && self.textbox_level == 0 => {
                let side = |key: &str| {
                    attr(e, key)
                        .and_then(|v| v.parse::<i64>().ok())
                        .map_or(0, |v| v.clamp(0, 100_000) as u32)
                };
                let crop = [side("l"), side("t"), side("r"), side("b")];
                if crop[0] + crop[2] < 100_000 && crop[1] + crop[3] < 100_000 {
                    self.crop = crop;
                }
            }
            "wsp" => self.shape = true,
            // 묶음 도형: 맨 바깥 묶음의 변환부터 읽는다.
            "wgp" if !empty => {
                self.group = Some(Group {
                    stack: vec![GroupXfrm::default()],
                    ..Group::default()
                })
            }
            // 차트·SmartArt·잉크 등
            "chart" => self.chart = attr(e, "id"),
            "wgp" | "grpSp" | "relIds" | "contentPart" => self.unsupported = true,
            "spPr" if !empty => self.in_shape_props = true,
            "xfrm" if self.in_shape_props || (self.frame && !empty) => {
                self.flip_h = attr(e, "flipH").as_deref() == Some("1");
                self.flip_v = attr(e, "flipV").as_deref() == Some("1");
                self.in_xfrm = !empty;
            }
            "off" if self.in_xfrm => {
                let v = |k| attr(e, k).and_then(|v| v.parse::<i64>().ok()).unwrap_or(0);
                self.xfrm_off = Some((v("x"), v("y")));
            }
            "ext" if self.in_xfrm => {
                let v = |k| attr(e, k).and_then(|v| v.parse::<i64>().ok()).unwrap_or(0);
                self.xfrm_ext = Some((v("cx"), v("cy")));
            }
            "prstGeom" if self.in_shape_props => self.geometry = attr(e, "prst"),
            "custGeom" if self.in_shape_props => self.geometry = Some("custom".into()),
            "ln" if self.in_shape_props => {
                self.in_line = !empty;
                self.line_width = attr(e, "w").and_then(|v| v.parse().ok());
            }
            "noFill" if self.in_shape_props => {
                if self.in_line {
                    self.line = Paint::None;
                } else {
                    self.fill = Paint::None;
                }
            }
            "solidFill" if self.in_shape_props && !empty => self.in_solid = true,
            "srgbClr" | "schemeClr" | "sysClr" | "prstClr" if self.in_solid => {
                let paint = match name {
                    "srgbClr" => hex(attr(e, "val")).map_or(Paint::Theme, Paint::Color),
                    "sysClr" => hex(attr(e, "lastClr")).map_or(Paint::Theme, Paint::Color),
                    _ => Paint::Theme,
                };
                if self.in_line {
                    self.line = paint;
                } else {
                    self.fill = paint;
                }
            }
            "gradFill" | "pattFill" | "blipFill" if self.in_shape_props && !self.in_line => {
                self.fill = Paint::Theme
            }
            "style" => self.has_style = true,
            "txbxContent" if !empty => self.textbox_level = 1,
            "bodyPr" => {
                let inset = |key: &str, default: i64| {
                    attr(e, key)
                        .and_then(|v| v.parse::<i64>().ok())
                        .map_or(default, |v| v.max(0))
                };
                let [l, t, r, b] = DEFAULT_INSETS;
                self.insets = Some([
                    inset("lIns", l),
                    inset("tIns", t),
                    inset("rIns", r),
                    inset("bIns", b),
                ]);
                self.text_anchor = match attr(e, "anchor").as_deref() {
                    Some("ctr") => 1,
                    Some("b") => 2,
                    _ => 0,
                };
            }
            _ => {}
        }
    }

    /// 요소 끝.
    pub fn end(&mut self, name: &str) {
        if let (Some(group), false) = (self.group.as_mut(), self.group_done) {
            if name != "wgp" {
                group.end(name);
                return;
            }
            // 맨 바깥 묶음이 끝났다. 이후 요소(anchor 끝 등)는 이 그리기 것이다.
            self.group_done = true;
            return;
        }
        if self.textbox_level > 0 && name != "txbxContent" {
            if name == "t" {
                self.in_text = false;
            }
            return;
        }
        match name {
            "posOffset" => {
                self.in_offset = false;
                let value = self.offset_text.trim().parse().unwrap_or(0);
                match self.position {
                    Some((true, base)) => self.horizontal = Some((base, value)),
                    Some((false, base)) => self.vertical = Some((base, value)),
                    None => {}
                }
            }
            "align" if self.in_align => {
                self.in_offset = false;
                self.in_align = false;
                let code = match self.offset_text.trim() {
                    "center" => 1,
                    "right" | "bottom" => 2,
                    "inside" => 3,
                    "outside" => 4,
                    _ => 0,
                };
                match self.position {
                    Some((true, base)) => {
                        self.horizontal = Some((base, 0));
                        self.align.0 = code;
                    }
                    Some((false, base)) => {
                        self.vertical = Some((base, 0));
                        self.align.1 = code;
                    }
                    None => {}
                }
            }
            "positionH" | "positionV" => self.position = None,
            "spPr" => self.in_shape_props = false,
            "xfrm" => self.in_xfrm = false,
            "ln" => self.in_line = false,
            "solidFill" => self.in_solid = false,
            "txbxContent" => self.textbox_level = self.textbox_level.saturating_sub(1),
            "t" => self.in_text = false,
            _ => {}
        }
    }

    /// 글자 내용.
    pub fn text(&mut self, text: &str) {
        if let (Some(group), false) = (self.group.as_mut(), self.group_done) {
            group.text(text);
            return;
        }
        if self.in_offset {
            self.offset_text.push_str(text);
        } else if self.in_text {
            self.push_text(text);
        }
    }

    /// 글상자 내용(`w:txbxContent`)을 HTML 로 바꾼 것. 묶음이면 읽는 중인 안쪽 도형 것이다.
    pub fn set_textbox_html(&mut self, html: String) {
        if let (Some(group), false) = (self.group.as_mut(), self.group_done) {
            group.set_textbox_html(html);
            return;
        }
        self.html = Some(html);
    }

    fn push_text(&mut self, text: &str) {
        match self.text.last_mut() {
            Some(last) => last.push_str(text),
            None => self.text.push(text.to_owned()),
        }
    }

    /// 다 읽은 그리기의 도형들과 옮기지 못한 개체 수. 묶음이면 안의 도형을 하나씩 풀어
    /// 묶음 자리 기준으로 놓는다(글자처럼 놓은 묶음도 문단 기준으로 떠 있게 놓는다).
    pub fn into_shapes(mut self) -> (Vec<DocxShape>, usize) {
        let Some(group) = self.group.take() else {
            return match self.into_shape() {
                Some(shape) => (vec![shape], 0),
                None => (Vec::new(), 1),
            };
        };
        let mut dropped = group.dropped;
        let (base_h, base_x) = self.horizontal.unwrap_or((Relative::Column, 0));
        let (base_v, base_y) = self.vertical.unwrap_or((Relative::Paragraph, 0));
        let wrap = match self.wrap {
            Some(wrap) if !self.inline => wrap,
            _ => Wrap::InFront,
        };
        let mut shapes = Vec::new();
        for Placed {
            drawing: child,
            a,
            b,
        } in group.done
        {
            let (x0, x1) = (a.0.min(b.0), a.0.max(b.0));
            let (y0, y1) = (a.1.min(b.1), a.1.max(b.1));
            let child = Drawing {
                cx: Some(x1 - x0),
                cy: Some(y1 - y0),
                ..child
            };
            match child.into_shape() {
                Some(shape) => shapes.push(DocxShape {
                    inline: false,
                    horizontal: (base_h, base_x + x0.round() as i64),
                    vertical: (base_v, base_y + y0.round() as i64),
                    wrap,
                    ..shape
                }),
                None => dropped += 1,
            }
        }
        // 옮길 것이 하나도 없던 묶음은 통째로 빠진 것으로 센다.
        if shapes.is_empty() && dropped == 0 {
            dropped = 1;
        }
        (shapes, dropped)
    }

    /// 다 읽은 도형. 옮길 수 없는 것이면 None.
    fn into_shape(self) -> Option<DocxShape> {
        let pending = match (&self.chart, &self.embed) {
            (Some(chart), _) => Some(Pending::Chart(chart.clone())),
            (None, Some(embed)) if self.picture => Some(Pending::Image(embed.clone())),
            _ => None,
        };
        if self.unsupported || (!self.shape && pending.is_none()) {
            return None;
        }
        if let Some(pending) = pending {
            let size = |value: Option<f64>| value.filter(|v| *v > 0.0).map_or(0, |v| v as i64);
            let (width, height) = (size(self.cx), size(self.cy));
            if width == 0 || height == 0 {
                return None;
            }
            return Some(DocxShape {
                kind: ShapeKind::Rectangle,
                width,
                height,
                inline: self.inline,
                horizontal: self.horizontal.unwrap_or((Relative::Column, 0)),
                vertical: self.vertical.unwrap_or((Relative::Paragraph, 0)),
                wrap: self.wrap.unwrap_or(Wrap::InFront),
                fill: None,
                line: None,
                line_width: 0,
                flip_h: self.flip_h,
                flip_v: self.flip_v,
                text: Vec::new(),
                html: None,
                image: None,
                insets: DEFAULT_INSETS,
                text_anchor: 0,
                align: self.align,
                crop: self.crop,
                pending: Some(pending),
            });
        }
        let kind = match self.geometry.as_deref() {
            Some("ellipse") => ShapeKind::Ellipse,
            Some("roundRect") => ShapeKind::RoundRectangle,
            Some(
                "line" | "straightConnector1" | "bentConnector2" | "bentConnector3"
                | "curvedConnector3",
            ) => ShapeKind::Line,
            // 그 밖의 모양(화살표·별 등)은 사각형으로 놓아 글상자 글자라도 지킨다.
            _ => ShapeKind::Rectangle,
        };
        let size = |value: Option<f64>| value.filter(|v| *v >= 0.0).map_or(0, |v| v as i64);
        let (width, height) = (size(self.cx), size(self.cy));
        if width == 0 && height == 0 {
            return None;
        }
        let resolve = |paint: Paint, theme: u32| match paint {
            Paint::Color(color) => Some(color),
            Paint::None => None,
            Paint::Theme => Some(theme),
            // 지정이 없으면 도형 스타일(테마)을 따른다. 스타일도 없으면 없음.
            Paint::Unset => self.has_style.then_some(theme),
        };
        let line_kind = kind == ShapeKind::Line;
        // 글자가 없어도 그림·표가 든 글상자(빈칸 + 그림 둘 같은 것)는 글상자로 둔다.
        let has_text = self.text.iter().any(|t| !t.trim().is_empty())
            || self
                .html
                .as_deref()
                .is_some_and(|html| html.contains("<img") || html.contains("<table"));
        Some(DocxShape {
            kind,
            width,
            height,
            inline: self.inline,
            horizontal: self.horizontal.unwrap_or((Relative::Column, 0)),
            vertical: self.vertical.unwrap_or((Relative::Paragraph, 0)),
            wrap: self.wrap.unwrap_or(Wrap::InFront),
            fill: if line_kind {
                None
            } else {
                resolve(self.fill, THEME_FILL)
            },
            line: resolve(self.line, THEME_LINE),
            line_width: self.line_width.unwrap_or(DEFAULT_LINE_WIDTH).max(0),
            flip_h: self.flip_h,
            flip_v: self.flip_v,
            html: self.html.filter(|_| has_text && !line_kind),
            text: if has_text && !line_kind {
                self.text
            } else {
                Vec::new()
            },
            image: None,
            insets: self.insets.unwrap_or(DEFAULT_INSETS),
            text_anchor: self.text_anchor,
            align: self.align,
            crop: [0; 4],
            pending: None,
        })
    }
}
