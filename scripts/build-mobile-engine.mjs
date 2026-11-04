#!/usr/bin/env node
// 모바일 앱에 넣을 Papy 엔진(papy-ffi)을 빌드하고 Kotlin·Swift 바인딩을 만든다(UniFFI, ADR 0006).
//
//   node scripts/build-mobile-engine.mjs bindings   # Kotlin·Swift 바인딩만 다시 만든다(커밋 대상)
//   node scripts/build-mobile-engine.mjs android    # libpapy_ffi.so (arm64-v8a, x86_64) → androidApp/src/main/jniLibs
//   node scripts/build-mobile-engine.mjs ios        # PapyFFI.xcframework (기기, 시뮬레이터) → iosApp/Frameworks
//   node scripts/build-mobile-engine.mjs all
//
// 필요한 것: rustup 타깃(aarch64-linux-android x86_64-linux-android aarch64-apple-ios aarch64-apple-ios-sim),
// Android NDK(ANDROID_NDK_HOME 또는 $ANDROID_HOME/ndk 의 가장 새 버전), iOS 는 Xcode.
// 결과 라이브러리(.so, .xcframework)는 git 에 넣지 않는다. 바인딩 소스는 넣는다(앱 빌드가 Rust 없이도 컴파일되게).

import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const engine = join(root, "engine");
const mobile = join(root, "apps", "mobile");
const kotlinOut = join(mobile, "androidApp", "src", "main", "kotlin");
const jniLibs = join(mobile, "androidApp", "src", "main", "jniLibs");
const swiftOut = join(mobile, "iosApp", "iosApp", "engine");
const frameworks = join(mobile, "iosApp", "Frameworks");
/** Android 최소 API(gradle/libs.versions.toml 의 android-minSdk 와 같게) */
const ANDROID_API = 24;
const ANDROID_TARGETS = [
  ["aarch64-linux-android", "arm64-v8a", "aarch64-linux-android"],
  ["x86_64-linux-android", "x86_64", "x86_64-linux-android"],
];
const IOS_TARGETS = ["aarch64-apple-ios", "aarch64-apple-ios-sim"];

const run = (cmd, args, env = {}) =>
  execFileSync(cmd, args, { cwd: engine, stdio: ["ignore", "inherit", "inherit"], env: { ...process.env, ...env } });
const cargo = (args, env) => run("cargo", args, env);

/** 바인딩 생성용 호스트 라이브러리(디버그 빌드 — 내보낸 항목 정보만 읽는다. release 는 LTO 로 느리다) */
function hostLibrary() {
  cargo(["build", "-p", "papy-ffi", "--lib"]);
  const name = process.platform === "darwin" ? "libpapy_ffi.dylib" : process.platform === "win32" ? "papy_ffi.dll" : "libpapy_ffi.so";
  return join(engine, "target", "debug", name);
}

function bindgen(language, library, outDir, extra = []) {
  cargo([
    "run", "-q", "-p", "papy-ffi", "--features", "bindgen", "--bin", "uniffi-bindgen", "--",
    "generate", "--library", library, "--language", language, "--out-dir", outDir, "--no-format", ...extra,
  ]);
}

function bindings() {
  const library = hostLibrary();
  const tmp = join(engine, "target", "uniffi");
  rmSync(tmp, { recursive: true, force: true });
  bindgen("kotlin", library, join(tmp, "kotlin"));
  bindgen("swift", library, join(tmp, "swift"));
  // Kotlin: uniffi/papy_ffi/papy_ffi.kt
  cpSync(join(tmp, "kotlin", "uniffi"), join(kotlinOut, "uniffi"), { recursive: true });
  // Swift: papy_ffi.swift 는 앱 소스로, 헤더·modulemap 은 xcframework 에 넣는다.
  mkdirSync(swiftOut, { recursive: true });
  // 템플릿 규칙(iOS 파일은 소문자 점 표기)에 맞춰 papy.ffi.swift 로 둔다.
  cpSync(join(tmp, "swift", "papy_ffi.swift"), join(swiftOut, "papy.ffi.swift"));
  console.log("바인딩 → androidApp/src/main/kotlin/uniffi, iosApp/iosApp/engine/papy.ffi.swift");
}

/** HWP 글꼴이 없을 때 쓰는 한글 글꼴(OFL). 데스크톱과 같은 파일(fetch-rhwp.mjs 가 받는다). */
function fonts() {
  const dir = join(engine, "vendor", "rhwp", "ttfs", "opensource");
  const files = ["NotoSansKR-Regular.ttf", "NotoSansKR-OFL.txt"];
  if (!existsSync(join(dir, files[0]))) throw new Error("글꼴이 없습니다. 먼저 node scripts/fetch-rhwp.mjs 를 실행하세요.");
  for (const out of [
    join(mobile, "androidApp", "src", "main", "assets", "fonts"),
    join(mobile, "iosApp", "iosApp", "Resources", "EngineFonts"),
  ]) {
    mkdirSync(out, { recursive: true });
    for (const file of files) cpSync(join(dir, file), join(out, file));
  }
}

function ndkHome() {
  if (process.env.ANDROID_NDK_HOME && existsSync(process.env.ANDROID_NDK_HOME)) return process.env.ANDROID_NDK_HOME;
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  const dir = sdk && join(sdk, "ndk");
  if (!dir || !existsSync(dir)) throw new Error("Android NDK 를 찾지 못했습니다(ANDROID_NDK_HOME 또는 ANDROID_HOME).");
  const versions = readdirSync(dir).sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  return join(dir, versions.at(-1));
}

function android() {
  fonts();
  const ndk = ndkHome();
  const host = process.platform === "darwin" ? "darwin-x86_64" : process.platform === "win32" ? "windows-x86_64" : "linux-x86_64";
  const bin = join(ndk, "toolchains", "llvm", "prebuilt", host, "bin");
  const ext = process.platform === "win32" ? ".cmd" : "";
  for (const [target, abi, clangPrefix] of ANDROID_TARGETS) {
    const clang = join(bin, `${clangPrefix}${ANDROID_API}-clang${ext}`);
    const key = target.toUpperCase().replaceAll("-", "_");
    cargo(["build", "--release", "-p", "papy-ffi", "--target", target, "--lib"], {
      [`CARGO_TARGET_${key}_LINKER`]: clang,
      [`CC_${target.replaceAll("-", "_")}`]: clang,
      [`AR_${target.replaceAll("-", "_")}`]: join(bin, `llvm-ar${process.platform === "win32" ? ".exe" : ""}`),
      // Google Play: Android 15+ 대상 앱의 네이티브 라이브러리는 16KB 페이지 정렬이어야 한다.
      [`CARGO_TARGET_${key}_RUSTFLAGS`]: "-C link-arg=-Wl,-z,max-page-size=16384",
    });
    const out = join(jniLibs, abi);
    mkdirSync(out, { recursive: true });
    cpSync(join(engine, "target", target, "release", "libpapy_ffi.so"), join(out, "libpapy_ffi.so"));
    // UniFFI Kotlin 은 JNA 로 libpapy_ffi.so 를 연다. C++ 런타임은 쓰지 않는다.
  }
  console.log(`Android → ${jniLibs}`);
}

function ios() {
  if (process.platform !== "darwin") throw new Error("iOS 빌드는 macOS 에서만 됩니다.");
  fonts();
  const library = hostLibrary();
  const tmp = join(engine, "target", "uniffi-ios");
  rmSync(tmp, { recursive: true, force: true });
  const headers = join(tmp, "headers");
  bindgen("swift", library, headers);
  // xcframework 헤더 폴더에는 헤더와 module.modulemap 만 둔다.
  rmSync(join(headers, "papy_ffi.swift"));
  cpSync(join(headers, "papy_ffiFFI.modulemap"), join(headers, "module.modulemap"));
  rmSync(join(headers, "papy_ffiFFI.modulemap"));
  const args = ["-create-xcframework"];
  for (const target of IOS_TARGETS) {
    cargo(["build", "--release", "-p", "papy-ffi", "--target", target, "--lib"], { IPHONEOS_DEPLOYMENT_TARGET: "16.0" });
    args.push("-library", join(engine, "target", target, "release", "libpapy_ffi.a"), "-headers", headers);
  }
  const out = join(frameworks, "PapyFFI.xcframework");
  rmSync(out, { recursive: true, force: true });
  mkdirSync(frameworks, { recursive: true });
  execFileSync("xcodebuild", [...args, "-output", out], { stdio: ["ignore", "inherit", "inherit"] });
  console.log(`iOS → ${out}`);
}

const what = process.argv[2] ?? "all";
const steps = { bindings, android, ios, all: () => (bindings(), android(), process.platform === "darwin" && ios()) };
if (!steps[what]) {
  console.error(`사용법: node scripts/build-mobile-engine.mjs [${Object.keys(steps).join("|")}]`);
  process.exit(2);
}
steps[what]();
