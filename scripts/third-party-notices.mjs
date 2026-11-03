#!/usr/bin/env node
// 데스크톱 앱에 함께 배포되는 서드파티 구성요소의 라이선스 고지를 한 파일로 모은다.
//
//   node scripts/third-party-notices.mjs
//
// 결과: apps/desktop/src-tauri/notices/THIRD_PARTY_NOTICES.txt (build.rs 가 앱 리소스 licenses/ 에 넣는다)
// - Rust: `cargo metadata` 로 데스크톱 빌드에 들어가는 crate 목록을 얻고, 각 crate 폴더의 LICENSE*/COPYING*/NOTICE* 를 붙인다.
// - JS: 화면 번들에 들어가는 apps/desktop 의 dependencies(개발 도구 제외)와 그 하위 의존성.
// - 엔진 외부 파일: PDFium(licenses/ 포함), rhwp, Noto Sans KR.
// 외부 도구를 내려받지 않는다. 엔진 외부 파일이 없으면 `pnpm engine:vendor` 를 먼저 실행한다.

import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const tauriDir = join(root, "apps", "desktop", "src-tauri");
const outFile = join(tauriDir, "notices", "THIRD_PARTY_NOTICES.txt");
const LICENSE_FILE = /^(licen[cs]e|copying|notice|ofl)([-._].*)?$/i;

/** 폴더 바로 아래의 라이선스 파일 본문들. */
function licenseTexts(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((name) => LICENSE_FILE.test(name) && statSync(join(dir, name)).isFile())
    .sort()
    .map((name) => ({ name, text: readFileSync(join(dir, name), "utf8").trim() }));
}

const sections = [];
const missing = [];
function add(title, license, texts) {
  if (texts.length === 0) missing.push(title);
  sections.push({ title, license, texts });
}

// ---- Rust ----
const metadata = JSON.parse(
  execFileSync("cargo", ["metadata", "--format-version", "1", "--locked"], {
    cwd: tauriDir,
    encoding: "utf8",
    maxBuffer: 256 * 1024 * 1024,
  }),
);
// 데스크톱 패키지에서 정상(normal)·빌드 의존성으로 닿는 crate 만(개발 의존성 제외).
const nodes = new Map(metadata.resolve.nodes.map((node) => [node.id, node]));
const reachable = new Set();
const stack = [metadata.resolve.root];
while (stack.length) {
  const id = stack.pop();
  if (reachable.has(id)) continue;
  reachable.add(id);
  for (const dep of nodes.get(id)?.deps ?? []) {
    if (dep.dep_kinds.some((kind) => kind.kind !== "dev")) stack.push(dep.pkg);
  }
}
const ownPrefix = root + "/";
for (const pkg of metadata.packages
  .filter((pkg) => reachable.has(pkg.id))
  .sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version))) {
  const dir = dirname(pkg.manifest_path);
  // Papy 자체 crate 는 PolyForm NC(저장소 LICENSE) — 고지 대상이 아니다. 단 vendor(rhwp)는 서드파티.
  if (dir.startsWith(ownPrefix) && !dir.includes("/engine/vendor/")) continue;
  add(`${pkg.name} ${pkg.version} (Rust crate)`, pkg.license ?? pkg.license_file ?? "(see text)", licenseTexts(dir));
}

// ---- JS (화면 번들) ----
const desktop = join(root, "apps", "desktop");
const seen = new Set();
function addNpm(name, fromDir) {
  // pnpm 은 의존성을 패키지 옆 node_modules 에 링크한다.
  let dir = join(fromDir, "node_modules", name);
  if (!existsSync(dir)) dir = join(desktop, "node_modules", name);
  if (!existsSync(dir)) return;
  const pkg = JSON.parse(readFileSync(join(dir, "package.json"), "utf8"));
  const key = `${pkg.name}@${pkg.version}`;
  if (seen.has(key)) return;
  seen.add(key);
  add(`${pkg.name} ${pkg.version} (npm)`, pkg.license ?? "(see text)", licenseTexts(dir));
  const real = execFileSync("node", ["-e", "process.stdout.write(require('fs').realpathSync(process.argv[1]))", dir], {
    encoding: "utf8",
  });
  // 실제 위치(.pnpm/<pkg>/node_modules/<name>)의 형제 폴더가 그 패키지의 의존성이다.
  const siblings = dirname(name.startsWith("@") ? dirname(real) : real);
  for (const dep of Object.keys(pkg.dependencies ?? {})) addNpm(dep, join(siblings, ".."));
}
const desktopPkg = JSON.parse(readFileSync(join(desktop, "package.json"), "utf8"));
for (const name of Object.keys(desktopPkg.dependencies ?? {}).sort()) addNpm(name, desktop);

// ---- 엔진 외부 파일 ----
const vendor = join(root, "engine", "vendor");
const host = execFileSync("rustc", ["-vV"], { encoding: "utf8" }).match(/^host: (.+)$/m)[1].trim();
const target = process.env.PAPY_TARGET ?? host;
const pdfium = join(vendor, "pdfium", target);
if (!existsSync(pdfium)) {
  console.error(`PDFium(${target}) 가 없습니다. 먼저 \`node scripts/fetch-pdfium.mjs ${target}\` 를 실행하세요.`);
  process.exit(1);
}
const pdfiumTexts = licenseTexts(pdfium);
const licensesDir = join(pdfium, "licenses");
if (existsSync(licensesDir)) {
  for (const name of readdirSync(licensesDir).sort()) {
    pdfiumTexts.push({ name: `licenses/${name}`, text: readFileSync(join(licensesDir, name), "utf8").trim() });
  }
}
add("PDFium (chromium/8125, bblanchon/pdfium-binaries)", "BSD-3-Clause AND Apache-2.0 (and bundled third-party licenses)", pdfiumTexts);
add("Noto Sans KR Regular (font)", "OFL-1.1", licenseTexts(join(vendor, "rhwp", "ttfs", "opensource")));

// ---- 쓰기 ----
const rule = "=".repeat(78);
let out = `Papy — Third-party notices\n\nPapy itself is licensed under PolyForm Noncommercial 1.0.0 (see LICENSE).\nThis file lists third-party components distributed with the desktop app and their licenses.\nComponents: ${sections.length}\n`;
// 같은 본문(흔한 MIT·Apache 전문)은 처음 한 번만 싣고 뒤에서는 가리킨다. 저작권 줄이 다르면 다른 본문이다.
const firstSeen = new Map();
for (const section of sections) {
  out += `\n${rule}\n${section.title}\nLicense: ${section.license}\n${rule}\n`;
  for (const { name, text } of section.texts) {
    const earlier = firstSeen.get(text);
    if (earlier) {
      out += `\n--- ${name}: same text as ${earlier} ---\n`;
    } else {
      firstSeen.set(text, `${section.title} / ${name}`);
      out += `\n--- ${name} ---\n${text}\n`;
    }
  }
  if (section.texts.length === 0) out += "\n(no license file shipped in the package; see the license identifier above)\n";
}
mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(outFile, out);
console.log(`${relative(root, outFile)}: ${sections.length} components, ${(out.length / 1024).toFixed(0)} KiB`);
if (missing.length) console.log(`라이선스 파일이 없는 구성요소 ${missing.length}개(식별자만 표기): ${missing.slice(0, 8).join(", ")}${missing.length > 8 ? " …" : ""}`);
