//! 앱 ⇄ 플러그인 연결(`papy-ai serve`). 표준 입출력 위의 줄 단위 JSON-RPC.
//!
//! 앱 → 플러그인 요청: `initialize`, `keys/status`, `keys/set`, `keys/delete`, `models/list`,
//! `chat/send`, `chat/cancel`, `chat/reset`, `agents/detect`, `shutdown`.
//! 플러그인 → 앱 요청: `tool/call {conversation, name, input}` — 앱이 열린 문서에서 도구를 실행한다.
//! 플러그인 → 앱 알림: `chat/event {conversation, event}` — 글 조각·도구·사용량·끝(`done`)·오류(`error`).
//!
//! ACP 에이전트는 Papy MCP 서버(`papy-ai mcp --attach`)를 띄우고, 그 서버는 이 프로세스의 중계 소켓
//! (127.0.0.1, 대화마다 다른 토큰)으로 도구 호출을 넘긴다. 결국 모든 도구 실행은 앱의 `tool/call` 로 모인다.

use std::collections::HashMap;
use std::collections::hash_map::RandomState;
use std::hash::{BuildHasher, Hasher};
use std::io::{BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use papy_tools::{Mode, specs};
use serde_json::{Value, json};

use crate::acp::{self, AcpHost, AcpSession, McpLaunch};
use crate::agent::{self, Conversation, Host};
use crate::keys;
use crate::llm::{self, Kind, Provider};
use crate::rpc::{Incoming, Peer, RpcError};

/// 앱이 도구 하나를 실행하는 데 기다리는 시간.
const TOOL_TIMEOUT: Duration = Duration::from_secs(120);

/// 회사별 기본 모델.
pub fn default_model(kind: Kind) -> &'static str {
    match kind {
        Kind::Anthropic => "claude-opus-5-5",
        Kind::Openai => "gpt-5",
        Kind::Gemini => "gemini-2.5-flash",
        Kind::Ollama => "",
    }
}

fn token() -> String {
    let mut out = String::new();
    for _ in 0..2 {
        let mut hasher = RandomState::new().build_hasher();
        hasher.write_u128(
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_nanos())
                .unwrap_or(0),
        );
        hasher.write_u32(std::process::id());
        out.push_str(&format!("{:016x}", hasher.finish()));
    }
    out
}

enum Backend {
    Direct(Conversation),
    Agent { id: String, session: AcpSession },
}

struct Chat {
    backend: Option<Backend>,
    token: String,
}

/// (멈춤, 실행 중) 깃발.
type Flags = (Arc<AtomicBool>, Arc<AtomicBool>);

struct State {
    peer: Peer,
    chats: Mutex<HashMap<String, Arc<Mutex<Chat>>>>,
    flags: Mutex<HashMap<String, Flags>>,
    relay: String,
    tokens: Mutex<HashMap<String, String>>,
    exe: PathBuf,
}

impl State {
    fn chat(&self, conversation: &str) -> Arc<Mutex<Chat>> {
        let mut chats = self.chats.lock().unwrap_or_else(|p| p.into_inner());
        Arc::clone(chats.entry(conversation.to_owned()).or_insert_with(|| {
            let token = token();
            self.tokens
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .insert(token.clone(), conversation.to_owned());
            Arc::new(Mutex::new(Chat {
                backend: None,
                token,
            }))
        }))
    }

    /// (멈춤, 실행 중) 깃발. 대화가 잠겨 있어도 읽을 수 있게 따로 둔다.
    fn flags(&self, conversation: &str) -> Flags {
        let mut flags = self.flags.lock().unwrap_or_else(|p| p.into_inner());
        flags
            .entry(conversation.to_owned())
            .or_insert_with(|| {
                (
                    Arc::new(AtomicBool::new(false)),
                    Arc::new(AtomicBool::new(false)),
                )
            })
            .clone()
    }

    fn emit(&self, conversation: &str, event: Value) {
        self.peer.notify(
            "chat/event",
            json!({ "conversation": conversation, "event": event }),
        );
    }

    fn call_tool(&self, conversation: &str, name: &str, input: &Value) -> Result<Value, String> {
        self.peer
            .request(
                "tool/call",
                json!({ "conversation": conversation, "name": name, "input": input }),
                TOOL_TIMEOUT,
            )
            .map_err(|error| error.message)
    }
}

struct ChatHost {
    state: Arc<State>,
    conversation: String,
    cancel: Arc<AtomicBool>,
}

impl Host for ChatHost {
    fn call_tool(&self, name: &str, input: &Value) -> Result<Value, String> {
        self.state.call_tool(&self.conversation, name, input)
    }

    fn event(&self, event: Value) {
        self.state.emit(&self.conversation, event);
    }

    fn cancelled(&self) -> bool {
        self.cancel.load(Ordering::Relaxed)
    }
}

impl AcpHost for ChatHost {
    fn event(&self, event: Value) {
        self.state.emit(&self.conversation, event);
    }

    fn cancelled(&self) -> bool {
        self.cancel.load(Ordering::Relaxed)
    }
}

fn str_param<'a>(params: &'a Value, key: &str) -> Result<&'a str, RpcError> {
    params
        .get(key)
        .and_then(Value::as_str)
        .ok_or_else(|| RpcError::invalid(format!("`{key}` is required")))
}

fn kind_param(params: &Value) -> Result<Kind, RpcError> {
    let name = str_param(params, "provider")?;
    Kind::parse(name).ok_or_else(|| RpcError::invalid(format!("unknown provider `{name}`")))
}

/// 연결 설정을 만든다. 키는 키체인에서 읽는다.
fn provider(params: &Value, kind: Kind) -> Result<Provider, RpcError> {
    let key =
        if kind.needs_key() {
            Some(keys::get(kind).ok_or_else(|| {
                RpcError::new(-32001, format!("no API key saved for {}", kind.id()))
            })?)
        } else {
            None
        };
    let base = params
        .get("base")
        .and_then(Value::as_str)
        .filter(|b| !b.trim().is_empty())
        .unwrap_or(kind.default_base())
        .trim_end_matches('/')
        .to_owned();
    let model = params
        .get("model")
        .and_then(Value::as_str)
        .filter(|m| !m.trim().is_empty())
        .unwrap_or(default_model(kind))
        .to_owned();
    if model.is_empty() {
        return Err(RpcError::invalid("choose a model"));
    }
    Ok(Provider {
        kind,
        model,
        key,
        base,
    })
}

fn initialize() -> Value {
    let providers: Vec<Value> = [Kind::Anthropic, Kind::Openai, Kind::Gemini, Kind::Ollama]
        .into_iter()
        .map(|kind| {
            json!({
                "id": kind.id(),
                "needsKey": kind.needs_key(),
                "external": kind.is_external(),
                "defaultModel": default_model(kind),
                "defaultBase": kind.default_base(),
            })
        })
        .collect();
    json!({
        "name": "papy-ai",
        "version": env!("CARGO_PKG_VERSION"),
        "protocol": 1,
        "providers": providers,
        "agents": acp::detect(),
    })
}

fn keys_status() -> Value {
    let mut out = serde_json::Map::new();
    for kind in [Kind::Anthropic, Kind::Openai, Kind::Gemini] {
        out.insert(kind.id().to_owned(), json!(keys::has(kind)));
    }
    Value::Object(out)
}

fn price_of(kind: Kind, model: &str, usage: llm::Usage) -> Value {
    match llm::price(kind, model) {
        // 캐시 읽기는 입력 값의 0.1배, 캐시 쓰기는 1.25배.
        Some((input, output)) => json!(
            (usage.input as f64 * input
                + usage.cache_read as f64 * input * 0.1
                + usage.cache_write as f64 * input * 1.25
                + usage.output as f64 * output)
                / 1_000_000.0
        ),
        None => Value::Null,
    }
}

fn acp_prompt(text: &str, document: &Value, language: &str, first: bool) -> String {
    let doc = if document.is_null() {
        "No document is open in Papy.".to_owned()
    } else {
        format!("Open document in Papy: {document}")
    };
    if first {
        format!(
            "[Papy] You are helping inside Papy, a document app. Work on the user's document only through the `papy` MCP tools \
             (read_paragraphs, find_text, replace_text, …). Do not use shell or file tools. Editing tools only propose changes \
             that the user applies in Papy. Answer in {}.\n{doc}\n\n{text}",
            if language.starts_with("ko") {
                "Korean"
            } else {
                "the user's language"
            }
        )
    } else {
        format!("[Papy] {doc}\n\n{text}")
    }
}

/// 대화 하나를 실행한다(별도 스레드).
fn run_chat(state: Arc<State>, conversation: String, params: Value) {
    let chat = state.chat(&conversation);
    let (cancel, running) = state.flags(&conversation);
    let host = Arc::new(ChatHost {
        state: Arc::clone(&state),
        conversation: conversation.clone(),
        cancel: Arc::clone(&cancel),
    });
    let text = params
        .get("text")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_owned();
    let document = params.get("document").cloned().unwrap_or(Value::Null);
    let language = params
        .get("language")
        .and_then(Value::as_str)
        .unwrap_or("en")
        .to_owned();
    let target = params
        .get("provider")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_owned();
    let mut guard = chat.lock().unwrap_or_else(|p| p.into_inner());
    let result: Result<Value, String> = if let Some(agent_id) = target.strip_prefix("acp:") {
        (|| {
            let spec = acp::agent(agent_id).ok_or_else(|| format!("unknown agent `{agent_id}`"))?;
            let reuse = match &mut guard.backend {
                Some(Backend::Agent { id, session }) => id == agent_id && session.alive(),
                _ => false,
            };
            let first = !reuse;
            if !reuse {
                guard.backend = None;
                host.state.emit(
                    &conversation,
                    json!({ "type": "status", "code": "agentStarting", "detail": spec.name }),
                );
                let mcp = McpLaunch {
                    command: state.exe.clone(),
                    args: vec![
                        "mcp".into(),
                        "--attach".into(),
                        state.relay.clone(),
                        "--token".into(),
                        guard.token.clone(),
                    ],
                };
                let cwd = std::env::temp_dir().join("papy-ai-agent");
                let session = AcpSession::start(spec, &mcp, &cwd)?;
                guard.backend = Some(Backend::Agent {
                    id: agent_id.to_owned(),
                    session,
                });
            }
            let Some(Backend::Agent { session, .. }) = &mut guard.backend else {
                unreachable!()
            };
            let stop = session.prompt(
                &acp_prompt(&text, &document, &language, first),
                host.clone(),
            )?;
            if stop == "cancelled" {
                return Err("cancelled".to_owned());
            }
            Ok(json!({ "stop": stop }))
        })()
    } else {
        (|| {
            let kind =
                Kind::parse(&target).ok_or_else(|| format!("unknown provider `{target}`"))?;
            let provider = provider(&params, kind).map_err(|e| e.message)?;
            let tools = specs(Mode::App);
            if !matches!(&guard.backend, Some(Backend::Direct(c)) if c.kind == kind) {
                guard.backend = Some(Backend::Direct(Conversation::new(kind)));
            }
            let Some(Backend::Direct(conv)) = &mut guard.backend else {
                unreachable!()
            };
            let system = agent::system_prompt(&document, &language);
            let outcome = agent::run(&provider, conv, &system, &text, &tools, host.as_ref())?;
            Ok(json!({
                "stop": outcome.stop,
                "steps": outcome.steps,
                "usage": outcome.usage,
                "total": conv.usage,
                "cost": price_of(kind, &provider.model, outcome.usage),
                "totalCost": price_of(kind, &provider.model, conv.usage),
            }))
        })()
    };
    drop(guard);
    let cancelled = cancel.swap(false, Ordering::Relaxed);
    running.store(false, Ordering::Relaxed);
    match result {
        Ok(outcome) => state.emit(&conversation, json!({ "type": "done", "outcome": outcome })),
        Err(_) if cancelled => state.emit(&conversation, json!({ "type": "cancelled" })),
        Err(message) => state.emit(
            &conversation,
            json!({ "type": "error", "message": message }),
        ),
    }
}

fn handle(state: &Arc<State>, message: &Incoming) -> Result<Value, RpcError> {
    let params = &message.params;
    match message.method.as_str() {
        "initialize" => Ok(initialize()),
        "agents/detect" => Ok(acp::detect()),
        "keys/status" => Ok(keys_status()),
        "keys/set" => {
            let kind = kind_param(params)?;
            keys::set(kind, str_param(params, "key")?).map_err(RpcError::internal)?;
            Ok(keys_status())
        }
        "keys/delete" => {
            keys::delete(kind_param(params)?).map_err(RpcError::internal)?;
            Ok(keys_status())
        }
        "models/list" => {
            let kind = kind_param(params)?;
            let mut p = params.clone();
            p["model"] = json!("-");
            let provider = provider(&p, kind)?;
            let models = llm::list_models(&provider).map_err(RpcError::internal)?;
            Ok(json!({ "models": models }))
        }
        "chat/send" => {
            let conversation = str_param(params, "conversation")?.to_owned();
            str_param(params, "provider")?;
            if str_param(params, "text")?.trim().is_empty() {
                return Err(RpcError::invalid("the message is empty"));
            }
            let (cancel, running) = state.flags(&conversation);
            if running.swap(true, Ordering::Relaxed) {
                return Err(RpcError::new(
                    -32002,
                    "this conversation is still answering",
                ));
            }
            cancel.store(false, Ordering::Relaxed);
            let state = Arc::clone(state);
            let params = params.clone();
            std::thread::spawn(move || run_chat(state, conversation, params));
            Ok(json!({ "started": true }))
        }
        "chat/cancel" => {
            let conversation = str_param(params, "conversation")?;
            let (cancel, running) = state.flags(conversation);
            if running.load(Ordering::Relaxed) {
                cancel.store(true, Ordering::Relaxed);
            }
            Ok(json!({ "ok": true }))
        }
        "chat/reset" => {
            let conversation = str_param(params, "conversation")?;
            let (cancel, running) = state.flags(conversation);
            if running.load(Ordering::Relaxed) {
                cancel.store(true, Ordering::Relaxed);
            }
            let removed = state
                .chats
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .remove(conversation);
            if let Some(chat) = removed {
                // 실행 중이면 그 스레드가 끝날 때 함께 사라진다.
                let token = chat.lock().unwrap_or_else(|p| p.into_inner()).token.clone();
                state
                    .tokens
                    .lock()
                    .unwrap_or_else(|p| p.into_inner())
                    .remove(&token);
            }
            Ok(json!({ "ok": true }))
        }
        "shutdown" => {
            state
                .chats
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .clear();
            Ok(json!({ "ok": true }))
        }
        other => Err(RpcError::no_method(other)),
    }
}

/// 중계 소켓 접속 하나: 첫 줄 토큰, 이후 `{id,name,input}` → `{id,result}` | `{id,error}`.
fn relay_client(state: Arc<State>, stream: TcpStream) {
    let _ = stream.set_read_timeout(Some(Duration::from_secs(10)));
    let mut reader = BufReader::new(match stream.try_clone() {
        Ok(s) => s,
        Err(_) => return,
    });
    let mut writer = stream;
    let mut first = String::new();
    if reader.read_line(&mut first).is_err() {
        return;
    }
    let Some(conversation) = state
        .tokens
        .lock()
        .unwrap_or_else(|p| p.into_inner())
        .get(first.trim())
        .cloned()
    else {
        return;
    };
    let _ = writer.set_read_timeout(None);
    let _ = reader.get_ref().set_read_timeout(None);
    let tools = specs(Mode::App);
    for line in reader.lines() {
        let Ok(line) = line else { break };
        let Ok(request) = serde_json::from_str::<Value>(&line) else {
            continue;
        };
        let id = request.get("id").cloned().unwrap_or(Value::Null);
        let name = request.get("name").and_then(Value::as_str).unwrap_or("");
        let input = request.get("input").cloned().unwrap_or_else(|| json!({}));
        let reply = if !tools.iter().any(|t| t.name == name) {
            json!({ "id": id, "error": format!("unknown tool `{name}`") })
        } else {
            match state.call_tool(&conversation, name, &input) {
                Ok(result) => json!({ "id": id, "result": result }),
                Err(error) => json!({ "id": id, "error": error }),
            }
        };
        if writer.write_all(format!("{reply}\n").as_bytes()).is_err() {
            break;
        }
    }
}

/// 표준 입출력으로 앱과 이야기한다(앱이 끊을 때까지).
pub fn serve_stdio() -> std::io::Result<()> {
    let listener = TcpListener::bind("127.0.0.1:0")?;
    let relay = listener.local_addr()?.to_string();
    let (peer, incoming) = Peer::start(std::io::stdin(), std::io::stdout());
    let state = Arc::new(State {
        peer: peer.clone(),
        chats: Mutex::new(HashMap::new()),
        flags: Mutex::new(HashMap::new()),
        relay,
        tokens: Mutex::new(HashMap::new()),
        exe: std::env::current_exe()?,
    });
    {
        let state = Arc::clone(&state);
        std::thread::spawn(move || {
            for stream in listener.incoming().flatten() {
                let state = Arc::clone(&state);
                std::thread::spawn(move || relay_client(state, stream));
            }
        });
    }
    for message in incoming {
        let Some(id) = message.id.clone() else {
            continue;
        };
        let state = Arc::clone(&state);
        // 모델 목록처럼 오래 걸리는 요청이 다른 요청을 막지 않게 따로 돈다.
        std::thread::spawn(move || {
            let result = handle(&state, &message);
            let shutdown = message.method == "shutdown";
            state.peer.respond(&id, result);
            if shutdown {
                std::process::exit(0);
            }
        });
    }
    Ok(())
}
