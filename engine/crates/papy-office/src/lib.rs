//! Papy Office 문서 변환 — DOCX(Word), 구형 DOC(Word 97~2003 바이너리, [`doc_to_html`]).
//!
//! DOCX 는 자체 배치 엔진을 만들지 않고 rhwp 문서 구조로 바꿔 HWP 와 같은 엔진으로 보이고 편집한다
//! (docs/decisions/0004·로드맵 6b, 2026-11-03 결정).
//!
//! - [`docx_to_html`]: DOCX → HTML(문단·글자 서식·표). rhwp 의 HTML 붙여넣기로 문서를 만든다.
//! - [`docx_from_document`]: rhwp 문서 → DOCX(문단·글자 서식·정렬·줄 간격·표·쪽 크기).
//!
//! 그림·도형(사각형·타원·선·글상자)·묶음 도형(안의 그림 포함)·차트(그림으로)·머리말·꼬리말·각주·미주(글자)·
//! 번호 목록(번호 글자)을 옮긴다. SmartArt·VML 개체·변경 추적은 옮기지 않는다.

mod chart;
mod doc;
mod doc_art;
mod doc_crypt;
mod notes;
mod read;
mod shapes;
mod write;

pub use doc::{doc_to_html, doc_to_html_with_password, is_doc};
pub use notes::{
    BOOKMARK_MARK, CAPTION_MARKS, CAPTION_MORE_MARK, COLUMN_BREAK_MARK, COMMENT_MARK, ENDNOTE_MARK,
    FOOTNOTE_MARK, HeaderFooterText, LINK_END_MARK, LINK_START_MARK, PAGE_BREAK_MARK,
    PAGE_COUNT_FIELD, PAGE_COUNT_MARK, PAGE_NUMBER_FIELD, PAGE_NUMBER_MARK, SECTION_MARK,
};
pub use read::{DocxComment, DocxHeading, DocxReadReport, PageSetup, docx_to_html, is_docx};
pub use shapes::{CELL_SHAPE_MARK, DocxShape, Relative, SHAPE_MARK, ShapeImage, ShapeKind, Wrap};
pub use write::{
    EquationRenderer, docx_from_document, docx_from_document_with, docx_from_document_with_outline,
};

#[cfg(test)]
mod tests;
