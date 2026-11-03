#!/usr/bin/env node
// rhwp-studio(HWP/HWPX 웹 편집기, MIT)를 고정 커밋에서 받아 오프라인용으로 빌드하고 데스크톱 앱에 넣는다.
//
//   node scripts/fetch-rhwp-studio.mjs
//
// 결정 기록: docs/decisions/0005-rhwp-studio-editor.md
// - 소스: rhwp 저장소의 rhwp-studio·assets/fonts·npm/editor 만 sparse 로 받는다(엔진과 같은 커밋).
// - WASM: npm 의 @rhwp/core(엔진과 같은 버전)를 무결성 해시(sha512)로 확인해 쓴다.
// - 빌드: `npm ci --ignore-scripts`(설치 스크립트 실행 안 함) → 외부 웹폰트·HwpCtrl 플러그인을 끄고 vite build.
// - 정리: 예제 문서·서비스 워커·PWA 매니페스트를 지운다(앱 안에서는 쓰지 않는다).
//   재배포 조건이 분명하지 않은 글꼴(FONTS.md 에 "무료 배포"로만 적힌 Cafe24·행복고딕)은 넣지 않는다 — studio 가 다른 글꼴로 대신한다.
// - 패치: 부모 창 origin 이 tauri: 일 때도 연결을 받도록 한 줄 고친다(Tauri 는 macOS·Linux 에서 tauri://localhost).
//
// 결과: apps/desktop/public/studio/ (git 제외, Vite 가 앱 번들에 넣는다)
// 버전을 올리면 fetch-rhwp.mjs 와 같은 TAG·COMMIT 으로 맞추고 CORE_INTEGRITY 를 `npm view @rhwp/core@<버전> dist.integrity` 로 바꾼다.

import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const REPO = "https://github.com/edwardkim/rhwp.git";
const TAG = "v0.8.7";
const COMMIT = "1a76570e833917d15817415a53c09ad61ab3203f";
const CORE = "@rhwp/core@0.8.7";
const CORE_INTEGRITY = "sha512-qgNpY9aclCvy9D7cPnpuplu42kUFZCCPhdjAS5HBso4Z4XeHG3uchYdUleQBoas/zEuwfJ7H4/ulepqdL/LXDA==";
const PATHS = ["/rhwp-studio/", "/assets/fonts/", "/npm/editor/", "/LICENSE"];

/** 업스트림 파일에 적용하는 패치: [파일, 찾을 문자열, 바꿀 문자열]. 찾지 못하면 실패한다(업스트림이 바뀐 것). */
const PATCHES = [
  [
    "rhwp-studio/src/embed/protocol.ts",
    "return protocol === 'http:' || protocol === 'https:';",
    "return protocol === 'http:' || protocol === 'https:' || protocol === 'tauri:'; // Papy: Tauri(macOS·Linux) 부모 창",
  ],
];

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const work = join(root, "engine", "vendor", "rhwp-studio");
const outDir = join(root, "apps", "desktop", "public", "studio");
const stamp = join(outDir, ".papy-release");
/** 앱에 넣지 않는 글꼴 파일(라이선스가 OFL 등으로 분명하지 않음) */
const EXCLUDED_FONTS = /^(Cafe24|Happiness)/;
const expected = `${TAG} ${COMMIT} ${CORE} ${PATCHES.length} ${EXCLUDED_FONTS.source}`;

if (existsSync(stamp) && readFileSync(stamp, "utf8").trim() === expected) {
  console.log(`rhwp-studio ${TAG} 이미 있음: ${outDir}`);
  process.exit(0);
}

// Windows 에서 npm.cmd 같은 배치 파일은 셸로만 실행된다(인자는 고정 문자열뿐이다).
const shell = process.platform === "win32";
const run = (cmd, args, cwd, env = {}) =>
  execFileSync(cmd, args, { cwd, shell, stdio: ["ignore", "inherit", "inherit"], env: { ...process.env, ...env } });
const npm = process.platform === "win32" ? "npm.cmd" : "npm";
const npx = process.platform === "win32" ? "npx.cmd" : "npx";

// 1. 소스
console.log(`내려받는 중: rhwp-studio ${TAG} (${COMMIT.slice(0, 12)})`);
rmSync(work, { recursive: true, force: true });
mkdirSync(work, { recursive: true });
run("git", ["init", "-q"], work);
run("git", ["remote", "add", "origin", REPO], work);
run("git", ["sparse-checkout", "set", "--no-cone", ...PATHS], work);
run("git", ["fetch", "-q", "--depth", "1", "--filter=blob:none", "origin", COMMIT], work);
run("git", ["checkout", "-q", "--detach", COMMIT], work);
const head = execFileSync("git", ["-C", work, "rev-parse", "HEAD"], { encoding: "utf8" }).trim();
if (head !== COMMIT) throw new Error(`commit mismatch: expected ${COMMIT}, got ${head}`);

// 2. WASM 패키지(@rhwp/core) — 무결성 확인 후 ../pkg 에 푼다(studio 의 vite 별칭 @wasm).
const pkg = join(work, "pkg");
mkdirSync(pkg, { recursive: true });
const tarballName = execFileSync(npm, ["pack", CORE, "--silent"], { cwd: pkg, shell, encoding: "utf8" }).trim().split(/\r?\n/).pop();
const tarball = join(pkg, tarballName);
const digest = `sha512-${createHash("sha512").update(readFileSync(tarball)).digest("base64")}`;
if (digest !== CORE_INTEGRITY) throw new Error(`${CORE} integrity mismatch: ${digest}`);
run("tar", ["-xzf", tarballName, "--strip-components=1"], pkg);

// 3. 패치
for (const [file, from, to] of PATCHES) {
  const path = join(work, file);
  const text = readFileSync(path, "utf8");
  if (!text.includes(from)) throw new Error(`patch target not found in ${file}`);
  writeFileSync(path, text.replace(from, to));
}

// 4. 빌드(설치 스크립트 없이)
const studio = join(work, "rhwp-studio");
run(npm, ["ci", "--ignore-scripts", "--no-audit", "--no-fund"], studio);
run(npx, ["vite", "build", "--base=./"], studio, { RHWP_DISABLE_EXTERNAL_WEBFONTS: "1", RHWP_WITHOUT_HWPCTRL: "1" });

// 5. 정리 후 앱으로 복사
const dist = join(studio, "dist");
for (const name of ["samples", "sw.js", "registerSW.js", "manifest.webmanifest"]) rmSync(join(dist, name), { recursive: true, force: true });
for (const name of readdirSync(dist)) {
  if (/^workbox-.*\.js$/.test(name)) rmSync(join(dist, name));
}
for (const name of readdirSync(join(dist, "fonts"))) {
  if (EXCLUDED_FONTS.test(name)) rmSync(join(dist, "fonts", name));
}
const indexPath = join(dist, "index.html");
writeFileSync(
  indexPath,
  readFileSync(indexPath, "utf8")
    .replace(/\s*<script id="vite-plugin-pwa:register-sw"[^>]*><\/script>/, "")
    .replace(/\s*<link rel="manifest"[^>]*>/, ""),
);
rmSync(outDir, { recursive: true, force: true });
mkdirSync(dirname(outDir), { recursive: true });
cpSync(dist, outDir, { recursive: true, dereference: true });
// 라이선스: studio·SDK·WASM 은 저장소 LICENSE(MIT), 글꼴은 fonts/FONTS.md 와 각 OFL 파일.
cpSync(join(work, "LICENSE"), join(outDir, "LICENSE.rhwp.txt"));
writeFileSync(stamp, `${expected}\n`);
console.log(`rhwp-studio ${TAG} → ${outDir}`);
