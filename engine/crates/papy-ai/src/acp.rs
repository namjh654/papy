//! ACP(Agent Client Protocol v1) 연결: Claude Code·Codex·Gemini CLI 를 대화창의 상대로 쓴다.
//!
//! 에이전트는 자기 계정(구독)으로 로그인돼 있고 Papy 는 API 키를 받지 않는다. 에이전트에게는
//! `session/new` 의 `mcpServers` 로 Papy MCP 서버(앱 연결 모드)를 붙여 주고, 문서 도구는 그쪽으로만 쓴다.
//!
//! - 파일·터미널 기능(`fs/*`, `terminal/*`)은 알리지 않는다. 에이전트가 요청해도 거절한다.
//! - 권한 요청은 Papy 도구만 허용하고 나머지(셸·파일 수정 등)는 거절한다.

use std::collections::VecDeque;
use std::io::{BufRead, BufReader};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde::Serialize;
use serde_json::{Value, json};

use crate::rpc::{Incoming, Peer, RpcError};

/// ACP 프로토콜 버전.
pub const PROTOCOL_VERSION: u64 = 1;

/// 알려진 에이전트.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AgentSpec {
    /// id(`acp:` 뒤에 붙는 이름).
    pub id: &'static str,
    /// 보이는 이름.
    pub name: &'static str,
    /// 실행 파일.
    pub command: &'static str,
    /// 인자.
    pub args: &'static [&'static str],
    /// 먼저 설치·로그인할 것.
    pub setup: &'static str,
}

/// 지원하는 에이전트 목록.
pub const AGENTS: [AgentSpec; 3] = [
    AgentSpec {
        id: "claude-code",
        name: "Claude Code",
        command: "npx",
        args: &["-y", "@agentclientprotocol/claude-agent-acp"],
        setup: "Node.js 22+ and a Claude Code sign-in (run `claude` once)",
    },
    AgentSpec {
        id: "codex",
        name: "Codex",
        command: "npx",
        args: &["-y", "@agentclientprotocol/codex-acp"],
        setup: "Node.js 22+ and a Codex sign-in (run `codex login` once)",
    },
    AgentSpec {
        id: "gemini",
        name: "Gemini CLI",
        command: "gemini",
        args: &["--experimental-acp"],
        setup: "Gemini CLI (`npm i -g @google/gemini-cli`) and a sign-in (run `gemini` once)",
    },
];

/// id 로 찾는다.
pub fn agent(id: &str) -> Option<&'static AgentSpec> {
    AGENTS.iter().find(|a| a.id == id)
}

/// `PATH` 에서 실행 파일을 찾는다(Windows 는 `.cmd`·`.exe` 도).
pub fn which(command: &str) -> Option<PathBuf> {
    let path = std::env::var_os("PATH")?;
    let names: Vec<String> = if cfg!(windows) {
        vec![
            format!("{command}.cmd"),
            format!("{command}.exe"),
            command.to_owned(),
        ]
    } else {
        vec![command.to_owned()]
    };
    std::env::split_paths(&path)
        .flat_map(|dir| names.iter().map(move |name| dir.join(name)))
        .find(|candidate| candidate.is_file())
}

/// `PAPY_AI_AGENT_<ID>` 로 바꾼 실행 방법.
fn override_command(id: &str) -> Option<Vec<String>> {
    let name = format!(
        "PAPY_AI_AGENT_{}",
        id.to_ascii_uppercase().replace('-', "_")
    );
    serde_json::from_str::<Vec<String>>(&std::env::var(name).ok()?)
        .ok()
        .filter(|v| !v.is_empty())
}

/// 에이전트별로 실행할 수 있는지.
pub fn detect() -> Value {
    Value::Array(
        AGENTS
            .iter()
            .map(|spec| {
                let mut value = serde_json::to_value(spec).unwrap_or_default();
                value["available"] =
                    json!(override_command(spec.id).is_some() || which(spec.command).is_some());
                value
            })
            .collect(),
    )
}

/// 에이전트에게 붙여 줄 MCP 서버 실행 방법.
#[derive(Debug, Clone)]
pub struct McpLaunch {
    /// 절대 경로.
    pub command: PathBuf,
    /// 인자.
    pub args: Vec<String>,
}

/// 에이전트 쪽 사건을 받는 곳.
pub trait AcpHost: Send + Sync {
    /// 화면 알림(`agent::run` 과 같은 모양).
    fn event(&self, event: Value);
    /// 사용자가 멈췄는가.
    fn cancelled(&self) -> bool;
}

/// 권한 요청에 답한다: Papy 도구만 한 번 허용한다.
pub fn permission_reply(params: &Value) -> Value {
    let call = params.get("toolCall").cloned().unwrap_or(Value::Null);
    let options = params
        .get("options")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    let pick = |kinds: &[&str]| {
        options
            .iter()
            .find(|o| {
                o.get("kind")
                    .and_then(Value::as_str)
                    .is_some_and(|k| kinds.contains(&k))
            })
            .and_then(|o| o.get("optionId").and_then(Value::as_str))
            .map(str::to_owned)
    };
    let choice = if is_papy_tool(&call) {
        pick(&["allow_once", "allow_always"])
    } else {
        pick(&["reject_once", "reject_always"])
    };
    match choice {
        Some(option_id) => json!({ "outcome": { "outcome": "selected", "optionId": option_id } }),
        None => json!({ "outcome": { "outcome": "cancelled" } }),
    }
}

/// Papy MCP 도구 호출인가(이름·제목에 서버 이름이 붙는다: `mcp__papy__find_text`, `papy/find_text` 등).
pub fn is_papy_tool(call: &Value) -> bool {
    let tool_names: Vec<String> = papy_tools::specs(papy_tools::Mode::App)
        .into_iter()
        .map(|t| t.name.to_owned())
        .collect();
    ["name", "title"]
        .iter()
        .filter_map(|key| call.get(*key).and_then(Value::as_str))
        .any(|label| {
            let label = label.to_ascii_lowercase();
            let papy = label.contains("papy");
            papy && tool_names.iter().any(|tool| label.contains(tool.as_str()))
        })
}

/// 세션 알림을 화면 알림으로. 보일 것이 없으면 `None`.
pub fn map_update(update: &Value) -> Option<Value> {
    let kind = update.get("sessionUpdate").and_then(Value::as_str)?;
    let text = || {
        update
            .pointer("/content/text")
            .and_then(Value::as_str)
            .map(str::to_owned)
    };
    match kind {
        "agent_message_chunk" => text().map(|text| json!({ "type": "text", "text": text })),
        "agent_thought_chunk" => text().map(|text| json!({ "type": "thinking", "text": text })),
        "tool_call" => Some(json!({
            "type": "toolStart",
            "id": update.get("toolCallId"),
            "name": update.get("title").or_else(|| update.get("name")),
            "input": update.get("rawInput"),
        })),
        "tool_call_update" => match update.get("status").and_then(Value::as_str) {
            Some(status @ ("completed" | "failed")) => Some(json!({
                "type": "toolEnd",
                "id": update.get("toolCallId"),
                "name": update.get("title").or_else(|| update.get("name")),
                "result": { "ok": status == "completed" },
            })),
            _ => None,
        },
        "plan" => Some(json!({ "type": "plan", "entries": update.get("entries") })),
        _ => None,
    }
}

const STDERR_KEEP: usize = 40;

/// 에이전트 하나와의 세션(대화 하나).
pub struct AcpSession {
    child: Child,
    peer: Peer,
    session_id: String,
    host: Arc<Mutex<Option<Arc<dyn AcpHost>>>>,
    stderr: Arc<Mutex<VecDeque<String>>>,
    /// 에이전트 이름.
    pub name: String,
}

fn rpc_message(error: RpcError, stderr: &Mutex<VecDeque<String>>) -> String {
    let tail: Vec<String> = stderr
        .lock()
        .unwrap_or_else(|p| p.into_inner())
        .iter()
        .cloned()
        .collect();
    let auth = error.code == -32000 || error.message.to_ascii_lowercase().contains("auth");
    let mut message = error.message;
    if auth {
        message.push_str(" — sign in with the agent's own app first");
    }
    if !tail.is_empty() {
        message.push('\n');
        message.push_str(&tail.join("\n"));
    }
    message
}

impl AcpSession {
    /// 에이전트를 띄우고 세션을 만든다.
    ///
    /// 환경 변수 `PAPY_AI_AGENT_<ID>`(예: `PAPY_AI_AGENT_CLAUDE_CODE`)에 JSON 배열 `["명령", "인자", …]` 을
    /// 두면 그것으로 띄운다(다른 곳에 설치한 경우·시험).
    pub fn start(spec: &AgentSpec, mcp: &McpLaunch, cwd: &Path) -> Result<Self, String> {
        if let Some(custom) = override_command(spec.id) {
            let (command, args) = custom.split_first().ok_or("the agent override is empty")?;
            return Self::start_command(spec.name, Path::new(command), args, mcp, cwd);
        }
        let command = which(spec.command)
            .ok_or_else(|| format!("{} is not installed. Needs: {}", spec.command, spec.setup))?;
        Self::start_command(
            spec.name,
            &command,
            &spec
                .args
                .iter()
                .map(|a| (*a).to_owned())
                .collect::<Vec<_>>(),
            mcp,
            cwd,
        )
    }

    /// 실행 파일을 직접 지정해 띄운다(시험용으로도 쓴다).
    pub fn start_command(
        name: &str,
        command: &Path,
        args: &[String],
        mcp: &McpLaunch,
        cwd: &Path,
    ) -> Result<Self, String> {
        std::fs::create_dir_all(cwd)
            .map_err(|e| format!("cannot prepare a working folder: {e}"))?;
        let mut child = Command::new(command)
            .args(args)
            .current_dir(cwd)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .map_err(|e| format!("cannot start {name}: {e}"))?;
        let stdin = child.stdin.take().ok_or("no stdin")?;
        let stdout = child.stdout.take().ok_or("no stdout")?;
        let stderr_pipe = child.stderr.take().ok_or("no stderr")?;
        let stderr = Arc::new(Mutex::new(VecDeque::new()));
        {
            let stderr = Arc::clone(&stderr);
            std::thread::spawn(move || {
                for line in BufReader::new(stderr_pipe).lines().map_while(Result::ok) {
                    let mut tail = stderr.lock().unwrap_or_else(|p| p.into_inner());
                    if tail.len() >= STDERR_KEEP {
                        tail.pop_front();
                    }
                    tail.push_back(line);
                }
            });
        }
        let (peer, incoming) = Peer::start(stdout, stdin);
        let host: Arc<Mutex<Option<Arc<dyn AcpHost>>>> = Arc::new(Mutex::new(None));
        {
            let peer = peer.clone();
            let host = Arc::clone(&host);
            std::thread::spawn(move || {
                for message in incoming {
                    handle_incoming(&peer, &host, message);
                }
            });
        }
        let mut session = Self {
            child,
            peer,
            session_id: String::new(),
            host,
            stderr,
            name: name.to_owned(),
        };
        let init = session
            .peer
            .request(
                "initialize",
                json!({
                    "protocolVersion": PROTOCOL_VERSION,
                    "clientCapabilities": { "fs": { "readTextFile": false, "writeTextFile": false }, "terminal": false },
                    "clientInfo": { "name": "papy", "title": "Papy", "version": env!("CARGO_PKG_VERSION") },
                }),
                // npx 가 처음 받을 때는 오래 걸린다.
                Duration::from_secs(180),
            )
            .map_err(|e| rpc_message(e, &session.stderr))?;
        if init
            .get("protocolVersion")
            .and_then(Value::as_u64)
            .is_some_and(|v| v != PROTOCOL_VERSION)
        {
            return Err(format!("{name} speaks a different ACP version"));
        }
        let new_session = json!({
            "cwd": cwd.to_string_lossy(),
            "mcpServers": [{
                "name": "papy",
                "command": mcp.command.to_string_lossy(),
                "args": mcp.args,
                "env": [],
            }],
        });
        let created = session
            .peer
            .request("session/new", new_session, Duration::from_secs(120))
            .map_err(|e| rpc_message(e, &session.stderr))?;
        session.session_id = created
            .get("sessionId")
            .and_then(Value::as_str)
            .ok_or_else(|| format!("{name} did not open a session"))?
            .to_owned();
        Ok(session)
    }

    /// 글 하나를 보내고 답이 끝날 때까지 기다린다. 멈춘 이유(`end_turn` 등)를 돌려준다.
    pub fn prompt(&mut self, text: &str, host: Arc<dyn AcpHost>) -> Result<String, String> {
        *self.host.lock().unwrap_or_else(|p| p.into_inner()) = Some(Arc::clone(&host));
        let reply = self.peer.send_request(
            "session/prompt",
            json!({ "sessionId": self.session_id, "prompt": [{ "type": "text", "text": text }] }),
        );
        let result = match reply {
            Err(error) => Err(rpc_message(error, &self.stderr)),
            Ok(rx) => {
                let mut cancel_sent = false;
                loop {
                    match rx.recv_timeout(Duration::from_millis(200)) {
                        Ok(Ok(value)) => {
                            break Ok(value
                                .get("stopReason")
                                .and_then(Value::as_str)
                                .unwrap_or("end_turn")
                                .to_owned());
                        }
                        Ok(Err(error)) => break Err(rpc_message(error, &self.stderr)),
                        Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {
                            if !cancel_sent && host.cancelled() {
                                self.peer.notify(
                                    "session/cancel",
                                    json!({ "sessionId": self.session_id }),
                                );
                                cancel_sent = true;
                            }
                            if let Ok(Some(status)) = self.child.try_wait() {
                                break Err(rpc_message(
                                    RpcError::internal(format!("{} stopped ({status})", self.name)),
                                    &self.stderr,
                                ));
                            }
                        }
                        Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                            break Err(rpc_message(
                                RpcError::internal("the agent closed the connection"),
                                &self.stderr,
                            ));
                        }
                    }
                }
            }
        };
        *self.host.lock().unwrap_or_else(|p| p.into_inner()) = None;
        result
    }

    /// 아직 살아 있는가.
    pub fn alive(&mut self) -> bool {
        matches!(self.child.try_wait(), Ok(None))
    }
}

impl Drop for AcpSession {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

fn handle_incoming(peer: &Peer, host: &Mutex<Option<Arc<dyn AcpHost>>>, message: Incoming) {
    let current = host.lock().unwrap_or_else(|p| p.into_inner()).clone();
    match message.method.as_str() {
        "session/update" => {
            if let (Some(host), Some(event)) =
                (current, message.params.get("update").and_then(map_update))
            {
                host.event(event);
            }
        }
        "session/request_permission" => {
            let reply = permission_reply(&message.params);
            if let Some(host) = &current {
                let allowed = reply.pointer("/outcome/optionId").is_some()
                    && is_papy_tool(message.params.get("toolCall").unwrap_or(&Value::Null));
                if !allowed {
                    host.event(json!({
                        "type": "notice",
                        "code": "permissionDenied",
                        "detail": message.params.pointer("/toolCall/title"),
                    }));
                }
            }
            if let Some(id) = &message.id {
                peer.respond(id, Ok(reply));
            }
        }
        other => {
            if let Some(id) = &message.id {
                peer.respond(id, Err(RpcError::no_method(other)));
            }
        }
    }
}
