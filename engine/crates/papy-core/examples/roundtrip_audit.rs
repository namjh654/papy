//! 변환 왕복 점검: 문서를 다른 형식으로 저장하고 다시 열어 요소(문단·글자·서식·표·칸·그림·각주·머리말·쪽 설정 등)
//! 수가 바뀐 것을 적는다. 쪽 수가 아니라 내용·구조가 빠졌는지 본다.
//!
//!   cargo run --release -p papy-core --example roundtrip_audit -- <문서 경로 목록 파일> > 결과.tsv
//!
//! 경로(원본 형식 → 거치는 형식): Word 원본(DOCX·DOC)은 `hwpx`·`hwpx>docx`·`docx`, 한글 원본(HWP·HWPX)은
//! `docx`·`docx>hwpx`·`hwpx`. 마지막 것은 같은 계열 저장 왕복(기준선)이다.
//! 출력 열: 파일 · 경로 · 요소 · 원본 값 · 결과 값. 끝에 `#` 으로 시작하는 줄로 경로·요소마다 바뀐 문서 수를 요약한다.
//! 열기·저장이 실패하면 요소 자리에 `error` 를 적는다.

use std::collections::BTreeMap;
use std::path::PathBuf;

use papy_core::{
    DocumentFormat, DocumentSource, Engine, Limits, OpenOptions, SaveFormat, SessionId, Structure,
};

fn format_of(name: &str) -> Option<SaveFormat> {
    match name {
        "docx" => Some(SaveFormat::Docx),
        "hwpx" => Some(SaveFormat::Hwpx),
        "hwp" => Some(SaveFormat::Hwp),
        _ => None,
    }
}

/// `id` 를 경로(`a>b`)대로 저장·다시 열기를 거친 문서의 구조.
fn through(engine: &Engine, id: SessionId, path: &str) -> Result<Structure, String> {
    let mut current = id;
    let mut opened = Vec::new();
    let result = (|| {
        for step in path.split('>') {
            let format = format_of(step).ok_or_else(|| format!("unknown:{step}"))?;
            let bytes = engine
                .save(current, format)
                .map_err(|e| format!("save:{step}:{:?}", e.code))?;
            current = engine
                .open(DocumentSource::Bytes(bytes.into()), OpenOptions::default())
                .map_err(|e| format!("reopen:{step}:{:?}", e.code))?;
            opened.push(current);
        }
        engine
            .structure(current)
            .map_err(|e| format!("structure:{:?}", e.code))
    })();
    for id in opened {
        let _ = engine.close(id);
    }
    result
}

fn main() {
    let list = std::fs::read_to_string(std::env::args().nth(1).expect("문서 경로 목록 파일"))
        .expect("목록");
    let engine = Engine::new(Limits::default());
    println!("file\tpath\tkey\tsource\tresult");
    // (경로, 요소) → (바뀐 문서 수, 원본 합, 결과 합)
    let mut summary: BTreeMap<(String, String), (u64, u64, u64)> = BTreeMap::new();
    let mut documents: BTreeMap<String, u64> = BTreeMap::new();
    for path in list.lines().map(str::trim).filter(|l| !l.is_empty()) {
        let name = path.rsplit(['/', '\\']).next().unwrap_or(path);
        let id = match engine.open(
            DocumentSource::Path(PathBuf::from(path)),
            OpenOptions::default(),
        ) {
            Ok(id) => id,
            Err(e) => {
                println!("{name}\t-\terror\topen:{:?}\t-", e.code);
                continue;
            }
        };
        let Ok(source) = engine.structure(id) else {
            println!("{name}\t-\terror\tstructure\t-");
            let _ = engine.close(id);
            continue;
        };
        let word = matches!(
            engine.info(id).map(|i| i.format),
            Ok(DocumentFormat::Docx | DocumentFormat::Doc)
        );
        let paths: &[&str] = if word {
            &["hwpx", "hwpx>docx", "docx"]
        } else {
            &["docx", "docx>hwpx", "hwpx"]
        };
        for route in paths {
            *documents.entry(route.to_string()).or_default() += 1;
            let result = match through(&engine, id, route) {
                Ok(result) => result,
                Err(error) => {
                    println!("{name}\t{route}\terror\t{error}\t-");
                    summary
                        .entry((route.to_string(), "error".into()))
                        .or_default()
                        .0 += 1;
                    continue;
                }
            };
            let keys: std::collections::BTreeSet<&String> =
                source.keys().chain(result.keys()).collect();
            for key in keys {
                let (a, b) = (
                    source.get(key).copied().unwrap_or(0),
                    result.get(key).copied().unwrap_or(0),
                );
                if a != b {
                    println!("{name}\t{route}\t{key}\t{a}\t{b}");
                    let entry = summary.entry((route.to_string(), key.clone())).or_default();
                    entry.0 += 1;
                    entry.1 += a;
                    entry.2 += b;
                }
            }
        }
        let _ = engine.close(id);
    }
    println!("# 경로\t요소\t바뀐 문서\t원본 합\t결과 합");
    for ((route, key), (docs, a, b)) in &summary {
        let total = documents.get(route).copied().unwrap_or(0);
        println!("# {route}\t{key}\t{docs}/{total}\t{a}\t{b}");
    }
}
