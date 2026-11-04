//! 오래 도는 점검(기본 `cargo test` 에서는 건너뛴다). `--release -- --ignored --nocapture` 로 돌린다.
//!
//! - `random_damage`: 무작위 손상 반복 점검(fuzz 대용, stable Rust). 시험 문서를 정해진 시드로 망가뜨려 수천 번 연다.
//!   ZIP 형식(HWPX·DOCX)은 압축을 풀고 안의 XML 을 망가뜨린 뒤 다시 묶어, 압축 검사에서 바로 막히지 않고 해석기까지 닿게 한다.
//!   `PAPY_FUZZ_ITERS`(기본 3000), `PAPY_FUZZ_SEED`, `PAPY_FUZZ_OUT`(실패한 입력을 저장할 폴더).
//! - `soak`: 같은 일을 여러 번 되풀이하며(열기·모든 쪽 그리기·편집·되돌리기·모든 형식 저장·닫기) 메모리가
//!   계속 늘지 않는지 본다. `PAPY_SOAK_ROUNDS`(기본 200). 메모리는 `ps` 로 재므로 macOS·Linux 에서만 본다.

mod common;

use std::io::{Cursor, Read, Write};
use std::time::{Duration, Instant};

use papy_core::{CancelToken, DocumentSource, OpenOptions, SaveFormat, TextPos};
use papy_render::RenderRequest;

/// 망가뜨린 문서 하나에 허용하는 시간(release 기준).
const PER_CASE: Duration = Duration::from_secs(10);

struct Rng(u64);

impl Rng {
    fn next(&mut self) -> u64 {
        // xorshift64*
        self.0 ^= self.0 >> 12;
        self.0 ^= self.0 << 25;
        self.0 ^= self.0 >> 27;
        self.0.wrapping_mul(0x2545_f491_4f6c_dd1d)
    }
    fn below(&mut self, n: usize) -> usize {
        if n == 0 {
            0
        } else {
            (self.next() % n as u64) as usize
        }
    }
}

/// 바이트열 하나에 손상 1~4 가지를 겹쳐 준다.
fn damage(rng: &mut Rng, data: &mut Vec<u8>) {
    for _ in 0..1 + rng.below(4) {
        let len = data.len();
        if len == 0 {
            data.push(rng.next() as u8);
            continue;
        }
        let at = rng.below(len);
        match rng.below(7) {
            // 바이트 뒤집기
            0 => data[at] ^= 1 << rng.below(8),
            // 흥미로운 값(경계값) 덮기
            1 => {
                let value: [u8; 4] = match rng.below(4) {
                    0 => [0; 4],
                    1 => [0xFF; 4],
                    2 => [0x7F, 0xFF, 0xFF, 0xFF],
                    _ => [0x80, 0, 0, 0],
                };
                let n = (len - at).min(1 + rng.below(4));
                data[at..at + n].copy_from_slice(&value[..n]);
            }
            // 덩어리 지우기
            2 => {
                let n = (len - at).min(1 + rng.below(64));
                data.drain(at..at + n);
            }
            // 덩어리 되풀이(길이·개수 필드와 어긋나게)
            3 => {
                let n = (len - at).min(1 + rng.below(64));
                let chunk = data[at..at + n].to_vec();
                data.splice(at..at, chunk);
            }
            // 무작위 바이트 끼우기
            4 => {
                let n = 1 + rng.below(16);
                let bytes: Vec<u8> = (0..n).map(|_| rng.next() as u8).collect();
                data.splice(at..at, bytes);
            }
            // 앞부분만 남기기
            5 => data.truncate(at.max(1)),
            // 다른 자리의 덩어리를 덮어쓰기
            _ => {
                let from = rng.below(len);
                let n = (len - at).min(len - from).min(1 + rng.below(32));
                let chunk = data[from..from + n].to_vec();
                data[at..at + n].copy_from_slice(&chunk);
            }
        }
    }
}

/// ZIP 안의 항목 하나를(압축을 푼 상태로) 망가뜨려 다시 묶는다. ZIP 이 아니면 `None`.
fn damage_zip_member(rng: &mut Rng, bytes: &[u8]) -> Option<Vec<u8>> {
    let mut archive = zip::ZipArchive::new(Cursor::new(bytes)).ok()?;
    let mut entries = Vec::new();
    for i in 0..archive.len() {
        let mut file = archive.by_index(i).ok()?;
        let mut data = Vec::new();
        file.read_to_end(&mut data).ok()?;
        entries.push((file.name().to_owned(), data));
    }
    // 그림 같은 큰 바이너리보다 XML 을 고른다.
    let xml: Vec<usize> = (0..entries.len())
        .filter(|&i| entries[i].0.ends_with(".xml") || entries[i].0.ends_with(".hpf"))
        .collect();
    let pick = if xml.is_empty() {
        rng.below(entries.len())
    } else {
        xml[rng.below(xml.len())]
    };
    damage(rng, &mut entries[pick].1);
    let mut out = Cursor::new(Vec::new());
    let mut writer = zip::ZipWriter::new(&mut out);
    for (name, data) in &entries {
        let options = if name == "mimetype" {
            zip::write::SimpleFileOptions::default()
                .compression_method(zip::CompressionMethod::Stored)
        } else {
            zip::write::SimpleFileOptions::default()
        };
        writer.start_file(name.as_str(), options).ok()?;
        writer.write_all(data).ok()?;
    }
    writer.finish().ok()?;
    Some(out.into_inner())
}

fn env_number(key: &str, default: u64) -> u64 {
    std::env::var(key)
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(default)
}

#[test]
#[ignore = "오래 걸린다: --release -- --ignored"]
fn random_damage() {
    let iterations = env_number("PAPY_FUZZ_ITERS", 3000);
    let seed = env_number("PAPY_FUZZ_SEED", 0x5eed_9a9f);
    let out_dir = std::env::var_os("PAPY_FUZZ_OUT").map(std::path::PathBuf::from);
    let (engine, has_pdf) = common::engine();
    let samples = common::samples(&engine, has_pdf);
    // 시드를 섞어 0 이 아닌 상태로(이웃한 시드가 같은 흐름이 되지 않게).
    let mut rng = Rng(seed.wrapping_mul(0x9e37_79b9_7f4a_7c15) | 1);
    let mut failures = Vec::new();
    let mut opened = 0;
    let mut slowest = Duration::ZERO;
    let started = Instant::now();
    for i in 0..iterations {
        let (format, bytes) = &samples[i as usize % samples.len()];
        let zip_member = matches!(*format, "hwpx" | "docx") && rng.below(2) == 0;
        let data = if zip_member {
            damage_zip_member(&mut rng, bytes).unwrap_or_else(|| bytes.clone())
        } else {
            let mut data = bytes.clone();
            damage(&mut rng, &mut data);
            data
        };
        let t = Instant::now();
        let result = common::exercise(&engine, data.clone());
        let took = t.elapsed();
        slowest = slowest.max(took);
        let problem = match result {
            Ok(was_opened) => {
                opened += usize::from(was_opened);
                (took > PER_CASE).then(|| format!("{} ms 걸림", took.as_millis()))
            }
            Err(message) => Some(message),
        };
        if let Some(problem) = problem {
            let name = format!("case-{i:05}.{format}");
            if let Some(dir) = &out_dir {
                let _ = std::fs::create_dir_all(dir);
                let _ = std::fs::write(dir.join(&name), &data);
            }
            failures.push(format!("{name}: {problem}"));
        }
    }
    eprintln!(
        "무작위 손상 {iterations}번(시드 {seed:#x}): 열림 {opened}, 가장 느린 것 {} ms, 전체 {} s",
        slowest.as_millis(),
        started.elapsed().as_secs()
    );
    assert_eq!(engine.session_count(), 0, "닫히지 않은 문서");
    assert!(
        failures.is_empty(),
        "실패 {}건:\n{}",
        failures.len(),
        failures.join("\n")
    );
}

/// 이 프로세스가 지금 쓰는 실제 메모리(KB). `ps` 가 없으면 `None`.
fn resident_kb() -> Option<u64> {
    let out = std::process::Command::new("ps")
        .args(["-o", "rss=", "-p", &std::process::id().to_string()])
        .output()
        .ok()?;
    String::from_utf8(out.stdout).ok()?.trim().parse().ok()
}

#[test]
#[ignore = "오래 걸린다: --release -- --ignored"]
fn soak() {
    let rounds = env_number("PAPY_SOAK_ROUNDS", 200) as usize;
    let (engine, has_pdf) = common::engine();
    let samples = common::samples(&engine, has_pdf);
    let none = CancelToken::new();
    let mut memory = Vec::new();
    let started = Instant::now();
    for round in 0..rounds {
        for (format, bytes) in &samples {
            let id = engine
                .open(
                    DocumentSource::Bytes(bytes.clone().into()),
                    OpenOptions::default(),
                )
                .unwrap_or_else(|e| panic!("{format} 열기: {e:?}"));
            let pages = engine.page_count(id).unwrap();
            for page in 0..pages {
                engine
                    .render_page(id, page, &RenderRequest::with_scale(1.0))
                    .unwrap();
                engine.extract_text(id, page).unwrap();
            }
            engine.find_text(id, "표", 100, &none).unwrap();
            engine.outline(id).unwrap();
            let info = engine.info(id).unwrap();
            if info.capabilities.edit {
                engine
                    .edit(id, |doc| {
                        doc.insert_text(TextPos::default(), "반복 ")?;
                        doc.undo()?;
                        Ok(())
                    })
                    .unwrap();
            }
            for format in info.capabilities.save_formats.clone() {
                if format != SaveFormat::Text {
                    engine.save(id, format).unwrap();
                }
            }
            engine.close(id).unwrap();
        }
        // 처음 몇 번은 캐시·글꼴이 채워지므로 10번째부터 잰다.
        if round >= 10 && (round % 10 == 0 || round + 1 == rounds) {
            if let Some(kb) = resident_kb() {
                memory.push((round, kb));
            }
        }
    }
    assert_eq!(engine.session_count(), 0);
    eprintln!(
        "장시간 반복 {rounds}번 · {} s · 메모리(KB, 회차별): {:?}",
        started.elapsed().as_secs(),
        memory
    );
    // 처음 잰 값보다 마지막이 크게(25% 그리고 64MB 넘게) 늘었으면 새는 것으로 본다.
    if let (Some(&(_, first)), Some(&(_, last))) = (memory.first(), memory.last()) {
        let grown = last.saturating_sub(first);
        assert!(
            grown < 64 * 1024 || last * 4 < first * 5,
            "메모리가 계속 늘었다: {first} KB → {last} KB"
        );
    }
}
