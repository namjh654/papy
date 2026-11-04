//! 문서 글자 찾기. 쪽마다 꺼낸 글자에서 찾으므로 모든 형식에 같은 규칙이 쓰인다.
//!
//! - 대소문자를 가리지 않는다(한글은 그대로).
//! - 공백 종류(줄바꿈·탭·여러 칸)를 가리지 않는다 — 줄이 바뀐 자리에 걸친 낱말도 찾는다.

use papy_model::TextMatch;

/// 찾은 글자 앞뒤로 붙이는 글자 수.
const CONTEXT: usize = 24;

/// 비교용 글자: 소문자로, 공백은 모두 ' ' 로.
fn fold(c: char) -> char {
    if c.is_whitespace() {
        ' '
    } else {
        c.to_lowercase().next().unwrap_or(c)
    }
}

/// 비교용으로 접은 글자열. 이어진 공백은 하나로 줄이고, 원문 글자 위치(`chars` 의 번호)를 함께 둔다.
fn folded(text: &[char]) -> (Vec<char>, Vec<usize>) {
    let mut out = Vec::with_capacity(text.len());
    let mut at = Vec::with_capacity(text.len());
    for (i, &c) in text.iter().enumerate() {
        let c = fold(c);
        if c == ' ' && out.last() == Some(&' ') {
            continue;
        }
        out.push(c);
        at.push(i);
    }
    (out, at)
}

/// 검색어를 비교용으로 접는다. 앞뒤 공백은 뺀다. 비면 `None`.
pub(crate) fn fold_query(query: &str) -> Option<Vec<char>> {
    let chars: Vec<char> = query.trim().chars().collect();
    let (folded, _) = folded(&chars);
    (!folded.is_empty()).then_some(folded)
}

fn snippet(chars: &[char]) -> String {
    chars
        .iter()
        .map(|&c| if c.is_whitespace() { ' ' } else { c })
        .collect()
}

/// 한 쪽 글자에서 `query` 를 찾아 `out` 에 붙인다. `out` 이 `max` 개가 되면 멈추고 `false` 를 돌려준다.
pub(crate) fn find_in_page(
    page: u32,
    text: &str,
    query: &[char],
    out: &mut Vec<TextMatch>,
    max: usize,
) -> bool {
    let chars: Vec<char> = text.chars().collect();
    let (hay, at) = folded(&chars);
    let mut index = 0u32;
    let mut from = 0;
    while from + query.len() <= hay.len() {
        if out.len() >= max {
            return false;
        }
        if hay[from..from + query.len()] != *query {
            from += 1;
            continue;
        }
        let start = at[from];
        let end = at[from + query.len() - 1] + 1;
        // 앞뒤 글자는 같은 줄(문단) 안에서만 붙인다.
        let mut before_start = start.saturating_sub(CONTEXT);
        if let Some(i) = chars[before_start..start].iter().rposition(|&c| c == '\n') {
            before_start += i + 1;
        }
        let mut after_end = (end + CONTEXT).min(chars.len());
        if let Some(i) = chars[end..after_end].iter().position(|&c| c == '\n') {
            after_end = end + i;
        }
        out.push(TextMatch {
            page,
            index,
            before: snippet(&chars[before_start..start]).trim_start().to_owned(),
            matched: chars[start..end].iter().collect(),
            after: snippet(&chars[end..after_end]).trim_end().to_owned(),
        });
        index += 1;
        from += query.len();
    }
    out.len() < max
}

#[cfg(test)]
mod tests {
    use super::*;

    fn find(text: &str, query: &str) -> Vec<TextMatch> {
        let mut out = Vec::new();
        find_in_page(3, text, &fold_query(query).unwrap(), &mut out, 100);
        out
    }

    #[test]
    fn ignores_case_and_spacing() {
        let hits = find("Papy 문서\n뷰어 papy\tVIEWER", "papy");
        assert_eq!(hits.len(), 2);
        assert_eq!(hits[0].matched, "Papy");
        assert_eq!(hits[1].matched, "papy");
        assert_eq!((hits[1].page, hits[1].index), (3, 1));
        // 줄바꿈에 걸친 낱말
        let hits = find("Papy 문서\n뷰어", "문서 뷰어");
        assert_eq!(hits.len(), 1);
        assert_eq!(hits[0].matched, "문서\n뷰어");
        assert_eq!(hits[0].before, "Papy ");
        // 앞뒤 글자는 줄을 넘지 않는다.
        let hits = find("첫 줄\n둘째 줄 낱말 셋\n넷째", "낱말");
        assert_eq!(
            (hits[0].before.as_str(), hits[0].after.as_str()),
            ("둘째 줄 ", " 셋")
        );
    }

    #[test]
    fn context_is_trimmed() {
        let text = format!("{}찾는 글자{}", "가".repeat(40), "나".repeat(40));
        let hits = find(&text, "찾는");
        assert_eq!(hits[0].before.chars().count(), CONTEXT);
        assert_eq!(hits[0].after, format!(" 글자{}", "나".repeat(CONTEXT - 3)));
    }

    #[test]
    fn matches_do_not_overlap_and_stop_at_max() {
        assert_eq!(find("aaaa", "aa").len(), 2);
        let mut out = Vec::new();
        let query = fold_query("a").unwrap();
        assert!(!find_in_page(0, "aaaa", &query, &mut out, 3));
        assert_eq!(out.len(), 3);
        assert!(fold_query("  \n").is_none());
    }
}
