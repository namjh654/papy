//! Papy AI 플러그인. 앱과 따로 받는 선택 기능이다(ADR 0009).
//!
//! - [`serve`]: 앱 대화창의 뒤쪽. Claude·ChatGPT·Gemini·Ollama 는 API 로, Claude Code·Codex·Gemini CLI 는 ACP 로 부른다.
//! - [`mcp`]: 다른 AI 앱이 Papy 도구를 쓰게 하는 MCP 서버.

pub mod acp;
pub mod agent;
pub mod keys;
pub mod llm;
pub mod mcp;
/// 줄 단위 JSON-RPC(앱과 같은 것을 쓴다).
pub use papy_tools::rpc;
pub mod serve;
