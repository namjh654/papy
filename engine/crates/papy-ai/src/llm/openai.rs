//! Chat Completions 형식(`POST {주소}/chat/completions`): OpenAI, Google Gemini(OpenAI 호환 주소), Ollama(`/v1`).
//!
//! 도구 인자는 조각으로 오므로 이어 붙인 뒤 JSON 으로 검사한다. Gemini 는 도구 호출 id 를 비워 보낼 때가 있어
//! 비었으면 만들어 붙인다.

use papy_tools::ToolSpec;
use serde_json::{Value, json};

use super::{
    Kind, LlmError, ModelInfo, Provider, Sink, Stop, ToolCall, Turn, Usage, agent, http_error,
    result_text, sse_data, status_error,
};

fn name(kind: Kind) -> &'static str {
    match kind {
        Kind::Openai => "OpenAI",
        Kind::Gemini => "Gemini",
        Kind::Ollama => "Ollama",
        Kind::Anthropic => "Anthropic",
    }
}

fn tools_json(tools: &[ToolSpec]) -> Vec<Value> {
    tools
        .iter()
        .map(|tool| {
            json!({
                "type": "function",
                "function": { "name": tool.name, "description": tool.description, "parameters": tool.input_schema },
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
    usage: bool,
) -> Value {
    let mut all = vec![json!({ "role": "system", "content": system })];
    all.extend_from_slice(messages);
    let mut body = json!({ "model": provider.model, "stream": true, "messages": all });
    if usage {
        body["stream_options"] = json!({ "include_usage": true });
    }
    if !tools.is_empty() {
        body["tools"] = Value::Array(tools_json(tools));
    }
    body
}

fn post(provider: &Provider, path: &str) -> ureq::Request {
    let mut request = agent()
        .post(&format!("{}{path}", provider.base))
        .set("content-type", "application/json");
    if let Some(key) = &provider.key {
        request = request.set("authorization", &format!("Bearer {key}"));
    }
    request
}

#[derive(Default)]
struct PartialCall {
    id: String,
    name: String,
    arguments: String,
}

/// 스트림 조각을 쌓는다.
#[derive(Default)]
pub struct Accumulator {
    text: String,
    calls: Vec<PartialCall>,
    finish: Option<String>,
    usage: Usage,
}

impl Accumulator {
    /// SSE `data` 하나를 반영한다.
    pub fn push(&mut self, data: &str, sink: &dyn Sink, provider: &str) -> Result<(), LlmError> {
        let chunk: Value = serde_json::from_str(data)
            .map_err(|e| format!("{provider}: bad stream chunk ({e})"))?;
        if let Some(error) = chunk.get("error") {
            let message = error
                .get("message")
                .and_then(Value::as_str)
                .unwrap_or("stream error");
            return Err(format!("{provider}: {message}"));
        }
        if let Some(usage) = chunk.get("usage").filter(|u| u.is_object()) {
            let get = |key: &str| usage.get(key).and_then(Value::as_u64).unwrap_or(0);
            let cached = usage
                .pointer("/prompt_tokens_details/cached_tokens")
                .and_then(Value::as_u64)
                .unwrap_or(0);
            self.usage = Usage {
                input: get("prompt_tokens").saturating_sub(cached),
                output: get("completion_tokens"),
                cache_read: cached,
                cache_write: 0,
            };
        }
        let Some(choice) = chunk
            .get("choices")
            .and_then(Value::as_array)
            .and_then(|c| c.first())
        else {
            return Ok(());
        };
        if let Some(reason) = choice.get("finish_reason").and_then(Value::as_str) {
            self.finish = Some(reason.to_owned());
        }
        let Some(delta) = choice.get("delta") else {
            return Ok(());
        };
        if let Some(piece) = delta.get("content").and_then(Value::as_str) {
            self.text.push_str(piece);
            sink.text(piece);
        }
        for call in delta
            .get("tool_calls")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
        {
            let index = call
                .get("index")
                .and_then(Value::as_u64)
                .map(|i| i as usize)
                .unwrap_or(self.calls.len());
            if self.calls.len() <= index {
                self.calls.resize_with(index + 1, PartialCall::default);
            }
            let slot = &mut self.calls[index];
            if let Some(id) = call
                .get("id")
                .and_then(Value::as_str)
                .filter(|id| !id.is_empty())
            {
                slot.id = id.to_owned();
            }
            if let Some(name) = call.pointer("/function/name").and_then(Value::as_str) {
                slot.name.push_str(name);
            }
            if let Some(arguments) = call.pointer("/function/arguments") {
                match arguments {
                    Value::String(piece) => slot.arguments.push_str(piece),
                    // Ollama 는 인자를 객체로 보내기도 한다.
                    other if other.is_object() => slot.arguments = other.to_string(),
                    _ => {}
                }
            }
        }
        Ok(())
    }

    /// 응답을 마무리한다.
    pub fn finish(self) -> Turn {
        let mut calls = Vec::new();
        let mut tool_calls = Vec::new();
        for (index, call) in self
            .calls
            .into_iter()
            .enumerate()
            .filter(|(_, c)| !c.name.is_empty())
        {
            let id = if call.id.is_empty() {
                format!("call_{index}")
            } else {
                call.id
            };
            let (input, invalid) =
                match serde_json::from_str::<Value>(if call.arguments.trim().is_empty() {
                    "{}"
                } else {
                    &call.arguments
                }) {
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
                };
            tool_calls.push(json!({
                "id": id,
                "type": "function",
                "function": { "name": call.name, "arguments": if call.arguments.trim().is_empty() { "{}".to_owned() } else { call.arguments } },
            }));
            calls.push(ToolCall {
                id,
                name: call.name,
                input,
                invalid,
            });
        }
        let mut message = json!({ "role": "assistant", "content": if self.text.is_empty() { Value::Null } else { json!(self.text) } });
        if !tool_calls.is_empty() {
            message["tool_calls"] = Value::Array(tool_calls);
        }
        let stop = if !calls.is_empty() {
            Stop::ToolUse
        } else {
            match self.finish.as_deref() {
                Some("stop") | None => Stop::End,
                Some("length") => Stop::MaxTokens,
                Some("content_filter") => Stop::Refusal("content filter".to_owned()),
                Some("tool_calls") | Some("function_call") => Stop::End,
                Some(other) => Stop::Other(other.to_owned()),
            }
        };
        Turn {
            message,
            text: self.text,
            calls,
            stop,
            usage: self.usage,
        }
    }
}

/// 한 번 부른다. 사용량 옵션을 받지 않는 서버면 빼고 다시 보낸다.
pub fn stream_turn(
    provider: &Provider,
    system: &str,
    messages: &[Value],
    tools: &[ToolSpec],
    sink: &dyn Sink,
) -> Result<Turn, LlmError> {
    let label = name(provider.kind);
    let send = |usage: bool| {
        post(provider, "/chat/completions")
            .send_string(&request_body(provider, system, messages, tools, usage).to_string())
            .map_err(Box::new)
    };
    let response = match send(true).map_err(|e| *e) {
        Err(ureq::Error::Status(400, response)) => {
            let body = response.into_string().unwrap_or_default();
            if body.contains("stream_options") {
                send(false).map_err(|e| http_error(label, *e))?
            } else {
                return Err(status_error(label, 400, &body));
            }
        }
        other => other.map_err(|e| http_error(label, e))?,
    };
    let mut acc = Accumulator::default();
    sse_data(response.into_reader(), sink, |data| {
        acc.push(data, sink, label)
    })?;
    Ok(acc.finish())
}

/// 도구 결과 메시지(결과마다 하나).
pub fn tool_results(results: &[(ToolCall, Result<Value, String>)]) -> Vec<Value> {
    results
        .iter()
        .map(|(call, result)| {
            let (text, is_error) = result_text(result);
            let content = if is_error {
                format!("ERROR: {text}")
            } else {
                text
            };
            json!({ "role": "tool", "tool_call_id": call.id, "content": content })
        })
        .collect()
}

/// 대화에 쓸 수 없는 모델(그림·소리·임베딩 등)을 뺀다.
fn chat_model(kind: Kind, id: &str) -> bool {
    let id = id.to_ascii_lowercase();
    let skip = [
        "embedding",
        "whisper",
        "tts",
        "dall-e",
        "image",
        "audio",
        "realtime",
        "transcribe",
        "moderation",
        "search",
        "aqa",
        "imagen",
        "veo",
        "learnlm",
    ];
    if skip.iter().any(|word| id.contains(word)) {
        return false;
    }
    match kind {
        Kind::Openai => {
            id.starts_with("gpt-")
                || id.starts_with("chatgpt")
                || (id.starts_with('o') && id.chars().nth(1).is_some_and(|c| c.is_ascii_digit()))
        }
        Kind::Gemini => id.contains("gemini"),
        _ => true,
    }
}

/// 모델 목록(`GET {주소}/models`).
pub fn list_models(provider: &Provider) -> Result<Vec<ModelInfo>, LlmError> {
    let label = name(provider.kind);
    let mut request = agent().get(&format!("{}/models", provider.base));
    if let Some(key) = &provider.key {
        request = request.set("authorization", &format!("Bearer {key}"));
    }
    let body: Value = request
        .call()
        .map_err(|e| http_error(label, e))?
        .into_json()
        .map_err(|e| format!("{label}: bad model list ({e})"))?;
    let mut models: Vec<ModelInfo> = body
        .get("data")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(|item| item.get("id").and_then(Value::as_str))
        .map(|id| id.trim_start_matches("models/").to_owned())
        .filter(|id| chat_model(provider.kind, id))
        .map(|id| ModelInfo {
            name: id.clone(),
            id,
        })
        .collect();
    models.sort_by(|a, b| b.id.cmp(&a.id));
    models.dedup_by(|a, b| a.id == b.id);
    Ok(models)
}
