//! 엔진 외부 구성요소(PDFium 라이브러리, 한글 글꼴, 서드파티 고지)를 `resources/` 에 모은다.
//! tauri.conf.json 의 `bundle.resources` 가 이 폴더를 앱에 넣는다. 원본은 `pnpm engine:vendor` 로 받는다.

use std::path::{Path, PathBuf};
use std::{env, fs};

fn main() {
    stage_resources();
    tauri_build::build()
}

fn stage_resources() {
    let manifest = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let vendor = manifest.join("../../../engine/vendor");
    let target = env::var("TARGET").unwrap();
    let pdfium = vendor.join("pdfium").join(&target);
    let rhwp = vendor.join("rhwp");
    for stamp in [pdfium.join(".release"), rhwp.join(".papy-release")] {
        println!("cargo:rerun-if-changed={}", stamp.display());
        if !stamp.exists() {
            panic!(
                "{} 가 없습니다. 저장소 루트에서 `pnpm engine:vendor` (교차 빌드는 `node scripts/fetch-pdfium.mjs {target}`)를 먼저 실행하세요.",
                stamp.parent().unwrap().display()
            );
        }
    }

    let out = manifest.join("resources");
    let _ = fs::remove_dir_all(&out);
    let library = ["libpdfium.dylib", "pdfium.dll", "libpdfium.so"]
        .iter()
        .map(|name| pdfium.join(name))
        .find(|path| path.exists())
        .expect("PDFium library missing in vendor folder");
    copy(
        &library,
        &out.join("pdfium").join(library.file_name().unwrap()),
    );
    copy(
        &pdfium.join("LICENSE"),
        &out.join("licenses/pdfium/LICENSE"),
    );
    copy_dir(
        &pdfium.join("licenses"),
        &out.join("licenses/pdfium/licenses"),
    );
    let fonts = rhwp.join("ttfs/opensource");
    copy(
        &fonts.join("NotoSansKR-Regular.ttf"),
        &out.join("fonts/NotoSansKR-Regular.ttf"),
    );
    copy(
        &fonts.join("NotoSansKR-OFL.txt"),
        &out.join("licenses/noto-sans-kr/OFL.txt"),
    );
    // 명조·굵게 대체 글꼴(`node scripts/fetch-fonts.mjs`). 엔진은 한글 글꼴과 같은 폴더의 글꼴을 모두 읽는다.
    let extra = vendor.join("fonts");
    println!("cargo:rerun-if-changed={}", extra.display());
    let mut copied = 0;
    if let Ok(entries) = fs::read_dir(&extra) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().is_some_and(|ext| ext == "ttf") {
                copy(&path, &out.join("fonts").join(path.file_name().unwrap()));
                copied += 1;
            }
        }
        if extra.join("Noto-OFL.txt").exists() {
            copy(
                &extra.join("Noto-OFL.txt"),
                &out.join("licenses/noto-fonts/OFL.txt"),
            );
        }
    }
    if copied == 0 && env::var("PROFILE").as_deref() == Ok("release") {
        println!(
            "cargo:warning=명조·굵게 글꼴이 없습니다. `node scripts/fetch-fonts.mjs` 를 먼저 실행하세요."
        );
    }
    copy(&rhwp.join("LICENSE"), &out.join("licenses/rhwp/LICENSE"));
    // 전체 고지는 `node scripts/third-party-notices.mjs` 로 만든다(배포 빌드는 CI 가 만든다).
    let notices = manifest.join("notices/THIRD_PARTY_NOTICES.txt");
    println!("cargo:rerun-if-changed={}", notices.display());
    if notices.exists() {
        copy(&notices, &out.join("licenses/THIRD_PARTY_NOTICES.txt"));
    } else if env::var("PROFILE").as_deref() == Ok("release") {
        println!(
            "cargo:warning=THIRD_PARTY_NOTICES.txt 가 없습니다. `node scripts/third-party-notices.mjs` 를 먼저 실행하세요."
        );
    }
    copy(
        &rhwp.join("THIRD_PARTY_LICENSES.md"),
        &out.join("licenses/rhwp/THIRD_PARTY_LICENSES.md"),
    );
}

fn copy(from: &Path, to: &Path) {
    fs::create_dir_all(to.parent().unwrap()).unwrap();
    fs::copy(from, to).unwrap_or_else(|e| panic!("copy {}: {e}", from.display()));
}

fn copy_dir(from: &Path, to: &Path) {
    for entry in fs::read_dir(from).unwrap() {
        let entry = entry.unwrap();
        let path = entry.path();
        if path.is_dir() {
            copy_dir(&path, &to.join(entry.file_name()));
        } else {
            copy(&path, &to.join(entry.file_name()));
        }
    }
}
