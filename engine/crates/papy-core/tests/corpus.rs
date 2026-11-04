//! 문서 모음 회귀 점검(기본 실행에서 빠진다):
//!
//!   node scripts/fetch-corpus.mjs            # rhwp 샘플(MIT)을 tests/corpus/rhwp-samples 에 받는다
//!   cd engine && PAPY_CORPUS=tests/corpus/rhwp-samples PAPY_CORPUS_GOLDEN=tests/corpus/rhwp-samples.golden.tsv \
//!     cargo test --release -p papy-core --test corpus -- --ignored --nocapture
//!
//! 상대 경로는 저장소 루트 기준이다.
//!
//! 폴더 안의 PDF·HWP·HWPX·DOCX 를 모두 열어 쪽을 그리고(1배) 글을 꺼낸다. 열기 실패(암호 제외)·그리기·글 꺼내기
//! 실패·패닉이 하나라도 있으면 실패한다. 문서 내용은 출력하지 않는다(글은 해시만).
//!
//! 환경 변수:
//! - `PAPY_CORPUS_GOLDEN`: 기준값 파일(문서마다 형식·쪽 수·쪽 크기 해시·글 해시·못 옮긴 요소 수). 있으면 비교해서 다르면
//!   실패한다. `PAPY_CORPUS_UPDATE=1` 이면 비교하지 않고 이번 결과로 다시 쓴다.
//! - `PAPY_CORPUS_RENDER`: `all`(기본, 모든 쪽) · `first`(첫 쪽만) · `none`(그리지 않음).
//! - `PAPY_CORPUS_ROUNDTRIP=1`: 문서를 같은 형식으로 저장한 뒤 다시 열어 쪽 수·글이 같은지 본다(PDF·보기 전용 HWP 3.x 제외).
//!   다시 열기 실패는 실패로, 쪽 수·글 차이는 개수만 출력한다(rhwp 쓰기가 줄 정보를 바꿀 수 있다).
//! - `PAPY_CORPUS_JOBS`: 동시에 여는 문서 수(기본: CPU 수). 시간을 잴 때는 1 로 둔다.
//! - `PAPY_CORPUS_PIXELS=파일`: 그린 쪽마다 픽셀 해시를 쓴다. 조판 엔진을 고칠 때 전후 두 번 돌려 diff 한다.
//! - `PAPY_PDFIUM_LIBRARY`: PDFium 라이브러리 경로. 없으면 PDF 는 건너뛴다(기준값 비교에서도 뺀다).

use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::{Duration, Instant};

use papy_core::{
    DocumentFormat, DocumentSource, Engine, EngineResources, ErrorCode, Limits, OpenOptions,
    SaveFormat,
};
use papy_render::RenderRequest;

#[derive(Clone, Copy, PartialEq)]
enum RenderMode {
    All,
    First,
    None,
}

/// 문서 하나의 결과.
struct Outcome {
    /// 기준값 줄(`형식\t쪽\t크기 해시\t글 해시\t못 옮긴 요소`, 또는 `error\t코드`). 건너뛴 문서는 `None`.
    golden: Option<String>,
    failures: Vec<String>,
    roundtrip_diff: bool,
    pages: u32,
    open: Duration,
    render: Duration,
    slowest_page: Duration,
    /// 목차 항목 수.
    outline: usize,
    /// 그린 쪽에서 제대로 그리지 못한 개체 수(차트·OLE·없는 그림 자리).
    unsupported: usize,
    /// 그중 차트·OLE(점선 자리표시를 그린 것).
    placeholders: usize,
    /// 그린 쪽마다 픽셀 해시(`PAPY_CORPUS_PIXELS` 로 파일에 쓴다).
    pixels: Vec<(u32, u64)>,
    /// 첫 쪽 표시 시간(열기 + 첫 쪽 그리기). 그리지 않았으면 `None`.
    first_page: Option<Duration>,
    /// 형식 이름(시간 통계를 형식별로 나눈다).
    format: &'static str,
    note: String,
}

#[test]
#[ignore = "PAPY_CORPUS 폴더가 있을 때만"]
fn corpus_opens_renders_and_extracts() {
    let Some(dir) = std::env::var_os("PAPY_CORPUS").map(repo_path) else {
        eprintln!("PAPY_CORPUS 가 없어 건너뜀");
        return;
    };
    let golden_path = std::env::var_os("PAPY_CORPUS_GOLDEN").map(repo_path);
    let update = std::env::var("PAPY_CORPUS_UPDATE").is_ok_and(|v| v == "1");
    let roundtrip = std::env::var("PAPY_CORPUS_ROUNDTRIP").is_ok_and(|v| v == "1");
    let mode = match std::env::var("PAPY_CORPUS_RENDER").as_deref() {
        Ok("first") => RenderMode::First,
        Ok("none") => RenderMode::None,
        _ => RenderMode::All,
    };
    let jobs = std::env::var("PAPY_CORPUS_JOBS")
        .ok()
        .and_then(|v| v.parse::<usize>().ok())
        .filter(|&n| n > 0)
        .unwrap_or_else(|| std::thread::available_parallelism().map_or(1, |n| n.get()));
    let has_pdfium = std::env::var_os("PAPY_PDFIUM_LIBRARY").is_some();
    let engine = Engine::with_resources(
        Limits::default(),
        EngineResources {
            pdfium_library: std::env::var_os("PAPY_PDFIUM_LIBRARY")
                .map(PathBuf::from)
                .unwrap_or_default(),
            korean_font: None,
        },
    );

    let mut files: Vec<PathBuf> = walk(&dir)
        .into_iter()
        .filter(|path| extension(path).is_some_and(|ext| has_pdfium || ext != "pdf"))
        .collect();
    files.sort();
    let names: Vec<String> = files
        .iter()
        .map(|path| {
            path.strip_prefix(&dir)
                .unwrap_or(path)
                .to_string_lossy()
                .replace('\\', "/")
        })
        .collect();

    let started = Instant::now();
    let next = AtomicUsize::new(0);
    let results: Mutex<BTreeMap<usize, Outcome>> = Mutex::new(BTreeMap::new());
    std::thread::scope(|scope| {
        for _ in 0..jobs.min(files.len().max(1)) {
            scope.spawn(|| {
                loop {
                    let i = next.fetch_add(1, Ordering::Relaxed);
                    let Some(path) = files.get(i) else { break };
                    let outcome =
                        catch_unwind(AssertUnwindSafe(|| check(&engine, path, mode, roundtrip)))
                            .unwrap_or_else(|_| Outcome::failed("패닉".into()));
                    results.lock().unwrap().insert(i, outcome);
                }
            });
        }
    });
    let results = results.into_inner().unwrap();

    let mut failures = Vec::new();
    let mut golden = BTreeMap::new();
    let mut total_pages = 0;
    let mut roundtrip_diffs = 0;
    let mut with_outline = 0;
    let mut with_unsupported = 0;
    for (i, outcome) in &results {
        with_outline += usize::from(outcome.outline > 0);
        with_unsupported += usize::from(outcome.unsupported > 0);
        let name = &names[*i];
        eprintln!(
            "{name}: {}쪽 · 열기 {} ms · 그리기 {} ms (가장 느린 쪽 {} ms){}",
            outcome.pages,
            outcome.open.as_millis(),
            outcome.render.as_millis(),
            outcome.slowest_page.as_millis(),
            outcome.note,
        );
        failures.extend(outcome.failures.iter().map(|f| format!("{name}: {f}")));
        if let Some(line) = &outcome.golden {
            golden.insert(name.clone(), line.clone());
        }
        total_pages += outcome.pages;
        roundtrip_diffs += usize::from(outcome.roundtrip_diff);
    }

    let mut slowest: Vec<_> = results
        .iter()
        .map(|(i, o)| (o.open + o.render, o.pages, &names[*i]))
        .collect();
    slowest.sort_by_key(|entry| std::cmp::Reverse(entry.0));
    eprintln!("\n가장 오래 걸린 문서:");
    for (time, pages, name) in slowest.iter().take(10) {
        eprintln!("  {} ms · {pages}쪽 · {name}", time.as_millis());
    }
    eprintln!(
        "\n문서 {}개 · {total_pages}쪽 · 목차 있는 문서 {with_outline}개 · 표시 못 한 개체가 있는 문서 {with_unsupported}개 · {} ms (동시 {jobs}){}",
        results.len(),
        started.elapsed().as_millis(),
        if roundtrip {
            format!(" · 저장 왕복에서 쪽 수·글이 바뀐 문서 {roundtrip_diffs}개")
        } else {
            String::new()
        },
    );

    first_page_report(&results);
    // 조판 엔진을 고칠 때 그림이 그대로인지 보려고 쪽마다 픽셀 해시를 쓴다(두 번 돌려 diff).
    if let Some(path) = std::env::var_os("PAPY_CORPUS_PIXELS").map(repo_path) {
        let mut out = String::new();
        for (i, outcome) in &results {
            for (page, hash) in &outcome.pixels {
                out.push_str(&format!("{}\t{page}\t{hash:016x}\n", names[*i]));
            }
        }
        std::fs::write(&path, out).expect("write pixel hashes");
        eprintln!("픽셀 해시: {}", path.display());
    }

    if let Some(golden_path) = &golden_path {
        if update {
            write_golden(golden_path, &golden);
            eprintln!("기준값을 다시 썼다: {}", golden_path.display());
        } else {
            failures.extend(compare_golden(golden_path, &golden, has_pdfium));
        }
    }

    assert!(
        failures.is_empty(),
        "실패 {}건:\n{}",
        failures.len(),
        failures.join("\n")
    );
}

impl Outcome {
    fn failed(message: String) -> Self {
        Self {
            golden: None,
            failures: vec![message],
            roundtrip_diff: false,
            pages: 0,
            open: Duration::ZERO,
            render: Duration::ZERO,
            slowest_page: Duration::ZERO,
            outline: 0,
            unsupported: 0,
            placeholders: 0,
            pixels: Vec::new(),
            first_page: None,
            format: "",
            note: String::new(),
        }
    }
}

/// 첫 쪽 표시 시간(열기 + 첫 쪽 그리기, 배율 1)을 형식별 분위수로 보인다.
/// 동시에 여러 문서를 돌리면 느려지므로 정확한 값은 `PAPY_CORPUS_JOBS=1` 로 잰다.
fn first_page_report(results: &BTreeMap<usize, Outcome>) {
    let mut by_format: BTreeMap<&str, Vec<Duration>> = BTreeMap::new();
    for outcome in results.values() {
        if let Some(time) = outcome.first_page {
            by_format.entry(outcome.format).or_default().push(time);
            by_format.entry("전체").or_default().push(time);
        }
    }
    if by_format.is_empty() {
        return;
    }
    eprintln!(
        "\n첫 쪽 표시 시간(열기 + 첫 쪽 그리기, ms): 형식 · 문서 수 · 중앙값 · 90% · 99% · 최대"
    );
    for (format, mut times) in by_format {
        times.sort();
        let at = |q: f64| times[((times.len() - 1) as f64 * q).round() as usize].as_millis();
        eprintln!(
            "  {format} · {} · {} · {} · {} · {}",
            times.len(),
            at(0.5),
            at(0.9),
            at(0.99),
            at(1.0)
        );
    }
}

fn check(engine: &Engine, path: &Path, mode: RenderMode, roundtrip: bool) -> Outcome {
    let mut outcome = Outcome::failed(String::new());
    outcome.failures.clear();
    let t0 = Instant::now();
    let id = match engine.open(
        DocumentSource::Path(path.to_path_buf()),
        OpenOptions::default(),
    ) {
        Ok(id) => id,
        Err(error) if error.code == ErrorCode::PasswordRequired => {
            outcome.note = " · 암호 문서".into();
            outcome.golden = Some(format!("error\t{:?}", error.code));
            return outcome;
        }
        Err(error) => {
            // 열지 못하는 문서(지원하지 않는 HWP 3.x 등)도 기준값에 남겨, 열리던 문서가 못 열리게 되면 잡는다.
            outcome.note = format!(" · 열기 실패 {:?}", error.code);
            outcome.golden = Some(format!("error\t{:?}", error.code));
            if error.code == ErrorCode::Internal {
                outcome.failures.push(format!("열기 실패 {:?}", error.code));
            }
            return outcome;
        }
    };
    outcome.open = t0.elapsed();
    let info = engine.info(id).unwrap();
    outcome.pages = info.page_count;
    outcome.format = info.format.name();

    let mut sizes = Fnv::new();
    let mut text = Fnv::new();
    let t1 = Instant::now();
    for page in 0..info.page_count {
        match engine.page_size(id, page) {
            Ok(size) => {
                sizes.write(format!("{:.1}x{:.1};", size.width, size.height).as_bytes());
            }
            Err(error) => outcome
                .failures
                .push(format!("{page}쪽 크기 실패 {:?}", error.code)),
        }
        let draw = match mode {
            RenderMode::All => true,
            RenderMode::First => page == 0,
            RenderMode::None => false,
        };
        if draw {
            let t = Instant::now();
            match engine.render_page(id, page, &RenderRequest::with_scale(1.0)) {
                Ok(bitmap) => {
                    let mut hash = Fnv::new();
                    hash.write(&bitmap.pixels);
                    outcome.pixels.push((page, hash.finish()));
                }
                Err(error) => outcome
                    .failures
                    .push(format!("{page}쪽 그리기 실패 {:?}", error.code)),
            }
            outcome.slowest_page = outcome.slowest_page.max(t.elapsed());
            if page == 0 {
                outcome.first_page = Some(outcome.open + t.elapsed());
            }
            match engine.unsupported_objects(id, page) {
                Ok(objects) => {
                    outcome.unsupported += objects.len();
                    outcome.placeholders += objects
                        .iter()
                        .filter(|o| o.kind != papy_core::UnsupportedKind::MissingImage)
                        .count();
                }
                Err(error) => outcome
                    .failures
                    .push(format!("{page}쪽 미지원 개체 확인 실패 {:?}", error.code)),
            }
        }
        match engine.extract_text(id, page) {
            Ok(page_text) => {
                text.write(page_text.as_bytes());
                text.write(b"\x0c");
            }
            Err(error) => outcome
                .failures
                .push(format!("{page}쪽 글 꺼내기 실패 {:?}", error.code)),
        }
    }
    // 목차: 오류 없이 나오고 가리키는 쪽이 문서 안에 있어야 한다(기준값에는 넣지 않는다).
    if info.capabilities.outline {
        fn walk(items: &[papy_core::OutlineItem], pages: u32, bad: &mut usize, count: &mut usize) {
            for item in items {
                *count += 1;
                if item.page.is_some_and(|page| page >= pages) {
                    *bad += 1;
                }
                walk(&item.children, pages, bad, count);
            }
        }
        let t = Instant::now();
        let result = engine.outline(id);
        let took = t.elapsed();
        if took.as_millis() >= 100 {
            outcome.note += &format!(" · 목차 {} ms", took.as_millis());
        }
        match result {
            Ok(items) => {
                let (mut bad, mut count) = (0, 0);
                walk(&items, info.page_count, &mut bad, &mut count);
                if bad > 0 {
                    outcome
                        .failures
                        .push(format!("목차 {bad}개가 없는 쪽을 가리킴"));
                }
                outcome.outline = count;
            }
            Err(error) => outcome.failures.push(format!("목차 실패 {:?}", error.code)),
        }
    }
    outcome.render = t1.elapsed();
    if outcome.unsupported > 0 {
        outcome.note += &format!(
            " · 표시 못 한 개체 {}(차트·OLE {})",
            outcome.unsupported, outcome.placeholders
        );
    }
    if info.dropped_objects > 0 {
        outcome.note = format!(" · 못 옮긴 요소 {}", info.dropped_objects);
    }
    outcome.golden = Some(format!(
        "{}\t{}\t{:016x}\t{:016x}\t{}",
        info.format.name(),
        info.page_count,
        sizes.finish(),
        text.finish(),
        info.dropped_objects,
    ));

    if roundtrip
        && let Some(format) = same_format(info.format)
            .filter(|format| info.capabilities.save_formats.contains(format))
    {
        match engine.save(id, format) {
            Ok(bytes) => {
                match engine.open(DocumentSource::Bytes(bytes.into()), OpenOptions::default()) {
                    Ok(again) => {
                        let pages = engine.info(again).map(|i| i.page_count).unwrap_or(0);
                        let mut text2 = Fnv::new();
                        for page in 0..pages {
                            if let Ok(page_text) = engine.extract_text(again, page) {
                                text2.write(page_text.as_bytes());
                                text2.write(b"\x0c");
                            }
                        }
                        if pages != info.page_count || text2.finish() != text.finish() {
                            outcome.roundtrip_diff = true;
                            outcome.note.push_str(&format!(
                                " · 저장 왕복 차이(쪽 {} → {pages})",
                                info.page_count
                            ));
                        }
                        let _ = engine.close(again);
                    }
                    Err(error) => outcome
                        .failures
                        .push(format!("저장 후 다시 열기 실패 {:?}", error.code)),
                }
            }
            Err(error) => {
                outcome
                    .failures
                    .push(format!("{} 저장 실패 {:?}", format.extension(), error.code))
            }
        }
    }
    let _ = engine.close(id);
    outcome
}

fn same_format(format: DocumentFormat) -> Option<SaveFormat> {
    match format {
        DocumentFormat::CompoundFile => Some(SaveFormat::Hwp),
        DocumentFormat::Hwpx => Some(SaveFormat::Hwpx),
        DocumentFormat::Docx => Some(SaveFormat::Docx),
        _ => None,
    }
}

const GOLDEN_HEADER: &str = "# Papy 문서 모음 기준값 — 경로\t형식\t쪽 수\t쪽 크기 해시\t글 해시\t못 옮긴 요소 (또는 error\t코드)\n\
# 다시 만들기: PAPY_CORPUS_UPDATE=1 (crates/papy-core/tests/corpus.rs)\n";

fn write_golden(path: &Path, golden: &BTreeMap<String, String>) {
    let mut out = String::from(GOLDEN_HEADER);
    for (name, line) in golden {
        let _ = writeln!(out, "{name}\t{line}");
    }
    std::fs::write(path, out).expect("기준값 파일 쓰기");
}

fn compare_golden(path: &Path, actual: &BTreeMap<String, String>, has_pdfium: bool) -> Vec<String> {
    let Ok(text) = std::fs::read_to_string(path) else {
        return vec![format!("기준값 파일을 읽지 못함: {}", path.display())];
    };
    let mut expected = BTreeMap::new();
    for line in text
        .lines()
        .filter(|l| !l.starts_with('#') && !l.is_empty())
    {
        if let Some((name, rest)) = line.split_once('\t') {
            expected.insert(name.to_string(), rest.to_string());
        }
    }
    let mut failures = Vec::new();
    for (name, want) in &expected {
        let skipped_pdf = !has_pdfium && name.to_ascii_lowercase().ends_with(".pdf");
        match actual.get(name) {
            Some(got) if got == want => {}
            Some(got) => failures.push(format!(
                "{name}: 기준값과 다름\n  기준 {want}\n  이번 {got}"
            )),
            None if skipped_pdf => {}
            None => failures.push(format!("{name}: 기준값에 있는 문서가 없음")),
        }
    }
    for name in actual.keys().filter(|name| !expected.contains_key(*name)) {
        failures.push(format!(
            "{name}: 기준값에 없는 문서(PAPY_CORPUS_UPDATE=1 로 추가)"
        ));
    }
    failures
}

/// FNV-1a 64비트. 플랫폼과 무관하게 같은 값을 낸다.
struct Fnv(u64);

impl Fnv {
    fn new() -> Self {
        Self(0xcbf2_9ce4_8422_2325)
    }
    fn write(&mut self, bytes: &[u8]) {
        for &b in bytes {
            self.0 ^= u64::from(b);
            self.0 = self.0.wrapping_mul(0x0100_0000_01b3);
        }
    }
    fn finish(&self) -> u64 {
        self.0
    }
}

fn extension(path: &Path) -> Option<&'static str> {
    let ext = path.extension()?.to_str()?.to_ascii_lowercase();
    ["pdf", "hwp", "hwpx", "docx"]
        .into_iter()
        .find(|known| *known == ext)
}

/// 상대 경로를 저장소 루트 기준으로 바꾼다(cargo 는 테스트를 crate 폴더에서 실행한다).
fn repo_path(value: std::ffi::OsString) -> PathBuf {
    let path = PathBuf::from(value);
    if path.is_absolute() {
        path
    } else {
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../..")
            .join(path)
    }
}

fn walk(dir: &Path) -> Vec<PathBuf> {
    let mut out = Vec::new();
    let Ok(entries) = std::fs::read_dir(dir) else {
        return out;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            out.extend(walk(&path));
        } else {
            out.push(path);
        }
    }
    out
}
