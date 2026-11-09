//! DOCX 하이퍼링크를 한글 하이퍼링크로 넣는다(본문 HTML 을 붙여넣은 뒤).
//!
//! 본문·표 칸 문단의 [`papy_office::LINK_START_MARK`]·[`papy_office::LINK_END_MARK`] 사이 글에 하이퍼링크를 건다
//! (시작 표시 차례 = 주소 목록 차례). 표시는 모두 지운다. rhwp 가 받지 않는 주소(HTTP·HTTPS 가 아닌 것)는 글만 남긴다.
//! rhwp 는 개체(각주·그림 등)가 든 문단에 링크를 걸지 못해, 다른 표시를 개체로 바꾸기 전(붙여넣은 바로 뒤)에 건다.
//! 뒤에서 표시 글자를 지우거나 개체를 넣으면 링크 범위도 함께 옮겨진다.

use papy_office::{LINK_END_MARK, LINK_START_MARK};
use rhwp::document_core::hyperlink::HyperlinkTarget;
use rhwp::model::control::Control;
use rhwp::model::paragraph::Paragraph;

use crate::Rhwp;

/// 걸지 못한 하이퍼링크 수(글은 남는다)는 세지 않는다.
pub(crate) fn apply(doc: &mut Rhwp, uris: &[String]) {
    if uris.is_empty() {
        return;
    }
    let mut document = doc.document().clone();
    // (자리, 시작, 끝, 주소 차례)
    let mut found: Vec<(HyperlinkTarget, usize, usize, usize)> = Vec::new();
    let mut next = 0usize;
    fn marks(paragraph: &mut Paragraph, next: &mut usize) -> Vec<(usize, usize, usize)> {
        let mut out = Vec::new();
        while let Some(start) = paragraph.text.chars().position(|c| c == LINK_START_MARK) {
            paragraph.delete_text_at(start, 1);
            let end = paragraph
                .text
                .chars()
                .skip(start)
                .position(|c| c == LINK_END_MARK)
                .map(|i| start + i);
            if let Some(end) = end {
                paragraph.delete_text_at(end, 1);
            }
            let end = end.unwrap_or_else(|| paragraph.text.chars().count());
            out.push((start, end, *next));
            *next += 1;
        }
        // 짝 없는 끝 표시
        while let Some(end) = paragraph.text.chars().position(|c| c == LINK_END_MARK) {
            paragraph.delete_text_at(end, 1);
        }
        out
    }
    fn walk(
        paragraphs: &mut [Paragraph],
        section: usize,
        para: Option<usize>,
        path: &[(usize, usize, usize)],
        next: &mut usize,
        found: &mut Vec<(HyperlinkTarget, usize, usize, usize)>,
        depth: usize,
    ) {
        if depth > 16 {
            return;
        }
        for (index, paragraph) in paragraphs.iter_mut().enumerate() {
            let (body, cell_path) = match para {
                None => (index, Vec::new()),
                Some(body) => {
                    let mut path = path.to_vec();
                    if let Some(last) = path.last_mut() {
                        last.2 = index;
                    }
                    (body, path)
                }
            };
            for (start, end, uri) in marks(paragraph, next) {
                found.push((
                    HyperlinkTarget {
                        section,
                        para: body,
                        cell_path: cell_path.clone(),
                    },
                    start,
                    end,
                    uri,
                ));
            }
            for (control_index, control) in paragraph.controls.iter_mut().enumerate() {
                if let Control::Table(table) = control {
                    for (cell_index, cell) in table.cells.iter_mut().enumerate() {
                        let mut path = cell_path.clone();
                        path.push((control_index, cell_index, 0));
                        walk(
                            &mut cell.paragraphs,
                            section,
                            Some(body),
                            &path,
                            next,
                            found,
                            depth + 1,
                        );
                    }
                }
            }
        }
    }
    for (index, section) in document.sections.iter_mut().enumerate() {
        walk(
            &mut section.paragraphs,
            index,
            None,
            &[],
            &mut next,
            &mut found,
            0,
        );
        section.raw_stream = None;
    }
    doc.set_document(document);
    for (target, start, end, uri) in found {
        if let Some(uri) = uris.get(uri).filter(|_| start < end) {
            let _ = doc.insert_hyperlink_native(&target, start, end, uri);
        }
    }
}
