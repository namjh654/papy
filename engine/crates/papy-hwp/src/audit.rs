//! 문서 구조 요약: 변환·저장 왕복에서 무엇이 빠지거나 바뀌었는지 보려고 요소마다 수를 센다.
//!
//! 글자 수는 빈칸·제어 글자를 뺀 것. 자리(본문·칸·머리말·꼬리말·주석·글상자·캡션)마다 따로도 센다.
//! 뒤 구역의 시작은 쪽 나누기로 센다. 쪽 설정 값은 mm(반올림). 같은 문서를 두 형식으로 거쳐 다시 열었을 때 값이 같아야 하는 것들이다.

use std::collections::{BTreeMap, BTreeSet};

use rhwp::model::control::{AutoNumberType, Control};
use rhwp::model::document::Document;
use rhwp::model::paragraph::{ColumnBreakType, Paragraph};
use rhwp::model::shape::ShapeObject;
use rhwp::model::style::{Alignment, HeadType, UnderlineType};

/// 요소 이름 → 수.
pub type Structure = BTreeMap<String, u64>;

#[derive(Clone, Copy, PartialEq)]
enum Place {
    Body,
    Cell,
    Header,
    Footer,
    Note,
    TextBox,
    Caption,
}

impl Place {
    fn name(self) -> &'static str {
        match self {
            Place::Body => "body",
            Place::Cell => "cell",
            Place::Header => "header",
            Place::Footer => "footer",
            Place::Note => "note",
            Place::TextBox => "textbox",
            Place::Caption => "caption",
        }
    }
}

struct Counter<'a> {
    doc: &'a Document,
    /// 문서 첫 본문 문단
    first: *const Paragraph,
    out: Structure,
    fonts: BTreeSet<String>,
}

impl Counter<'_> {
    fn add(&mut self, key: &str, n: u64) {
        if n > 0 {
            *self.out.entry(key.to_string()).or_default() += n;
        }
    }

    fn paragraphs(&mut self, paragraphs: &[Paragraph], place: Place, depth: usize) {
        for paragraph in paragraphs {
            self.paragraph(paragraph, place, depth);
        }
    }

    fn paragraph(&mut self, paragraph: &Paragraph, place: Place, depth: usize) {
        self.add("paragraphs", 1);
        // 글자: 빈칸·제어 글자·Papy 내부 표시 글자(사용자 영역 U+E0F0~)는 뺀다.
        let visible = |c: char| {
            !c.is_whitespace() && !c.is_control() && !('\u{E000}'..='\u{F8FF}').contains(&c)
        };
        let chars: Vec<char> = paragraph.text.chars().collect();
        let count = chars.iter().filter(|c| visible(**c)).count() as u64;
        // 머리말·꼬리말의 쪽 번호·전체 쪽 수 필드 글자(DOCX 에서 연 문서): 한글 쪽 번호 컨트롤과 같게 센다.
        let fields = chars
            .iter()
            .filter(|c| {
                matches!(
                    **c,
                    papy_office::PAGE_NUMBER_FIELD | papy_office::PAGE_COUNT_FIELD
                )
            })
            .count() as u64;
        self.add("page_numbers", fields);
        self.add("chars", count);
        self.add(&format!("chars.{}", place.name()), count);
        // 글자 모양: 런마다 덮는 글자 수(char_offsets 와 같은 단위의 시작 위치). rhwp 처럼 첫 런 앞 글자는 첫 런
        // 모양을, 런이 없는 문단은 0 번 모양을 쓴다.
        let info = &self.doc.doc_info;
        let default_run = [rhwp::model::paragraph::CharShapeRef {
            start_pos: 0,
            char_shape_id: 0,
        }];
        let runs: &[_] = if paragraph.char_shapes.is_empty() {
            &default_run
        } else {
            &paragraph.char_shapes
        };
        for (index, run) in runs.iter().enumerate() {
            let end = runs.get(index + 1).map_or(u32::MAX, |next| next.start_pos);
            let start = if index == 0 { 0 } else { run.start_pos };
            let covered = chars
                .iter()
                .enumerate()
                .filter(|(i, c)| {
                    let offset = paragraph.char_offsets.get(*i).copied().unwrap_or(*i as u32);
                    offset >= start && offset < end && visible(**c)
                })
                .count() as u64;
            let Some(shape) = info.char_shapes.get(run.char_shape_id as usize) else {
                continue;
            };
            if covered == 0 {
                continue;
            }
            if shape.bold {
                self.add("chars.bold", covered);
            }
            if shape.italic {
                self.add("chars.italic", covered);
            }
            if shape.underline_type != UnderlineType::None {
                self.add("chars.underline", covered);
            }
            if shape.text_color & 0x00FF_FFFF != 0 {
                self.add("chars.colored", covered);
            }
            // 크기별(반 pt 로 반올림 — Word 가 담는 단위): 글자 크기가 바뀌면 줄 높이·쪽 수가 바뀐다.
            // 1 pt 로 반올림하면 11.48 pt(→ 11)가 Word 를 거쳐 11.5 pt(→ 12)가 되어 바뀐 것처럼 보였다.
            let half = (shape.base_size + 25) / 50;
            let size = if half % 2 == 0 {
                format!("{}", half / 2)
            } else {
                format!("{}.5", half / 2)
            };
            self.add(&format!("chars.size.{size}pt"), covered);
            for (lang, id) in shape.font_ids.iter().enumerate().take(2) {
                if let Some(font) = info.font_faces.get(lang).and_then(|f| f.get(*id as usize)) {
                    self.fonts.insert(font.name.clone());
                }
            }
        }
        // 정렬·목록은 글이 있는 문단만(빈 문단의 정렬은 보이지 않는다).
        if let Some(shape) = info
            .para_shapes
            .get(paragraph.para_shape_id as usize)
            .filter(|_| count > 0)
        {
            match shape.alignment {
                Alignment::Center => self.add("para.center", 1),
                Alignment::Right => self.add("para.right", 1),
                _ => {}
            }
            if shape.head_type != HeadType::None {
                self.add("para.list", 1);
            }
        }
        // 문서 첫 문단의 나누기는 보이지 않고 Word·rhwp 로 옮기면 사라진다(세지 않는다).
        let first = std::ptr::eq(paragraph, self.first);
        if place == Place::Body && !first {
            match paragraph.column_type {
                ColumnBreakType::Page => self.add("break.page", 1),
                ColumnBreakType::Column => self.add("break.column", 1),
                _ => {}
            }
        }
        for control in &paragraph.controls {
            self.control(control, place, depth);
        }
    }

    fn control(&mut self, control: &Control, place: Place, depth: usize) {
        match control {
            Control::Table(table) => {
                self.add("tables", 1);
                if place == Place::Cell {
                    self.add("tables.nested", 1);
                } else if place != Place::Body {
                    self.add(&format!("tables.in_{}", place.name()), 1);
                }
                if !table.common.treat_as_char {
                    self.add("tables.floating", 1);
                }
                self.add("cells", table.cells.len() as u64);
                let merged = table
                    .cells
                    .iter()
                    .filter(|c| c.row_span > 1 || c.col_span > 1)
                    .count();
                self.add("cells.merged", merged as u64);
                if depth < 8 {
                    for cell in &table.cells {
                        self.paragraphs(&cell.paragraphs, Place::Cell, depth + 1);
                    }
                    if let Some(caption) = &table.caption {
                        self.paragraphs(&caption.paragraphs, Place::Caption, depth + 1);
                    }
                }
            }
            Control::Picture(picture) => {
                self.add("pictures", 1);
                if !matches!(place, Place::Body | Place::Cell) {
                    self.add(&format!("pictures.in_{}", place.name()), 1);
                }
                if let Some(caption) = &picture.caption {
                    self.paragraphs(&caption.paragraphs, Place::Caption, depth + 1);
                }
            }
            Control::Shape(shape) => {
                if place != Place::Body {
                    self.add(&format!("shapes.in_{}", place.name()), 1);
                }
                self.shape(shape, depth)
            }
            Control::Header(header) => {
                if has_text(&header.paragraphs) {
                    self.add("headers", 1);
                }
                self.paragraphs(&header.paragraphs, Place::Header, depth + 1);
            }
            Control::Footer(footer) => {
                if has_text(&footer.paragraphs) {
                    self.add("footers", 1);
                }
                self.paragraphs(&footer.paragraphs, Place::Footer, depth + 1);
            }
            Control::Footnote(note) => {
                self.add("footnotes", 1);
                self.paragraphs(&note.paragraphs, Place::Note, depth + 1);
            }
            Control::Endnote(note) => {
                self.add("endnotes", 1);
                self.paragraphs(&note.paragraphs, Place::Note, depth + 1);
            }
            Control::Equation(_) => self.add("equations", 1),
            Control::Form(_) => self.add("forms", 1),
            Control::Hyperlink(_) => self.add("hyperlinks", 1),
            Control::Bookmark(_) => self.add("bookmarks", 1),
            // 한글 메모(메모 필드)는 Word 메모·숨은 설명과 같은 것으로 센다.
            Control::Field(field) if field.command.starts_with("MEMO/") => self.add("comments", 1),
            Control::Field(_) => self.add("fields", 1),
            Control::HiddenComment(_) => self.add("comments", 1),
            Control::AutoNumber(number) => match number.number_type {
                AutoNumberType::Page | AutoNumberType::TotalPage => self.add("page_numbers", 1),
                _ => self.add("auto_numbers", 1),
            },
            Control::PageNumberPos(_) => self.add("page_numbers", 1),
            Control::ColumnDef(def) if def.column_count > 1 => self.add("columns.multi", 1),
            _ => {}
        }
    }

    fn shape(&mut self, shape: &ShapeObject, depth: usize) {
        match shape {
            ShapeObject::Group(group) => {
                self.add("shapes.groups", 1);
                if depth < 8 {
                    for child in &group.children {
                        self.shape(child, depth + 1);
                    }
                }
                return;
            }
            ShapeObject::Picture(_) => {
                self.add("pictures", 1);
                return;
            }
            ShapeObject::Chart(_) => self.add("charts", 1),
            ShapeObject::Ole(_) => self.add("ole", 1),
            _ => self.add("shapes", 1),
        }
        if let Some(text_box) = shape.drawing().and_then(|d| d.text_box.as_ref()) {
            if has_text(&text_box.paragraphs) {
                self.add("shapes.textboxes", 1);
            }
            if depth < 8 {
                self.paragraphs(&text_box.paragraphs, Place::TextBox, depth + 1);
            }
        }
    }
}

/// 글이나 개체(표·그림·쪽 번호 필드 글자 등)가 있는가.
fn has_text(paragraphs: &[Paragraph]) -> bool {
    paragraphs.iter().any(|p| {
        !p.controls.is_empty()
            || p.text.chars().any(|c| {
                (!c.is_whitespace() && !c.is_control())
                    || matches!(c, papy_office::PAGE_NUMBER_FIELD | papy_office::PAGE_COUNT_FIELD)
            })
    })
}

/// 문서 구조 요약.
pub(crate) fn structure(doc: &Document) -> Structure {
    let mut counter = Counter {
        doc,
        first: doc
            .sections
            .first()
            .and_then(|s| s.paragraphs.first())
            .map_or(std::ptr::null(), |p| p as *const Paragraph),
        out: Structure::new(),
        fonts: BTreeSet::new(),
    };
    counter.add("sections", doc.sections.len() as u64);
    for (index, section) in doc.sections.iter().enumerate() {
        // 뒤 구역의 시작은 쪽 나누기로 센다: Word 로 옮기면 구역 하나에 쪽 나누기가 된다.
        if index > 0
            && section
                .paragraphs
                .first()
                .is_some_and(|p| p.column_type != ColumnBreakType::Page)
        {
            counter.add("break.page", 1);
        }
        counter.paragraphs(&section.paragraphs, Place::Body, 0);
    }
    let fonts = counter.fonts.len() as u64;
    counter.add("fonts", fonts);
    if let Some(page) = doc.sections.first().map(|s| &s.section_def.page_def) {
        // HWPUNIT(1/7200 in) → mm
        let mm = |v: u32| ((f64::from(v) * 25.4 / 7200.0).round()) as u64;
        let (mut width, mut height) = (page.width, page.height);
        if page.landscape && width < height {
            std::mem::swap(&mut width, &mut height);
        }
        for (key, value) in [
            ("page.width_mm", width),
            ("page.height_mm", height),
            ("page.margin_left_mm", page.margin_left),
            ("page.margin_right_mm", page.margin_right),
            ("page.margin_top_mm", page.margin_top),
            ("page.margin_bottom_mm", page.margin_bottom),
        ] {
            counter.add(key, mm(value));
        }
    }
    counter.out
}
