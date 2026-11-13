//! AI 회사 API 연결. Claude 는 Anthropic Messages API, ChatGPT·Gemini·Ollama 는 Chat Completions 형식
//! (OpenAI, Google 의 OpenAI 호환 주소, Ollama 의 `/v1`)으로 부른다. 모두 실시간 출력(SSE)이다.
//!
//! 대화 기록은 회사마다 모양이 달라서 각 연결이 자기 모양의 메시지(JSON)를 만든다.

pub mod anthropic;
pub mod openai;

use std::io::{BufRead, BufReader, Read};
use std::time::Duration;

use papy_tools::ToolSpec;
use serde::{Deserialize, Serialize};
use serde_json::Value;

/// 연결 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Kind {
    /// Anthropic Claude.
    Anthropic,
    /// OpenAI ChatGPT.
    Openai,
    /// Google Gemini(OpenAI 호환 주소).
    Gemini,
    /// 내 컴퓨터의 Ollama.
    Ollama,
}

impl Kind {
    /// 키체인 항목 이름·로그용 이름.
    pub fn id(self) -> &'static str {
        match self {
            Self::Anthropic => "anthropic",
            Self::Openai => "openai",
            Self::Gemini => "gemini",
            Self::Ollama => "ollama",
        }
    }

    /// 기본 주소.
    pub fn default_base(self) -> &'static str {
        match self {
            Self::Anthropic => "https://api.anthropic.com",
            Self::Openai => "https://api.openai.com/v1",
            Self::Gemini => "https://generativelanguage.googleapis.com/v1beta/openai",
            Self::Ollama => "http://127.0.0.1:11434/v1",
        }
    }

    /// API 키가 필요한가.
    pub fn needs_key(self) -> bool {
        self != Self::Ollama
    }

    /// 문서 내용이 내 컴퓨터 밖으로 나가는가.
    pub fn is_external(self) -> bool {
        self != Self::Ollama
    }

    /// 이름으로 찾는다.
    pub fn parse(name: &str) -> Option<Self> {
        [Self::Anthropic, Self::Openai, Self::Gemini, Self::Ollama]
            .into_iter()
            .find(|k| k.id() == name)
    }
}

/// 연결 설정 하나.
#[derive(Debug, Clone)]
pub struct Provider {
    /// 종류.
    pub kind: Kind,
    /// 모델 id.
    pub model: String,
    /// API 키(Ollama 는 없음).
    pub key: Option<String>,
    /// 주소(끝 `/` 없이).
    pub base: String,
}

/// 모델이 부른 도구.
#[derive(Debug, Clone, Serialize)]
pub struct ToolCall {
    /// 호출 id(결과를 돌려줄 때 쓴다).
    pub id: String,
    /// 도구 이름.
    pub name: String,
    /// 인자.
    pub input: Value,
    /// 인자 JSON 이 깨졌으면 그 이유. 실행하지 않고 오류로 돌려준다.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub invalid: Option<String>,
}

/// 토큰 사용량.
#[derive(Debug, Clone, Copy, Default, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Usage {
    /// 입력 토큰(캐시 아닌 것).
    pub input: u64,
    /// 출력 토큰.
    pub output: u64,
    /// 캐시에서 읽은 입력 토큰.
    pub cache_read: u64,
    /// 캐시에 쓴 입력 토큰.
    pub cache_write: u64,
}

impl Usage {
    /// 더한다.
    pub fn add(&mut self, other: Usage) {
        self.input += other.input;
        self.output += other.output;
        self.cache_read += other.cache_read;
        self.cache_write += other.cache_write;
    }
}

/// 왜 멈췄는가.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase", tag = "kind", content = "detail")]
pub enum Stop {
    /// 답을 마쳤다.
    End,
    /// 도구를 불렀다.
    ToolUse,
    /// 출력 길이 한도.
    MaxTokens,
    /// 정책으로 거절했다.
    Refusal(String),
    /// 서버가 잠시 멈췄다(같은 요청을 이어서 보낸다).
    Pause,
    /// 그 밖의 이유.
    Other(String),
}

/// 한 번의 응답.
#[derive(Debug, Clone)]
pub struct Turn {
    /// 대화 기록에 붙일 모델 메시지(그 회사 모양).
    pub message: Value,
    /// 보인 글 전체.
    pub text: String,
    /// 부른 도구.
    pub calls: Vec<ToolCall>,
    /// 멈춘 이유.
    pub stop: Stop,
    /// 사용량.
    pub usage: Usage,
}

/// 실시간 출력 받는 곳.
pub trait Sink {
    /// 글 조각.
    fn text(&self, delta: &str);
    /// 사용자가 멈췄는가.
    fn cancelled(&self) -> bool;
}

/// 모델 목록 한 줄.
#[derive(Debug, Clone, Serialize)]
pub struct ModelInfo {
    /// id.
    pub id: String,
    /// 보이는 이름.
    pub name: String,
}

/// 연결 오류 설명(키는 넣지 않는다).
pub type LlmError = String;

pub(crate) fn agent() -> ureq::Agent {
    ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(15))
        .timeout_read(Duration::from_secs(300))
        .user_agent(concat!("papy-ai/", env!("CARGO_PKG_VERSION")))
        .build()
}

/// 상태 코드와 응답 본문을 사람이 읽을 설명으로.
pub(crate) fn status_error(provider: &str, code: u16, body: &str) -> LlmError {
    let detail = serde_json::from_str::<Value>(body)
        .ok()
        .and_then(|v| {
            v.pointer("/error/message")
                .or_else(|| v.pointer("/0/error/message"))
                .or_else(|| v.get("error").filter(|e| e.is_string()))
                .and_then(Value::as_str)
                .map(str::to_owned)
        })
        .unwrap_or_else(|| body.chars().take(300).collect());
    let hint = match code {
        401 | 403 => " — check the API key",
        404 => " — check the model name",
        429 => " — rate limit or quota reached; try again later",
        500..=599 => " — the service had a problem; try again",
        _ => "",
    };
    format!("{provider} {code}: {detail}{hint}")
}

/// HTTP 오류를 사람이 읽을 설명으로.
pub(crate) fn http_error(provider: &str, error: ureq::Error) -> LlmError {
    match error {
        ureq::Error::Status(code, response) => {
            status_error(provider, code, &response.into_string().unwrap_or_default())
        }
        ureq::Error::Transport(transport) => format!("{provider}: cannot connect ({transport})"),
    }
}

/// SSE 본문에서 `data:` 줄을 차례로 넘긴다. `[DONE]` 이면 멈춘다. 취소되면 `Err`.
pub(crate) fn sse_data(
    body: impl Read,
    sink: &dyn Sink,
    mut on: impl FnMut(&str) -> Result<(), LlmError>,
) -> Result<(), LlmError> {
    let reader = BufReader::new(body);
    for line in reader.lines() {
        if sink.cancelled() {
            return Err("cancelled".to_owned());
        }
        let line = line.map_err(|e| format!("stream interrupted: {e}"))?;
        let Some(data) = line.strip_prefix("data:") else {
            continue;
        };
        let data = data.trim_start();
        if data == "[DONE]" {
            break;
        }
        if !data.is_empty() {
            on(data)?;
        }
    }
    Ok(())
}

/// 한 번 부른다(실시간 출력).
pub fn stream_turn(
    provider: &Provider,
    system: &str,
    messages: &[Value],
    tools: &[ToolSpec],
    sink: &dyn Sink,
) -> Result<Turn, LlmError> {
    match provider.kind {
        Kind::Anthropic => anthropic::stream_turn(provider, system, messages, tools, sink),
        _ => openai::stream_turn(provider, system, messages, tools, sink),
    }
}

/// 사용자 글 메시지.
pub fn user_text(kind: Kind, text: &str) -> Value {
    match kind {
        Kind::Anthropic => {
            serde_json::json!({ "role": "user", "content": [{ "type": "text", "text": text }] })
        }
        _ => serde_json::json!({ "role": "user", "content": text }),
    }
}

/// 도구 결과를 대화 기록에 붙일 메시지로. Anthropic 은 한 메시지에 모두, 나머지는 결과마다 하나.
pub fn tool_results(kind: Kind, results: &[(ToolCall, Result<Value, String>)]) -> Vec<Value> {
    match kind {
        Kind::Anthropic => vec![anthropic::tool_results(results)],
        _ => openai::tool_results(results),
    }
}

/// 모델 목록.
pub fn list_models(provider: &Provider) -> Result<Vec<ModelInfo>, LlmError> {
    match provider.kind {
        Kind::Anthropic => anthropic::list_models(provider),
        _ => openai::list_models(provider),
    }
}

/// 도구 결과를 모델에게 줄 글로.
pub(crate) fn result_text(result: &Result<Value, String>) -> (String, bool) {
    match result {
        Ok(value) => (serde_json::to_string(value).unwrap_or_default(), false),
        Err(message) => (message.clone(), true),
    }
}

/// 백만 토큰당 (입력, 출력) 달러. 모르는 모델은 `None`(토큰 수만 보인다).
pub fn price(kind: Kind, model: &str) -> Option<(f64, f64)> {
    if kind != Kind::Anthropic {
        return None;
    }
    let table: [(&str, f64, f64); 9] = [
        ("claude-fable-5-2", 8.0, 40.0),
        ("claude-fable-5-1", 10.0, 50.0),
        ("claude-fable-5", 10.0, 50.0),
        ("claude-opus-5-5", 4.0, 20.0),
        ("claude-opus-5", 5.0, 25.0),
        ("claude-sonnet-5-5", 2.0, 10.0),
        ("claude-sonnet-5", 2.0, 10.0),
        ("claude-haiku-5-5", 0.10, 0.50),
        ("claude-haiku-4-5", 1.0, 5.0),
    ];
    table
        .iter()
        .find(|(id, _, _)| *id == model)
        .map(|(_, i, o)| (*i, *o))
}
