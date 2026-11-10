#!/usr/bin/env node
// 회귀 점검용 문서 모음을 고정 커밋에서 받는다(git 에서 빠진다).
//
//   node scripts/fetch-corpus.mjs          # 둘 다
//   node scripts/fetch-corpus.mjs rhwp     # rhwp 샘플만(약 700MB)
//   node scripts/fetch-corpus.mjs poi      # Apache POI Word 문서만(약 30MB)
//
// - tests/corpus/rhwp-samples/: rhwp 저장소 samples/(MIT). HWP·HWPX·DOCX·PDF 등.
// - tests/corpus/poi-documents/: Apache POI test-data/document/ 의 .doc·.docx(Apache-2.0).
// 기준값은 tests/corpus/<이름>.golden.tsv 에 커밋한다. rhwp 버전을 올리면 scripts/fetch-rhwp.mjs 와
// 같은 커밋으로 맞추고 기준값을 다시 만든다(docs/formats/test-corpus.md).

import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const SETS = {
  rhwp: {
    repo: "https://github.com/edwardkim/rhwp.git",
    tag: "v0.9.0",
    commit: "c9095c5f67632e6721e800ccc7fb43ea49d26682",
    folder: "samples",
    license: ["LICENSE", "LICENSE-rhwp"],
    out: "rhwp-samples",
  },
  poi: {
    repo: "https://github.com/apache/poi.git",
    tag: "trunk@2026-11-04",
    commit: "7f4d36854109efa17152b27a5fb1b2c7d842c314",
    folder: "test-data/document",
    license: ["legal/LICENSE", "LICENSE-poi"],
    out: "poi-documents",
    // Word 문서만 남긴다.
    keep: (name) => /\.(doc|docx)$/i.test(name),
  },
};

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const wanted = process.argv.slice(2);
for (const [name, set] of Object.entries(SETS)) {
  if (wanted.length === 0 || wanted.includes(name)) fetchSet(set);
}

function fetchSet({ repo, tag, commit, folder, license, out, keep }) {
  const outDir = join(root, "tests", "corpus", out);
  const stamp = join(outDir, ".papy-release");
  const expected = `${tag} ${commit}`;
  if (existsSync(stamp) && readFileSync(stamp, "utf8").trim() === expected) {
    console.log(`${out} 이미 있음: ${outDir}`);
    return;
  }
  const work = `${outDir}.download`;
  const git = (...args) => execFileSync("git", ["-C", work, ...args], { stdio: ["ignore", "inherit", "inherit"] });
  console.log(`내려받는 중: ${repo} ${tag} (${commit.slice(0, 12)}) ${folder}/`);
  rmSync(work, { recursive: true, force: true });
  mkdirSync(work, { recursive: true });
  git("init", "-q");
  git("remote", "add", "origin", repo);
  git("sparse-checkout", "set", "--no-cone", `/${folder}/`, `/${license[0]}`);
  git("fetch", "-q", "--depth", "1", "--filter=blob:none", "origin", commit);
  git("checkout", "-q", "--detach", commit);
  const head = execFileSync("git", ["-C", work, "rev-parse", "HEAD"], { encoding: "utf8" }).trim();
  if (head !== commit) throw new Error(`commit mismatch: expected ${commit}, got ${head}`);
  rmSync(outDir, { recursive: true, force: true });
  cpSync(join(work, folder), outDir, { recursive: true });
  if (keep) {
    for (const entry of readdirSync(outDir, { withFileTypes: true })) {
      if (!entry.isFile() || !keep(entry.name)) rmSync(join(outDir, entry.name), { recursive: true, force: true });
    }
  }
  cpSync(join(work, license[0]), join(outDir, license[1]));
  rmSync(work, { recursive: true, force: true });
  writeFileSync(stamp, `${expected}\n`);
  console.log(`${out} → ${outDir}`);
}
