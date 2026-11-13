//! 문서 도구 점검: 저장소의 공개 문서(tests/fixtures)로 읽기·제안·적용·되돌리기·저장을 돌린다.

use std::path::{Path, PathBuf};

use papy_core::{DocumentSource, Engine, Limits, OpenOptions};
use papy_tools::{Mode, ToolKind, Workspace, apply, call_read, kind_of, plan, specs};
use serde_json::{Value, json};

fn fixture(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../../tests/fixtures")
        .join(name)
}

fn open(engine: &Engine, name: &str) -> papy_core::SessionId {
    engine
        .open(DocumentSource::Path(fixture(name)), OpenOptions::default())
        .unwrap()
}

/// 글자가 있고 개체가 없는 첫 문단(id, 글).
fn first_text_paragraph(engine: &Engine, id: papy_core::SessionId) -> (String, String) {
    let reply = call_read(engine, id, "read_paragraphs", &json!({})).unwrap();
    reply["paragraphs"]
        .as_array()
        .unwrap()
        .iter()
        .map(|p| {
            (
                p["id"].as_str().unwrap().to_owned(),
                p["text"].as_str().unwrap().to_owned(),
            )
        })
        .find(|(_, text)| text.chars().count() > 8 && !text.contains('\u{FFFC}'))
        .expect("a plain paragraph")
}

fn paragraph_text(engine: &Engine, id: papy_core::SessionId, pid: &str) -> String {
    let reply = call_read(engine, id, "read_paragraphs", &json!({ "count": 400 })).unwrap();
    reply["paragraphs"]
        .as_array()
        .unwrap()
        .iter()
        .find(|p| p["id"] == pid)
        .map(|p| p["text"].as_str().unwrap().to_owned())
        .unwrap()
}

#[test]
fn schemas_are_strict_objects_and_modes_differ() {
    for mode in [Mode::App, Mode::Standalone] {
        for tool in specs(mode) {
            assert_eq!(tool.input_schema["type"], "object", "{}", tool.name);
            assert_eq!(
                tool.input_schema["additionalProperties"], false,
                "{}",
                tool.name
            );
            for required in tool.input_schema["required"].as_array().unwrap() {
                let key = required.as_str().unwrap();
                assert!(
                    tool.input_schema["properties"].get(key).is_some(),
                    "{}: {key}",
                    tool.name
                );
            }
        }
    }
    assert_eq!(kind_of(Mode::App, "export_copy"), Some(ToolKind::Export));
    assert_eq!(kind_of(Mode::App, "save_copy"), None);
    assert_eq!(
        kind_of(Mode::Standalone, "open_document"),
        Some(ToolKind::Workspace)
    );
    assert_eq!(kind_of(Mode::App, "replace_text"), Some(ToolKind::Edit));
    let standalone = specs(Mode::Standalone);
    let replace = standalone
        .iter()
        .find(|t| t.name == "replace_text")
        .unwrap();
    assert_eq!(replace.input_schema["required"][0], "document");
}

#[test]
fn read_tools_work_on_word_document() {
    let engine = Engine::new(Limits::default());
    let id = open(&engine, "poi/DiffFirstPageHeadFoot.doc");
    let info = call_read(&engine, id, "document_info", &json!({})).unwrap();
    assert_eq!(info["format"], "DOC");
    assert!(info["pages"].as_u64().unwrap() >= 1);
    assert_eq!(info["editable"], true);

    let first = call_read(&engine, id, "read_paragraphs", &json!({ "count": 2 })).unwrap();
    assert_eq!(
        first["paragraphs"].as_array().unwrap().len().min(2),
        first["paragraphs"].as_array().unwrap().len()
    );
    let page = call_read(&engine, id, "read_page_text", &json!({ "page": 1 })).unwrap();
    assert!(!page["text"].as_str().unwrap().is_empty());
    assert!(call_read(&engine, id, "read_page_text", &json!({ "page": 0 })).is_err());
    assert!(call_read(&engine, id, "read_page_text", &json!({ "page": 9999 })).is_err());

    let (_, text) = first_text_paragraph(&engine, id);
    let word: String = text.chars().take(4).collect();
    let found = call_read(&engine, id, "find_text", &json!({ "query": word })).unwrap();
    assert!(!found["matches"].as_array().unwrap().is_empty());
}

#[test]
fn proposals_apply_undo_and_detect_conflicts() {
    let engine = Engine::new(Limits::default());
    let id = open(&engine, "poi/DiffFirstPageHeadFoot.doc");
    let (pid, text) = first_text_paragraph(&engine, id);
    let word: String = text.chars().take(3).collect();

    // 제안만 만들면 문서는 그대로다.
    let replace = plan(
        &engine,
        id,
        "replace_text",
        &json!({ "paragraph": pid, "find": word, "replace": "XYZ", "all": true }),
    )
    .unwrap();
    assert_eq!(replace.changes[0].before, text);
    assert_eq!(replace.changes[0].after, text.replace(&word, "XYZ"));
    assert_eq!(paragraph_text(&engine, id, &pid), text);

    apply(&engine, id, &replace).unwrap();
    assert_eq!(
        paragraph_text(&engine, id, &pid),
        text.replace(&word, "XYZ")
    );

    // 같은 제안을 다시 적용하면 문단이 바뀌어서 거절한다.
    assert!(apply(&engine, id, &replace).is_err());

    // 되돌리면 원래 글로 돌아온다.
    while paragraph_text(&engine, id, &pid) != text {
        let undone = engine.edit(id, |doc| doc.undo()).unwrap();
        assert!(
            undone.is_some(),
            "undo ran out before restoring the paragraph"
        );
    }

    let rewrite = plan(
        &engine,
        id,
        "rewrite_paragraph",
        &json!({ "paragraph": pid, "text": "새 문단 글입니다." }),
    )
    .unwrap();
    apply(&engine, id, &rewrite).unwrap();
    assert_eq!(paragraph_text(&engine, id, &pid), "새 문단 글입니다.");

    let insert = plan(
        &engine,
        id,
        "insert_paragraphs",
        &json!({ "paragraph": pid, "text": "하나\n둘" }),
    )
    .unwrap();
    let before = call_read(&engine, id, "read_paragraphs", &json!({})).unwrap()["total"]
        .as_u64()
        .unwrap();
    apply(&engine, id, &insert).unwrap();
    let after = call_read(&engine, id, "read_paragraphs", &json!({})).unwrap()["total"]
        .as_u64()
        .unwrap();
    assert_eq!(after, before + 2);

    let format = plan(
        &engine,
        id,
        "format_text",
        &json!({ "paragraph": pid, "find": "문단", "bold": true }),
    )
    .unwrap();
    apply(&engine, id, &format).unwrap();
    let align = plan(
        &engine,
        id,
        "align_paragraph",
        &json!({ "paragraph": pid, "alignment": "center" }),
    )
    .unwrap();
    apply(&engine, id, &align).unwrap();
    assert_eq!(paragraph_text(&engine, id, &pid), "새 문단 글입니다.");
}

#[test]
fn bad_arguments_are_rejected_with_reasons() {
    let engine = Engine::new(Limits::default());
    let id = open(&engine, "poi/DiffFirstPageHeadFoot.doc");
    let (pid, _) = first_text_paragraph(&engine, id);
    let err = |args: Value, tool: &str| plan(&engine, id, tool, &args).unwrap_err().message;
    assert!(
        err(
            json!({ "paragraph": "99.0", "find": "a", "replace": "b" }),
            "replace_text"
        )
        .contains("does not exist")
    );
    assert!(
        err(
            json!({ "paragraph": "x", "find": "a", "replace": "b" }),
            "replace_text"
        )
        .contains("0.12")
    );
    assert!(
        err(
            json!({ "paragraph": pid, "find": "없는글자ZZQ", "replace": "b" }),
            "replace_text"
        )
        .contains("not found")
    );
    assert!(err(json!({ "paragraph": pid }), "format_text").contains("at least one"));
    assert!(
        err(
            json!({ "paragraph": pid, "alignment": "middle" }),
            "align_paragraph"
        )
        .contains("unknown alignment")
    );
    assert!(
        err(json!({ "paragraph": pid, "text": "a" }), "read_paragraphs")
            .contains("not an editing tool")
    );
}

#[test]
fn standalone_workspace_edits_copy_and_never_overwrites_original() {
    let dir = std::env::temp_dir().join(format!("papy-tools-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let original = dir.join("원본.doc");
    std::fs::copy(fixture("poi/DiffFirstPageHeadFoot.doc"), &original).unwrap();
    let before = std::fs::read(&original).unwrap();

    let mut ws = Workspace::new(Engine::new(Limits::default()));
    assert!(
        ws.call("open_document", &json!({ "path": "relative.doc" }))
            .is_err()
    );
    let opened = ws
        .call("open_document", &json!({ "path": original }))
        .unwrap();
    let name = opened["document"].as_str().unwrap().to_owned();
    assert_eq!(name, "원본.doc");

    let paragraphs = ws
        .call("read_paragraphs", &json!({ "document": name }))
        .unwrap();
    let (pid, text) = paragraphs["paragraphs"]
        .as_array()
        .unwrap()
        .iter()
        .map(|p| {
            (
                p["id"].as_str().unwrap().to_owned(),
                p["text"].as_str().unwrap().to_owned(),
            )
        })
        .find(|(_, t)| t.chars().count() > 8 && !t.contains('\u{FFFC}'))
        .unwrap();
    let word: String = text.chars().take(3).collect();
    let edited = ws
        .call("replace_text", &json!({ "document": name, "paragraph": pid, "find": word, "replace": "바꿈", "all": true }))
        .unwrap();
    assert_eq!(edited["status"], "applied");

    // 원본 경로로는 저장하지 않는다.
    assert!(
        ws.call("save_copy", &json!({ "document": name, "path": original }))
            .is_err()
    );
    let copy = dir.join("사본.hwpx");
    let saved = ws
        .call("save_copy", &json!({ "document": name, "path": copy }))
        .unwrap();
    assert_eq!(saved["status"], "saved");
    // 있는 파일은 overwrite 없이는 덮지 않는다.
    assert!(
        ws.call("save_copy", &json!({ "document": name, "path": copy }))
            .is_err()
    );
    assert!(
        ws.call(
            "save_copy",
            &json!({ "document": name, "path": copy, "overwrite": true })
        )
        .is_ok()
    );
    assert!(
        ws.call(
            "save_copy",
            &json!({ "document": name, "path": dir.join("x.xyz") })
        )
        .is_err()
    );
    assert_eq!(
        std::fs::read(&original).unwrap(),
        before,
        "original must stay untouched"
    );

    // 저장한 사본을 다시 열면 바꾼 글이 있다.
    let reopened = ws.call("open_document", &json!({ "path": copy })).unwrap();
    let copy_name = reopened["document"].as_str().unwrap().to_owned();
    let found = ws
        .call(
            "find_text",
            &json!({ "document": copy_name, "query": "바꿈" }),
        )
        .unwrap();
    assert!(!found["matches"].as_array().unwrap().is_empty());
    let listed = ws.call("list_documents", &json!({})).unwrap();
    assert_eq!(listed["documents"].as_array().unwrap().len(), 2);
    std::fs::remove_dir_all(&dir).ok();
}

/// 모든 문단을 다시 쓰고 바꿔도 표·그림·도형·각주 수는 그대로다(고칠 수 없는 문단은 거절한다).
#[test]
fn edits_never_drop_objects() {
    for name in [
        "poi/DiffFirstPageHeadFoot.doc",
        "poi/Bug60942b.doc",
        "poi/WithArtShapes.doc",
        "poi/table-merges.doc",
        "poi/equation.doc",
        "poi/Word6.doc",
    ] {
        for tool in ["rewrite_paragraph", "replace_text"] {
            let engine = Engine::new(Limits::default());
            let id = open(&engine, name);
            let before = engine.structure(id).unwrap();
            for p in engine.paragraphs(id).unwrap().iter().rev() {
                let pid = format!("{}.{}", p.section, p.paragraph);
                let args = if tool == "rewrite_paragraph" {
                    json!({ "paragraph": pid, "text": "바뀐 글" })
                } else {
                    let find: String = p
                        .text
                        .chars()
                        .filter(|c| *c != '\u{FFFC}')
                        .take(2)
                        .collect();
                    if find.chars().count() < 2 || !p.text.contains(&find) {
                        continue;
                    }
                    json!({ "paragraph": pid, "find": find, "replace": "바뀐", "all": true })
                };
                if let Ok(proposal) = plan(&engine, id, tool, &args) {
                    apply(&engine, id, &proposal).unwrap();
                }
            }
            let after = engine.structure(id).unwrap();
            for (key, value) in &before {
                if key.starts_with("chars") || key.starts_with("fonts") || key.starts_with("para.")
                {
                    continue;
                }
                assert_eq!(after.get(key), Some(value), "{name} {tool}: {key}");
            }
        }
    }
}
