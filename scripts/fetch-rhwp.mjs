#!/usr/bin/env node
// rhwp(HWP/HWPX 엔진, MIT) 소스를 고정 커밋에서 빌드에 필요한 파일만 받는다.
//
//   node scripts/fetch-rhwp.mjs
//
// rhwp 저장소는 문서·샘플 포함 수 GB 라 cargo git 의존성으로 쓰지 않는다. 얕은(depth 1)·blob 지연
// 받기 + sparse checkout 으로 약 50MB 만 받는다. 커밋 SHA 로 받고 HEAD 를 확인하므로 내용은 git 이 검증한다.
//
// 결과: engine/vendor/rhwp/ (papy-hwp 가 path 의존성으로 쓴다)
// 버전을 올리면 TAG·COMMIT 을 함께 바꾸고 docs/development/versions.md 를 갱신한다.
//
// 받은 뒤 engine/patches/rhwp/*.patch 를 이름 순서로 적용한다(Papy 쪽 성능 수정 등, 업스트림에 알릴 것).
// 패치 내용이 바뀌면 다시 받는다. rhwp 를 올릴 때는 패치가 아직 필요한지 보고, 업스트림에 들어갔으면 지운다.

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const REPO = "https://github.com/edwardkim/rhwp.git";
const TAG = "v0.9.0";
const COMMIT = "c9095c5f67632e6721e800ccc7fb43ea49d26682";

/** 라이브러리 빌드에 필요한 경로. 테스트·도구·문서·샘플은 받지 않는다. */
const PATHS = [
  "/Cargo.toml",
  "/build.rs",
  "/LICENSE",
  "/THIRD_PARTY_LICENSES.md",
  "/src/",
  "/crates/",
  // 0.9.0 부터 Cargo.toml 이 경로로 쓰는 내부 사본(jpeg·svg2pdf·PDF 가져오기). PDF 가져오기는 Papy 가
  // 켜지 않는 선택 기능이지만 cargo 가 의존성을 풀 때 매니페스트를 읽어서 함께 받는다.
  "/vendor/rhwp-zune-jpeg/",
  "/vendor/rhwp-jpeg-encoder/",
  "/vendor/svg2pdf/",
  "/vendor/rhwp-pdf-import-backend/",
  // 빈 문서 템플릿(include_bytes!)
  "/saved/blank2010.hwp",
  // 0.9.0 부터 소스가 include_str!·include_bytes! 로 넣는 파일(crates.io 패키지 include 목록과 같다):
  // 글꼴 측정값·수식 글꼴(OFL)·글꼴 규칙·에이전트 안내 문서.
  "/README.md",
  "/README_EN.md",
  "/llms.txt",
  "/assets/fonts/enclosed-pua-metrics.json",
  "/assets/fonts/LatinModernMath-VariableOutline-subset.otf",
  "/assets/fonts/MANIFEST-RHWP-LMM-VariableOutline.txt",
  "/assets/fonts/licenses/",
  "/assets/font-rules/font_rule_registry_v2.json",
  "/data/font-metrics/nimbus-roman/",
  "/mydocs/manual/agent_knowledge_map.md",
  "/mydocs/manual/agent_troubleshooting_guide.md",
  "/mydocs/manual/gym_optional_tool.md",
  "/mydocs/manual/recipes/",
  "/mydocs/tech/agent_roadmap/atlas_r1_r200.md",
  // 한글 글꼴(OFL-1.1): PDF 텍스트 상자에 넣고, 한글 글꼴이 없는 PC 에서 HWP 대체 글꼴로 쓴다.
  "/ttfs/opensource/NotoSansKR-Regular.ttf",
  "/ttfs/opensource/NotoSansKR-OFL.txt",
];

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const outDir = join(root, "engine", "vendor", "rhwp");
const stamp = join(outDir, ".papy-release");
const patchDir = join(root, "engine", "patches", "rhwp");
const patches = existsSync(patchDir)
  ? readdirSync(patchDir)
      .filter((name) => name.endsWith(".patch"))
      .sort()
      .map((name) => join(patchDir, name))
  : [];
const patchHash = createHash("sha256");
for (const patch of patches) patchHash.update(readFileSync(patch));
const expected = `${TAG} ${COMMIT} ${PATHS.join(",")} patches:${patchHash.digest("hex").slice(0, 16)}`;

if (existsSync(stamp) && readFileSync(stamp, "utf8").trim() === expected) {
  console.log(`rhwp ${TAG} 이미 있음: ${outDir}`);
  process.exit(0);
}

const git = (...args) => execFileSync("git", ["-C", outDir, ...args], { stdio: ["ignore", "inherit", "inherit"] });

console.log(`내려받는 중: rhwp ${TAG} (${COMMIT.slice(0, 12)})`);
rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });
git("init", "-q");
git("remote", "add", "origin", REPO);
git("sparse-checkout", "set", "--no-cone", ...PATHS);
git("fetch", "-q", "--depth", "1", "--filter=blob:none", "origin", COMMIT);
git("checkout", "-q", "--detach", COMMIT);

const head = execFileSync("git", ["-C", outDir, "rev-parse", "HEAD"], { encoding: "utf8" }).trim();
if (head !== COMMIT) throw new Error(`commit mismatch: expected ${COMMIT}, got ${head}`);
for (const patch of patches) {
  git("apply", "--whitespace=nowarn", patch);
  console.log(`패치 적용: ${patch.slice(root.length + 1)}`);
}
// 중첩 저장소는 cargo 의 파일 목록 계산을 깨뜨린다. 커밋은 위에서 확인했으니 .git 은 지운다.
rmSync(join(outDir, ".git"), { recursive: true, force: true });
writeFileSync(stamp, `${expected}\n`);
console.log(`rhwp ${TAG} → ${outDir}`);
