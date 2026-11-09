#!/usr/bin/env node
// PDFium 사전 빌드(bblanchon/pdfium-binaries)를 고정 버전·SHA-256 검증으로 내려받는다.
//
//   node scripts/fetch-pdfium.mjs                 # 현재 머신의 Rust 타깃
//   node scripts/fetch-pdfium.mjs x86_64-pc-windows-msvc
//
// 결과: engine/vendor/pdfium/<rust-target>/{libpdfium.dylib|pdfium.dll|libpdfium.so, LICENSE, licenses/}
// 버전을 올리면 RELEASE 와 SHA256 을 함께 바꾸고 docs/development/versions.md 를 갱신한다.

import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const RELEASE = "chromium/8125";

/** Rust 타깃 → [자산 이름, SHA-256, 압축 안 라이브러리 경로] */
const TARGETS = {
  "aarch64-apple-darwin": ["pdfium-mac-arm64.tgz", "4939bd935ac83ddc09a3a4f038bc43a19cea1660316a3667f26cb07a070e139d", "lib/libpdfium.dylib"],
  "x86_64-apple-darwin": ["pdfium-mac-x64.tgz", "8a5193535b80c7215d8932f6647ae5097465b6dba2811602c21e120c2af112b0", "lib/libpdfium.dylib"],
  "x86_64-pc-windows-msvc": ["pdfium-win-x64.tgz", "9a964261760cff52570aabf038ab346d3a5034dd8b30d0d5fe8861235d356b8b", "bin/pdfium.dll"],
  "aarch64-pc-windows-msvc": ["pdfium-win-arm64.tgz", "d394ba61991fb53f4d87f70988035f337d9e73f2bd2c3c1b966df8c43fcabb05", "bin/pdfium.dll"],
  "x86_64-unknown-linux-gnu": ["pdfium-linux-x64.tgz", "e1c3ce6e270cb5e0c20816b379ad9f2c1309972697c505f508de92bf5e7d6659", "lib/libpdfium.so"],
  "aarch64-unknown-linux-gnu": ["pdfium-linux-arm64.tgz", "8b8818f2ec57d1b213d952ca6d7f0ffeca9d0110487d0e20ad83ba918bc92efa", "lib/libpdfium.so"],
};

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const target = process.argv[2] ?? hostTarget();
const entry = TARGETS[target];
if (!entry) {
  console.error(`지원하지 않는 타깃: ${target}\n가능: ${Object.keys(TARGETS).join(", ")}`);
  process.exit(1);
}
const [asset, sha256, libPath] = entry;
const outDir = join(root, "engine", "vendor", "pdfium", target);
const stamp = join(outDir, ".release");

if (existsSync(stamp) && readFileSync(stamp, "utf8").trim() === `${RELEASE} ${sha256}`) {
  console.log(`PDFium ${RELEASE} (${target}) 이미 있음: ${outDir}`);
  process.exit(0);
}

const url = `https://github.com/bblanchon/pdfium-binaries/releases/download/${RELEASE}/${asset}`;
console.log(`내려받는 중: ${url}`);
// GitHub 릴리스 내려받기는 가끔 5xx·연결 오류로 실패한다(CI 504). 네 번까지 기다렸다 다시 받는다.
async function download(url, tries = 4) {
  for (let attempt = 1; ; attempt++) {
    try {
      const res = await fetch(url);
      if (res.ok) return Buffer.from(await res.arrayBuffer());
      if (res.status < 500 || attempt >= tries) throw new Error(`download failed: HTTP ${res.status}`);
      console.log(`HTTP ${res.status}, 다시 받는 중 (${attempt}/${tries - 1})`);
    } catch (error) {
      if (attempt >= tries || String(error.message).startsWith("download failed: HTTP 4")) throw error;
      console.log(`${error.message}, 다시 받는 중 (${attempt}/${tries - 1})`);
    }
    await new Promise((resolve) => setTimeout(resolve, 5000 * attempt));
  }
}
const data = await download(url);
const actual = createHash("sha256").update(data).digest("hex");
if (actual !== sha256) throw new Error(`SHA-256 mismatch for ${asset}: expected ${sha256}, got ${actual}`);

const work = mkdtempSync(join(tmpdir(), "papy-pdfium-"));
try {
  const archive = join(work, asset);
  writeFileSync(archive, data);
  // Windows 에서는 시스템 tar(bsdtar)를 쓴다. Git Bash 의 GNU tar 는 "C:\..." 를 원격 호스트로 읽는다.
  const tar = process.platform === "win32" ? join(process.env.SystemRoot ?? "C:\\Windows", "System32", "tar.exe") : "tar";
  execFileSync(tar, ["-xzf", archive, "-C", work]);
  rmSync(outDir, { recursive: true, force: true });
  mkdirSync(outDir, { recursive: true });
  cpSync(join(work, libPath), join(outDir, libPath.split("/").pop()));
  cpSync(join(work, "LICENSE"), join(outDir, "LICENSE"));
  cpSync(join(work, "licenses"), join(outDir, "licenses"), { recursive: true });
  writeFileSync(stamp, `${RELEASE} ${sha256}\n`);
} finally {
  rmSync(work, { recursive: true, force: true });
}
console.log(`PDFium ${RELEASE} (${target}) → ${outDir}`);

function hostTarget() {
  const out = execFileSync("rustc", ["-vV"], { encoding: "utf8" });
  return /^host: (.+)$/m.exec(out)[1].trim();
}
