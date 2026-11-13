//! 실행 파일 점검: MCP 서버(단독), 앱 연결(`serve`)로 API 대화와 ACP 에이전트 대화를 끝까지 돌린다.
//!
//! ACP 에이전트는 이 시험 실행 파일 자신이 맡는다(`mock_agent`, 환경 변수 `PAPY_MOCK_AGENT=1` 일 때만 동작).

mod common;

use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::mpsc::Receiver;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use common::{MockServer, Reply};
use papy_ai::rpc::{Incoming, Peer};
use serde_json::{Value, json};

const WAIT: Duration = Duration::from_secs(60);

fn exe() -> &'static str {
    env!("CARGO_BIN_EXE_papy-ai")
}

fn fixture(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../../tests/fixtures")
        .join(name)
}

struct Proc {
    child: Child,
    peer: Peer,
    incoming: Receiver<Incoming>,
}

impl Drop for Proc {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

fn spawn(args: &[&str], env: &[(&str, String)]) -> Proc {
    let mut command = Command::new(exe());
    command
        .args(args)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit());
    command.env("PAPY_AI_KEYSTORE", "memory");
    for (k, v) in env {
        command.env(k, v);
    }
    let mut child = command.spawn().unwrap();
    let (peer, incoming) = Peer::start(child.stdout.take().unwrap(), child.stdin.take().unwrap());
    Proc {
        child,
        peer,
        incoming,
    }
}

fn req(proc: &Proc, method: &str, params: Value) -> Value {
    proc.peer
        .request(method, params, WAIT)
        .unwrap_or_else(|e| panic!("{method}: {e}"))
}

#[test]
fn mcp_standalone() {
    let proc = spawn(&["mcp"], &[]);
    let init = req(
        &proc,
        "initialize",
        json!({ "protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": { "name": "t", "version": "1" } }),
    );
    assert_eq!(init["protocolVersion"], "2025-03-26");
    assert_eq!(init["serverInfo"]["name"], "papy");
    proc.peer.notify("notifications/initialized", json!({}));
    let tools = req(&proc, "tools/list", json!({}));
    let names: Vec<&str> = tools["tools"]
        .as_array()
        .unwrap()
        .iter()
        .map(|t| t["name"].as_str().unwrap())
        .collect();
    for name in [
        "open_document",
        "list_documents",
        "save_copy",
        "read_paragraphs",
        "replace_text",
    ] {
        assert!(names.contains(&name), "{name} missing");
    }
    assert!(!names.contains(&"export_copy"));
    let opened = req(
        &proc,
        "tools/call",
        json!({ "name": "open_document", "arguments": { "path": fixture("poi/DiffFirstPageHeadFoot.doc") } }),
    );
    assert_eq!(opened["isError"], false);
    let doc = opened["structuredContent"]["document"]
        .as_str()
        .unwrap()
        .to_owned();
    let read = req(
        &proc,
        "tools/call",
        json!({ "name": "read_paragraphs", "arguments": { "document": doc, "count": 3 } }),
    );
    assert_eq!(read["isError"], false);
    assert!(
        !read["structuredContent"]["paragraphs"]
            .as_array()
            .unwrap()
            .is_empty()
    );
    let bad = req(
        &proc,
        "tools/call",
        json!({ "name": "read_paragraphs", "arguments": { "document": "nope.doc" } }),
    );
    assert_eq!(bad["isError"], true);
    assert!(
        proc.peer
            .request("tools/call", json!({ "name": "rm_rf" }), WAIT)
            .is_err()
    );
    assert!(proc.peer.request("nope", json!({}), WAIT).is_err());
}

/// 앱 노릇: 도구 호출에 답하고, 대화 알림을 모은다.
struct App {
    proc: Proc,
    events: Arc<Mutex<Vec<Value>>>,
    tool_calls: Arc<Mutex<Vec<Value>>>,
}

impl App {
    fn start(env: &[(&str, String)]) -> Self {
        let mut proc = spawn(&["serve"], env);
        let events = Arc::new(Mutex::new(Vec::new()));
        let tool_calls = Arc::new(Mutex::new(Vec::new()));
        let (tx, rx) = std::sync::mpsc::channel();
        let incoming = std::mem::replace(&mut proc.incoming, rx);
        drop(tx);
        let peer = proc.peer.clone();
        {
            let events = Arc::clone(&events);
            let tool_calls = Arc::clone(&tool_calls);
            std::thread::spawn(move || {
                for message in incoming {
                    match message.method.as_str() {
                        "chat/event" => events.lock().unwrap().push(message.params),
                        "tool/call" => {
                            tool_calls.lock().unwrap().push(message.params.clone());
                            let result = match message.params["name"].as_str() {
                                Some("find_text") => Ok(
                                    json!({ "matches": [{ "paragraph": "0.2", "page": 1, "text": "hello world" }] }),
                                ),
                                _ => Err(papy_ai::rpc::RpcError::internal("no such paragraph")),
                            };
                            peer.respond(message.id.as_ref().unwrap(), result);
                        }
                        _ => {}
                    }
                }
            });
        }
        Self {
            proc,
            events,
            tool_calls,
        }
    }

    /// 끝 알림(done·error·cancelled)이 올 때까지 기다린다.
    fn finish(&self, conversation: &str) -> Value {
        self.finish_within(conversation, WAIT)
    }

    fn finish_within(&self, conversation: &str, wait: Duration) -> Value {
        let start = Instant::now();
        loop {
            let found = self
                .events
                .lock()
                .unwrap()
                .iter()
                .filter(|e| e["conversation"] == conversation)
                .map(|e| e["event"].clone())
                .find(|e| matches!(e["type"].as_str(), Some("done" | "error" | "cancelled")));
            if let Some(event) = found {
                return event;
            }
            assert!(
                start.elapsed() < wait,
                "no end event; got {:?}",
                self.events.lock().unwrap()
            );
            std::thread::sleep(Duration::from_millis(20));
        }
    }

    fn events_of(&self, conversation: &str) -> Vec<Value> {
        let all = self
            .events
            .lock()
            .unwrap()
            .iter()
            .filter(|e| e["conversation"] == conversation)
            .map(|e| e["event"].clone())
            .collect();
        self.events
            .lock()
            .unwrap()
            .retain(|e| e["conversation"] != conversation);
        all
    }
}

fn text_of(events: &[Value]) -> String {
    events
        .iter()
        .filter(|e| e["type"] == "text")
        .map(|e| e["text"].as_str().unwrap_or(""))
        .collect()
}

#[test]
fn serve_api_chat() {
    let server = MockServer::start(vec![
        Reply::sse(&[
            json!({ "type": "message_start", "message": { "usage": { "input_tokens": 1000, "output_tokens": 1 } } }),
            json!({ "type": "content_block_start", "index": 0, "content_block": { "type": "tool_use", "id": "t1", "name": "find_text", "input": {} } }),
            json!({ "type": "content_block_delta", "index": 0, "delta": { "type": "input_json_delta", "partial_json": "{\"query\":\"hello\"}" } }),
            json!({ "type": "content_block_stop", "index": 0 }),
            json!({ "type": "message_delta", "delta": { "stop_reason": "tool_use" }, "usage": { "output_tokens": 100 } }),
        ]),
        Reply::sse(&[
            json!({ "type": "message_start", "message": { "usage": { "input_tokens": 1000, "output_tokens": 1 } } }),
            json!({ "type": "content_block_start", "index": 0, "content_block": { "type": "text", "text": "" } }),
            json!({ "type": "content_block_delta", "index": 0, "delta": { "type": "text_delta", "text": "It is on page 1." } }),
            json!({ "type": "message_delta", "delta": { "stop_reason": "end_turn" }, "usage": { "output_tokens": 100 } }),
        ]),
        Reply::json(
            200,
            json!({ "data": [{ "id": "claude-sonnet-5-5", "display_name": "Claude Sonnet 5.5" }] }),
        ),
    ]);
    let app = App::start(&[]);
    let init = req(&app.proc, "initialize", json!({}));
    assert_eq!(init["providers"].as_array().unwrap().len(), 4);
    assert_eq!(init["agents"].as_array().unwrap().len(), 3);

    // 키 없이 보내면 바로 오류 알림.
    req(
        &app.proc,
        "chat/send",
        json!({ "conversation": "c0", "provider": "anthropic", "text": "hi" }),
    );
    let end = app.finish("c0");
    assert_eq!(end["type"], "error");
    assert!(end["message"].as_str().unwrap().contains("no API key"));

    let status = req(
        &app.proc,
        "keys/set",
        json!({ "provider": "anthropic", "key": " sk-test " }),
    );
    assert_eq!(status["anthropic"], true);
    assert_eq!(status["openai"], false);

    req(
        &app.proc,
        "chat/send",
        json!({ "conversation": "c1", "provider": "anthropic", "model": "claude-opus-5-5", "base": server.base, "text": "where is hello?",
                "document": { "name": "a.hwp", "pages": 3 }, "language": "ko" }),
    );
    // 답하는 동안 같은 대화로 또 보내면 거절한다.
    let busy = app.proc.peer.request(
        "chat/send",
        json!({ "conversation": "c1", "provider": "anthropic", "text": "x" }),
        WAIT,
    );
    let end = app.finish("c1");
    assert_eq!(end["type"], "done", "{end}");
    if let Err(error) = busy {
        assert!(error.message.contains("still answering"));
    }
    let outcome = &end["outcome"];
    assert_eq!(outcome["steps"], 2);
    assert_eq!(outcome["usage"]["input"], 2000);
    // Opus 5.5: 입력 $4, 출력 $20 (백만 토큰당) → 2000×4 + 200×20 = 12000 / 1e6
    assert!(
        (outcome["cost"].as_f64().unwrap() - 0.012).abs() < 1e-9,
        "{outcome}"
    );
    let events = app.events_of("c1");
    assert_eq!(text_of(&events), "It is on page 1.");
    assert!(
        events
            .iter()
            .any(|e| e["type"] == "toolStart" && e["name"] == "find_text")
    );
    let calls = app.tool_calls.lock().unwrap().clone();
    assert_eq!(calls.last().unwrap()["conversation"], "c1");
    assert_eq!(calls.last().unwrap()["input"], json!({ "query": "hello" }));
    let requests = server.requests();
    assert_eq!(requests[0].header("x-api-key"), Some("sk-test"));
    let system = requests[0].json()["system"].as_str().unwrap().to_owned();
    assert!(system.contains("a.hwp") && system.contains("Korean"));

    let models = req(
        &app.proc,
        "models/list",
        json!({ "provider": "anthropic", "base": server.base }),
    );
    assert_eq!(models["models"][0]["id"], "claude-sonnet-5-5");

    let status = req(&app.proc, "keys/delete", json!({ "provider": "anthropic" }));
    assert_eq!(status["anthropic"], false);
    assert!(
        app.proc
            .peer
            .request(
                "keys/set",
                json!({ "provider": "anthropic", "key": "  " }),
                WAIT
            )
            .is_err()
    );
    req(&app.proc, "chat/reset", json!({ "conversation": "c1" }));
}

#[test]
fn serve_acp_agent_uses_papy_tools_through_the_relay() {
    let me = std::env::current_exe().unwrap();
    let command = json!([
        me,
        "--exact",
        "mock_agent",
        "--nocapture",
        "--test-threads=1"
    ])
    .to_string();
    let app = App::start(&[
        ("PAPY_AI_AGENT_CLAUDE_CODE", command),
        ("PAPY_MOCK_AGENT", "1".to_owned()),
    ]);
    let agents = req(&app.proc, "agents/detect", json!({}));
    assert_eq!(agents[0]["id"], "claude-code");
    assert_eq!(agents[0]["available"], true);

    req(
        &app.proc,
        "chat/send",
        json!({ "conversation": "a1", "provider": "acp:claude-code", "text": "find hello", "document": { "name": "b.docx" } }),
    );
    let end = app.finish("a1");
    assert_eq!(
        end["type"],
        "done",
        "{end} {:?}",
        app.events.lock().unwrap()
    );
    assert_eq!(end["outcome"]["stop"], "end_turn");
    let events = app.events_of("a1");
    let text = text_of(&events);
    assert!(text.contains("prompt has papy guide: true"), "{text}");
    assert!(text.contains("papy=allow"), "{text}");
    assert!(text.contains("bash=reject"), "{text}");
    assert!(text.contains("tool said: hello world"), "{text}");
    assert!(text.contains("bad tool error: true"), "{text}");
    assert!(
        events
            .iter()
            .any(|e| e["type"] == "notice" && e["code"] == "permissionDenied")
    );
    assert!(
        events
            .iter()
            .any(|e| e["type"] == "toolEnd" && e["result"]["ok"] == true)
    );
    let calls = app.tool_calls.lock().unwrap().clone();
    assert!(
        calls
            .iter()
            .any(|c| c["conversation"] == "a1" && c["name"] == "find_text")
    );

    // 두 번째 글은 같은 에이전트 세션으로(안내 글은 처음에만).
    req(
        &app.proc,
        "chat/send",
        json!({ "conversation": "a1", "provider": "acp:claude-code", "text": "again" }),
    );
    let end = app.finish("a1");
    assert_eq!(end["type"], "done", "{end}");
    let text = text_of(&app.events_of("a1"));
    assert!(
        text.contains("prompt has papy guide: false") && text.contains("session s1 prompt 2"),
        "{text}"
    );

    // 멈추기: 에이전트가 오래 걸리는 글("slow")에서 session/cancel 을 받고 cancelled 로 끝낸다.
    req(
        &app.proc,
        "chat/send",
        json!({ "conversation": "a1", "provider": "acp:claude-code", "text": "slow" }),
    );
    std::thread::sleep(Duration::from_millis(300));
    req(&app.proc, "chat/cancel", json!({ "conversation": "a1" }));
    let end = app.finish("a1");
    assert_eq!(end["type"], "cancelled", "{end}");
}

/// 가짜 ACP 에이전트. `PAPY_MOCK_AGENT=1` 일 때만 표준 입출력으로 동작한다.
#[test]
fn mock_agent() {
    if std::env::var("PAPY_MOCK_AGENT").is_err() {
        return;
    }
    // 시험 틀이 줄바꿈 없이 "test mock_agent ... " 를 찍어 두므로 줄을 먼저 끝낸다.
    {
        use std::io::Write;
        let _ = std::io::stdout().write_all(b"\n");
    }
    let (peer, incoming) = Peer::start(std::io::stdin(), std::io::stdout());
    let mut mcp: Option<(Child, Peer)> = None;
    let mut keep = Vec::new();
    let mut prompts = 0;
    let cancelled = Arc::new(std::sync::atomic::AtomicBool::new(false));
    for message in incoming {
        let id = message.id.clone();
        let reply = |result: Value| {
            if let Some(id) = &id {
                peer.respond(id, Ok(result));
            }
        };
        match message.method.as_str() {
            "initialize" => reply(
                json!({ "protocolVersion": 1, "agentCapabilities": { "loadSession": false }, "authMethods": [] }),
            ),
            "session/new" => {
                let server = &message.params["mcpServers"][0];
                assert_eq!(server["name"], "papy");
                let args: Vec<String> = server["args"]
                    .as_array()
                    .unwrap()
                    .iter()
                    .map(|a| a.as_str().unwrap().to_owned())
                    .collect();
                let mut child = Command::new(server["command"].as_str().unwrap())
                    .args(&args)
                    .stdin(Stdio::piped())
                    .stdout(Stdio::piped())
                    .spawn()
                    .unwrap();
                let (mcp_peer, rx) =
                    Peer::start(child.stdout.take().unwrap(), child.stdin.take().unwrap());
                mcp_peer
                    .request(
                        "initialize",
                        json!({ "protocolVersion": "2025-06-18" }),
                        WAIT,
                    )
                    .unwrap();
                keep.push(rx);
                mcp = Some((child, mcp_peer));
                reply(json!({ "sessionId": "s1" }));
            }
            "session/cancel" => cancelled.store(true, std::sync::atomic::Ordering::Relaxed),
            "session/prompt" => {
                prompts += 1;
                let session = message.params["sessionId"].as_str().unwrap().to_owned();
                let text = message.params["prompt"][0]["text"]
                    .as_str()
                    .unwrap()
                    .to_owned();
                let say = |text: String| {
                    peer.notify(
                        "session/update",
                        json!({ "sessionId": session, "update": { "sessionUpdate": "agent_message_chunk", "content": { "type": "text", "text": text } } }),
                    );
                };
                if text.ends_with("slow") {
                    let peer = peer.clone();
                    let cancelled = Arc::clone(&cancelled);
                    std::thread::spawn(move || {
                        let start = Instant::now();
                        while !cancelled.load(std::sync::atomic::Ordering::Relaxed)
                            && start.elapsed() < WAIT
                        {
                            std::thread::sleep(Duration::from_millis(20));
                        }
                        peer.respond(
                            id.as_ref().unwrap(),
                            Ok(json!({ "stopReason": "cancelled" })),
                        );
                    });
                    continue;
                }
                say(format!(
                    "session {session} prompt {prompts}\nprompt has papy guide: {}\n",
                    text.contains("`papy` MCP tools")
                ));
                if prompts == 1 {
                    let ask = |title: &str| {
                        let result = peer
                            .request(
                                "session/request_permission",
                                json!({ "sessionId": session, "toolCall": { "toolCallId": title, "title": title },
                                        "options": [{ "optionId": "yes", "name": "Allow", "kind": "allow_once" }, { "optionId": "no", "name": "Reject", "kind": "reject_once" }] }),
                                WAIT,
                            )
                            .unwrap();
                        match result.pointer("/outcome/optionId").and_then(Value::as_str) {
                            Some("yes") => "allow",
                            Some("no") => "reject",
                            _ => "cancelled",
                        }
                    };
                    say(format!(
                        "papy={} bash={}\n",
                        ask("mcp__papy__find_text"),
                        ask("Bash")
                    ));
                    peer.notify("session/update", json!({ "sessionId": session, "update": { "sessionUpdate": "tool_call", "toolCallId": "c1", "title": "mcp__papy__find_text", "status": "pending" } }));
                    let (_, mcp_peer) = mcp.as_ref().unwrap();
                    let found = mcp_peer
                        .request(
                            "tools/call",
                            json!({ "name": "find_text", "arguments": { "query": "hello" } }),
                            WAIT,
                        )
                        .unwrap();
                    say(format!(
                        "tool said: {}\n",
                        found["structuredContent"]["matches"][0]["text"]
                            .as_str()
                            .unwrap_or("?")
                    ));
                    let bad = mcp_peer.request("tools/call", json!({ "name": "rewrite_paragraph", "arguments": { "paragraph": "9.9", "text": "x" } }), WAIT).unwrap();
                    say(format!("bad tool error: {}\n", bad["isError"]));
                    peer.notify("session/update", json!({ "sessionId": session, "update": { "sessionUpdate": "tool_call_update", "toolCallId": "c1", "status": "completed" } }));
                    // 클라이언트가 알리지 않은 파일 읽기는 거절돼야 한다.
                    let fs = peer.request(
                        "fs/read_text_file",
                        json!({ "sessionId": session, "path": "/etc/hosts" }),
                        WAIT,
                    );
                    assert!(fs.is_err());
                }
                reply(json!({ "stopReason": "end_turn" }));
            }
            _ => {
                if let Some(id) = &id {
                    peer.respond(id, Err(papy_ai::rpc::RpcError::no_method(&message.method)));
                }
            }
        }
    }
    if let Some((mut child, _)) = mcp {
        let _ = child.kill();
    }
    std::process::exit(0);
}

/// 실제 Claude Code(ACP 어댑터)로 확인. 로그인과 인터넷이 필요해 평소엔 건너뛴다:
/// `cargo test -p papy-ai --test plugin real_claude_code -- --ignored --nocapture`
#[test]
#[ignore]
fn real_claude_code() {
    let app = App::start(&[]);
    req(
        &app.proc,
        "chat/send",
        json!({ "conversation": "r1", "provider": "acp:claude-code", "language": "en",
                "text": "Use the papy find_text tool to search for \"hello\" and tell me the paragraph id it returns. Reply in one short sentence.",
                "document": { "name": "test.docx", "pages": 1 } }),
    );
    let end = app.finish_within("r1", Duration::from_secs(300));
    let events = app.events_of("r1");
    for event in events.iter().filter(|e| e["type"] != "text") {
        println!("{event}");
    }
    let text = text_of(&events);
    println!("TEXT: {text}");
    assert_eq!(end["type"], "done", "{end}");
    assert!(
        app.tool_calls
            .lock()
            .unwrap()
            .iter()
            .any(|c| c["name"] == "find_text"),
        "find_text was not called"
    );
    assert!(text.contains("0.2"), "{text}");
}
