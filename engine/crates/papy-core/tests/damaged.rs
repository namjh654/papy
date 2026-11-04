//! 손상 파일 회귀 점검(기본 `cargo test` 에서 돈다).
//!
//! 엔진으로 HWP·HWPX·DOCX·PDF 문서를 만든 뒤 여러 방식으로 망가뜨려(앞부분만 남기기, 바이트 뒤집기, 0 으로 덮기)
//! 연다. 어떤 입력이든 패닉 없이 정해진 오류 코드로 끝나거나, 열리면 첫 쪽을 그리고 글을 꺼낼 수 있어야 한다.
//! 문서 하나는 시간 상한 안에 끝나야 한다(무한 반복 방지). PDF 는 PDFium 이 있을 때만 본다.

mod common;

use std::time::{Duration, Instant};

/// 망가뜨린 문서 하나에 허용하는 시간. 디버그 빌드 기준으로 넉넉하게 잡는다.
const PER_CASE: Duration = Duration::from_secs(20);

/// 바이트열을 망가뜨리는 방법들. 같은 입력이면 늘 같은 결과(재현 가능).
fn mutations(bytes: &[u8]) -> Vec<(String, Vec<u8>)> {
    let len = bytes.len();
    let mut out = Vec::new();
    // 앞부분만 남기기: 헤더 근처는 촘촘히, 나머지는 고르게.
    let mut cuts: Vec<usize> = vec![0, 1, 4, 8, 16, 31, 64, 100, 512, 513];
    cuts.extend((1..16).map(|i| len * i / 16));
    cuts.push(len.saturating_sub(1));
    cuts.sort_unstable();
    cuts.dedup();
    for cut in cuts.into_iter().filter(|&c| c < len) {
        out.push((format!("앞 {cut}바이트만"), bytes[..cut].to_vec()));
    }
    // 바이트 뒤집기: 의사 난수(xorshift) 자리 여러 군데.
    let mut seed = 0x9e37_79b9_7f4a_7c15_u64 ^ len as u64;
    for round in 0..12 {
        let mut data = bytes.to_vec();
        for _ in 0..(1 + round * 4) {
            seed ^= seed << 13;
            seed ^= seed >> 7;
            seed ^= seed << 17;
            let at = (seed % len as u64) as usize;
            data[at] ^= (seed >> 32) as u8 | 1;
        }
        out.push((format!("바이트 뒤집기 {round}"), data));
    }
    // 가운데 한 덩어리를 0 으로 덮기(압축 스트림·레코드 길이 손상).
    for i in 1..4 {
        let mut data = bytes.to_vec();
        let start = len * i / 4;
        let end = (start + 256).min(len);
        data[start..end].fill(0);
        out.push((format!("{}/4 지점 0 덮기", i), data));
    }
    // 뒤에 쓰레기 붙이기.
    let mut data = bytes.to_vec();
    data.extend(std::iter::repeat_n(0xAB, 4096));
    out.push(("뒤에 쓰레기".into(), data));
    out
}

#[test]
fn damaged_documents_fail_cleanly() {
    let (engine, has_pdf) = common::engine();
    let mut failures = Vec::new();
    let mut opened = 0;
    let mut cases = 0;
    for (format, bytes) in common::samples(&engine, has_pdf) {
        for (how, data) in mutations(&bytes) {
            cases += 1;
            let started = Instant::now();
            match common::exercise(&engine, data) {
                Ok(was_opened) => opened += usize::from(was_opened),
                Err(message) => failures.push(format!("{format} {how}: {message}")),
            }
            let took = started.elapsed();
            if took > PER_CASE {
                failures.push(format!("{format} {how}: {} ms 걸림", took.as_millis()));
            }
        }
    }
    eprintln!("손상 문서 {cases}개 중 {opened}개는 복구해서 열림");
    assert_eq!(engine.session_count(), 0, "닫히지 않은 문서");
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}
