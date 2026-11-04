//! Papy Office 문서 변환 — 지금은 DOCX(Word).
//!
//! DOCX 는 자체 배치 엔진을 만들지 않고 rhwp 문서 구조로 바꿔 HWP 와 같은 엔진으로 보이고 편집한다
//! (docs/decisions/0004·로드맵 6b, 2026-11-03 결정).
//!
//! - [`docx_to_html`]: DOCX → HTML(문단·글자 서식·표). rhwp 의 HTML 붙여넣기로 문서를 만든다.
//! - [`docx_from_document`]: rhwp 문서 → DOCX(문단·글자 서식·정렬·줄 간격·표·쪽 크기).
//!
//! 그림·머리말·꼬리말·본문 각주(글자)·번호 목록(번호 글자)을 옮긴다. 도형·미주·표 안 각주·변경 추적은 아직 옮기지 않는다.

mod notes;
mod read;
mod write;

pub use notes::{FOOTNOTE_MARK, HeaderFooterText};
pub use read::{DocxReadReport, docx_to_html, is_docx};
pub use write::docx_from_document;

#[cfg(test)]
mod tests;
