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
/** 글꼴 폴더의 "NotoSansKR-OFL.txt" 같은 OFL 본문 */
const OFL_FILE = /(^|[-_.])ofl[-_.].*txt$|[-_]ofl\.txt$/i;

/** 폴더 바로 아래의 라이선스 파일 본문들. */
function licenseTexts(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((name) => (LICENSE_FILE.test(name) || OFL_FILE.test(name)) && statSync(join(dir, name)).isFile())
    .sort()
    .map((name) => ({ name, text: readFileSync(join(dir, name), "utf8").trim() }));
}

const sections = [];
const missing = [];
function add(title, license, texts, extra = {}) {
  if (texts.length === 0) missing.push(title);
  sections.push({ title, license, texts, ...extra });
}

// 라이선스 파일을 싣지 않은 crate: SPDX 식별자의 표준 본문을 저작자 이름으로 채운다(MIT 를 고를 수 있으면 MIT).
const MIT_TEXT = (holders) => `MIT License

Copyright (c) ${holders}

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.`;
const BSD3_TEXT = (holders) => `BSD 3-Clause License

Copyright (c) ${holders}

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.`;
/** 저작자 표기(Cargo authors 의 이메일은 뺀다) */
const holdersOf = (pkg) =>
  (pkg.authors ?? []).map((author) => author.replace(/\s*<[^>]*>/, "").trim()).filter(Boolean).join(", ") ||
  `the ${pkg.name} developers`;
/** 표준 본문으로 채울 수 있으면 [이름, 본문]. MPL 등은 다른 crate 가 실은 전문을 가리킨다(아래 fill 단계). */
function standardText(pkg) {
  const offered = (pkg.license ?? "").split(/\s+OR\s+|\//).map((id) => id.trim());
  if (offered.includes("MIT")) return ["LICENSE (MIT, chosen from: " + pkg.license + ")", MIT_TEXT(holdersOf(pkg))];
  if (offered.includes("BSD-3-Clause")) return ["LICENSE (BSD-3-Clause)", BSD3_TEXT(holdersOf(pkg))];
  return null;
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
  let texts = licenseTexts(dir);
  const extra = { source: `https://crates.io/crates/${pkg.name}/${pkg.version}` };
  if (texts.length === 0) {
    const standard = standardText(pkg);
    if (standard) texts = [{ name: standard[0], text: standard[1] }];
    else if ((pkg.license ?? "").includes("MPL-2.0")) extra.fullTextOf = "Mozilla Public License Version 2.0";
  }
  add(`${pkg.name} ${pkg.version} (Rust crate)`, pkg.license ?? pkg.license_file ?? "(see text)", texts, extra);
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

// ---- HWP 고급 편집기(rhwp-studio, scripts/fetch-rhwp-studio.mjs) — 받아 둔 경우만 ----
const studioOut = join(root, "apps", "desktop", "public", "studio");
const studioSrc = join(vendor, "rhwp-studio");
if (existsSync(studioOut) && existsSync(studioSrc)) {
  add("rhwp-studio, @rhwp/core (WASM), @rhwp/editor (HWP editor)", "MIT", licenseTexts(studioSrc));
  const fontsDir = join(studioOut, "fonts");
  const fontTexts = [{ name: "FONTS.md (font list and licenses)", text: readFileSync(join(fontsDir, "FONTS.md"), "utf8").trim() }];
  for (const name of readdirSync(fontsDir).filter((file) => /\.txt$/i.test(file))) {
    fontTexts.push({ name, text: readFileSync(join(fontsDir, name), "utf8").trim() });
  }
  add("rhwp-studio bundled fonts (Noto, Nanum, Pretendard, Gowun, D2Coding, Spoqa Han Sans, Source Han Serif, Latin Modern Math)", "OFL-1.1, GUST Font License", fontTexts);
  const studioApp = join(studioSrc, "rhwp-studio");
  const studioPkg = JSON.parse(readFileSync(join(studioApp, "package.json"), "utf8"));
  for (const name of Object.keys(studioPkg.dependencies ?? {}).sort()) {
    const dir = join(studioApp, "node_modules", name);
    if (!existsSync(dir)) continue;
    const pkg = JSON.parse(readFileSync(join(dir, "package.json"), "utf8"));
    add(`${pkg.name} ${pkg.version} (npm, rhwp-studio)`, pkg.license ?? "(see text)", licenseTexts(dir));
  }
}

// ---- 쓰기 ----
const rule = "=".repeat(78);
let out = `Papy — Third-party notices\n\nPapy itself is licensed under PolyForm Noncommercial 1.0.0 (see LICENSE).\nThis file lists third-party components distributed with the desktop app and their licenses.\nComponents: ${sections.length}\n`;
// 같은 본문(흔한 MIT·Apache 전문)은 처음 한 번만 싣고 뒤에서는 가리킨다. 저작권 줄이 다르면 다른 본문이다.
const firstSeen = new Map();
for (const section of sections) {
  out += `\n${rule}\n${section.title}\nLicense: ${section.license}\n${rule}\n`;
  if (section.source) out += `Source: ${section.source}\n`;
  if (section.texts.length === 0 && section.fullTextOf) {
    // 같은 라이선스 전문을 실은 다른 구성요소를 가리킨다(본문을 중복해 싣지 않는다).
    const holder = sections.find((other) => other.texts.some((t) => t.text.includes(section.fullTextOf)));
    if (holder) {
      out += `\n--- ${section.license} full text: same text as ${holder.title} ---\n`;
      missing.splice(missing.indexOf(section.title), 1);
      continue;
    }
  }
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
