//! 열린 HWP/HWPX 문서: 보기(렌더·텍스트)와 편집(커서·입력·서식·되돌리기·저장).
//!
//! rhwp 좌표는 96 DPI px 이고 Papy 계약은 pt 라서 경계에서 바꾼다(1px = 0.75pt).
//! 되돌리기는 rhwp 문서 스냅샷으로 한다. 이어 치는 글자는 한 단계로 묶는다.

use papy_model::{
    Alignment, CharFormat, DocumentInfo, EditOutcome, Error, ErrorCode, PageRect, PageSize,
    ParaFormat, Result, SaveFormat, TextPos,
};
use papy_render::{Bitmap, Document, RenderRequest};
use rhwp::wasm_api::HwpDocument as Rhwp;
use serde_json::Value;

use crate::{guard, rasterize};

/// rhwp px → pt.
const PT_PER_PX: f32 = 0.75;
/// 되돌리기 단계 상한(스냅샷은 문서 전체 복사본이다).
const MAX_UNDO: usize = 100;
/// 한 단계로 묶을 연속 입력 글자 수 상한.
const MAX_COALESCE: u32 = 40;

struct Step {
    snapshot: u32,
    /// 이 단계를 되돌렸을(또는 다시 했을) 때 커서 위치.
    caret: TextPos,
}

pub(crate) struct HwpDocument {
    doc: Rhwp,
    info: DocumentInfo,
    sizes: Vec<Option<PageSize>>,
    /// 열 때 쓴 암호. 같은 형식으로 저장할 때 다시 암호화한다.
    password: Option<String>,
    undo: Vec<Step>,
    redo: Vec<Step>,
    /// 직전 입력이 끝난 위치와 그 단계에 묶인 글자 수. 여기서 이어 치면 새 단계를 만들지 않는다.
    typing: Option<(TextPos, u32)>,
    /// rhwp 가 패닉한 뒤에는 내부 상태를 믿을 수 없으니 더 부르지 않는다.
    broken: bool,
}

fn engine_error(what: &str) -> Error {
    Error::new(ErrorCode::InvalidArgument, format!("could not {what}"))
}

fn json(text: &str) -> Result<Value> {
    serde_json::from_str(text)
        .map_err(|_| Error::new(ErrorCode::Internal, "unexpected reply from HWP engine"))
}

fn uint(value: &Value, key: &str) -> Option<u32> {
    value.get(key)?.as_u64().map(|v| v as u32)
}

fn float(value: &Value, key: &str) -> Option<f32> {
    value.get(key)?.as_f64().map(|v| v as f32)
}

impl HwpDocument {
    pub(crate) fn new(doc: Rhwp, info: DocumentInfo, password: Option<String>) -> Self {
        let pages = info.page_count as usize;
        Self {
            doc,
            info,
            sizes: vec![None; pages],
            password,
            undo: Vec::new(),
            redo: Vec::new(),
            typing: None,
            broken: false,
        }
    }

    fn check(&self) -> Result<()> {
        if self.broken {
            Err(Error::new(
                ErrorCode::Internal,
                "document is unusable after an engine failure",
            ))
        } else {
            Ok(())
        }
    }

    /// 읽기 호출. rhwp 오류는 `what` 만 남긴 오류로 바꾼다.
    fn read<T>(
        &mut self,
        what: &str,
        f: impl FnOnce(&Rhwp) -> std::result::Result<T, rhwp::HwpError>,
    ) -> Result<T> {
        self.check()?;
        match guard(|| f(&self.doc)) {
            Ok(result) => result.map_err(|_| engine_error(what)),
            Err(error) => {
                self.broken = true;
                Err(error)
            }
        }
    }

    /// rhwp 의 wasm 래퍼(오류 시 JsValue 를 만들다 패닉한다)를 부르는 읽기 호출.
    /// 읽기 전용이라 패닉해도 문서 상태는 그대로이므로 `broken` 으로 표시하지 않는다.
    fn read_wrapper<T, E>(
        &self,
        what: &str,
        f: impl FnOnce(&Rhwp) -> std::result::Result<T, E>,
    ) -> Result<T> {
        self.check()?;
        guard(|| f(&self.doc).ok())?.ok_or_else(|| engine_error(what))
    }

    /// 쓰기 호출. 실패해도 편집 단계 시작 때 만든 스냅샷이 있어 되돌리기로 복구할 수 있다.
    fn write<T>(
        &mut self,
        what: &str,
        f: impl FnOnce(&mut Rhwp) -> std::result::Result<T, rhwp::HwpError>,
    ) -> Result<T> {
        self.check()?;
        let result = match guard(|| f(&mut self.doc)) {
            Ok(result) => result.map_err(|_| engine_error(what)),
            Err(error) => {
                self.broken = true;
                Err(error)
            }
        };
        self.refresh();
        result
    }

    /// 편집 뒤 쪽 수·크기 캐시를 새로 맞춘다.
    fn refresh(&mut self) {
        let pages = self.doc.page_count();
        self.info.page_count = pages;
        self.sizes = vec![None; pages as usize];
    }

    /// 편집 단계를 시작한다. `typing_at` 이 직전 입력 끝과 같으면 그 단계에 이어 붙인다.
    fn begin_step(&mut self, caret: TextPos, typing_at: Option<(TextPos, u32)>) {
        if let (Some((at, added)), Some((last, count))) = (typing_at, self.typing) {
            if at == last && count + added <= MAX_COALESCE {
                return;
            }
        }
        let snapshot = self.doc.save_snapshot_native();
        self.undo.push(Step { snapshot, caret });
        if self.undo.len() > MAX_UNDO {
            let old = self.undo.remove(0);
            self.doc.discard_snapshot_native(old.snapshot);
        }
        for step in self.redo.drain(..) {
            self.doc.discard_snapshot_native(step.snapshot);
        }
        self.typing = None;
    }

    fn paragraph_len(&mut self, section: u32, paragraph: u32) -> Result<u32> {
        self.read("read paragraph", |doc| {
            doc.get_paragraph_length_native(section as usize, paragraph as usize)
        })
        .map(|len| len as u32)
    }

    fn paragraph_count(&mut self, section: u32) -> Result<u32> {
        self.read("read section", |doc| {
            doc.get_paragraph_count_native(section as usize)
        })
        .map(|count| count as u32)
    }

    fn same_section(start: TextPos, end: TextPos) -> Result<()> {
        if start.section != end.section || start > end {
            return Err(Error::new(
                ErrorCode::InvalidArgument,
                "selection must be ordered and within one section",
            ));
        }
        Ok(())
    }

    fn insert_plain(&mut self, pos: TextPos, text: &str) -> Result<TextPos> {
        let mut caret = pos;
        for (index, line) in text.split('\n').enumerate() {
            if index > 0 {
                let reply = self.write("split paragraph", |doc| {
                    doc.split_paragraph_native(
                        caret.section as usize,
                        caret.paragraph as usize,
                        caret.offset as usize,
                        None,
                    )
                })?;
                let reply = json(&reply)?;
                caret.paragraph = uint(&reply, "paraIdx").unwrap_or(caret.paragraph + 1);
                caret.offset = uint(&reply, "charOffset").unwrap_or(0);
            }
            let line = line.trim_end_matches('\r');
            if line.is_empty() {
                continue;
            }
            let reply = self.write("insert text", |doc| {
                doc.insert_text_native(
                    caret.section as usize,
                    caret.paragraph as usize,
                    caret.offset as usize,
                    line,
                )
            })?;
            caret.offset = uint(&json(&reply)?, "charOffset")
                .unwrap_or(caret.offset + line.chars().count() as u32);
        }
        Ok(caret)
    }

    fn delete_plain(&mut self, start: TextPos, end: TextPos) -> Result<()> {
        if start == end {
            return Ok(());
        }
        self.write("delete", |doc| {
            doc.delete_range_native(
                start.section as usize,
                start.paragraph as usize,
                start.offset as usize,
                end.paragraph as usize,
                end.offset as usize,
                None,
            )
        })?;
        Ok(())
    }

    fn restore(&mut self, from: Step) -> Result<Step> {
        self.check()?;
        let current = self.doc.save_snapshot_native();
        let restored = guard(|| self.doc.restore_snapshot_native(from.snapshot));
        self.doc.discard_snapshot_native(from.snapshot);
        match restored {
            Ok(Ok(_)) => {}
            Ok(Err(_)) => return Err(engine_error("restore")),
            Err(error) => {
                self.broken = true;
                return Err(error);
            }
        }
        self.refresh();
        self.typing = None;
        Ok(Step {
            snapshot: current,
            caret: from.caret,
        })
    }
}

fn parse_pos(value: &Value) -> Option<TextPos> {
    Some(TextPos {
        section: uint(value, "sectionIndex")?,
        // 표 안을 눌렀으면 표를 가진 본문 문단으로 간다(셀 편집은 아직 없다).
        paragraph: uint(value, "parentParaIndex").or_else(|| uint(value, "paragraphIndex"))?,
        offset: if value.get("parentParaIndex").is_some() {
            0
        } else {
            uint(value, "charOffset")?
        },
    })
}

fn parse_rect(value: &Value) -> Option<PageRect> {
    Some(PageRect {
        page: uint(value, "pageIndex")?,
        x: float(value, "x")? * PT_PER_PX,
        y: float(value, "y")? * PT_PER_PX,
        width: float(value, "width").unwrap_or(0.0) * PT_PER_PX,
        height: float(value, "height")? * PT_PER_PX,
    })
}

/// `get_page_info_native` 의 JSON(px, 96 DPI)에서 페이지 크기를 pt 로 읽는다.
pub(crate) fn parse_page_size(json: &str) -> Option<PageSize> {
    let value: Value = serde_json::from_str(json).ok()?;
    let (width, height) = (float(&value, "width")?, float(&value, "height")?);
    (width > 0.0 && height > 0.0).then_some(PageSize {
        width: width * PT_PER_PX,
        height: height * PT_PER_PX,
    })
}

fn char_format_json(format: &CharFormat, font_ids: Option<[i32; 7]>) -> String {
    let mut props = serde_json::Map::new();
    let mut set = |key: &str, value: Value| {
        props.insert(key.into(), value);
    };
    if let Some(v) = format.bold {
        set("bold", v.into());
    }
    if let Some(v) = format.italic {
        set("italic", v.into());
    }
    if let Some(v) = format.underline {
        set("underline", v.into());
    }
    if let Some(v) = format.strikethrough {
        set("strikethrough", v.into());
    }
    if let Some(v) = format.font_size {
        // rhwp 글자 크기 단위는 1/100 pt
        set("fontSize", ((v * 100.0).round() as i64).into());
    }
    if let Some(v) = &format.color {
        set("textColor", v.clone().into());
    }
    if let Some(ids) = font_ids {
        set("fontIds", ids.to_vec().into());
    }
    Value::Object(props).to_string()
}

fn alignment_name(alignment: Alignment) -> &'static str {
    match alignment {
        Alignment::Left => "left",
        Alignment::Center => "center",
        Alignment::Right => "right",
        Alignment::Justify => "justify",
    }
}

impl Document for HwpDocument {
    fn info(&self) -> DocumentInfo {
        self.info.clone()
    }

    fn page_size(&mut self, page: u32) -> Result<PageSize> {
        if let Some(Some(size)) = self.sizes.get(page as usize) {
            return Ok(*size);
        }
        let info = self.read("read page size", |doc| doc.get_page_info_native(page))?;
        let size = parse_page_size(&info).ok_or_else(|| {
            Error::new(ErrorCode::Internal, "unexpected page info from HWP engine")
        })?;
        if let Some(slot) = self.sizes.get_mut(page as usize) {
            *slot = Some(size);
        }
        Ok(size)
    }

    fn render_page(&mut self, page: u32, request: &RenderRequest) -> Result<Bitmap> {
        let (width, height) = request.pixel_size(self.page_size(page)?);
        request.cancel.check()?;
        let svg = self.read("lay out page", |doc| doc.render_page_svg_native(page))?;
        request.cancel.check()?;
        guard(|| rasterize(&svg, width, height, request.white_background))?
    }

    fn extract_text(&mut self, page: u32) -> Result<String> {
        self.read("extract text", |doc| doc.extract_page_text_native(page))
    }

    fn save(&mut self, format: SaveFormat) -> Result<Vec<u8>> {
        let password = self.password.clone();
        match format {
            SaveFormat::Hwpx => self.read("save HWPX", |doc| match &password {
                Some(pw) => doc.export_hwpx_native_with_password(pw.as_bytes()),
                None => doc.export_hwpx_native(),
            }),
            SaveFormat::Hwp => match password {
                Some(pw) => {
                    self.read_wrapper("save HWP", |doc| doc.export_hwp_with_password_wasm(&pw))
                }
                None => self.read("save HWP", |doc| doc.export_hwp_native()),
            },
            SaveFormat::Pdf => self.read("export PDF", |doc| doc.render_document_pdf_native()),
            SaveFormat::Text => {
                let mut text = String::new();
                for page in 0..self.info.page_count {
                    let page_text = self.extract_text(page)?;
                    text.push_str(page_text.trim_end_matches('\n'));
                    text.push('\n');
                }
                Ok(text.into_bytes())
            }
            _ => Err(Error::new(
                ErrorCode::NotImplemented,
                "unsupported save format",
            )),
        }
    }

    fn hit_test(&mut self, page: u32, x: f32, y: f32) -> Result<TextPos> {
        let reply = self.read("hit test", |doc| {
            doc.hit_test_native(page, (x / PT_PER_PX) as f64, (y / PT_PER_PX) as f64)
        })?;
        parse_pos(&json(&reply)?).ok_or_else(|| engine_error("hit test"))
    }

    fn caret_rect(&mut self, pos: TextPos) -> Result<PageRect> {
        let reply = self.read("locate caret", |doc| {
            doc.get_cursor_rect_native(
                pos.section as usize,
                pos.paragraph as usize,
                pos.offset as usize,
            )
        })?;
        parse_rect(&json(&reply)?).ok_or_else(|| engine_error("locate caret"))
    }

    fn move_vertical(&mut self, pos: TextPos, lines: i32, preferred_x: f32) -> Result<TextPos> {
        let reply = self.read_wrapper("move caret", |doc| {
            doc.move_vertical(
                pos.section,
                pos.paragraph,
                pos.offset,
                lines,
                (preferred_x / PT_PER_PX) as f64,
                u32::MAX,
                0,
                0,
                0,
            )
        })?;
        parse_pos(&json(&reply)?).ok_or_else(|| engine_error("move caret"))
    }

    fn selection_rects(&mut self, start: TextPos, end: TextPos) -> Result<Vec<PageRect>> {
        Self::same_section(start, end)?;
        let reply = self.read_wrapper("measure selection", |doc| {
            doc.get_selection_rects(
                start.section,
                start.paragraph,
                start.offset,
                end.paragraph,
                end.offset,
            )
        })?;
        let rects = json(&reply)?;
        Ok(rects
            .as_array()
            .map(|items| items.iter().filter_map(parse_rect).collect())
            .unwrap_or_default())
    }

    fn text_in_range(&mut self, start: TextPos, end: TextPos) -> Result<String> {
        Self::same_section(start, end)?;
        let mut text = String::new();
        for paragraph in start.paragraph..=end.paragraph {
            let from = if paragraph == start.paragraph {
                start.offset
            } else {
                0
            };
            let to = if paragraph == end.paragraph {
                end.offset
            } else {
                self.paragraph_len(start.section, paragraph)?
            };
            if paragraph > start.paragraph {
                text.push('\n');
            }
            if to > from {
                let part = self.read("read text", |doc| {
                    doc.get_text_range_native(
                        start.section as usize,
                        paragraph as usize,
                        from as usize,
                        (to - from) as usize,
                    )
                })?;
                text.push_str(&part);
            }
        }
        Ok(text)
    }

    fn insert_text(&mut self, pos: TextPos, text: &str) -> Result<EditOutcome> {
        let added = text.chars().count() as u32;
        let plain_typing = !text.contains('\n') && !text.contains(' ');
        self.begin_step(pos, plain_typing.then_some((pos, added)));
        let caret = self.insert_plain(pos, text)?;
        self.typing = match (plain_typing, self.typing.take()) {
            (true, Some((_, count))) => Some((caret, count + added)),
            (true, None) => Some((caret, added)),
            (false, _) => None,
        };
        Ok(EditOutcome { caret })
    }

    fn delete_range(&mut self, start: TextPos, end: TextPos) -> Result<EditOutcome> {
        Self::same_section(start, end)?;
        self.begin_step(start, None);
        self.delete_plain(start, end)?;
        Ok(EditOutcome { caret: start })
    }

    fn delete_backward(&mut self, pos: TextPos) -> Result<EditOutcome> {
        if pos.offset > 0 {
            let start = TextPos {
                offset: pos.offset - 1,
                ..pos
            };
            return self.delete_range(start, pos);
        }
        if pos.paragraph == 0 {
            return Ok(EditOutcome { caret: pos });
        }
        self.begin_step(pos, None);
        let reply = self.write("merge paragraphs", |doc| {
            doc.merge_paragraph_native(pos.section as usize, pos.paragraph as usize)
        })?;
        let reply = json(&reply)?;
        Ok(EditOutcome {
            caret: TextPos {
                section: pos.section,
                paragraph: uint(&reply, "paraIdx").unwrap_or(pos.paragraph - 1),
                offset: uint(&reply, "charOffset").unwrap_or(0),
            },
        })
    }

    fn delete_forward(&mut self, pos: TextPos) -> Result<EditOutcome> {
        let len = self.paragraph_len(pos.section, pos.paragraph)?;
        if pos.offset < len {
            let end = TextPos {
                offset: pos.offset + 1,
                ..pos
            };
            return self.delete_range(pos, end);
        }
        if pos.paragraph + 1 >= self.paragraph_count(pos.section)? {
            return Ok(EditOutcome { caret: pos });
        }
        self.begin_step(pos, None);
        self.write("merge paragraphs", |doc| {
            doc.merge_paragraph_native(pos.section as usize, pos.paragraph as usize + 1)
        })?;
        Ok(EditOutcome { caret: pos })
    }

    fn char_format(&mut self, pos: TextPos) -> Result<CharFormat> {
        // 커서는 앞 글자의 서식을 따른다(줄 처음이면 첫 글자).
        let offset = pos.offset.saturating_sub(1);
        let reply = self.read("read format", |doc| {
            doc.get_char_properties_at_native(
                pos.section as usize,
                pos.paragraph as usize,
                offset as usize,
            )
        })?;
        let value = json(&reply)?;
        let flag = |key: &str| value.get(key).and_then(Value::as_bool);
        Ok(CharFormat {
            font_family: value
                .get("fontFamily")
                .and_then(Value::as_str)
                .map(str::to_owned),
            font_size: float(&value, "fontSize").map(|size| size / 100.0),
            bold: flag("bold"),
            italic: flag("italic"),
            underline: flag("underline"),
            strikethrough: flag("strikethrough"),
            color: value
                .get("textColor")
                .and_then(Value::as_str)
                .map(str::to_owned),
        })
    }

    fn apply_char_format(
        &mut self,
        start: TextPos,
        end: TextPos,
        format: &CharFormat,
    ) -> Result<()> {
        Self::same_section(start, end)?;
        if start == end {
            return Ok(());
        }
        self.begin_step(start, None);
        let font_ids = match &format.font_family {
            Some(name) => {
                let name = name.clone();
                Some(self.write("set font", |doc| {
                    let mut ids = [0; 7];
                    for (lang, id) in ids.iter_mut().enumerate() {
                        *id = doc.find_or_create_font_id_for_lang(lang, &name);
                    }
                    Ok(ids)
                })?)
            }
            None => None,
        };
        let props = char_format_json(format, font_ids);
        for paragraph in start.paragraph..=end.paragraph {
            let from = if paragraph == start.paragraph {
                start.offset
            } else {
                0
            };
            let to = if paragraph == end.paragraph {
                end.offset
            } else {
                self.paragraph_len(start.section, paragraph)?
            };
            if to > from {
                let props = props.clone();
                self.write("apply format", |doc| {
                    doc.apply_char_format_native(
                        start.section as usize,
                        paragraph as usize,
                        from as usize,
                        to as usize,
                        &props,
                    )
                })?;
            }
        }
        Ok(())
    }

    fn para_format(&mut self, pos: TextPos) -> Result<ParaFormat> {
        let reply = self.read("read paragraph format", |doc| {
            doc.get_para_properties_at_native(pos.section as usize, pos.paragraph as usize)
        })?;
        let value = json(&reply)?;
        let alignment = match value.get("alignment").and_then(Value::as_str) {
            Some("center") => Alignment::Center,
            Some("right") => Alignment::Right,
            Some("justify") | Some("distribute") | Some("split") => Alignment::Justify,
            _ => Alignment::Left,
        };
        let percent = value.get("lineSpacingType").and_then(Value::as_str) == Some("Percent");
        Ok(ParaFormat {
            alignment: Some(alignment),
            line_spacing: percent.then(|| float(&value, "lineSpacing")).flatten(),
        })
    }

    fn apply_para_format(
        &mut self,
        start: TextPos,
        end: TextPos,
        format: &ParaFormat,
    ) -> Result<()> {
        Self::same_section(start, end)?;
        let mut props = serde_json::Map::new();
        if let Some(alignment) = format.alignment {
            props.insert("alignment".into(), alignment_name(alignment).into());
        }
        if let Some(spacing) = format.line_spacing {
            props.insert("lineSpacing".into(), spacing.into());
            props.insert("lineSpacingType".into(), "Percent".into());
        }
        let props = Value::Object(props).to_string();
        self.begin_step(start, None);
        for paragraph in start.paragraph..=end.paragraph {
            let props = props.clone();
            self.write("apply paragraph format", |doc| {
                doc.apply_para_format_native(start.section as usize, paragraph as usize, &props)
            })?;
        }
        Ok(())
    }

    fn undo(&mut self) -> Result<Option<EditOutcome>> {
        let Some(step) = self.undo.pop() else {
            return Ok(None);
        };
        let caret = step.caret;
        let redo = self.restore(step)?;
        self.redo.push(redo);
        Ok(Some(EditOutcome { caret }))
    }

    fn redo(&mut self) -> Result<Option<EditOutcome>> {
        let Some(step) = self.redo.pop() else {
            return Ok(None);
        };
        let caret = step.caret;
        let undo = self.restore(step)?;
        self.undo.push(undo);
        Ok(Some(EditOutcome { caret }))
    }
}
