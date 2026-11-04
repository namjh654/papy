// 화면 글자 검사: 언어마다 키가 같은지, 함수 글자가 인자를 받아 글자를 내는지, 언어 고르기 순서.
// 실행: pnpm --filter @papy/desktop test
import assert from "node:assert/strict";
import { test } from "node:test";
import { DEFAULT_LOCALE, MESSAGES, detectLocale, type Locale } from "../src/i18n/index.ts";

/** 잎 경로와 종류("string" | "function") */
function leaves(value: unknown, prefix = ""): string[] {
  if (typeof value === "string" || typeof value === "function") return [`${prefix}:${typeof value}`];
  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
    leaves(child, prefix ? `${prefix}.${key}` : key),
  );
}

test("모든 언어의 키와 종류가 기준 언어와 같다", () => {
  const base = leaves(MESSAGES[DEFAULT_LOCALE]).sort();
  for (const locale of Object.keys(MESSAGES) as Locale[]) {
    assert.deepEqual(leaves(MESSAGES[locale]).sort(), base, locale);
  }
});

test("빈 글자가 없고, 함수 글자는 인자를 넣으면 글자가 된다", () => {
  for (const locale of Object.keys(MESSAGES) as Locale[]) {
    const walk = (value: unknown, path: string) => {
      if (typeof value === "string") assert.ok(value.length > 0, `${locale}.${path}`);
      else if (typeof value === "function") {
        const out = (value as (...args: unknown[]) => unknown)("X", 3);
        assert.equal(typeof out, "string", `${locale}.${path}`);
        assert.ok(!String(out).includes("undefined"), `${locale}.${path}`);
      } else for (const [key, child] of Object.entries(value as object)) walk(child, path ? `${path}.${key}` : key);
    };
    walk(MESSAGES[locale], "");
  }
  assert.equal(MESSAGES.en.app.saved("a.hwp"), "Saved: a.hwp");
  assert.equal(MESSAGES.ko.common.page(3), "3쪽");
});

test("언어 고르기: 저장한 선택 → 운영체제 언어 → 한국어", () => {
  assert.equal(detectLocale("en", ["ko-KR"]), "en");
  assert.equal(detectLocale(null, ["en-US", "ko"]), "en");
  assert.equal(detectLocale(null, ["fr-FR", "ko-KR"]), "ko");
  assert.equal(detectLocale("zz", ["de"]), DEFAULT_LOCALE);
  assert.equal(detectLocale(null, []), DEFAULT_LOCALE);
});
