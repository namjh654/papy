// 엔진 대체 글꼴(OFL-1.1)을 만든다: rhwp-studio 의 woff2 를 TrueType 으로 풀어 engine/vendor/fonts/ 에 둔다.
//
//   node scripts/fetch-fonts.mjs        # 먼저 node scripts/fetch-rhwp-studio.mjs (woff2 원본)
//
// - Noto Sans KR Regular 은 fetch-rhwp.mjs 가 받는다(rhwp ttfs/opensource). 여기서는 굵게·명조를 더한다.
// - 엔진(fontdb)은 woff2 를 읽지 못해 빌드 때 한 번 푼다(wawoff2: Google woff2 를 WebAssembly 로, MIT).
// - 결과 SHA-256 은 fonts/manifest.json 에 적는다. 모바일 앱은 이 값으로 내려받은 글꼴을 확인한다.
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync, copyFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { decompress } from "wawoff2";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const source = join(root, "engine", "vendor", "rhwp-studio", "assets", "fonts");
const out = join(root, "engine", "vendor", "fonts");
/** 엔진에 더할 글꼴(파일, 글꼴 가족, 스타일): 문서의 명조·바탕(굵게 포함)과 고딕 굵게 */
const FONTS = [
  ["NotoSerifKR-Regular", "Noto Serif KR", "Regular"],
  ["NotoSerifKR-Bold", "Noto Serif KR", "Bold"],
  ["NotoSansKR-Bold", "Noto Sans KR", "Bold"],
];

/**
 * 이름 표(name)를 가족·스타일·전체 이름·PostScript 이름만 다시 쓴다.
 * 원본 woff2 는 가변 글꼴에서 잘라 낸 것이라 가족 이름이 "Noto Serif KR ExtraLight" 처럼 남아 있어,
 * 문서가 찾는 "Noto Serif KR"(rhwp 대체 글꼴 목록)과 맞지 않는다. 표 위치·체크섬도 다시 맞춘다.
 */
function renameFont(font, family, style) {
  const numTables = font.readUInt16BE(4);
  const tables = [];
  for (let i = 0; i < numTables; i++) {
    const at = 12 + i * 16;
    tables.push({
      tag: font.toString("latin1", at, at + 4),
      data: font.subarray(font.readUInt32BE(at + 8), font.readUInt32BE(at + 8) + font.readUInt32BE(at + 12)),
    });
  }
  const names = [
    [1, family],
    [2, style],
    [3, `${family.replaceAll(" ", "")}-${style};Papy`],
    [4, style === "Regular" ? family : `${family} ${style}`],
    [6, `${family.replaceAll(" ", "")}-${style}`],
  ];
  // 저작권·상표·라이선스 같은 나머지 Windows 이름(0, 5, 7~14 등)은 원본 그대로 둔다(OFL 고지 유지).
  const old = tables.find((t) => t.tag === "name").data;
  const keep = new Set([0, 5, 7, 8, 9, 10, 11, 12, 13, 14]);
  const kept = [];
  for (let i = 0, count = old.readUInt16BE(2), base = old.readUInt16BE(4); i < count; i++) {
    const at = 6 + i * 12;
    const [platform, encoding, language, id] = [0, 2, 4, 6].map((k) => old.readUInt16BE(at + k));
    if (platform === 3 && encoding === 1 && language === 0x409 && keep.has(id)) {
      const start = base + old.readUInt16BE(at + 10);
      kept.push([id, Buffer.from(old.subarray(start, start + old.readUInt16BE(at + 8)))]);
    }
  }
  const records = [...names.map(([id, text]) => [id, Buffer.from(text, "utf16le").swap16()]), ...kept].sort((a, b) => a[0] - b[0]);
  names.length = 0;
  names.push(...records.map(([id]) => [id]));
  const strings = records.map(([, bytes]) => bytes);
  const header = 6 + names.length * 12;
  const name = Buffer.alloc(header + strings.reduce((n, b) => n + b.length, 0));
  name.writeUInt16BE(0, 0);
  name.writeUInt16BE(names.length, 2);
  name.writeUInt16BE(header, 4);
  let offset = 0;
  names.forEach(([id], i) => {
    const at = 6 + i * 12;
    name.writeUInt16BE(3, at); // Windows
    name.writeUInt16BE(1, at + 2); // Unicode BMP
    name.writeUInt16BE(0x409, at + 4); // en-US
    name.writeUInt16BE(id, at + 6);
    name.writeUInt16BE(strings[i].length, at + 8);
    name.writeUInt16BE(offset, at + 10);
    strings[i].copy(name, header + offset);
    offset += strings[i].length;
  });
  for (const table of tables) if (table.tag === "name") table.data = name;
  // 다시 묶는다: 표는 4바이트 경계, head.checkSumAdjustment 는 0 으로 두고 계산한다.
  const pad = (n) => (n + 3) & ~3;
  const dirSize = 12 + numTables * 16;
  const total = dirSize + tables.reduce((n, t) => n + pad(t.data.length), 0);
  const outFont = Buffer.alloc(total);
  font.copy(outFont, 0, 0, 12);
  const checksum = (buf) => {
    let sum = 0;
    for (let i = 0; i < buf.length; i += 4) {
      const word = Buffer.alloc(4);
      buf.copy(word, 0, i, Math.min(i + 4, buf.length));
      sum = (sum + word.readUInt32BE(0)) >>> 0;
    }
    return sum;
  };
  let at = dirSize;
  let headAt = -1;
  tables.forEach((table, i) => {
    const entry = 12 + i * 16;
    outFont.write(table.tag, entry, "latin1");
    if (table.tag === "head") {
      headAt = at;
      table.data = Buffer.from(table.data);
      table.data.writeUInt32BE(0, 8);
    }
    outFont.writeUInt32BE(checksum(table.data), entry + 4);
    outFont.writeUInt32BE(at, entry + 8);
    outFont.writeUInt32BE(table.data.length, entry + 12);
    table.data.copy(outFont, at);
    at += pad(table.data.length);
  });
  if (headAt >= 0) outFont.writeUInt32BE((0xb1b0afba - checksum(outFont)) >>> 0, headAt + 8);
  return outFont;
}

if (!existsSync(source)) throw new Error("woff2 원본이 없습니다. 먼저 node scripts/fetch-rhwp-studio.mjs 를 실행하세요.");
mkdirSync(out, { recursive: true });
const manifest = [];
for (const [name, family, style] of FONTS) {
  const decoded = Buffer.from(await decompress(readFileSync(join(source, `${name}.woff2`))));
  if (decoded.readUInt32BE(0) !== 0x00010000 && decoded.toString("latin1", 0, 4) !== "OTTO") {
    throw new Error(`${name}: TrueType/OpenType 이 아닙니다`);
  }
  const ttf = renameFont(decoded, family, style);
  writeFileSync(join(out, `${name}.ttf`), ttf);
  const sha256 = createHash("sha256").update(ttf).digest("hex");
  manifest.push({ file: `${name}.ttf`, bytes: ttf.length, sha256 });
  console.log(`${name}.ttf  ${(ttf.length / 1024 / 1024).toFixed(2)} MB  ${sha256}`);
}
// 라이선스: rhwp-studio 의 OFL 본문(Noto 는 같은 OFL-1.1).
const ofl = join(root, "engine", "vendor", "rhwp", "ttfs", "opensource", "NotoSansKR-OFL.txt");
if (existsSync(ofl)) copyFileSync(ofl, join(out, "Noto-OFL.txt"));
writeFileSync(join(out, "manifest.json"), `${JSON.stringify({ fonts: manifest }, null, 2)}\n`);
