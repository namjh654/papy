// AI 플러그인(papy-ai) 실행 파일을 릴리스 파일로 만든다: gzip 으로 줄이고 ed25519 로 서명한다.
//
//   node scripts/package-ai-plugin.mjs <실행 파일> <대상> <내보낼 폴더>
//   → <폴더>/papy-ai-<대상>.gz, <폴더>/papy-ai-<대상>.gz.sig(서명 base64)
//
// 서명 비밀 키(PKCS#8 PEM)는 환경 변수 PAPY_AI_SIGNING_KEY 로 받는다(GitHub 비밀 값). 저장소에 두지 않는다.
// 공개 키는 앱(apps/desktop/src-tauri/src/ai.rs 의 SIGNING_KEY)에 들어 있고, 앱은 서명이 맞을 때만 설치한다.
import { createPrivateKey, createPublicKey, sign } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { gzipSync } from "node:zlib";

const [binary, target, out] = process.argv.slice(2);
if (!binary || !target || !out) {
  console.error("usage: node scripts/package-ai-plugin.mjs <binary> <target> <out-dir>");
  process.exit(2);
}
const pem = process.env.PAPY_AI_SIGNING_KEY;
if (!pem) {
  console.error("PAPY_AI_SIGNING_KEY is not set");
  process.exit(1);
}
const key = createPrivateKey(pem);
const gz = gzipSync(readFileSync(binary), { level: 9 });
const signature = sign(null, gz, key).toString("base64");
mkdirSync(out, { recursive: true });
const name = `papy-ai-${target}.gz`;
writeFileSync(join(out, name), gz);
writeFileSync(join(out, `${name}.sig`), `${signature}\n`);
const spki = createPublicKey(key).export({ format: "der", type: "spki" });
console.log(`${name}: ${gz.length} bytes, public key ${spki.subarray(spki.length - 32).toString("base64")}`);
