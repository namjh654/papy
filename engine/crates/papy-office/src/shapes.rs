//! DOCX 그리기 개체(`w:drawing`) 읽기: 그림과 도형(사각형·둥근 사각형·타원·선·글상자).
//!
//! 도형은 rhwp HTML 붙여넣기로 넣을 수 없어, 본문에는 [`SHAPE_MARK`] 만 두고 도형 정보는
//! [`crate::DocxReadReport::shapes`] 로 넘긴다. papy-hwp 가 붙여넣은 뒤 그 자리에 도형을 만든다.
//! 묶음 도형·차트·SmartArt 는 아직 옮기지 않는다.

use quick_xml::events::BytesStart;

use crate::read::attr;

/// 본문 문단 안 도형 자리 표시(사용자 영역 글자).
pub const SHAPE_MARK: char = '\u{E0F2}';

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
    in_shape_props: bool,
    in_line: bool,
    in_solid: bool,
    has_style: bool,
    fill: Paint,
    line: Paint,
    line_width: Option<i64>,
    flip_h: bool,
    flip_v: bool,
    in_textbox: bool,
    in_text: bool,
    text: Vec<String>,
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

    /// 요소 시작(빈 요소 포함).
    pub fn start(&mut self, name: &str, e: &BytesStart<'_>, empty: bool) {
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
            "wsp" => self.shape = true,
            // 묶음·차트·SmartArt·잉크 등
            "wgp" | "grpSp" | "chart" | "relIds" | "contentPart" => self.unsupported = true,
            "spPr" if !empty => self.in_shape_props = true,
            "xfrm" if self.in_shape_props => {
                self.flip_h = attr(e, "flipH").as_deref() == Some("1");
                self.flip_v = attr(e, "flipV").as_deref() == Some("1");
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
            "txbxContent" if !empty => self.in_textbox = true,
            "p" if self.in_textbox => self.text.push(String::new()),
            "t" if self.in_textbox && !empty => self.in_text = true,
            "tab" if self.in_textbox => self.push_text("\t"),
            "br" if self.in_textbox => self.push_text(" "),
            _ => {}
        }
    }

    /// 요소 끝.
    pub fn end(&mut self, name: &str) {
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
            "positionH" | "positionV" => self.position = None,
            "spPr" => self.in_shape_props = false,
            "ln" => self.in_line = false,
            "solidFill" => self.in_solid = false,
            "txbxContent" => self.in_textbox = false,
            "t" => self.in_text = false,
            _ => {}
        }
    }

    /// 글자 내용.
    pub fn text(&mut self, text: &str) {
        if self.in_offset {
            self.offset_text.push_str(text);
        } else if self.in_text {
            self.push_text(text);
        }
    }

    fn push_text(&mut self, text: &str) {
        match self.text.last_mut() {
            Some(last) => last.push_str(text),
            None => self.text.push(text.to_owned()),
        }
    }

    /// 다 읽은 도형. 옮길 수 없는 것이면 None.
    pub fn into_shape(self) -> Option<DocxShape> {
        if !self.shape || self.unsupported {
            return None;
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
        let has_text = self.text.iter().any(|t| !t.trim().is_empty());
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
            text: if has_text && !line_kind {
                self.text
            } else {
                Vec::new()
            },
        })
    }
}
