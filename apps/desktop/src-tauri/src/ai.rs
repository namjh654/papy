//! AI 플러그인 관리(ADR 0009). 플러그인(`papy-ai`)은 앱과 따로 받는 실행 파일이다.
//!
//! - 설치: 앱과 같은 버전의 GitHub 릴리스에서 `papy-ai-{대상}.gz` 와 서명(`.sig`)을 받아, 앱에 넣어 둔
//!   ed25519 공개 키로 확인한 뒤 앱 데이터 폴더에 푼다. 서명이 맞지 않으면 쓰지 않는다.
//! - 실행: `papy-ai serve` 를 띄워 줄 단위 JSON-RPC 로 이야기한다. API 키는 플러그인이 운영체제
//!   키체인에 둔다(앱은 키를 받아 넘기기만 하고 저장하지 않는다).
//! - 도구: 플러그인이 `tool/call` 로 부르면 그 대화에 묶인 문서에서 실행한다. 고치기 도구는 바로
//!   고치지 않고 제안을 만들어 화면(`ai-proposal`)에 넘긴다. 사용자가 적용을 눌러야 고친다.
//! - 개발: 환경 변수 `PAPY_AI_PLUGIN` 에 실행 파일 경로를 두면 설치 없이 그것을 쓴다.

use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use base64::Engine as _;
use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use papy_core::Engine;
use papy_tools::rpc::{Incoming, Peer, RpcError};
use papy_tools::{Mode, Plan, ToolKind};
use serde_json::{Value, json};
use tauri::{AppHandle, Emitter};

use crate::host;

/// 플러그인 서명 공개 키(ed25519, 원시 32바이트 base64). 비밀 키는 저장소 밖(GitHub 비밀 값)에 있다.
const SIGNING_KEY: &str = "iUEu7BavB3pzvvv63jTFF5ZgSkzWmpBpOdxqx489UNs=";
/// 릴리스 내려받기 주소. 시험할 때는 `PAPY_AI_RELEASES` 로 바꾼다.
const RELEASES: &str = "https://github.com/namjh654/papy/releases/download";
/// 받을 파일 크기 한도.
const MAX_DOWNLOAD: u64 = 200 * 1024 * 1024;
/// 플러그인 요청 응답 기다리는 시간.
const TIMEOUT: Duration = Duration::from_secs(60);

/// 이 앱이 도는 대상(릴리스 파일 이름에 쓴다).
pub fn target() -> Option<&'static str> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("macos", "aarch64") => Some("aarch64-apple-darwin"),
        ("macos", "x86_64") => Some("x86_64-apple-darwin"),
        ("windows", "x86_64") => Some("x86_64-pc-windows-msvc"),
        ("linux", "x86_64") => Some("x86_64-unknown-linux-gnu"),
        _ => None,
    }
}

fn exe_name() -> &'static str {
    if cfg!(windows) {
        "papy-ai.exe"
    } else {
        "papy-ai"
    }
}

/// 서명을 확인한다.
pub fn verify(data: &[u8], signature_b64: &str) -> Result<(), String> {
    let engine = base64::engine::general_purpose::STANDARD;
    let key: [u8; 32] = engine
        .decode(SIGNING_KEY)
        .ok()
        .and_then(|k| k.try_into().ok())
        .ok_or("bad built-in key")?;
    let key = VerifyingKey::from_bytes(&key).map_err(|_| "bad built-in key")?;
    let signature: [u8; 64] = engine
        .decode(signature_b64.trim())
        .ok()
        .and_then(|s| s.try_into().ok())
        .ok_or("the plugin signature is malformed")?;
    key.verify(data, &Signature::from_bytes(&signature))
        .map_err(|_| "the plugin signature does not match; it was not installed".to_owned())
}

struct Plugin {
    child: Child,
    peer: Peer,
    info: Value,
}

impl Drop for Plugin {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

/// 고치기 제안 하나.
struct Proposal {
    doc: u64,
    plan: Plan,
}

/// 앱이 들고 있는 AI 상태.
pub struct Ai {
    dir: PathBuf,
    version: &'static str,
    plugin: Mutex<Option<Plugin>>,
    proposals: Mutex<HashMap<u64, Proposal>>,
    next: AtomicU64,
    /// 대화 → 그 대화가 다루는 문서(세션 id). 보낼 때마다 지금 문서로 바꾼다.
    docs: Mutex<HashMap<String, Option<u64>>>,
    /// 설치 중 한 번만.
    installing: Mutex<()>,
}

fn lock<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|p| p.into_inner())
}

impl Ai {
    /// `dir` 은 앱 데이터 폴더 안 플러그인 자리.
    pub fn new(dir: PathBuf) -> Self {
        Self {
            dir,
            version: env!("CARGO_PKG_VERSION"),
            plugin: Mutex::new(None),
            proposals: Mutex::new(HashMap::new()),
            next: AtomicU64::new(1),
            docs: Mutex::new(HashMap::new()),
            installing: Mutex::new(()),
        }
    }

    fn dev_path() -> Option<PathBuf> {
        std::env::var_os("PAPY_AI_PLUGIN")
            .map(PathBuf::from)
            .filter(|p| p.is_file())
    }

    /// 실행 파일 위치(있으면).
    pub fn binary(&self) -> Option<PathBuf> {
        Self::dev_path().or_else(|| Some(self.dir.join(exe_name())).filter(|p| p.is_file()))
    }

    fn installed_version(&self) -> Option<String> {
        if Self::dev_path().is_some() {
            return Some(self.version.to_owned());
        }
        std::fs::read_to_string(self.dir.join("version"))
            .ok()
            .map(|v| v.trim().to_owned())
    }

    /// 플러그인을 띄운다(이미 떠 있으면 그대로).
    fn ensure(&self, app: &AppHandle, engine: &Arc<Engine>, me: &Arc<Ai>) -> Result<Peer, String> {
        let mut slot = lock(&self.plugin);
        if let Some(plugin) = slot.as_mut() {
            if matches!(plugin.child.try_wait(), Ok(None)) {
                return Ok(plugin.peer.clone());
            }
            *slot = None;
        }
        let binary = self.binary().ok_or("the AI plugin is not installed")?;
        let mut command = Command::new(&binary);
        command
            .arg("serve")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null());
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            // 콘솔 창을 띄우지 않는다.
            command.creation_flags(0x0800_0000);
        }
        let mut child = command
            .spawn()
            .map_err(|e| format!("cannot start the AI plugin: {e}"))?;
        let stdout = child.stdout.take().ok_or("no stdout")?;
        let stdin = child.stdin.take().ok_or("no stdin")?;
        let (peer, incoming) = Peer::start(stdout, stdin);
        {
            let app = app.clone();
            let engine = Arc::clone(engine);
            let me = Arc::clone(me);
            let peer = peer.clone();
            std::thread::spawn(move || {
                for message in incoming {
                    match message.method.as_str() {
                        "chat/event" => {
                            let _ = app.emit("ai-event", message.params);
                        }
                        "tool/call" => {
                            let (app, engine, me, peer) = (
                                app.clone(),
                                Arc::clone(&engine),
                                Arc::clone(&me),
                                peer.clone(),
                            );
                            std::thread::spawn(move || {
                                let result = me.tool_call(&app, &engine, &message);
                                if let Some(id) = &message.id {
                                    peer.respond(id, result);
                                }
                            });
                        }
                        _ => {
                            if let Some(id) = &message.id {
                                peer.respond(id, Err(RpcError::no_method(&message.method)));
                            }
                        }
                    }
                }
                let _ = app.emit(
                    "ai-event",
                    json!({ "conversation": null, "event": { "type": "pluginStopped" } }),
                );
            });
        }
        let info = peer
            .request(
                "initialize",
                json!({ "app": "papy", "version": self.version }),
                TIMEOUT,
            )
            .map_err(|e| e.message)?;
        *slot = Some(Plugin {
            child,
            peer: peer.clone(),
            info,
        });
        Ok(peer)
    }

    fn stop(&self) {
        *lock(&self.plugin) = None;
    }

    /// 플러그인에 요청한다.
    pub fn request(
        &self,
        app: &AppHandle,
        engine: &Arc<Engine>,
        me: &Arc<Ai>,
        method: &str,
        params: Value,
    ) -> Result<Value, String> {
        let peer = self.ensure(app, engine, me)?;
        peer.request(method, params, TIMEOUT).map_err(|e| e.message)
    }

    /// 상태: 설치 여부·버전·연결 가능한 곳·저장된 키.
    pub fn status(&self, app: &AppHandle, engine: &Arc<Engine>, me: &Arc<Ai>) -> Value {
        let installed = self.installed_version();
        let mut out = json!({
            "installed": installed.is_some(),
            "version": installed,
            "appVersion": self.version,
            "supported": target().is_some(),
            "dev": Self::dev_path().is_some(),
            "path": self.binary(),
        });
        if installed.as_deref().is_some_and(|v| v != self.version) {
            out["outdated"] = json!(true);
            return out;
        }
        if installed.is_some() {
            match self.ensure(app, engine, me) {
                Ok(peer) => {
                    out["info"] = lock(&self.plugin)
                        .as_ref()
                        .map(|p| p.info.clone())
                        .unwrap_or(Value::Null);
                    out["keys"] = peer
                        .request("keys/status", json!({}), TIMEOUT)
                        .unwrap_or(Value::Null);
                }
                Err(error) => out["error"] = json!(error),
            }
        }
        out
    }

    /// 내려받아 설치한다. 진행은 `ai-install` 이벤트로 알린다.
    pub fn install(&self, app: &AppHandle) -> Result<(), String> {
        let _guard = self
            .installing
            .try_lock()
            .map_err(|_| "already installing")?;
        let target = target().ok_or("the AI plugin is not available for this computer")?;
        let base = std::env::var("PAPY_AI_RELEASES").unwrap_or_else(|_| RELEASES.to_owned());
        self.stop();
        install_from(&base, self.version, target, &self.dir, &|stage| {
            let _ = app.emit("ai-install", json!({ "stage": stage }));
        })
    }

    /// 지운다(키체인의 키는 플러그인이 있을 때 먼저 지운다).
    pub fn uninstall(
        &self,
        app: &AppHandle,
        engine: &Arc<Engine>,
        me: &Arc<Ai>,
        forget_keys: bool,
    ) -> Result<(), String> {
        if forget_keys && self.binary().is_some() {
            for provider in ["anthropic", "openai", "gemini"] {
                let _ = self.request(
                    app,
                    engine,
                    me,
                    "keys/delete",
                    json!({ "provider": provider }),
                );
            }
        }
        self.stop();
        lock(&self.proposals).clear();
        if self.dir.exists() {
            std::fs::remove_dir_all(&self.dir)
                .map_err(|e| format!("cannot remove the plugin: {e}"))?;
        }
        Ok(())
    }

    /// 대화가 다룰 문서를 정한다.
    pub fn bind(&self, conversation: &str, doc: Option<u64>) {
        lock(&self.docs).insert(conversation.to_owned(), doc);
    }

    /// 대화를 지운다(그 대화의 제안도).
    pub fn forget(&self, conversation: &str) {
        lock(&self.docs).remove(conversation);
    }

    /// 문서를 닫으면 그 문서의 제안을 버린다.
    pub fn document_closed(&self, doc: u64) {
        lock(&self.proposals).retain(|_, p| p.doc != doc);
    }

    fn tool_call(
        &self,
        app: &AppHandle,
        engine: &Engine,
        message: &Incoming,
    ) -> Result<Value, RpcError> {
        let params = &message.params;
        let conversation = params
            .get("conversation")
            .and_then(Value::as_str)
            .unwrap_or("")
            .to_owned();
        let name = params.get("name").and_then(Value::as_str).unwrap_or("");
        let input = params.get("input").cloned().unwrap_or_else(|| json!({}));
        let doc = lock(&self.docs)
            .get(&conversation)
            .copied()
            .flatten()
            .ok_or_else(|| {
                RpcError::internal("No document is open in Papy. Ask the user to open one.")
            })?;
        let id = host::session(doc).map_err(|e| RpcError::internal(e.message))?;
        let fail = |e: papy_core::Error| RpcError::internal(e.message);
        match papy_tools::kind_of(Mode::App, name) {
            Some(ToolKind::Read) => papy_tools::call_read(engine, id, name, &input).map_err(fail),
            Some(ToolKind::Edit) => {
                let plan = papy_tools::plan(engine, id, name, &input).map_err(fail)?;
                let proposal = self.next.fetch_add(1, Ordering::Relaxed);
                let reply = papy_tools::plan_reply(&plan, proposal);
                let _ = app.emit(
                    "ai-proposal",
                    json!({ "conversation": conversation, "id": proposal, "doc": doc, "tool": plan.tool, "summary": plan.summary, "changes": plan.changes }),
                );
                lock(&self.proposals).insert(proposal, Proposal { doc, plan });
                Ok(reply)
            }
            Some(ToolKind::Export) => {
                // 이 문서가 그 형식으로 저장될 수 있는지만 먼저 확인한다(저장 위치는 사용자가 고른다).
                papy_tools::export_format(engine, id, &input).map_err(fail)?;
                let extension = input
                    .get("format")
                    .and_then(Value::as_str)
                    .unwrap_or("")
                    .to_owned();
                let _ = app.emit(
                    "ai-export",
                    json!({ "conversation": conversation, "doc": doc, "format": extension }),
                );
                Ok(
                    json!({ "status": "offered", "note": "Papy asked the user where to save the copy. It is not saved yet." }),
                )
            }
            _ => Err(RpcError::invalid(format!("unknown tool `{name}`"))),
        }
    }

    /// 제안을 적용한다.
    pub fn apply(&self, engine: &Engine, proposal: u64) -> Result<u64, papy_core::Error> {
        let taken = lock(&self.proposals).remove(&proposal);
        let Some(Proposal { doc, plan }) = taken else {
            return Err(papy_core::Error::new(
                papy_core::ErrorCode::InvalidArgument,
                "this proposal is no longer available",
            ));
        };
        papy_tools::apply(engine, host::session(doc)?, &plan)?;
        Ok(doc)
    }

    /// 제안을 버린다.
    pub fn reject(&self, proposal: u64) {
        lock(&self.proposals).remove(&proposal);
    }

    /// 다른 AI 앱에 붙일 MCP 서버 설정.
    pub fn mcp_config(&self, pdfium: &Path, font: Option<&Path>) -> Option<Value> {
        let binary = self.binary()?;
        let mut env = json!({ "PAPY_PDFIUM": pdfium });
        if let Some(font) = font {
            env["PAPY_FONT"] = json!(font);
        }
        Some(json!({ "command": binary, "args": ["mcp"], "env": env }))
    }
}

/// `{base}/v{version}/papy-ai-{target}.gz` 와 `.sig` 를 받아 서명을 확인하고 `dir` 에 푼다.
fn install_from(
    base: &str,
    version: &str,
    target: &str,
    dir: &Path,
    stage: &dyn Fn(&str),
) -> Result<(), String> {
    let url = format!(
        "{}/v{version}/papy-ai-{target}.gz",
        base.trim_end_matches('/')
    );
    stage("download");
    let gz = download(&url)?;
    let signature = String::from_utf8(download(&format!("{url}.sig"))?)
        .map_err(|_| "the plugin signature is malformed")?;
    stage("verify");
    verify(&gz, &signature)?;
    let mut binary = Vec::new();
    flate2::read::GzDecoder::new(gz.as_slice())
        .take(MAX_DOWNLOAD * 3)
        .read_to_end(&mut binary)
        .map_err(|e| format!("the plugin download is damaged: {e}"))?;
    std::fs::create_dir_all(dir).map_err(|e| format!("cannot create the plugin folder: {e}"))?;
    let temp = dir.join(format!("{}.part", exe_name()));
    std::fs::write(&temp, &binary).map_err(|e| format!("cannot write the plugin: {e}"))?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&temp, std::fs::Permissions::from_mode(0o755))
            .map_err(|e| e.to_string())?;
    }
    let final_path = dir.join(exe_name());
    let _ = std::fs::remove_file(&final_path);
    std::fs::rename(&temp, &final_path).map_err(|e| format!("cannot install the plugin: {e}"))?;
    std::fs::write(dir.join("version"), version).map_err(|e| e.to_string())?;
    stage("done");
    Ok(())
}

fn download(url: &str) -> Result<Vec<u8>, String> {
    let mut response = ureq::get(url).call().map_err(|e| match e {
        ureq::Error::StatusCode(404) => "this version's AI plugin is not published yet".to_owned(),
        other => format!("download failed: {other}"),
    })?;
    response
        .body_mut()
        .with_config()
        .limit(MAX_DOWNLOAD)
        .read_to_vec()
        .map_err(|e| format!("download failed: {e}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_the_release_key() {
        // scripts/sign-ai-plugin.mjs 와 같은 키로 "papy plugin test" 에 서명한 값.
        let signature = "0F1R+G6m9YzmbWC2uKs6P46JyKGNyuTs0t6FnSg9VX5CCrOpoaBqlN9O3vxeMzSVN+g/94VeUkwmP2wpX5LDDA==";
        assert!(verify(b"papy plugin test", signature).is_ok());
        assert!(verify(b"papy plugin tesT", signature).is_err());
    }

    #[test]
    fn rejects_bad_signatures() {
        assert!(verify(b"data", "AAAA").is_err());
        let zero = base64::engine::general_purpose::STANDARD.encode([0u8; 64]);
        assert!(
            verify(b"data", &zero)
                .unwrap_err()
                .contains("does not match")
        );
    }

    /// 정해 둔 파일을 돌려주는 작은 HTTP 서버.
    fn serve(files: Vec<(String, Vec<u8>)>) -> String {
        use std::io::{BufRead, BufReader, Write};
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let base = format!("http://{}", listener.local_addr().unwrap());
        std::thread::spawn(move || {
            for stream in listener.incoming() {
                let Ok(mut stream) = stream else { break };
                let mut reader = BufReader::new(stream.try_clone().unwrap());
                let mut line = String::new();
                reader.read_line(&mut line).unwrap();
                let path = line.split_whitespace().nth(1).unwrap_or("").to_owned();
                loop {
                    let mut header = String::new();
                    reader.read_line(&mut header).unwrap();
                    if header.trim().is_empty() {
                        break;
                    }
                }
                let body = files
                    .iter()
                    .find(|(p, _)| *p == path)
                    .map(|(_, b)| b.clone());
                let (status, body) = match body {
                    Some(body) => ("200 OK", body),
                    None => ("404 Not Found", Vec::new()),
                };
                let head = format!(
                    "HTTP/1.1 {status}\r\ncontent-length: {}\r\nconnection: close\r\n\r\n",
                    body.len()
                );
                let _ = stream.write_all(head.as_bytes());
                let _ = stream.write_all(&body);
            }
        });
        base
    }

    // node crypto 로 같은 키에 서명한 작은 꾸러미(gzip "#!/bin/sh\necho papy-ai test\n").
    const PAYLOAD: &str = "H4sIAAAAAAACE1NW1E/KzNMvzuBKTc7IVyhILKjUTcxUKEktLuECABFfGZIcAAAA";
    const PAYLOAD_SIG: &str =
        "/x7or5m/BqxVsjM74kpGSfEozNiHgZF7PcP37ukqKVAYNzb7ITZBqDHB6EGauRvngaYxi7HLr3Se81vbaKfvBA==";

    #[test]
    fn installs_a_signed_plugin_and_refuses_tampered_ones() {
        let gz = base64::engine::general_purpose::STANDARD
            .decode(PAYLOAD)
            .unwrap();
        let mut tampered = gz.clone();
        *tampered.last_mut().unwrap() ^= 1;
        let path = |version: &str| format!("/v{version}/papy-ai-test-target.gz");
        let base = serve(vec![
            (path("1.0.0"), gz.clone()),
            (
                format!("{}.sig", path("1.0.0")),
                PAYLOAD_SIG.as_bytes().to_vec(),
            ),
            (path("2.0.0"), tampered),
            (
                format!("{}.sig", path("2.0.0")),
                PAYLOAD_SIG.as_bytes().to_vec(),
            ),
        ]);
        let dir = std::env::temp_dir().join(format!("papy-ai-install-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let stages = Mutex::new(Vec::new());
        let record = |stage: &str| stages.lock().unwrap().push(stage.to_owned());

        install_from(&base, "1.0.0", "test-target", &dir, &record).unwrap();
        assert_eq!(*stages.lock().unwrap(), ["download", "verify", "done"]);
        let installed = std::fs::read(dir.join(exe_name())).unwrap();
        assert_eq!(installed, b"#!/bin/sh\necho papy-ai test\n");
        assert_eq!(
            std::fs::read_to_string(dir.join("version")).unwrap(),
            "1.0.0"
        );
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mode = std::fs::metadata(dir.join(exe_name()))
                .unwrap()
                .permissions()
                .mode();
            assert_eq!(mode & 0o111, 0o111);
        }

        // 바뀐 파일은 설치하지 않는다(앞 버전이 그대로 남는다).
        let error = install_from(&base, "2.0.0", "test-target", &dir, &record).unwrap_err();
        assert!(error.contains("does not match"), "{error}");
        assert_eq!(
            std::fs::read_to_string(dir.join("version")).unwrap(),
            "1.0.0"
        );
        // 아직 올리지 않은 버전.
        let error = install_from(&base, "3.0.0", "test-target", &dir, &record).unwrap_err();
        assert!(error.contains("not published"), "{error}");
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn has_a_target() {
        assert!(target().is_some());
    }
}
