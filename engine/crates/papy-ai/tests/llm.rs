//! AI 회사 연결과 대화 실행기 점검: 가짜 서버가 실시간 응답(SSE)을 돌려준다.

mod common;

use std::sync::Mutex;

use common::{MockServer, Reply};
use papy_ai::agent::{self, Conversation, Host};
use papy_ai::llm::{Kind, Provider, Stop};
use papy_tools::{Mode, specs};
use serde_json::{Value, json};

#[derive(Default)]
struct TestHost {
    calls: Mutex<Vec<(String, Value)>>,
    events: Mutex<Vec<Value>>,
}

impl Host for TestHost {
    fn call_tool(&self, name: &str, input: &Value) -> Result<Value, String> {
        self.calls
            .lock()
            .unwrap()
            .push((name.to_owned(), input.clone()));
        Ok(json!({ "matches": [{ "paragraph": "0.3", "page": 1 }] }))
    }

    fn event(&self, event: Value) {
        self.events.lock().unwrap().push(event);
    }

    fn cancelled(&self) -> bool {
        false
    }
}

impl TestHost {
    fn text(&self) -> String {
        self.events
            .lock()
            .unwrap()
            .iter()
            .filter(|e| e["type"] == "text")
            .map(|e| e["text"].as_str().unwrap().to_owned())
            .collect()
    }

    fn of_type(&self, kind: &str) -> Vec<Value> {
        self.events
            .lock()
            .unwrap()
            .iter()
            .filter(|e| e["type"] == kind)
            .cloned()
            .collect()
    }
}

fn provider(kind: Kind, model: &str, base: &str) -> Provider {
    Provider {
        kind,
        model: model.to_owned(),
        key: Some("test-key".to_owned()),
        base: base.to_owned(),
    }
}

fn anthropic_tool_turn(json_parts: &[&str], stop: &str) -> Reply {
    let mut events = vec![
        json!({ "type": "message_start", "message": { "usage": { "input_tokens": 100, "output_tokens": 1, "cache_read_input_tokens": 40, "cache_creation_input_tokens": 0 } } }),
        json!({ "type": "content_block_start", "index": 0, "content_block": { "type": "thinking", "thinking": "", "signature": "" } }),
        json!({ "type": "content_block_delta", "index": 0, "delta": { "type": "thinking_delta", "thinking": "Find it." } }),
        json!({ "type": "content_block_delta", "index": 0, "delta": { "type": "signature_delta", "signature": "sig==" } }),
        json!({ "type": "content_block_stop", "index": 0 }),
        json!({ "type": "content_block_start", "index": 1, "content_block": { "type": "text", "text": "" } }),
        json!({ "type": "content_block_delta", "index": 1, "delta": { "type": "text_delta", "text": "Let me " } }),
        json!({ "type": "content_block_delta", "index": 1, "delta": { "type": "text_delta", "text": "look." } }),
        json!({ "type": "content_block_stop", "index": 1 }),
        json!({ "type": "content_block_start", "index": 2, "content_block": { "type": "tool_use", "id": "toolu_1", "name": "find_text", "input": {} } }),
    ];
    for part in json_parts {
        events.push(json!({ "type": "content_block_delta", "index": 2, "delta": { "type": "input_json_delta", "partial_json": part } }));
    }
    events.push(json!({ "type": "content_block_stop", "index": 2 }));
    events.push(json!({ "type": "message_delta", "delta": { "stop_reason": stop }, "usage": { "output_tokens": 30 } }));
    events.push(json!({ "type": "message_stop" }));
    Reply::sse(&events)
}

fn anthropic_text_turn(text: &str) -> Reply {
    Reply::sse(&[
        json!({ "type": "message_start", "message": { "usage": { "input_tokens": 20, "output_tokens": 1 } } }),
        json!({ "type": "content_block_start", "index": 0, "content_block": { "type": "text", "text": "" } }),
        json!({ "type": "content_block_delta", "index": 0, "delta": { "type": "text_delta", "text": text } }),
        json!({ "type": "content_block_stop", "index": 0 }),
        json!({ "type": "message_delta", "delta": { "stop_reason": "end_turn" }, "usage": { "output_tokens": 5 } }),
        json!({ "type": "message_stop" }),
    ])
}

#[test]
fn anthropic_tool_loop() {
    let server = MockServer::start(vec![
        anthropic_tool_turn(&["{\"query\":", " \"hello\"}"], "tool_use"),
        anthropic_text_turn("Found it on page 1."),
    ]);
    let provider = provider(Kind::Anthropic, "claude-opus-5-5", &server.base);
    let host = TestHost::default();
    let mut conv = Conversation::new(Kind::Anthropic);
    let tools = specs(Mode::App);
    let outcome = agent::run(
        &provider,
        &mut conv,
        "system",
        "where is hello?",
        &tools,
        &host,
    )
    .unwrap();

    assert_eq!(outcome.stop, Stop::End);
    assert_eq!(outcome.steps, 2);
    assert_eq!(outcome.usage.input, 120);
    assert_eq!(outcome.usage.output, 35);
    assert_eq!(outcome.usage.cache_read, 40);
    assert_eq!(host.text(), "Let me look.Found it on page 1.");
    assert_eq!(
        host.calls.lock().unwrap().as_slice(),
        &[("find_text".to_owned(), json!({ "query": "hello" }))]
    );
    assert_eq!(host.of_type("toolEnd")[0]["result"]["ok"], true);

    let requests = server.requests();
    assert_eq!(requests.len(), 2);
    let first = &requests[0];
    assert_eq!(first.method, "POST");
    assert_eq!(first.path, "/v1/messages");
    assert_eq!(first.header("x-api-key"), Some("test-key"));
    assert_eq!(first.header("anthropic-version"), Some("2026-10-21"));
    assert_eq!(
        first.header("anthropic-beta"),
        Some("server-side-fallback-2026-07-01")
    );
    let body = first.json();
    assert_eq!(body["model"], "claude-opus-5-5");
    assert_eq!(body["stream"], true);
    assert_eq!(body["fallbacks"], "default");
    assert_eq!(body["output_config"]["effort"], "medium");
    assert_eq!(body["cache_control"]["type"], "ephemeral");
    assert!(body.get("temperature").is_none());
    assert_eq!(body["tools"][0]["eager_input_streaming"], true);

    // 두 번째 요청: 생각 블록을 그대로 돌려주고, 도구 결과는 사용자 메시지 하나에.
    let second = requests[1].json();
    let messages = second["messages"].as_array().unwrap();
    assert_eq!(messages.len(), 3);
    let assistant = &messages[1]["content"];
    assert_eq!(
        assistant[0],
        json!({ "type": "thinking", "thinking": "Find it.", "signature": "sig==" })
    );
    assert_eq!(assistant[2]["input"], json!({ "query": "hello" }));
    assert_eq!(messages[2]["role"], "user");
    assert_eq!(messages[2]["content"][0]["type"], "tool_result");
    assert_eq!(messages[2]["content"][0]["tool_use_id"], "toolu_1");
    assert!(messages[2]["content"][0].get("is_error").is_none());

    // 대화 기록이 남는다(사용자, 모델, 도구 결과, 모델).
    assert_eq!(conv.messages.len(), 4);
}

#[test]
fn anthropic_broken_tool_json_is_not_run() {
    let server = MockServer::start(vec![
        anthropic_tool_turn(&["{\"query\": \"hel"], "tool_use"),
        anthropic_text_turn("Sorry."),
    ]);
    let provider = provider(Kind::Anthropic, "claude-haiku-4-5", &server.base);
    let host = TestHost::default();
    let mut conv = Conversation::new(Kind::Anthropic);
    agent::run(&provider, &mut conv, "s", "q", &specs(Mode::App), &host).unwrap();
    assert!(host.calls.lock().unwrap().is_empty());
    let requests = server.requests();
    assert!(requests[0].header("anthropic-beta").is_none());
    assert!(requests[0].json().get("fallbacks").is_none());
    assert!(requests[0].json().get("output_config").is_none());
    let result = &requests[1].json()["messages"][2]["content"][0];
    assert_eq!(result["is_error"], true);
    assert!(
        result["content"]
            .as_str()
            .unwrap()
            .starts_with("INVALID_JSON")
    );
}

#[test]
fn anthropic_truncated_turn_does_not_run_tools() {
    let server = MockServer::start(vec![
        anthropic_tool_turn(&["{\"query\": \"x\"}"], "max_tokens"),
        anthropic_text_turn("ok"),
    ]);
    let host = TestHost::default();
    let mut conv = Conversation::new(Kind::Anthropic);
    agent::run(
        &provider(Kind::Anthropic, "claude-opus-5-5", &server.base),
        &mut conv,
        "s",
        "q",
        &specs(Mode::App),
        &host,
    )
    .unwrap();
    assert!(host.calls.lock().unwrap().is_empty());
    assert_eq!(
        server.requests()[1].json()["messages"][2]["content"][0]["is_error"],
        true
    );
}

#[test]
fn anthropic_refusal_and_errors() {
    let server = MockServer::start(vec![
        Reply::sse(&[
            json!({ "type": "message_start", "message": { "usage": { "input_tokens": 5 } } }),
            json!({ "type": "message_delta", "delta": { "stop_reason": "refusal", "stop_details": { "type": "refusal", "category": "cyber", "explanation": "not allowed" } }, "usage": { "output_tokens": 0 } }),
        ]),
        Reply::json(
            401,
            json!({ "type": "error", "error": { "type": "authentication_error", "message": "invalid x-api-key" } }),
        ),
    ]);
    let host = TestHost::default();
    let mut conv = Conversation::new(Kind::Anthropic);
    let p = provider(Kind::Anthropic, "claude-opus-5-5", &server.base);
    let outcome = agent::run(&p, &mut conv, "s", "q", &[], &host).unwrap();
    assert_eq!(outcome.stop, Stop::Refusal("not allowed".to_owned()));
    assert_eq!(host.of_type("notice")[0]["code"], "refusal");

    // 오류면 이번 요청의 기록을 되돌린다.
    let before = conv.messages.len();
    let error = agent::run(&p, &mut conv, "s", "again", &[], &host).unwrap_err();
    assert!(
        error.contains("401")
            && error.contains("invalid x-api-key")
            && error.contains("check the API key"),
        "{error}"
    );
    assert!(!error.contains("test-key"));
    assert_eq!(conv.messages.len(), before);
}

fn openai_tool_turn(id: &str) -> Reply {
    Reply::chunks(&[
        json!({ "choices": [{ "index": 0, "delta": { "role": "assistant", "content": "Checking" } }] }),
        json!({ "choices": [{ "index": 0, "delta": { "tool_calls": [{ "index": 0, "id": id, "type": "function", "function": { "name": "find_text", "arguments": "" } }] } }] }),
        json!({ "choices": [{ "index": 0, "delta": { "tool_calls": [{ "index": 0, "function": { "arguments": "{\"query\"" } }] } }] }),
        json!({ "choices": [{ "index": 0, "delta": { "tool_calls": [{ "index": 0, "function": { "arguments": ":\"x\"}" } }] } }] }),
        json!({ "choices": [{ "index": 0, "delta": {}, "finish_reason": "tool_calls" }] }),
        json!({ "choices": [], "usage": { "prompt_tokens": 50, "completion_tokens": 5, "prompt_tokens_details": { "cached_tokens": 10 } } }),
    ])
}

fn openai_text_turn(text: &str) -> Reply {
    Reply::chunks(&[
        json!({ "choices": [{ "index": 0, "delta": { "content": text } }] }),
        json!({ "choices": [{ "index": 0, "delta": {}, "finish_reason": "stop" }] }),
    ])
}

#[test]
fn openai_tool_loop() {
    let server = MockServer::start(vec![openai_tool_turn("call_a"), openai_text_turn(" done")]);
    let host = TestHost::default();
    let mut conv = Conversation::new(Kind::Openai);
    let outcome = agent::run(
        &provider(Kind::Openai, "gpt-5", &server.base),
        &mut conv,
        "sys",
        "find x",
        &specs(Mode::App),
        &host,
    )
    .unwrap();
    assert_eq!(outcome.stop, Stop::End);
    assert_eq!(outcome.usage.input, 40);
    assert_eq!(outcome.usage.cache_read, 10);
    assert_eq!(outcome.usage.output, 5);
    assert_eq!(host.text(), "Checking done");
    assert_eq!(host.calls.lock().unwrap()[0].1, json!({ "query": "x" }));

    let requests = server.requests();
    assert_eq!(requests[0].path, "/chat/completions");
    assert_eq!(requests[0].header("authorization"), Some("Bearer test-key"));
    let body = requests[0].json();
    assert_eq!(
        body["messages"][0],
        json!({ "role": "system", "content": "sys" })
    );
    assert_eq!(body["stream_options"]["include_usage"], true);
    assert_eq!(body["tools"][0]["type"], "function");
    let second = requests[1].json();
    let messages = second["messages"].as_array().unwrap();
    assert_eq!(messages[2]["tool_calls"][0]["id"], "call_a");
    assert_eq!(
        messages[2]["tool_calls"][0]["function"]["arguments"],
        "{\"query\":\"x\"}"
    );
    assert_eq!(messages[3]["role"], "tool");
    assert_eq!(messages[3]["tool_call_id"], "call_a");
}

#[test]
fn gemini_empty_ids_and_stream_options_retry() {
    let server = MockServer::start(vec![
        Reply::json(
            400,
            json!([{ "error": { "code": 400, "message": "Invalid JSON payload received. Unknown name \"stream_options\"" } }]),
        ),
        openai_tool_turn(""),
        openai_text_turn("ok"),
    ]);
    let host = TestHost::default();
    let mut conv = Conversation::new(Kind::Gemini);
    agent::run(
        &provider(Kind::Gemini, "gemini-2.5-flash", &server.base),
        &mut conv,
        "s",
        "q",
        &specs(Mode::App),
        &host,
    )
    .unwrap();
    let requests = server.requests();
    assert_eq!(requests.len(), 3);
    assert!(requests[0].json().get("stream_options").is_some());
    assert!(requests[1].json().get("stream_options").is_none());
    let messages = requests[2].json()["messages"].clone();
    assert_eq!(messages[2]["tool_calls"][0]["id"], "call_0");
    assert_eq!(messages[3]["tool_call_id"], "call_0");
}

#[test]
fn switching_provider_starts_a_new_history() {
    let server = MockServer::start(vec![openai_text_turn("a"), anthropic_text_turn("b")]);
    let host = TestHost::default();
    let mut conv = Conversation::new(Kind::Openai);
    agent::run(
        &provider(Kind::Ollama, "llama3", &server.base),
        &mut conv,
        "s",
        "one",
        &[],
        &host,
    )
    .unwrap();
    assert_eq!(conv.kind, Kind::Ollama);
    assert_eq!(conv.messages.len(), 2);
    agent::run(
        &provider(Kind::Anthropic, "claude-opus-5-5", &server.base),
        &mut conv,
        "s",
        "two",
        &[],
        &host,
    )
    .unwrap();
    assert_eq!(conv.kind, Kind::Anthropic);
    assert_eq!(
        server.requests()[1].json()["messages"]
            .as_array()
            .unwrap()
            .len(),
        1
    );
}

#[test]
fn model_lists() {
    let server = MockServer::start(vec![
        Reply::json(
            200,
            json!({ "data": [{ "id": "claude-opus-5-5", "display_name": "Claude Opus 5.5" }], "has_more": false }),
        ),
        Reply::json(
            200,
            json!({ "data": [{ "id": "gpt-5" }, { "id": "text-embedding-3-large" }, { "id": "o4-mini" }, { "id": "dall-e-3" }, { "id": "gpt-4o-realtime-preview" }] }),
        ),
        Reply::json(
            200,
            json!({ "data": [{ "id": "models/gemini-2.5-flash" }, { "id": "models/embedding-001" }, { "id": "models/imagen-3" }] }),
        ),
    ]);
    let models = papy_ai::llm::list_models(&provider(Kind::Anthropic, "-", &server.base)).unwrap();
    assert_eq!(models[0].name, "Claude Opus 5.5");
    let models = papy_ai::llm::list_models(&provider(Kind::Openai, "-", &server.base)).unwrap();
    let ids: Vec<_> = models.iter().map(|m| m.id.as_str()).collect();
    assert_eq!(ids, ["o4-mini", "gpt-5"]);
    let models = papy_ai::llm::list_models(&provider(Kind::Gemini, "-", &server.base)).unwrap();
    assert_eq!(models.len(), 1);
    assert_eq!(models[0].id, "gemini-2.5-flash");
    assert_eq!(server.requests()[0].path, "/v1/models");
}
