//! 줄 단위 JSON-RPC 2.0. 한 줄에 메시지 하나(MCP stdio, ACP, 앱 ⇄ 플러그인 모두 같은 틀).
//!
//! [`Peer`] 는 양쪽이 서로 요청을 보낼 수 있는 연결이다. 읽기 스레드가 들어온 줄을 나눠서
//! 응답은 기다리는 쪽에 넘기고, 요청·알림은 처리기에게 넘긴다.

use std::collections::HashMap;
use std::io::{BufRead, BufReader, Read, Write};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::mpsc::{Receiver, Sender, channel};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde_json::{Value, json};

/// JSON-RPC 오류.
#[derive(Debug, Clone)]
pub struct RpcError {
    /// 오류 코드.
    pub code: i64,
    /// 사람이 읽는 설명.
    pub message: String,
    /// 덧붙인 값.
    pub data: Option<Value>,
}

impl RpcError {
    /// 새 오류.
    pub fn new(code: i64, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
            data: None,
        }
    }

    /// 잘못된 인자(-32602).
    pub fn invalid(message: impl Into<String>) -> Self {
        Self::new(-32602, message)
    }

    /// 없는 메서드(-32601).
    pub fn no_method(method: &str) -> Self {
        Self::new(-32601, format!("method not found: {method}"))
    }

    /// 내부 오류(-32603).
    pub fn internal(message: impl Into<String>) -> Self {
        Self::new(-32603, message)
    }

    fn to_json(&self) -> Value {
        let mut out = json!({ "code": self.code, "message": self.message });
        if let Some(data) = &self.data {
            out["data"] = data.clone();
        }
        out
    }

    fn from_json(value: &Value) -> Self {
        Self {
            code: value.get("code").and_then(Value::as_i64).unwrap_or(-32603),
            message: value
                .get("message")
                .and_then(Value::as_str)
                .unwrap_or("error")
                .to_owned(),
            data: value.get("data").cloned(),
        }
    }
}

impl std::fmt::Display for RpcError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{} ({})", self.message, self.code)
    }
}

/// 들어온 요청 또는 알림.
#[derive(Debug, Clone)]
pub struct Incoming {
    /// 요청 id. 알림이면 `None`.
    pub id: Option<Value>,
    /// 메서드.
    pub method: String,
    /// 인자.
    pub params: Value,
}

type Pending = Arc<Mutex<HashMap<String, Sender<Result<Value, RpcError>>>>>;

/// 양방향 JSON-RPC 연결.
#[derive(Clone)]
pub struct Peer {
    writer: Arc<Mutex<Box<dyn Write + Send>>>,
    pending: Pending,
    next: Arc<AtomicU64>,
}

fn id_key(id: &Value) -> String {
    id.to_string()
}

impl Peer {
    /// 읽기·쓰기 스트림으로 연결을 만든다. 들어온 요청·알림은 돌려준 수신기로 온다.
    /// 상대가 끊으면 수신기가 닫힌다.
    pub fn start(
        reader: impl Read + Send + 'static,
        writer: impl Write + Send + 'static,
    ) -> (Self, Receiver<Incoming>) {
        let peer = Self {
            writer: Arc::new(Mutex::new(Box::new(writer))),
            pending: Arc::new(Mutex::new(HashMap::new())),
            next: Arc::new(AtomicU64::new(1)),
        };
        let (tx, rx) = channel();
        let pending = Arc::clone(&peer.pending);
        std::thread::spawn(move || {
            let reader = BufReader::new(reader);
            for line in reader.lines() {
                let Ok(line) = line else { break };
                let line = line.trim();
                if line.is_empty() {
                    continue;
                }
                let Ok(message) = serde_json::from_str::<Value>(line) else {
                    continue;
                };
                if let Some(method) = message.get("method").and_then(Value::as_str) {
                    let incoming = Incoming {
                        id: message.get("id").cloned(),
                        method: method.to_owned(),
                        params: message.get("params").cloned().unwrap_or(Value::Null),
                    };
                    if tx.send(incoming).is_err() {
                        break;
                    }
                } else if let Some(id) = message.get("id") {
                    let waiter = pending
                        .lock()
                        .unwrap_or_else(|p| p.into_inner())
                        .remove(&id_key(id));
                    if let Some(waiter) = waiter {
                        let result = match message.get("error") {
                            Some(error) => Err(RpcError::from_json(error)),
                            None => Ok(message.get("result").cloned().unwrap_or(Value::Null)),
                        };
                        let _ = waiter.send(result);
                    }
                }
            }
            // 끊기면 기다리던 요청을 모두 실패로 끝낸다.
            let waiters: Vec<_> = pending
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .drain()
                .collect();
            for (_, waiter) in waiters {
                let _ = waiter.send(Err(RpcError::internal("connection closed")));
            }
        });
        (peer, rx)
    }

    fn write(&self, message: &Value) -> std::io::Result<()> {
        let mut line = serde_json::to_string(message).unwrap_or_default();
        line.push('\n');
        let mut writer = self.writer.lock().unwrap_or_else(|p| p.into_inner());
        writer.write_all(line.as_bytes())?;
        writer.flush()
    }

    /// 알림을 보낸다.
    pub fn notify(&self, method: &str, params: Value) {
        let _ = self.write(&json!({ "jsonrpc": "2.0", "method": method, "params": params }));
    }

    /// 요청에 응답한다.
    pub fn respond(&self, id: &Value, result: Result<Value, RpcError>) {
        let message = match result {
            Ok(result) => json!({ "jsonrpc": "2.0", "id": id, "result": result }),
            Err(error) => json!({ "jsonrpc": "2.0", "id": id, "error": error.to_json() }),
        };
        let _ = self.write(&message);
    }

    /// 요청을 보내고 응답 받을 곳을 돌려준다(기다리지 않는다).
    pub fn send_request(
        &self,
        method: &str,
        params: Value,
    ) -> Result<Receiver<Result<Value, RpcError>>, RpcError> {
        let id = self.next.fetch_add(1, Ordering::Relaxed);
        let key = id_key(&json!(id));
        let (tx, rx) = channel();
        self.pending
            .lock()
            .unwrap_or_else(|p| p.into_inner())
            .insert(key.clone(), tx);
        if self
            .write(&json!({ "jsonrpc": "2.0", "id": id, "method": method, "params": params }))
            .is_err()
        {
            self.pending
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .remove(&key);
            return Err(RpcError::internal("connection closed"));
        }
        Ok(rx)
    }

    /// 요청을 보내고 응답을 기다린다. `timeout` 이 지나면 오류.
    pub fn request(
        &self,
        method: &str,
        params: Value,
        timeout: Duration,
    ) -> Result<Value, RpcError> {
        let rx = self.send_request(method, params)?;
        match rx.recv_timeout(timeout) {
            Ok(result) => result,
            Err(_) => Err(RpcError::internal(format!("{method}: no reply"))),
        }
    }
}
