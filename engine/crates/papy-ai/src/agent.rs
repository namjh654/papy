//! 대화 실행기: 모델을 부르고, 모델이 부른 도구를 실행해 결과를 돌려주기를 답이 끝날 때까지 되풀이한다.

use papy_tools::ToolSpec;
use serde_json::{Value, json};

use crate::llm::{self, Kind, Provider, Sink, Stop, Usage};

/// 한 번의 사용자 요청에서 모델을 부르는 최대 횟수(도구를 끝없이 부르지 않게).
pub const MAX_STEPS: usize = 24;

/// 대화 하나의 기록(그 회사 모양 메시지).
#[derive(Debug, Clone)]
pub struct Conversation {
    /// 연결 종류. 바뀌면 기록을 새로 시작한다.
    pub kind: Kind,
    /// 메시지.
    pub messages: Vec<Value>,
    /// 누적 사용량.
    pub usage: Usage,
}

impl Conversation {
    /// 빈 대화.
    pub fn new(kind: Kind) -> Self {
        Self {
            kind,
            messages: Vec::new(),
            usage: Usage::default(),
        }
    }
}

/// 도구 실행과 화면 알림을 맡는 쪽.
pub trait Host {
    /// 도구를 실행한다.
    fn call_tool(&self, name: &str, input: &Value) -> Result<Value, String>;
    /// 화면에 알린다(`type` 필드가 있는 객체).
    fn event(&self, event: Value);
    /// 사용자가 멈췄는가.
    fn cancelled(&self) -> bool;
}

struct HostSink<'a>(&'a dyn Host);

impl Sink for HostSink<'_> {
    fn text(&self, delta: &str) {
        self.0.event(json!({ "type": "text", "text": delta }));
    }

    fn cancelled(&self) -> bool {
        self.0.cancelled()
    }
}

/// 요청 하나의 결과.
#[derive(Debug, Clone)]
pub struct Outcome {
    /// 이번 요청의 사용량.
    pub usage: Usage,
    /// 마지막 멈춘 이유.
    pub stop: Stop,
    /// 모델을 부른 횟수.
    pub steps: usize,
}

/// 도구 결과를 화면에 보일 짧은 요약으로.
fn result_summary(result: &Result<Value, String>) -> Value {
    match result {
        Ok(value) => {
            let status = value.get("status").and_then(Value::as_str);
            json!({ "ok": true, "status": status, "proposal": value.get("proposal"), "summary": value.get("summary") })
        }
        Err(message) => json!({ "ok": false, "error": message }),
    }
}

/// 사용자 글 하나를 처리한다. 실패하거나 멈추면 이번 요청의 기록을 되돌려 대화를 깨끗이 둔다.
pub fn run(
    provider: &Provider,
    conversation: &mut Conversation,
    system: &str,
    text: &str,
    tools: &[ToolSpec],
    host: &dyn Host,
) -> Result<Outcome, String> {
    if conversation.kind != provider.kind {
        *conversation = Conversation::new(provider.kind);
    }
    let rollback = conversation.messages.len();
    conversation
        .messages
        .push(llm::user_text(provider.kind, text));
    let sink = HostSink(host);
    let mut usage = Usage::default();
    let mut steps = 0;
    let result = loop {
        if host.cancelled() {
            break Err("cancelled".to_owned());
        }
        if steps >= MAX_STEPS {
            host.event(json!({ "type": "notice", "code": "maxSteps" }));
            break Ok(Stop::Other("max steps".to_owned()));
        }
        steps += 1;
        let turn = match llm::stream_turn(provider, system, &conversation.messages, tools, &sink) {
            Ok(turn) => turn,
            Err(error) => break Err(error),
        };
        usage.add(turn.usage);
        host.event(json!({ "type": "usage", "usage": turn.usage, "total": usage }));
        conversation.messages.push(turn.message.clone());
        match &turn.stop {
            Stop::Pause => continue,
            Stop::Refusal(reason) => {
                host.event(json!({ "type": "notice", "code": "refusal", "detail": reason }));
                break Ok(turn.stop.clone());
            }
            Stop::MaxTokens if turn.calls.is_empty() => {
                host.event(json!({ "type": "notice", "code": "maxTokens" }));
                break Ok(turn.stop.clone());
            }
            _ if turn.calls.is_empty() => break Ok(turn.stop.clone()),
            _ => {}
        }
        // 응답이 길이 한도에서 잘렸으면 도구 인자도 잘렸을 수 있어 실행하지 않는다.
        let truncated = turn.stop == Stop::MaxTokens;
        let mut results = Vec::new();
        for call in &turn.calls {
            host.event(json!({ "type": "toolStart", "id": call.id, "name": call.name, "input": call.input }));
            let result = if truncated {
                Err("the response was cut off before the tool input was complete; call the tool again".to_owned())
            } else if let Some(invalid) = &call.invalid {
                Err(invalid.clone())
            } else if !tools.iter().any(|tool| tool.name == call.name) {
                Err(format!("unknown tool `{}`", call.name))
            } else {
                host.call_tool(&call.name, &call.input)
            };
            host.event(json!({ "type": "toolEnd", "id": call.id, "name": call.name, "result": result_summary(&result) }));
            results.push((call.clone(), result));
        }
        conversation
            .messages
            .extend(llm::tool_results(provider.kind, &results));
    };
    conversation.usage.add(usage);
    match result {
        Ok(stop) => Ok(Outcome { usage, stop, steps }),
        Err(error) => {
            conversation.messages.truncate(rollback);
            Err(error)
        }
    }
}

/// 시스템 글(대화창). 문서 정보와 답하는 방식.
pub fn system_prompt(document: &Value, language: &str) -> String {
    let doc = if document.is_null() {
        "No document is open right now. Tell the user to open one in Papy if they ask about a document.".to_owned()
    } else {
        format!(
            "The user has this document open in Papy: {}. Use the tools to read it before answering questions about it.",
            document
        )
    };
    let lang = if language.starts_with("ko") {
        "Korean"
    } else {
        "the user's language"
    };
    format!(
        "You are the document assistant inside Papy, a desktop app for PDF, HWP, HWPX, DOCX and DOC documents.\n\
         {doc}\n\
         - Read the parts you need with read_paragraphs, read_page_text or find_text. Quote page numbers when you cite.\n\
         - Editing tools only PROPOSE changes. The user reviews each proposal and applies or rejects it in Papy, \
           so never say a change has been made; say it is proposed.\n\
         - Make each proposal small and precise: one paragraph per call, exact text in find.\n\
         - Paragraphs containing U+FFFC hold tables or pictures; edit their text with replace_text only.\n\
         - If a tool returns an error, fix the arguments or explain the problem; do not invent document content.\n\
         - Answer in {lang}, briefly and plainly."
    )
}
