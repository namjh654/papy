#!/usr/bin/env node
// 회귀 점검용 문서 모음(rhwp 저장소의 samples/, MIT)을 고정 커밋에서 받는다. 약 700MB.
//
//   node scripts/fetch-corpus.mjs
//
// 결과: tests/corpus/rhwp-samples/ (git 에서 빠진다). 기준값은 tests/corpus/rhwp-samples.golden.tsv 에 커밋한다.
// rhwp 버전을 올리면 scripts/fetch-rhwp.mjs 와 같은 커밋으로 맞추고 기준값을 다시 만든다(docs/formats/test-corpus.md).

import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const REPO = "https://github.com/edwardkim/rhwp.git";
const TAG = "v0.8.7";
const COMMIT = "1a76570e833917d15817415a53c09ad61ab3203f";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const outDir = join(root, "tests", "corpus", "rhwp-samples");
const stamp = join(outDir, ".papy-release");
const expected = `${TAG} ${COMMIT}`;

if (existsSync(stamp) && readFileSync(stamp, "utf8").trim() === expected) {
  console.log(`rhwp ${TAG} 샘플 이미 있음: ${outDir}`);
  process.exit(0);
}

const work = `${outDir}.download`;
const git = (...args) => execFileSync("git", ["-C", work, ...args], { stdio: ["ignore", "inherit", "inherit"] });

console.log(`내려받는 중: rhwp ${TAG} (${COMMIT.slice(0, 12)}) samples/`);
rmSync(work, { recursive: true, force: true });
mkdirSync(work, { recursive: true });
git("init", "-q");
git("remote", "add", "origin", REPO);
git("sparse-checkout", "set", "--no-cone", "/samples/", "/LICENSE");
git("fetch", "-q", "--depth", "1", "--filter=blob:none", "origin", COMMIT);
git("checkout", "-q", "--detach", COMMIT);

const head = execFileSync("git", ["-C", work, "rev-parse", "HEAD"], { encoding: "utf8" }).trim();
if (head !== COMMIT) throw new Error(`commit mismatch: expected ${COMMIT}, got ${head}`);

rmSync(outDir, { recursive: true, force: true });
cpSync(join(work, "samples"), outDir, { recursive: true });
cpSync(join(work, "LICENSE"), join(outDir, "LICENSE-rhwp"));
rmSync(work, { recursive: true, force: true });
writeFileSync(stamp, `${expected}\n`);
console.log(`rhwp ${TAG} 샘플 → ${outDir}`);
