//! DOCX 메모를 한글 숨은 설명으로 넣는다(본문 HTML 을 붙여넣은 뒤).
//!
//! 본문·표 칸 문단의 [`papy_office::COMMENT_MARK`] 자리마다(문서 순서 = 목록 순서) 그 글자를 지우고 숨은 설명을
//! 넣는다. 한글 메모(메모 필드)는 메모 모양·범위가 맞지 않으면 한글이 파일을 열지 못해, 내용을 잃지 않는 숨은 설명을 쓴다.

use papy_office::COMMENT_MARK;
use rhwp::model::control::{Control, HiddenComment};
use rhwp::model::paragraph::Paragraph;

use crate::Rhwp;

/// 넣지 못한 메모 수를 돌려준다.
pub(crate) fn apply(doc: &mut Rhwp, comments: &[Vec<String>]) -> usize {
    if comments.is_empty() {
        return 0;
    }
    let mut document = doc.document().clone();
    let mut next = 0usize;
    fn walk(
        paragraphs: &mut [Paragraph],
        comments: &[Vec<String>],
        next: &mut usize,
        depth: usize,
    ) {
        if depth > 16 {
            return;
        }
        for paragraph in paragraphs {
            while let Some(offset) = paragraph.text.chars().position(|c| c == COMMENT_MARK) {
                paragraph.delete_text_at(offset, 1);
                let Some(text) = comments.get(*next) else {
                    continue;
                };
                *next += 1;
                let paragraphs = text
                    .iter()
                    .map(|line| {
                        let mut p = Paragraph::new_empty();
                        if !line.is_empty() {
                            p.insert_text_at(0, line);
                        }
                        p
                    })
                    .collect::<Vec<_>>();
                let comment = HiddenComment {
                    paragraphs: if paragraphs.is_empty() {
                        vec![Paragraph::new_empty()]
                    } else {
                        paragraphs
                    },
                };
                paragraph.insert_inline_control(offset, Control::HiddenComment(Box::new(comment)));
            }
            for control in &mut paragraph.controls {
                if let Control::Table(table) = control {
                    for cell in &mut table.cells {
                        walk(&mut cell.paragraphs, comments, next, depth + 1);
                    }
                }
            }
        }
    }
    for section in &mut document.sections {
        walk(&mut section.paragraphs, comments, &mut next, 0);
        section.raw_stream = None;
    }
    doc.set_document(document);
    comments.len().saturating_sub(next)
}

/// 본문·표 칸 문단의 [`papy_office::BOOKMARK_MARK`] 자리마다 책갈피를 넣는다(같은 이름은 한 번만).
pub(crate) fn bookmarks(doc: &mut Rhwp, names: &[String]) {
    use papy_office::BOOKMARK_MARK;
    use rhwp::model::control::Bookmark;
    if names.is_empty() {
        return;
    }
    let mut document = doc.document().clone();
    let mut next = 0usize;
    let mut seen = std::collections::HashSet::new();
    fn walk(
        paragraphs: &mut [Paragraph],
        names: &[String],
        next: &mut usize,
        seen: &mut std::collections::HashSet<String>,
        depth: usize,
    ) {
        if depth > 16 {
            return;
        }
        for paragraph in paragraphs {
            while let Some(offset) = paragraph.text.chars().position(|c| c == BOOKMARK_MARK) {
                paragraph.delete_text_at(offset, 1);
                let Some(name) = names.get(*next) else {
                    continue;
                };
                *next += 1;
                if seen.insert(name.clone()) {
                    paragraph.insert_inline_control(
                        offset,
                        Control::Bookmark(Bookmark { name: name.clone() }),
                    );
                }
            }
            for control in &mut paragraph.controls {
                if let Control::Table(table) = control {
                    for cell in &mut table.cells {
                        walk(&mut cell.paragraphs, names, next, seen, depth + 1);
                    }
                }
            }
        }
    }
    for section in &mut document.sections {
        walk(&mut section.paragraphs, names, &mut next, &mut seen, 0);
        section.raw_stream = None;
    }
    doc.set_document(document);
}
