//! Papy 문서 도구. AI 도우미(앱 안 대화창, MCP 서버)가 부르는 도구의 정의와 실행을 둔다.
//!
//! - 네트워크 코드는 없다. 앱과 플러그인(`papy-ai`)이 함께 쓴다.
//! - 문서를 고치는 도구는 바로 고치지 않고 [`Plan`](제안)을 만든다. 앱은 사용자가 미리보기에서
//!   **적용**을 누를 때 [`apply`] 로 고친다. 단독 MCP 서버는 메모리의 사본에 바로 적용하고, 사용자가
//!   지정한 새 파일로만 저장한다(원본은 덮어쓰지 않는다).
//! - 문단은 `"구역.문단"`(0부터, 예 `"0.12"`)으로 가리킨다.

pub mod rpc;

use std::path::{Path, PathBuf};

use papy_core::{
    Alignment, CharFormat, Document, DocumentSource, Engine, Error, ErrorCode, OpenOptions,
    ParaFormat, ParagraphText, Result, SaveFormat, SessionId, TextPos,
};
use serde::Serialize;
use serde_json::{Value, json};

/// 문단 안 개체(표·그림·글상자) 자리 표시.
pub const OBJECT_MARK: char = '\u{FFFC}';

/// 한 번에 돌려주는 글자 수 상한(대략). 넘으면 다음 시작 위치를 알려 준다.
const MAX_CHARS: usize = 60_000;

/// 도구를 어디서 쓰는가.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Mode {
    /// 앱 안 대화창: 앱에 열린 문서 하나를 다룬다. 고치기는 제안만 만든다.
    App,
    /// 단독 MCP 서버: 파일을 직접 열고, 문서 이름(`document`)으로 가리킨다.
    Standalone,
}

/// 도구 종류. 앱은 종류에 따라 확인 창·미리보기를 띄운다.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum ToolKind {
    /// 문서를 읽기만 한다.
    Read,
    /// 문서를 고친다(제안).
    Edit,
    /// 다른 형식의 새 파일로 내보낸다.
    Export,
    /// 단독 모드에서 문서를 열고 목록을 본다.
    Workspace,
}

/// 도구 정의(이름·설명·입력 JSON Schema).
#[derive(Debug, Clone, Serialize)]
pub struct ToolSpec {
    /// 도구 이름.
    pub name: &'static str,
    /// 모델에게 보여 줄 설명(영어).
    pub description: String,
    /// 입력 JSON Schema.
    pub input_schema: Value,
    /// 종류.
    pub kind: ToolKind,
}

fn object(properties: Value, required: &[&str]) -> Value {
    json!({
        "type": "object",
        "properties": properties,
        "required": required,
        "additionalProperties": false,
    })
}

const PARAGRAPH: &str = "Paragraph id \"section.paragraph\" from read_paragraphs, e.g. \"0.12\".";

/// 쓸 수 있는 도구 목록. 단독 모드는 모든 도구에 `document` 인자가 붙고 열기·목록·저장 도구가 더해진다.
pub fn specs(mode: Mode) -> Vec<ToolSpec> {
    let mut tools = vec![
        (
            "document_info",
            "Get the open document's format, page count, title and whether it can be edited.",
            json!({}),
            vec![],
            ToolKind::Read,
        ),
        (
            "read_paragraphs",
            "Read body paragraphs in order with their ids. Tables, pictures and text boxes show as U+FFFC. \
             hidden_objects counts footnotes, floating objects and settings anchored in a paragraph without a mark. \
             Use start/count to page through long documents; the reply gives next_start when more remain.",
            json!({
                "start": {"type": "integer", "minimum": 0, "description": "Index of the first paragraph (0-based). Default 0."},
                "count": {"type": "integer", "minimum": 1, "maximum": 400, "description": "How many paragraphs. Default 100."}
            }),
            vec![],
            ToolKind::Read,
        ),
        (
            "read_page_text",
            "Read the text of one page as laid out (includes tables and headers). Pages are 1-based.",
            json!({"page": {"type": "integer", "minimum": 1}}),
            vec!["page"],
            ToolKind::Read,
        ),
        (
            "find_text",
            "Find text in the document (case-insensitive). Returns pages (1-based) and surrounding text.",
            json!({"query": {"type": "string", "minLength": 1}}),
            vec!["query"],
            ToolKind::Read,
        ),
        (
            "replace_text",
            "Propose replacing exact text inside one paragraph. Keeps the surrounding formatting. \
             If the text occurs more than once, set all=true or make find longer.",
            json!({
                "paragraph": {"type": "string", "description": PARAGRAPH},
                "find": {"type": "string", "minLength": 1, "description": "Exact text to replace (case-sensitive)."},
                "replace": {"type": "string"},
                "all": {"type": "boolean", "description": "Replace every occurrence in the paragraph."}
            }),
            vec!["paragraph", "find", "replace"],
            ToolKind::Edit,
        ),
        (
            "rewrite_paragraph",
            "Propose replacing the whole text of one paragraph. Not allowed for paragraphs holding tables or pictures (U+FFFC) \
             or hidden_objects; use replace_text there. A newline in text starts a new paragraph.",
            json!({
                "paragraph": {"type": "string", "description": PARAGRAPH},
                "text": {"type": "string"}
            }),
            vec!["paragraph", "text"],
            ToolKind::Edit,
        ),
        (
            "insert_paragraphs",
            "Propose inserting new paragraphs before or after a paragraph. Newlines in text separate paragraphs.",
            json!({
                "paragraph": {"type": "string", "description": PARAGRAPH},
                "position": {"type": "string", "enum": ["before", "after"], "description": "Default after."},
                "text": {"type": "string", "minLength": 1}
            }),
            vec!["paragraph", "text"],
            ToolKind::Edit,
        ),
        (
            "format_text",
            "Propose character formatting for text in one paragraph (the whole paragraph when find is omitted). \
             Only the given properties change.",
            json!({
                "paragraph": {"type": "string", "description": PARAGRAPH},
                "find": {"type": "string", "minLength": 1, "description": "Exact text to format. Omit for the whole paragraph."},
                "all": {"type": "boolean"},
                "bold": {"type": "boolean"},
                "italic": {"type": "boolean"},
                "underline": {"type": "boolean"},
                "strikethrough": {"type": "boolean"},
                "color": {"type": "string", "pattern": "^#[0-9a-fA-F]{6}$"},
                "font_size": {"type": "number", "minimum": 1, "maximum": 400, "description": "Points."}
            }),
            vec!["paragraph"],
            ToolKind::Edit,
        ),
        (
            "align_paragraph",
            "Propose paragraph alignment and/or line spacing for one paragraph.",
            json!({
                "paragraph": {"type": "string", "description": PARAGRAPH},
                "alignment": {"type": "string", "enum": ["left", "center", "right", "justify"]},
                "line_spacing": {"type": "number", "minimum": 50, "maximum": 500, "description": "Percent, e.g. 160."}
            }),
            vec!["paragraph"],
            ToolKind::Edit,
        ),
    ];
    match mode {
        Mode::App => tools.push((
            "export_copy",
            "Propose saving a copy in another format (hwpx, hwp, docx, pdf, txt). The user picks where to save; \
             the open document is not changed.",
            json!({
                "format": {"type": "string", "enum": ["hwpx", "hwp", "docx", "pdf", "txt"]}
            }),
            vec!["format"],
            ToolKind::Export,
        )),
        Mode::Standalone => {
            tools.insert(
                0,
                (
                    "open_document",
                    "Open a PDF, HWP, HWPX, DOCX or DOC file by absolute path. Returns the document name other tools take. \
                     Edits change only Papy's in-memory copy until save_copy writes a new file.",
                    json!({
                        "path": {"type": "string", "description": "Absolute file path."},
                        "password": {"type": "string"}
                    }),
                    vec!["path"],
                    ToolKind::Workspace,
                ),
            );
            tools.insert(
                1,
                (
                    "list_documents",
                    "List documents opened with open_document.",
                    json!({}),
                    vec![],
                    ToolKind::Workspace,
                ),
            );
            tools.push((
                "save_copy",
                "Save the (edited) document to a NEW file. The format comes from the extension (hwpx, hwp, docx, pdf, txt). \
                 Never overwrites the original file; overwriting another existing file needs overwrite=true.",
                json!({
                    "path": {"type": "string", "description": "Absolute path of the new file."},
                    "overwrite": {"type": "boolean"}
                }),
                vec!["path"],
                ToolKind::Export,
            ));
        }
    }
    tools
        .into_iter()
        .map(|(name, description, mut properties, mut required, kind)| {
            if mode == Mode::Standalone && !matches!(name, "open_document" | "list_documents") {
                properties["document"] =
                    json!({"type": "string", "description": "Document name from open_document."});
                required.insert(0, "document");
            }
            ToolSpec {
                name,
                description: description.to_owned(),
                input_schema: object(properties, &required),
                kind,
            }
        })
        .collect()
}

/// 이름으로 도구 종류를 찾는다.
pub fn kind_of(mode: Mode, name: &str) -> Option<ToolKind> {
    specs(mode)
        .into_iter()
        .find(|tool| tool.name == name)
        .map(|tool| tool.kind)
}

fn bad(message: impl Into<String>) -> Error {
    Error::new(ErrorCode::InvalidArgument, message)
}

fn str_arg<'a>(args: &'a Value, key: &str) -> Result<&'a str> {
    args.get(key)
        .and_then(Value::as_str)
        .ok_or_else(|| bad(format!("missing string argument `{key}`")))
}

fn opt_str<'a>(args: &'a Value, key: &str) -> Option<&'a str> {
    args.get(key).and_then(Value::as_str)
}

fn opt_bool(args: &Value, key: &str) -> Option<bool> {
    args.get(key).and_then(Value::as_bool)
}

fn opt_u64(args: &Value, key: &str) -> Option<u64> {
    args.get(key).and_then(Value::as_u64)
}

fn opt_f64(args: &Value, key: &str) -> Option<f64> {
    args.get(key).and_then(Value::as_f64)
}

/// `"구역.문단"` → (구역, 문단).
pub fn parse_paragraph(id: &str) -> Result<(u32, u32)> {
    let (section, paragraph) = id
        .trim()
        .split_once('.')
        .ok_or_else(|| bad(format!("paragraph id `{id}` must look like \"0.12\"")))?;
    let section = section
        .parse()
        .map_err(|_| bad(format!("bad paragraph id `{id}`")))?;
    let paragraph = paragraph
        .parse()
        .map_err(|_| bad(format!("bad paragraph id `{id}`")))?;
    Ok((section, paragraph))
}

fn paragraph_id(section: u32, paragraph: u32) -> String {
    format!("{section}.{paragraph}")
}

fn char_len(text: &str) -> u32 {
    text.chars().count() as u32
}

/// 문단 안에서 `find` 가 나오는 글자 위치(시작, 끝)들. 겹치지 않게 앞에서부터 찾는다.
fn occurrences(text: &str, find: &str) -> Vec<(u32, u32)> {
    let mut out = Vec::new();
    let mut from = 0;
    while let Some(at) = text[from..].find(find) {
        let byte = from + at;
        let start = char_len(&text[..byte]);
        out.push((start, start + char_len(find)));
        from = byte + find.len();
    }
    out
}

// ---- 읽기 ----

/// 문서 요약.
pub fn document_info(engine: &Engine, id: SessionId) -> Result<Value> {
    let info = engine.info(id)?;
    let paragraphs = engine.paragraphs(id).map(|list| list.len()).ok();
    Ok(json!({
        "format": info.format.name(),
        "pages": info.page_count,
        "title": info.title,
        "editable": info.capabilities.edit && paragraphs.is_some(),
        "paragraphs": paragraphs,
        "save_formats": info.capabilities.save_formats.iter().map(|f| f.extension()).collect::<Vec<_>>(),
        "dropped_objects": info.dropped_objects,
    }))
}

/// 문단 목록 한 묶음.
pub fn read_paragraphs(engine: &Engine, id: SessionId, args: &Value) -> Result<Value> {
    let all = engine.paragraphs(id).map_err(|error| {
        if error.code == ErrorCode::NotImplemented {
            bad("this document has no editable paragraphs (PDF); use read_page_text")
        } else {
            error
        }
    })?;
    let start = opt_u64(args, "start").unwrap_or(0) as usize;
    let count = opt_u64(args, "count").unwrap_or(100).clamp(1, 400) as usize;
    let mut chars = 0;
    let mut items = Vec::new();
    let mut next = None;
    for (index, para) in all.iter().enumerate().skip(start) {
        if items.len() >= count || (chars > MAX_CHARS && !items.is_empty()) {
            next = Some(index);
            break;
        }
        chars += para.text.len();
        let mut item =
            json!({ "id": paragraph_id(para.section, para.paragraph), "text": para.text });
        if !para.anchors.is_empty() {
            item["hidden_objects"] = json!(para.anchors.len());
        }
        items.push(item);
    }
    Ok(json!({ "total": all.len(), "start": start, "paragraphs": items, "next_start": next }))
}

/// 쪽 글.
pub fn read_page_text(engine: &Engine, id: SessionId, args: &Value) -> Result<Value> {
    let page = opt_u64(args, "page").ok_or_else(|| bad("missing integer argument `page`"))?;
    let pages = engine.page_count(id)?;
    if page == 0 || page > u64::from(pages) {
        return Err(bad(format!("page must be between 1 and {pages}")));
    }
    let mut text = engine.extract_text(id, page as u32 - 1)?;
    if text.len() > MAX_CHARS {
        let cut = (0..=MAX_CHARS)
            .rev()
            .find(|i| text.is_char_boundary(*i))
            .unwrap_or(0);
        text.truncate(cut);
        text.push_str("\n[truncated]");
    }
    Ok(json!({ "page": page, "pages": pages, "text": text }))
}

/// 글자 찾기.
pub fn find_text(engine: &Engine, id: SessionId, args: &Value) -> Result<Value> {
    let query = str_arg(args, "query")?;
    let hits = engine.find_text(id, query, 101, &papy_core::CancelToken::new())?;
    let more = hits.len() > 100;
    let matches: Vec<Value> = hits
        .into_iter()
        .take(100)
        .map(|hit| json!({ "page": hit.page + 1, "before": hit.before, "matched": hit.matched, "after": hit.after }))
        .collect();
    Ok(json!({ "matches": matches, "more": more }))
}

// ---- 고치기 제안 ----

#[derive(Debug, Clone)]
enum Op {
    Delete {
        start: TextPos,
        end: TextPos,
    },
    Insert {
        pos: TextPos,
        text: String,
    },
    CharFormat {
        start: TextPos,
        end: TextPos,
        format: CharFormat,
    },
    ParaFormat {
        start: TextPos,
        end: TextPos,
        format: ParaFormat,
    },
}

/// 미리보기 한 줄: 문단 하나가 어떻게 바뀌는가.
#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct Change {
    /// 문단 id.
    pub paragraph: String,
    /// 바꾸기 전 글.
    pub before: String,
    /// 바꾼 뒤 글(서식만 바꾸면 같다).
    pub after: String,
}

/// 고치기 제안. 앱은 `summary`·`changes` 를 미리보기로 보여 주고, 적용하면 [`apply`] 를 부른다.
#[derive(Debug, Clone, Serialize)]
pub struct Plan {
    /// 제안을 만든 도구.
    pub tool: String,
    /// 한 줄 요약(영어, 모델에게도 돌려준다).
    pub summary: String,
    /// 바뀌는 문단.
    pub changes: Vec<Change>,
    #[serde(skip)]
    ops: Vec<Op>,
    /// 적용할 때 그대로여야 하는 문단(위치, 글).
    #[serde(skip)]
    guard: (u32, u32, String),
}

fn find_paragraph(all: &[ParagraphText], section: u32, paragraph: u32) -> Result<&ParagraphText> {
    all.iter()
        .find(|p| p.section == section && p.paragraph == paragraph)
        .ok_or_else(|| {
            bad(format!(
                "paragraph {} does not exist",
                paragraph_id(section, paragraph)
            ))
        })
}

fn ranges(text: &str, find: Option<&str>, all: bool) -> Result<Vec<(u32, u32)>> {
    let Some(find) = find else {
        return Ok(vec![(0, char_len(text))]);
    };
    if find.contains(OBJECT_MARK) {
        return Err(bad(
            "find must not contain U+FFFC (tables and pictures cannot be edited this way)",
        ));
    }
    let found = occurrences(text, find);
    match found.len() {
        0 => Err(bad(format!("`{find}` was not found in the paragraph"))),
        1 => Ok(found),
        _ if all => Ok(found),
        n => Err(bad(format!(
            "`{find}` occurs {n} times; set all=true or make find longer"
        ))),
    }
}

/// `start..end` 의 글을 `text` 로 바꾸는 단계: 새 글을 `end` 에 넣은 뒤 `start..end` 를 지운다.
/// 먼저 지우면 문단 끝·문단 전체를 지우게 되어 그 자리의 숨은 개체(떠 있는 표 등)까지 지워진다.
fn swap(ops: &mut Vec<Op>, start: TextPos, end: TextPos, text: &str) {
    if !text.is_empty() {
        ops.push(Op::Insert {
            pos: end,
            text: text.to_owned(),
        });
    }
    if start != end {
        ops.push(Op::Delete { start, end });
    }
}

/// `start..end` 를 지우면 숨은 개체가 함께 지워지는가. 범위 처음·안쪽 자리, 그리고 문단 끝까지 지울 때
/// 끝 자리의 개체(떠 있는 표·그림·각주)가 지워진다.
/// `len` 은 지울 때의 문단 길이(새 글을 먼저 넣었으면 넣은 뒤 길이).
fn endangers(anchors: &[u32], start: u32, end: u32, len: u32) -> bool {
    start < end
        && anchors
            .iter()
            .any(|&p| (start <= p && p < end) || (p == end && end == len))
}

/// 고치기 도구 인자로 제안을 만든다. 문서는 바꾸지 않는다.
pub fn plan(engine: &Engine, id: SessionId, tool: &str, args: &Value) -> Result<Plan> {
    if !engine.info(id)?.capabilities.edit {
        return Err(bad("this document cannot be edited"));
    }
    let all = engine.paragraphs(id)?;
    let (section, paragraph) = parse_paragraph(str_arg(args, "paragraph")?)?;
    let target = find_paragraph(&all, section, paragraph)?;
    let text = target.text.clone();
    let len = char_len(&text);
    let at = |offset: u32| TextPos {
        section,
        paragraph,
        offset,
    };
    let pid = paragraph_id(section, paragraph);
    let mut ops = Vec::new();
    let (summary, after) = match tool {
        "replace_text" => {
            let find = str_arg(args, "find")?;
            let replace = str_arg(args, "replace")?;
            if replace.contains(OBJECT_MARK) {
                return Err(bad("replace must not contain U+FFFC"));
            }
            let found = ranges(&text, Some(find), opt_bool(args, "all").unwrap_or(false))?;
            // 새 글을 먼저 끝에 넣고 옛 글을 지운다: 지우는 범위가 문단 끝·문단 전체가 되지 않아 개체가 남는다.
            let after_insert = len + char_len(replace);
            for &(start, end) in &found {
                if endangers(&target.anchors, start, end, after_insert) {
                    return Err(bad(format!(
                        "`{find}` spans a footnote, floating table, picture or other object anchored in paragraph {pid}; \
                         replace a shorter piece of text before or after it"
                    )));
                }
            }
            for &(start, end) in found.iter().rev() {
                swap(&mut ops, at(start), at(end), replace);
            }
            (
                format!(
                    "replace {} occurrence(s) of \"{find}\" in paragraph {pid}",
                    found.len()
                ),
                text.replace(find, replace),
            )
        }
        "rewrite_paragraph" => {
            let new = str_arg(args, "text")?;
            if text.contains(OBJECT_MARK) {
                return Err(bad(
                    "this paragraph holds a table or picture; use replace_text on its text instead",
                ));
            }
            if endangers(&target.anchors, 0, len, len + char_len(new)) {
                return Err(bad(
                    "this paragraph holds a footnote, floating table, picture or other object; use replace_text on its text instead",
                ));
            }
            if new.contains(OBJECT_MARK) {
                return Err(bad("text must not contain U+FFFC"));
            }
            swap(&mut ops, at(0), at(len), new);
            (format!("rewrite paragraph {pid}"), new.to_owned())
        }
        "insert_paragraphs" => {
            let new = str_arg(args, "text")?.trim_end_matches('\n');
            if new.is_empty() {
                return Err(bad("text is empty"));
            }
            if new.contains(OBJECT_MARK) {
                return Err(bad("text must not contain U+FFFC"));
            }
            let before = opt_str(args, "position") == Some("before");
            let (pos, insert, after) = if before {
                (at(0), format!("{new}\n"), format!("{new}\n{text}"))
            } else {
                (at(len), format!("\n{new}"), format!("{text}\n{new}"))
            };
            ops.push(Op::Insert { pos, text: insert });
            let count = new.split('\n').count();
            let side = if before { "before" } else { "after" };
            (
                format!("insert {count} paragraph(s) {side} paragraph {pid}"),
                after,
            )
        }
        "format_text" => {
            let format = CharFormat {
                font_family: None,
                font_size: opt_f64(args, "font_size").map(|size| size as f32),
                bold: opt_bool(args, "bold"),
                italic: opt_bool(args, "italic"),
                underline: opt_bool(args, "underline"),
                strikethrough: opt_bool(args, "strikethrough"),
                color: opt_str(args, "color").map(str::to_owned),
            };
            if format == CharFormat::default() {
                return Err(bad(
                    "give at least one of bold, italic, underline, strikethrough, color, font_size",
                ));
            }
            if len == 0 {
                return Err(bad("the paragraph is empty"));
            }
            let found = ranges(
                &text,
                opt_str(args, "find"),
                opt_bool(args, "all").unwrap_or(false),
            )?;
            for &(start, end) in &found {
                ops.push(Op::CharFormat {
                    start: at(start),
                    end: at(end),
                    format: format.clone(),
                });
            }
            let mut what = Vec::new();
            for (name, value) in [
                ("bold", format.bold),
                ("italic", format.italic),
                ("underline", format.underline),
                ("strikethrough", format.strikethrough),
            ] {
                if let Some(on) = value {
                    what.push(if on {
                        name.to_owned()
                    } else {
                        format!("no {name}")
                    });
                }
            }
            if let Some(color) = &format.color {
                what.push(format!("color {color}"));
            }
            if let Some(size) = format.font_size {
                what.push(format!("{size}pt"));
            }
            let target =
                opt_str(args, "find").map_or("whole paragraph".to_owned(), |f| format!("\"{f}\""));
            (
                format!("format {target} in paragraph {pid}: {}", what.join(", ")),
                text.clone(),
            )
        }
        "align_paragraph" => {
            let alignment = match opt_str(args, "alignment") {
                None => None,
                Some("left") => Some(Alignment::Left),
                Some("center") => Some(Alignment::Center),
                Some("right") => Some(Alignment::Right),
                Some("justify") => Some(Alignment::Justify),
                Some(other) => return Err(bad(format!("unknown alignment `{other}`"))),
            };
            let line_spacing = opt_f64(args, "line_spacing").map(|value| value as f32);
            if alignment.is_none() && line_spacing.is_none() {
                return Err(bad("give alignment and/or line_spacing"));
            }
            ops.push(Op::ParaFormat {
                start: at(0),
                end: at(len),
                format: ParaFormat {
                    alignment,
                    line_spacing,
                },
            });
            let mut what = Vec::new();
            if let Some(name) = opt_str(args, "alignment") {
                what.push(format!("align {name}"));
            }
            if let Some(value) = line_spacing {
                what.push(format!("line spacing {value}%"));
            }
            (
                format!("{} for paragraph {pid}", what.join(", ")),
                text.clone(),
            )
        }
        other => return Err(bad(format!("`{other}` is not an editing tool"))),
    };
    Ok(Plan {
        tool: tool.to_owned(),
        summary,
        changes: vec![Change {
            paragraph: pid,
            before: text.clone(),
            after,
        }],
        ops,
        guard: (section, paragraph, text),
    })
}

fn run(doc: &mut dyn Document, op: &Op) -> Result<()> {
    match op {
        Op::Delete { start, end } => doc.delete_range(*start, *end).map(drop),
        Op::Insert { pos, text } => doc.insert_text(*pos, text).map(drop),
        Op::CharFormat { start, end, format } => doc.apply_char_format(*start, *end, format),
        Op::ParaFormat { start, end, format } => doc.apply_para_format(*start, *end, format),
    }
}

/// 제안을 적용한다. 그사이 그 문단이 바뀌었으면 `Conflict` 오류로 아무것도 하지 않는다.
/// 적용한 단계는 문서의 되돌리기 목록에 쌓인다.
pub fn apply(engine: &Engine, id: SessionId, plan: &Plan) -> Result<()> {
    let (section, paragraph, ref expected) = plan.guard;
    engine.edit(id, |doc| {
        let all = doc.paragraphs()?;
        let current = find_paragraph(&all, section, paragraph)?;
        if &current.text != expected {
            return Err(Error::new(
                ErrorCode::InvalidArgument,
                "the paragraph changed after this proposal was made; ask again",
            ));
        }
        for op in &plan.ops {
            run(doc, op)?;
        }
        Ok(())
    })
}

/// 모델에게 돌려줄 제안 결과(앱 모드).
pub fn plan_reply(plan: &Plan, proposal: u64) -> Value {
    json!({
        "status": "proposed",
        "proposal": proposal,
        "summary": plan.summary,
        "note": "The user will review and apply or reject this change in Papy. Do not assume it is applied.",
    })
}

// ---- 내보내기 ----

/// `export_copy` 인자의 형식.
pub fn export_format(engine: &Engine, id: SessionId, args: &Value) -> Result<SaveFormat> {
    let name = str_arg(args, "format")?;
    let format =
        SaveFormat::from_extension(name).ok_or_else(|| bad(format!("unknown format `{name}`")))?;
    if !engine.info(id)?.capabilities.save_formats.contains(&format) {
        return Err(bad(format!("this document cannot be saved as {name}")));
    }
    Ok(format)
}

// ---- 단독 모드(MCP) ----

/// 단독 MCP 서버의 작업 공간: 파일을 열어 이름으로 다룬다.
pub struct Workspace {
    engine: Engine,
    docs: Vec<OpenDoc>,
}

struct OpenDoc {
    name: String,
    id: SessionId,
    path: PathBuf,
    edited: bool,
}

impl Workspace {
    /// 엔진으로 작업 공간을 만든다.
    pub fn new(engine: Engine) -> Self {
        Self {
            engine,
            docs: Vec::new(),
        }
    }

    fn doc(&self, args: &Value) -> Result<&OpenDoc> {
        let name = str_arg(args, "document")?;
        self.docs
            .iter()
            .find(|doc| doc.name == name)
            .ok_or_else(|| {
                bad(format!(
                    "no open document named `{name}`; call open_document first"
                ))
            })
    }

    fn unique_name(&self, path: &Path) -> String {
        let base = path
            .file_name()
            .map(|name| name.to_string_lossy().into_owned())
            .unwrap_or_else(|| "document".to_owned());
        let mut name = base.clone();
        let mut n = 2;
        while self.docs.iter().any(|doc| doc.name == name) {
            name = format!("{base} ({n})");
            n += 1;
        }
        name
    }

    /// 도구를 실행한다.
    pub fn call(&mut self, tool: &str, args: &Value) -> Result<Value> {
        match tool {
            "open_document" => {
                let path = PathBuf::from(str_arg(args, "path")?);
                if !path.is_absolute() {
                    return Err(bad("path must be absolute"));
                }
                if !path.is_file() {
                    return Err(Error::new(ErrorCode::Io, "file not found"));
                }
                let options = OpenOptions {
                    password: opt_str(args, "password").map(str::to_owned),
                    ..OpenOptions::default()
                };
                let id = self
                    .engine
                    .open(DocumentSource::Path(path.clone()), options)?;
                let name = self.unique_name(&path);
                self.docs.push(OpenDoc {
                    name: name.clone(),
                    id,
                    path,
                    edited: false,
                });
                let mut info = document_info(&self.engine, id)?;
                info["document"] = json!(name);
                Ok(info)
            }
            "list_documents" => Ok(json!({
                "documents": self
                    .docs
                    .iter()
                    .map(|doc| json!({ "document": doc.name, "path": doc.path, "edited": doc.edited }))
                    .collect::<Vec<_>>()
            })),
            "document_info" => document_info(&self.engine, self.doc(args)?.id),
            "read_paragraphs" => read_paragraphs(&self.engine, self.doc(args)?.id, args),
            "read_page_text" => read_page_text(&self.engine, self.doc(args)?.id, args),
            "find_text" => find_text(&self.engine, self.doc(args)?.id, args),
            "replace_text" | "rewrite_paragraph" | "insert_paragraphs" | "format_text"
            | "align_paragraph" => {
                let id = self.doc(args)?.id;
                let plan = plan(&self.engine, id, tool, args)?;
                apply(&self.engine, id, &plan)?;
                let name = str_arg(args, "document")?.to_owned();
                if let Some(doc) = self.docs.iter_mut().find(|doc| doc.name == name) {
                    doc.edited = true;
                }
                Ok(json!({
                    "status": "applied",
                    "summary": plan.summary,
                    "changes": plan.changes,
                    "note": "Applied to Papy's in-memory copy. Call save_copy to write a new file.",
                }))
            }
            "save_copy" => {
                let doc = self.doc(args)?;
                let path = PathBuf::from(str_arg(args, "path")?);
                if !path.is_absolute() {
                    return Err(bad("path must be absolute"));
                }
                let same = |a: &Path, b: &Path| {
                    a == b
                        || matches!((a.canonicalize(), b.canonicalize()), (Ok(x), Ok(y)) if x == y)
                };
                if self.docs.iter().any(|open| same(&open.path, &path)) {
                    return Err(bad(
                        "refusing to overwrite an original document; choose a new file name",
                    ));
                }
                if path.exists() && !opt_bool(args, "overwrite").unwrap_or(false) {
                    return Err(bad("the file exists; set overwrite=true to replace it"));
                }
                let extension = path
                    .extension()
                    .and_then(|ext| ext.to_str())
                    .unwrap_or_default();
                let format = SaveFormat::from_extension(extension).ok_or_else(|| {
                    bad("the file name must end in .hwpx, .hwp, .docx, .pdf or .txt")
                })?;
                if !self
                    .engine
                    .info(doc.id)?
                    .capabilities
                    .save_formats
                    .contains(&format)
                {
                    return Err(bad(format!("this document cannot be saved as {extension}")));
                }
                let bytes = self.engine.save(doc.id, format)?;
                std::fs::write(&path, &bytes)?;
                Ok(json!({ "status": "saved", "path": path, "bytes": bytes.len() }))
            }
            other => Err(bad(format!("unknown tool `{other}`"))),
        }
    }
}

/// 앱 모드 읽기 도구를 실행한다(고치기·내보내기는 앱이 따로 처리한다).
pub fn call_read(engine: &Engine, id: SessionId, tool: &str, args: &Value) -> Result<Value> {
    match tool {
        "document_info" => document_info(engine, id),
        "read_paragraphs" => read_paragraphs(engine, id, args),
        "read_page_text" => read_page_text(engine, id, args),
        "find_text" => find_text(engine, id, args),
        other => Err(bad(format!("`{other}` is not a reading tool"))),
    }
}
