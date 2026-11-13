//! MCP 서버(stdio). Claude 데스크톱·ChatGPT·Gemini CLI·Claude Code 같은 AI 앱이 Papy 도구를 부른다.
//!
//! - 단독(`papy-ai mcp`): 파일을 직접 열고 메모리 사본만 고친다. 저장은 새 파일로만 한다.
//! - 앱 연결(`papy-ai mcp --attach 주소 --token 값`): 앱 대화창이 연 ACP 에이전트가 쓴다.
//!   도구 호출을 플러그인(`serve`)에 넘기고, 플러그인이 앱에 열린 문서에서 실행한다.

use std::io::{BufRead, BufReader, Write};
use std::net::TcpStream;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use papy_core::{Engine, EngineResources, Limits};
use papy_tools::{Mode, ToolSpec, Workspace, specs};
use serde_json::{Value, json};

use crate::rpc::{Incoming, Peer, RpcError};

/// 지원하는 MCP 프로토콜 버전(새것부터).
const PROTOCOL_VERSIONS: [&str; 3] = ["2025-06-18", "2025-03-26", "2024-11-05"];

/// 도구 실행기.
pub trait Backend: Send {
    /// 쓸 수 있는 도구.
    fn tools(&self) -> Vec<ToolSpec>;
    /// 도구를 실행한다. 실패는 모델이 읽을 설명으로 돌려준다.
    fn call(&mut self, name: &str, args: &Value) -> Result<Value, String>;
}

/// 단독 실행기: 디스크 파일을 연다.
pub struct Standalone(Workspace);

impl Standalone {
    /// 엔진을 만든다. PDFium·글꼴은 플러그인 옆 또는 환경 변수 `PAPY_PDFIUM`·`PAPY_FONT` 에서 찾는다.
    pub fn new() -> Self {
        let exe_dir = std::env::current_exe()
            .ok()
            .and_then(|exe| exe.parent().map(PathBuf::from));
        let pdfium = std::env::var_os("PAPY_PDFIUM")
            .map(PathBuf::from)
            .or_else(|| {
                exe_dir
                    .as_ref()
                    .map(|dir| dir.join(papy_core::pdfium_library_file_name()))
                    .filter(|p| p.exists())
            });
        let font = std::env::var_os("PAPY_FONT")
            .map(PathBuf::from)
            .filter(|p| p.exists());
        let engine = match pdfium {
            Some(pdfium_library) => Engine::with_resources(
                Limits::default(),
                EngineResources {
                    pdfium_library,
                    korean_font: font,
                },
            ),
            None => Engine::new(Limits::default()),
        };
        Self(Workspace::new(engine))
    }
}

impl Default for Standalone {
    fn default() -> Self {
        Self::new()
    }
}

impl Backend for Standalone {
    fn tools(&self) -> Vec<ToolSpec> {
        specs(Mode::Standalone)
    }

    fn call(&mut self, name: &str, args: &Value) -> Result<Value, String> {
        self.0.call(name, args).map_err(|error| error.message)
    }
}

/// 앱 연결 실행기: 플러그인의 중계 소켓으로 넘긴다.
pub struct Attached {
    stream: Mutex<(BufReader<TcpStream>, TcpStream)>,
    next: u64,
}

impl Attached {
    /// 중계 소켓에 붙는다. 첫 줄로 접속 토큰을 보낸다.
    pub fn connect(address: &str, token: &str) -> std::io::Result<Self> {
        let mut stream = TcpStream::connect(address)?;
        stream.write_all(format!("{token}\n").as_bytes())?;
        let reader = BufReader::new(stream.try_clone()?);
        Ok(Self {
            stream: Mutex::new((reader, stream)),
            next: 1,
        })
    }
}

impl Backend for Attached {
    fn tools(&self) -> Vec<ToolSpec> {
        specs(Mode::App)
    }

    fn call(&mut self, name: &str, args: &Value) -> Result<Value, String> {
        let id = self.next;
        self.next += 1;
        let mut guard = self.stream.lock().unwrap_or_else(|p| p.into_inner());
        let (reader, writer) = &mut *guard;
        let line = json!({ "id": id, "name": name, "input": args }).to_string() + "\n";
        writer
            .write_all(line.as_bytes())
            .map_err(|e| format!("Papy is not reachable: {e}"))?;
        let mut reply = String::new();
        reader
            .read_line(&mut reply)
            .map_err(|e| format!("Papy is not reachable: {e}"))?;
        let reply: Value =
            serde_json::from_str(&reply).map_err(|_| "Papy closed the connection".to_owned())?;
        match reply.get("error").and_then(Value::as_str) {
            Some(error) => Err(error.to_owned()),
            None => Ok(reply.get("result").cloned().unwrap_or(Value::Null)),
        }
    }
}

/// 도구 정의를 MCP `tools/list` 모양으로.
pub fn tool_list(tools: &[ToolSpec]) -> Value {
    json!({
        "tools": tools
            .iter()
            .map(|tool| json!({
                "name": tool.name,
                "description": tool.description,
                "inputSchema": tool.input_schema,
                "annotations": {
                    "readOnlyHint": matches!(tool.kind, papy_tools::ToolKind::Read | papy_tools::ToolKind::Workspace),
                    "destructiveHint": false,
                },
            }))
            .collect::<Vec<_>>()
    })
}

/// 요청 하나를 처리한다. 알림이면 `None`.
pub fn handle(backend: &mut dyn Backend, incoming: &Incoming) -> Option<Result<Value, RpcError>> {
    incoming.id.as_ref()?;
    Some(match incoming.method.as_str() {
        "initialize" => {
            let asked = incoming
                .params
                .get("protocolVersion")
                .and_then(Value::as_str)
                .unwrap_or("");
            let version = PROTOCOL_VERSIONS
                .iter()
                .find(|v| **v == asked)
                .copied()
                .unwrap_or(PROTOCOL_VERSIONS[0]);
            Ok(json!({
                "protocolVersion": version,
                "capabilities": { "tools": { "listChanged": false } },
                "serverInfo": { "name": "papy", "title": "Papy documents", "version": env!("CARGO_PKG_VERSION") },
                "instructions": "Tools for reading and editing PDF, HWP, HWPX, DOCX and DOC documents with Papy. \
                    Read before you edit; refer to paragraphs by the ids read_paragraphs returns.",
            }))
        }
        "ping" => Ok(json!({})),
        "tools/list" => Ok(tool_list(&backend.tools())),
        "tools/call" => {
            let name = incoming
                .params
                .get("name")
                .and_then(Value::as_str)
                .unwrap_or("");
            let args = incoming
                .params
                .get("arguments")
                .cloned()
                .unwrap_or_else(|| json!({}));
            if !backend.tools().iter().any(|tool| tool.name == name) {
                return Some(Err(RpcError::invalid(format!("unknown tool: {name}"))));
            }
            Ok(match backend.call(name, &args) {
                Ok(result) => json!({
                    "content": [{ "type": "text", "text": serde_json::to_string_pretty(&result).unwrap_or_default() }],
                    "structuredContent": result,
                    "isError": false,
                }),
                Err(message) => json!({
                    "content": [{ "type": "text", "text": message }],
                    "isError": true,
                }),
            })
        }
        "resources/list" => Ok(json!({ "resources": [] })),
        "prompts/list" => Ok(json!({ "prompts": [] })),
        other => Err(RpcError::no_method(other)),
    })
}

/// 표준 입출력으로 MCP 서버를 돌린다(상대가 끊을 때까지).
pub fn serve_stdio(backend: Box<dyn Backend>) {
    let (peer, incoming) = Peer::start(std::io::stdin(), std::io::stdout());
    let backend = Arc::new(Mutex::new(backend));
    for message in incoming {
        let mut guard = backend.lock().unwrap_or_else(|p| p.into_inner());
        if let (Some(id), Some(result)) = (message.id.clone(), handle(guard.as_mut(), &message)) {
            peer.respond(&id, result);
        }
    }
}
