//! Anthropic Messages API (`POST /v1/messages`, API 버전 `2026-10-21`). 공식 Rust SDK 가 없어 HTTP 로 부른다.
//!
//! - 실시간 출력(SSE). 도구 인자도 나오는 대로 받는다(`eager_input_streaming`). 그래서 다 받은 인자 JSON 을
//!   스스로 검사하고, 깨졌으면 실행하지 않고 오류 결과로 돌려준다.
//! - 모델이 돌려준 내용 블록(생각 블록·대체 모델 블록 포함)은 **바꾸지 않고** 다음 요청에 그대로 붙인다.
//! - 새 모델(Claude 5 세대)은 정책 거절 시 서버가 다른 모델로 이어 받도록 `fallbacks: "default"` 를 켠다.
//! - 반복되는 앞부분(도구·시스템 글·지난 대화)은 자동 캐시(`cache_control`)로 아낀다.

use papy_tools::ToolSpec;
use serde_json::{Map, Value, json};

use super::{
    LlmError, ModelInfo, Provider, Sink, Stop, ToolCall, Turn, Usage, agent, http_error,
    result_text, sse_data,
};

const VERSION: &str = "2026-10-21";
const FALLBACK_BETA: &str = "server-side-fallback-2026-07-01";
/// 실시간 출력이라 넉넉히 둔다(대화창 답이 잘리지 않게).
const MAX_TOKENS: u32 = 32_000;

/// 서버 대체 모델(`fallbacks`)을 받는 모델.
fn supports_fallbacks(model: &str) -> bool {
    matches!(
        model,
        "claude-opus-5-5"
            | "claude-opus-5"
            | "claude-sonnet-5-5"
            | "claude-fable-5-1"
            | "claude-fable-5-2"
    )
}

/// `output_config.effort` 를 받는 모델(4.6 이후).
fn supports_effort(model: &str) -> bool {
    [
        "claude-fable-",
        "claude-mythos-",
        "claude-opus-5",
        "claude-sonnet-5",
        "claude-haiku-5",
        "claude-opus-4-6",
        "claude-opus-4-7",
        "claude-opus-4-8",
        "claude-sonnet-4-6",
    ]
    .iter()
    .any(|prefix| model.starts_with(prefix))
}

fn tools_json(tools: &[ToolSpec]) -> Vec<Value> {
    tools
        .iter()
        .map(|tool| {
            json!({
                "name": tool.name,
                "description": tool.description,
                "input_schema": tool.input_schema,
                "eager_input_streaming": true,
            })
        })
        .collect()
}

/// 요청 본문.
pub fn request_body(
    provider: &Provider,
    system: &str,
    messages: &[Value],
    tools: &[ToolSpec],
) -> Value {
    let mut body = json!({
        "model": provider.model,
        "max_tokens": MAX_TOKENS,
        "stream": true,
        "cache_control": { "type": "ephemeral" },
        "system": system,
        "messages": messages,
    });
    if !tools.is_empty() {
        body["tools"] = Value::Array(tools_json(tools));
    }
    if supports_effort(&provider.model) {
        // 대화창은 빨리 답하는 편이 낫다. Opus 5.5 의 기본값(medium)을 분명히 적는다.
        body["output_config"] = json!({ "effort": "medium" });
    }
    if supports_fallbacks(&provider.model) {
        body["fallbacks"] = json!("default");
    }
    body
}

fn request(provider: &Provider, path: &str) -> ureq::Request {
    let mut request = agent()
        .request(
            if path == "/v1/models" { "GET" } else { "POST" },
            &format!("{}{path}", provider.base),
        )
        .set("anthropic-version", VERSION)
        .set("content-type", "application/json");
    if let Some(key) = &provider.key {
        request = request.set("x-api-key", key);
    }
    request
}

struct Block {
    value: Map<String, Value>,
    json: String,
}

/// 스트림 이벤트를 쌓아 한 번의 응답을 만든다.
#[derive(Default)]
pub struct Accumulator {
    blocks: Vec<Option<Block>>,
    text: String,
    stop: Option<String>,
    refusal: Option<String>,
    usage: Usage,
}

impl Accumulator {
    /// SSE `data` 하나를 반영한다. 글 조각은 `sink` 로 보낸다.
    pub fn push(&mut self, data: &str, sink: &dyn Sink) -> Result<(), LlmError> {
        let event: Value =
            serde_json::from_str(data).map_err(|e| format!("Anthropic: bad stream event ({e})"))?;
        match event.get("type").and_then(Value::as_str).unwrap_or("") {
            "message_start" => {
                if let Some(usage) = event.pointer("/message/usage") {
                    self.read_usage(usage);
                }
            }
            "content_block_start" => {
                let index = event.get("index").and_then(Value::as_u64).unwrap_or(0) as usize;
                let value = event
                    .get("content_block")
                    .and_then(Value::as_object)
                    .cloned()
                    .unwrap_or_default();
                if self.blocks.len() <= index {
                    self.blocks.resize_with(index + 1, || None);
                }
                self.blocks[index] = Some(Block {
                    value,
                    json: String::new(),
                });
            }
            "content_block_delta" => {
                let index = event.get("index").and_then(Value::as_u64).unwrap_or(0) as usize;
                let delta = event.get("delta").cloned().unwrap_or(Value::Null);
                let Some(Some(block)) = self.blocks.get_mut(index) else {
                    return Ok(());
                };
                let append = |block: &mut Block, key: &str, piece: &str| {
                    let old = block
                        .value
                        .get(key)
                        .and_then(Value::as_str)
                        .unwrap_or("")
                        .to_owned();
                    block
                        .value
                        .insert(key.to_owned(), Value::String(old + piece));
                };
                match delta.get("type").and_then(Value::as_str).unwrap_or("") {
                    "text_delta" => {
                        let piece = delta.get("text").and_then(Value::as_str).unwrap_or("");
                        append(block, "text", piece);
                        self.text.push_str(piece);
                        sink.text(piece);
                    }
                    "input_json_delta" => {
                        block.json.push_str(
                            delta
                                .get("partial_json")
                                .and_then(Value::as_str)
                                .unwrap_or(""),
                        );
                    }
                    "thinking_delta" => {
                        append(
                            block,
                            "thinking",
                            delta.get("thinking").and_then(Value::as_str).unwrap_or(""),
                        );
                    }
                    "signature_delta" => {
                        append(
                            block,
                            "signature",
                            delta.get("signature").and_then(Value::as_str).unwrap_or(""),
                        );
                    }
                    _ => {}
                }
            }
            "message_delta" => {
                if let Some(reason) = event.pointer("/delta/stop_reason").and_then(Value::as_str) {
                    self.stop = Some(reason.to_owned());
                }
                if let Some(details) = event
                    .pointer("/delta/stop_details")
                    .filter(|v| !v.is_null())
                {
                    self.refusal = details
                        .get("explanation")
                        .or_else(|| details.get("category"))
                        .and_then(Value::as_str)
                        .map(str::to_owned);
                }
                if let Some(usage) = event.get("usage") {
                    self.read_usage(usage);
                }
            }
            "error" => {
                let message = event
                    .pointer("/error/message")
                    .and_then(Value::as_str)
                    .unwrap_or("stream error");
                return Err(format!("Anthropic: {message}"));
            }
            _ => {}
        }
        Ok(())
    }

    fn read_usage(&mut self, usage: &Value) {
        let get = |key: &str| usage.get(key).and_then(Value::as_u64);
        // message_start 에 입력이, message_delta 에 누적 출력이 온다. 값이 있는 것만 덮는다.
        if let Some(value) = get("input_tokens") {
            self.usage.input = value;
        }
        if let Some(value) = get("output_tokens") {
            self.usage.output = value;
        }
        if let Some(value) = get("cache_read_input_tokens") {
            self.usage.cache_read = value;
        }
        if let Some(value) = get("cache_creation_input_tokens") {
            self.usage.cache_write = value;
        }
    }

    /// 응답을 마무리한다.
    pub fn finish(self) -> Turn {
        let mut content = Vec::new();
        let mut calls = Vec::new();
        for Block { mut value, json } in self.blocks.into_iter().flatten() {
            if value.get("type").and_then(Value::as_str) == Some("tool_use") {
                let (input, invalid) = if json.trim().is_empty() {
                    (
                        value.get("input").cloned().unwrap_or_else(|| json!({})),
                        None,
                    )
                } else {
                    match serde_json::from_str::<Value>(&json) {
                        Ok(input) if input.is_object() => (input, None),
                        Ok(_) => (
                            json!({}),
                            Some("tool input is not a JSON object".to_owned()),
                        ),
                        Err(e) => (
                            json!({}),
                            Some(format!(
                                "INVALID_JSON: tool input could not be parsed ({e})"
                            )),
                        ),
                    }
                };
                value.insert("input".to_owned(), input.clone());
                calls.push(ToolCall {
                    id: value
                        .get("id")
                        .and_then(Value::as_str)
                        .unwrap_or("")
                        .to_owned(),
                    name: value
                        .get("name")
                        .and_then(Value::as_str)
                        .unwrap_or("")
                        .to_owned(),
                    input,
                    invalid,
                });
            }
            content.push(Value::Object(value));
        }
        let stop = match self.stop.as_deref() {
            Some("end_turn") | Some("stop_sequence") | None => Stop::End,
            Some("tool_use") => Stop::ToolUse,
            Some("max_tokens") => Stop::MaxTokens,
            Some("pause_turn") => Stop::Pause,
            Some("refusal") => Stop::Refusal(self.refusal.unwrap_or_default()),
            Some(other) => Stop::Other(other.to_owned()),
        };
        Turn {
            message: json!({ "role": "assistant", "content": content }),
            text: self.text,
            calls,
            stop,
            usage: self.usage,
        }
    }
}

/// 한 번 부른다.
pub fn stream_turn(
    provider: &Provider,
    system: &str,
    messages: &[Value],
    tools: &[ToolSpec],
    sink: &dyn Sink,
) -> Result<Turn, LlmError> {
    let body = request_body(provider, system, messages, tools);
    let mut req = request(provider, "/v1/messages");
    if supports_fallbacks(&provider.model) {
        req = req.set("anthropic-beta", FALLBACK_BETA);
    }
    let response = req
        .send_string(&body.to_string())
        .map_err(|e| http_error("Anthropic", e))?;
    let mut acc = Accumulator::default();
    sse_data(response.into_reader(), sink, |data| acc.push(data, sink))?;
    Ok(acc.finish())
}

/// 도구 결과 메시지(모든 결과를 한 사용자 메시지에).
pub fn tool_results(results: &[(ToolCall, Result<Value, String>)]) -> Value {
    let content: Vec<Value> = results
        .iter()
        .map(|(call, result)| {
            let (text, is_error) = result_text(result);
            let mut block =
                json!({ "type": "tool_result", "tool_use_id": call.id, "content": text });
            if is_error {
                block["is_error"] = json!(true);
            }
            block
        })
        .collect();
    json!({ "role": "user", "content": content })
}

/// 모델 목록(`GET /v1/models`).
pub fn list_models(provider: &Provider) -> Result<Vec<ModelInfo>, LlmError> {
    let response = request(provider, "/v1/models")
        .call()
        .map_err(|e| http_error("Anthropic", e))?;
    let body: Value = response
        .into_json()
        .map_err(|e| format!("Anthropic: bad model list ({e})"))?;
    Ok(body
        .get("data")
        .and_then(Value::as_array)
        .map(|items| {
            items
                .iter()
                .filter_map(|item| {
                    let id = item.get("id")?.as_str()?.to_owned();
                    let name = item
                        .get("display_name")
                        .and_then(Value::as_str)
                        .unwrap_or(&id)
                        .to_owned();
                    Some(ModelInfo { id, name })
                })
                .collect()
        })
        .unwrap_or_default())
}
