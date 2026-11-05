//! DOCX 차트(`c:chart`, `word/charts/chartN.xml`) → SVG 그림.
//!
//! rhwp 에는 차트 개체가 없어, 차트 XML 에 저장된 값(캐시)으로 막대·꺾은선·영역·원·분산형 그림을 그려
//! 그림으로 넣는다. 축 제목·데이터 레이블·3차원 효과·보조 축은 그리지 않는다(값과 제목, 범례만).

use quick_xml::Reader;
use quick_xml::events::Event;

use crate::read::{attr, local};

/// 차트 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum ChartKind {
    /// 세로 막대(`barDir="col"`)
    Column,
    /// 가로 막대(`barDir="bar"`)
    Bar,
    Line,
    Area,
    Pie,
    Doughnut,
    Scatter,
}

#[derive(Debug, Clone, Default)]
pub(crate) struct Series {
    pub name: String,
    pub values: Vec<Option<f64>>,
    /// 분산형의 x 값
    pub x: Vec<Option<f64>>,
    pub color: Option<u32>,
}

#[derive(Debug, Clone)]
pub(crate) struct Chart {
    pub kind: ChartKind,
    pub stacked: bool,
    pub title: Option<String>,
    pub categories: Vec<String>,
    pub series: Vec<Series>,
}

/// Office 기본 테마 강조 색.
const PALETTE: [u32; 10] = [
    0x4472C4, 0xED7D31, 0xA5A5A5, 0xFFC000, 0x5B9BD5, 0x70AD47, 0x264478, 0x9E480E, 0x636363,
    0x997300,
];
/// 계열·항목 상한(악성 문서 방어)
const MAX_SERIES: usize = 64;
const MAX_POINTS: usize = 4096;

/// 지금 읽는 값 목록.
#[derive(Clone, Copy, PartialEq)]
enum Slot {
    Name,
    Category,
    Value,
    X,
}

/// 차트 XML 을 읽는다. 그릴 것이 없으면 None.
pub(crate) fn parse(xml: &str) -> Option<Chart> {
    let mut reader = Reader::from_str(xml);
    let mut stack: Vec<String> = Vec::new();
    let mut kind: Option<ChartKind> = None;
    let mut stacked = false;
    let mut title = String::new();
    let mut title_deleted = false;
    let mut categories: Vec<String> = Vec::new();
    let mut series: Vec<Series> = Vec::new();
    let mut current: Option<Series> = None;
    let mut slot: Option<Slot> = None;
    let mut point: Option<usize> = None;
    let mut in_value = false;
    let mut in_title_text = false;
    let mut text = String::new();
    // 이 계열의 항목 이름(첫 계열 것을 차트 항목으로 쓴다)
    let mut series_categories: Vec<String> = Vec::new();

    loop {
        let event = match reader.read_event() {
            Ok(Event::Eof) | Err(_) => break,
            Ok(event) => event,
        };
        match event {
            Event::Start(ref e) | Event::Empty(ref e) => {
                let empty = matches!(event, Event::Empty(_));
                let name = local(e.name().as_ref()).to_owned();
                let parent = stack.last().map(String::as_str);
                match name.as_str() {
                    "barChart" | "bar3DChart" if kind.is_none() => kind = Some(ChartKind::Column),
                    "lineChart" | "line3DChart" | "radarChart" | "stockChart" if kind.is_none() => {
                        kind = Some(ChartKind::Line)
                    }
                    "areaChart" | "area3DChart" if kind.is_none() => kind = Some(ChartKind::Area),
                    "pieChart" | "pie3DChart" | "ofPieChart" if kind.is_none() => {
                        kind = Some(ChartKind::Pie)
                    }
                    "doughnutChart" if kind.is_none() => kind = Some(ChartKind::Doughnut),
                    "scatterChart" | "bubbleChart" if kind.is_none() => {
                        kind = Some(ChartKind::Scatter)
                    }
                    "barDir" if attr(e, "val").as_deref() == Some("bar") => {
                        if kind == Some(ChartKind::Column) && series.is_empty() {
                            kind = Some(ChartKind::Bar);
                        }
                    }
                    "grouping" if series.is_empty() => {
                        stacked = attr(e, "val").is_some_and(|v| v.contains("tacked"));
                    }
                    "autoTitleDeleted" => title_deleted = attr(e, "val").as_deref() != Some("0"),
                    "ser" if !empty && series.len() < MAX_SERIES => {
                        current = Some(Series::default());
                        series_categories.clear();
                    }
                    "tx" if current.is_some() && parent == Some("ser") => slot = Some(Slot::Name),
                    "cat" | "xVal" if current.is_some() => {
                        slot = Some(if kind == Some(ChartKind::Scatter) {
                            Slot::X
                        } else {
                            Slot::Category
                        })
                    }
                    "val" | "yVal" if current.is_some() && parent == Some("ser") => {
                        slot = Some(Slot::Value)
                    }
                    "pt" if slot.is_some() => {
                        point = attr(e, "idx")
                            .and_then(|v| v.parse::<usize>().ok())
                            .filter(|&i| i < MAX_POINTS);
                    }
                    "v" if !empty && slot.is_some() => {
                        in_value = true;
                        text.clear();
                    }
                    "srgbClr"
                        if current.is_some()
                            && stack.iter().any(|n| n == "spPr")
                            && !stack.iter().any(|n| n == "dPt") =>
                    {
                        if let (Some(series), Some(color)) = (
                            current.as_mut(),
                            attr(e, "val").and_then(|v| u32::from_str_radix(&v, 16).ok()),
                        ) {
                            series.color.get_or_insert(color);
                        }
                    }
                    // 차트 제목(축 제목이 아닌 것): c:chart/c:title 안의 a:t
                    "t" if !empty
                        && stack.len() >= 2
                        && stack.iter().rev().any(|n| n == "title")
                        && !stack.iter().any(|n| n.ends_with("Ax") || n == "ser") =>
                    {
                        in_title_text = true;
                    }
                    _ => {}
                }
                if !empty {
                    stack.push(name);
                }
            }
            Event::Text(t) => {
                let content = t.xml10_content();
                if in_value {
                    text.push_str(&content);
                } else if in_title_text {
                    title.push_str(&content);
                }
            }
            Event::GeneralRef(r) => {
                if let Ok(Some(ch)) = r.resolve_char_ref() {
                    if in_value {
                        text.push(ch);
                    } else if in_title_text {
                        title.push(ch);
                    }
                } else {
                    let ch = match r.into_inner().as_ref() {
                        "amp" => "&",
                        "lt" => "<",
                        "gt" => ">",
                        "quot" => "\"",
                        "apos" => "'",
                        _ => "",
                    };
                    if in_value {
                        text.push_str(ch);
                    } else if in_title_text {
                        title.push_str(ch);
                    }
                }
            }
            Event::End(e) => {
                let name = local(e.name().as_ref()).to_owned();
                stack.pop();
                match name.as_str() {
                    "t" => in_title_text = false,
                    "v" if in_value => {
                        in_value = false;
                        let value = text.trim().to_owned();
                        if let (Some(series), Some(slot)) = (current.as_mut(), slot) {
                            // c:tx 의 c:v(이름을 바로 쓴 것)에는 c:pt 가 없다.
                            let index = point.unwrap_or(0);
                            let put_number = |list: &mut Vec<Option<f64>>| {
                                if list.len() <= index {
                                    list.resize(index + 1, None);
                                }
                                list[index] = value.parse::<f64>().ok().filter(|v| v.is_finite());
                            };
                            match slot {
                                Slot::Name => series.name = value,
                                Slot::Value => put_number(&mut series.values),
                                Slot::X => put_number(&mut series.x),
                                Slot::Category => {
                                    if series_categories.len() <= index {
                                        series_categories.resize(index + 1, String::new());
                                    }
                                    series_categories[index] = value;
                                }
                            }
                        }
                    }
                    "pt" => point = None,
                    "tx" | "cat" | "xVal" | "val" | "yVal"
                        if stack.last().map(String::as_str) == Some("ser") =>
                    {
                        slot = None
                    }
                    "ser" => {
                        if let Some(done) = current.take() {
                            if categories.is_empty() {
                                categories = std::mem::take(&mut series_categories);
                            }
                            series.push(done);
                        }
                    }
                    _ => {}
                }
            }
            _ => {}
        }
    }
    let kind = kind?;
    if series.iter().all(|s| s.values.iter().all(Option::is_none)) {
        return None;
    }
    let title = title.trim();
    let title = if title.is_empty() {
        // 제목을 지우지 않았고 계열이 하나면 Word 는 계열 이름을 제목으로 보인다.
        (!title_deleted && series.len() == 1 && !series[0].name.is_empty())
            .then(|| series[0].name.clone())
    } else {
        Some(title.to_owned())
    };
    Some(Chart {
        kind,
        stacked,
        title,
        categories,
        series,
    })
}

fn color(series: &Series, index: usize) -> u32 {
    series.color.unwrap_or(PALETTE[index % PALETTE.len()])
}

fn esc(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
}

/// 눈금 값 글자: 소수점 아래 쓸데없는 0 을 뺀다.
fn label(value: f64) -> String {
    let text = if value.abs() >= 1000.0 || value.fract() == 0.0 {
        format!("{value:.0}")
    } else {
        format!("{value:.2}")
    };
    if text.contains('.') {
        text.trim_end_matches('0').trim_end_matches('.').to_owned()
    } else {
        text
    }
}

/// 보기 좋은 눈금 간격(1·2·5 × 10^n)과 범위.
fn nice_axis(min: f64, max: f64) -> (f64, f64, f64) {
    let (min, max) = if (max - min).abs() < f64::EPSILON {
        (min.min(0.0), max.max(min + 1.0))
    } else {
        (min, max)
    };
    let raw = (max - min) / 5.0;
    let power = 10f64.powf(raw.log10().floor());
    let step = [1.0, 2.0, 5.0, 10.0]
        .into_iter()
        .map(|m| m * power)
        .find(|s| *s >= raw)
        .unwrap_or(10.0 * power);
    (
        (min / step).floor() * step,
        (max / step).ceil() * step,
        step,
    )
}

impl Chart {
    /// `width`×`height` px 그림. 글꼴은 그리는 쪽 기본 글꼴(한글 대체 글꼴 포함)을 쓴다.
    pub(crate) fn to_svg(&self, width: f64, height: f64) -> String {
        let (w, h) = (width.max(40.0), height.max(30.0));
        let font = (h.min(w) * 0.045).clamp(7.0, 14.0);
        let mut svg = format!(
            r##"<svg xmlns="http://www.w3.org/2000/svg" width="{w:.0}" height="{h:.0}" viewBox="0 0 {w:.2} {h:.2}"><rect x="0.5" y="0.5" width="{:.2}" height="{:.2}" fill="#FFFFFF" stroke="#D9D9D9"/>"##,
            w - 1.0,
            h - 1.0
        );
        let mut top = font * 0.8;
        if let Some(title) = &self.title {
            let size = font * 1.3;
            svg.push_str(&format!(
                r##"<text x="{:.2}" y="{:.2}" font-size="{size:.1}" text-anchor="middle" fill="#404040">{}</text>"##,
                w / 2.0,
                top + size,
                esc(title)
            ));
            top += size * 1.6;
        }
        // 범례: 원·도넛은 항목, 나머지는 계열.
        let pie = matches!(self.kind, ChartKind::Pie | ChartKind::Doughnut);
        let legend: Vec<(String, u32)> = if pie {
            self.categories
                .iter()
                .enumerate()
                .map(|(i, name)| (name.clone(), PALETTE[i % PALETTE.len()]))
                .collect()
        } else {
            self.series
                .iter()
                .enumerate()
                .map(|(i, s)| {
                    let name = if s.name.is_empty() {
                        format!("계열 {}", i + 1)
                    } else {
                        s.name.clone()
                    };
                    (name, color(s, i))
                })
                .collect()
        };
        let show_legend = pie || self.series.len() > 1;
        let mut bottom = h - font * 0.8;
        if show_legend && !legend.is_empty() {
            let y = bottom - font * 0.3;
            let item_width: Vec<f64> = legend
                .iter()
                .map(|(name, _)| font * 1.6 + name.chars().count() as f64 * font * 0.75)
                .collect();
            let total: f64 = item_width.iter().sum();
            let mut x = ((w - total) / 2.0).max(4.0);
            for ((name, color), width) in legend.iter().zip(&item_width) {
                if x + width > w {
                    break;
                }
                svg.push_str(&format!(
                    r##"<rect x="{x:.2}" y="{:.2}" width="{:.2}" height="{:.2}" fill="#{color:06X}"/><text x="{:.2}" y="{y:.2}" font-size="{font:.1}" fill="#595959">{}</text>"##,
                    y - font * 0.75,
                    font * 0.7,
                    font * 0.7,
                    x + font,
                    esc(name)
                ));
                x += width;
            }
            bottom -= font * 1.8;
        }
        if pie {
            self.pie(&mut svg, w, top, bottom);
        } else {
            self.axes(&mut svg, w, top, bottom, font);
        }
        svg.push_str("</svg>");
        svg
    }

    fn pie(&self, svg: &mut String, w: f64, top: f64, bottom: f64) {
        let Some(series) = self.series.first() else {
            return;
        };
        let values: Vec<f64> = series
            .values
            .iter()
            .map(|v| v.unwrap_or(0.0).max(0.0))
            .collect();
        let total: f64 = values.iter().sum();
        if total <= 0.0 {
            return;
        }
        let radius = ((bottom - top) / 2.0).min(w / 2.0 - 8.0).max(4.0) * 0.92;
        let (cx, cy) = (w / 2.0, (top + bottom) / 2.0);
        let mut angle = -std::f64::consts::FRAC_PI_2;
        for (i, value) in values.iter().enumerate() {
            if *value <= 0.0 {
                continue;
            }
            let sweep = value / total * std::f64::consts::TAU;
            let color = PALETTE[i % PALETTE.len()];
            if sweep >= std::f64::consts::TAU - 1e-9 {
                svg.push_str(&format!(
                    r##"<circle cx="{cx:.2}" cy="{cy:.2}" r="{radius:.2}" fill="#{color:06X}" stroke="#FFFFFF"/>"##
                ));
            } else {
                let (x0, y0) = (cx + radius * angle.cos(), cy + radius * angle.sin());
                let end = angle + sweep;
                let (x1, y1) = (cx + radius * end.cos(), cy + radius * end.sin());
                let large = u8::from(sweep > std::f64::consts::PI);
                svg.push_str(&format!(
                    r##"<path d="M{cx:.2},{cy:.2} L{x0:.2},{y0:.2} A{radius:.2},{radius:.2} 0 {large} 1 {x1:.2},{y1:.2} Z" fill="#{color:06X}" stroke="#FFFFFF"/>"##
                ));
            }
            angle += sweep;
        }
        if self.kind == ChartKind::Doughnut {
            svg.push_str(&format!(
                r##"<circle cx="{cx:.2}" cy="{cy:.2}" r="{:.2}" fill="#FFFFFF"/>"##,
                radius * 0.5
            ));
        }
    }

    fn axes(&self, svg: &mut String, w: f64, top: f64, bottom: f64, font: f64) {
        let count = self
            .series
            .iter()
            .map(|s| s.values.len())
            .max()
            .unwrap_or(0)
            .max(self.categories.len())
            .max(1);
        // 값 범위(쌓은 막대·영역은 항목마다 더한 값).
        let (mut low, mut high) = (0.0f64, 0.0f64);
        let stacked = self.stacked && !matches!(self.kind, ChartKind::Scatter | ChartKind::Line);
        for i in 0..count {
            let (mut pos, mut neg) = (0.0, 0.0);
            for s in &self.series {
                let v = s.values.get(i).copied().flatten().unwrap_or(0.0);
                if stacked {
                    if v >= 0.0 { pos += v } else { neg += v }
                } else {
                    high = high.max(v);
                    low = low.min(v);
                }
            }
            if stacked {
                high = high.max(pos);
                low = low.min(neg);
            }
        }
        let (min, max, step) = nice_axis(low, high);
        let horizontal = self.kind == ChartKind::Bar;
        let tick_labels: Vec<String> = {
            let mut v = Vec::new();
            let mut t = min;
            while t <= max + step * 0.01 && v.len() < 50 {
                v.push(label(t));
                t += step;
            }
            v
        };
        let value_label_width = tick_labels
            .iter()
            .map(|t| t.chars().count())
            .max()
            .unwrap_or(1) as f64
            * font
            * 0.62;
        let category_label_width = self
            .categories
            .iter()
            .map(|c| c.chars().count())
            .max()
            .unwrap_or(0) as f64
            * font
            * 0.9;
        let left =
            8.0 + if horizontal {
                category_label_width.min(w * 0.3)
            } else {
                value_label_width
            } + font * 0.5;
        let (right, plot_top, plot_bottom) = (w - font, top + font * 0.5, bottom - font * 1.4);
        if right - left < 10.0 || plot_bottom - plot_top < 10.0 {
            return;
        }
        // 값 → 화면 좌표(세로 막대 등은 y, 가로 막대는 x)
        let value_pos = |v: f64| {
            let t = (v - min) / (max - min);
            if horizontal {
                left + t * (right - left)
            } else {
                plot_bottom - t * (plot_bottom - plot_top)
            }
        };
        // 눈금선과 값 글자
        let mut t = min;
        for text in &tick_labels {
            let p = value_pos(t);
            if horizontal {
                svg.push_str(&format!(
                    r##"<line x1="{p:.2}" y1="{plot_top:.2}" x2="{p:.2}" y2="{plot_bottom:.2}" stroke="#D9D9D9" stroke-width="0.75"/><text x="{p:.2}" y="{:.2}" font-size="{font:.1}" text-anchor="middle" fill="#595959">{text}</text>"##,
                    plot_bottom + font * 1.1
                ));
            } else {
                svg.push_str(&format!(
                    r##"<line x1="{left:.2}" y1="{p:.2}" x2="{right:.2}" y2="{p:.2}" stroke="#D9D9D9" stroke-width="0.75"/><text x="{:.2}" y="{:.2}" font-size="{font:.1}" text-anchor="end" fill="#595959">{text}</text>"##,
                    left - font * 0.4,
                    p + font * 0.35
                ));
            }
            t += step;
        }
        // 항목 띠
        let band = if horizontal {
            (plot_bottom - plot_top) / count as f64
        } else {
            (right - left) / count as f64
        };
        let band_start = |i: usize| {
            if horizontal {
                plot_top + band * i as f64
            } else {
                left + band * i as f64
            }
        };
        let every = ((category_label_width * 0.75 / band).ceil() as usize).max(1);
        for (i, name) in self.categories.iter().enumerate().take(count) {
            if horizontal {
                svg.push_str(&format!(
                    r##"<text x="{:.2}" y="{:.2}" font-size="{font:.1}" text-anchor="end" fill="#595959">{}</text>"##,
                    left - font * 0.4,
                    band_start(i) + band / 2.0 + font * 0.35,
                    esc(name)
                ));
            } else if i % every == 0 && self.kind != ChartKind::Scatter {
                svg.push_str(&format!(
                    r##"<text x="{:.2}" y="{:.2}" font-size="{font:.1}" text-anchor="middle" fill="#595959">{}</text>"##,
                    band_start(i) + band / 2.0,
                    plot_bottom + font * 1.1,
                    esc(name)
                ));
            }
        }
        let zero = value_pos(0.0f64.clamp(min, max));
        match self.kind {
            ChartKind::Column | ChartKind::Bar => {
                let n = self.series.len().max(1);
                let inner = band * 0.7;
                let mut base_pos = vec![0.0f64; count];
                let mut base_neg = vec![0.0f64; count];
                for (si, s) in self.series.iter().enumerate() {
                    let fill = color(s, si);
                    for i in 0..count {
                        let Some(v) = s.values.get(i).copied().flatten() else {
                            continue;
                        };
                        let (from, to, offset, thick) = if stacked {
                            let base = if v >= 0.0 {
                                &mut base_pos[i]
                            } else {
                                &mut base_neg[i]
                            };
                            let from = *base;
                            *base += v;
                            (from, *base, band * 0.15, inner)
                        } else {
                            let thick = inner / n as f64;
                            (0.0, v, band * 0.15 + thick * si as f64, thick)
                        };
                        let (a, b) = (value_pos(from), value_pos(to));
                        let start = band_start(i) + offset;
                        let (x, y, bw, bh) = if horizontal {
                            (a.min(b), start, (a - b).abs(), thick)
                        } else {
                            (start, a.min(b), thick, (a - b).abs())
                        };
                        svg.push_str(&format!(
                            r##"<rect x="{x:.2}" y="{y:.2}" width="{bw:.2}" height="{bh:.2}" fill="#{fill:06X}"/>"##
                        ));
                    }
                }
            }
            ChartKind::Line | ChartKind::Area | ChartKind::Scatter => {
                // 분산형은 x 값으로, 나머지는 항목 띠 가운데에 놓는다.
                let xs: Option<(f64, f64)> = (self.kind == ChartKind::Scatter).then(|| {
                    let all = self.series.iter().flat_map(|s| s.x.iter().flatten());
                    let (lo, hi) =
                        all.fold((f64::MAX, f64::MIN), |(lo, hi), v| (lo.min(*v), hi.max(*v)));
                    if lo <= hi { (lo, hi) } else { (0.0, 1.0) }
                });
                let mut base = vec![0.0f64; count];
                for (si, s) in self.series.iter().enumerate() {
                    let stroke = color(s, si);
                    let mut points: Vec<(f64, f64)> = Vec::new();
                    for (i, sum) in base.iter_mut().enumerate() {
                        let Some(mut v) = s.values.get(i).copied().flatten() else {
                            continue;
                        };
                        if stacked {
                            *sum += v;
                            v = *sum;
                        }
                        let x = match (xs, s.x.get(i).copied().flatten()) {
                            (Some((lo, hi)), Some(x)) if hi > lo => {
                                left + (x - lo) / (hi - lo) * (right - left)
                            }
                            (Some(_), _) => {
                                left + (right - left) * i as f64 / (count.max(2) - 1) as f64
                            }
                            _ => band_start(i) + band / 2.0,
                        };
                        points.push((x, value_pos(v)));
                    }
                    if points.is_empty() {
                        continue;
                    }
                    let path: Vec<String> = points
                        .iter()
                        .map(|(x, y)| format!("{x:.2},{y:.2}"))
                        .collect();
                    if self.kind == ChartKind::Area {
                        let (first, last) = (points[0].0, points[points.len() - 1].0);
                        svg.push_str(&format!(
                            r##"<polygon points="{first:.2},{zero:.2} {} {last:.2},{zero:.2}" fill="#{stroke:06X}" fill-opacity="0.75"/>"##,
                            path.join(" ")
                        ));
                    } else {
                        if self.kind == ChartKind::Line {
                            svg.push_str(&format!(
                                r##"<polyline points="{}" fill="none" stroke="#{stroke:06X}" stroke-width="2"/>"##,
                                path.join(" ")
                            ));
                        }
                        for (x, y) in &points {
                            svg.push_str(&format!(
                                r##"<circle cx="{x:.2}" cy="{y:.2}" r="{:.2}" fill="#{stroke:06X}"/>"##,
                                (font * 0.25).max(2.0)
                            ));
                        }
                    }
                }
            }
            ChartKind::Pie | ChartKind::Doughnut => {}
        }
        // 기준(0) 축
        if horizontal {
            svg.push_str(&format!(
                r##"<line x1="{zero:.2}" y1="{plot_top:.2}" x2="{zero:.2}" y2="{plot_bottom:.2}" stroke="#808080" stroke-width="0.75"/>"##
            ));
        } else {
            svg.push_str(&format!(
                r##"<line x1="{left:.2}" y1="{zero:.2}" x2="{right:.2}" y2="{zero:.2}" stroke="#808080" stroke-width="0.75"/>"##
            ));
        }
    }
}
