// 다국어 검사: 언어 파일의 키가 모두 같은지, 페이지가 언어마다 있는지, 빌드 결과가 언어별로 맞는지.
// 실행: pnpm --filter @papy/website test (빌드 후 dist/ 를 검사한다)
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import { DEFAULT_LOCALE, LOCALES, MESSAGES, localeBase, localeDir, type Locale } from "../i18n/index.ts";
import { PAGES } from "../site.config.ts";

const root = resolve(import.meta.dirname, "..");
const dist = resolve(root, "dist");
const read = (path: string) => readFileSync(path, "utf8");

/** 객체의 모든 잎 경로 ("site.name" …) */
function keys(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) return [prefix];
  return Object.entries(value).flatMap(([key, child]) => keys(child, prefix ? `${prefix}.${key}` : key)).sort();
}

/** 빌드된 페이지 파일 */
const built = (locale: Locale, file: string) => resolve(dist, localeDir(locale) + file);

const translatedPages = PAGES.filter((page) => !page.shared);
const others = LOCALES.filter((locale) => locale !== DEFAULT_LOCALE);

/** 영어 등 한글이 아닌 페이지에 남아도 되는 한글: 언어 선택 메뉴의 "한국어", 검색용 다른 이름 "페이피" */
const HANGUL_ALLOWED = (locale: Locale) => [MESSAGES.ko.name, "페이피"].filter(() => locale !== "ko");

test("모든 언어 파일의 키가 기본 언어와 같다", () => {
  const expected = keys(MESSAGES[DEFAULT_LOCALE]);
  for (const locale of LOCALES) assert.deepEqual(keys(MESSAGES[locale]), expected, `${locale} 의 문구 키가 다르다`);
});

test("빈 문구가 없다 (brandSub 제외)", () => {
  for (const locale of LOCALES) {
    for (const key of keys(MESSAGES[locale])) {
      if (key === "site.brandSub" || key.endsWith(".nav")) continue;
      let value: unknown = MESSAGES[locale];
      for (const part of key.split(".")) value = (value as Record<string, unknown>)[part];
      assert.ok(typeof value === "string" && value.trim(), `${locale}: ${key} 가 비었다`);
    }
  }
});

test("언어마다 모든 페이지 원본이 있다", () => {
  for (const locale of LOCALES) {
    for (const page of translatedPages) assert.ok(existsSync(resolve(root, localeDir(locale) + page.file)), `${localeDir(locale)}${page.file} 가 없다`);
  }
});

test("문의 함수의 기본 언어가 사이트 설정과 같다", () => {
  assert.match(read(resolve(root, "api/contact.ts")), new RegExp(`const DEFAULT_LOCALE = "${DEFAULT_LOCALE}"`));
});

test("빌드된 페이지: lang·canonical·hreflang·언어 메뉴", () => {
  assert.ok(existsSync(dist), "먼저 빌드한다 (pnpm build)");
  for (const page of translatedPages) {
    for (const locale of LOCALES) {
      const html = read(built(locale, page.file));
      const t = MESSAGES[locale];
      const where = `${localeDir(locale)}${page.file}`;
      assert.match(html, new RegExp(`<html lang="${t.htmlLang}">`), `${where}: html lang`);
      assert.ok(html.includes(`<title>${t.pages[page.id].title.replace(/&/g, "&amp;")}</title>`), `${where}: title`);
      assert.ok(html.includes(`<link rel="canonical" href="`) && html.includes(`${localeBase(locale)}${page.path}" />`), `${where}: canonical`);
      for (const other of LOCALES) {
        assert.ok(html.includes(`hreflang="${MESSAGES[other].htmlLang}" href="`), `${where}: hreflang ${other}`);
        assert.ok(html.includes(`<a href="${localeBase(other)}${page.path}" hreflang="${MESSAGES[other].htmlLang}"`), `${where}: 언어 메뉴 ${other}`);
      }
      assert.ok(html.includes('hreflang="x-default"'), `${where}: x-default`);
      assert.ok(html.includes(`<meta property="og:locale" content="${t.ogLocale}" />`), `${where}: og:locale`);
      assert.doesNotMatch(html, /\{\{[\w.]+\}\}/, `${where}: 남은 자리표시자`);
      // 브라우저 스크립트용 문구가 그 언어로 들어갔다.
      const json = html.match(/<script type="application\/json" id="papy-i18n">(.*?)<\/script>/s)?.[1];
      assert.ok(json, `${where}: papy-i18n`);
      assert.deepEqual(JSON.parse(json), t.client, `${where}: papy-i18n 내용`);
    }
  }
});

test("다른 언어 페이지의 사이트 안 링크는 그 언어 주소로 간다", () => {
  for (const locale of others) {
    for (const page of translatedPages) {
      const html = read(built(locale, page.file));
      for (const [, href, rest] of html.matchAll(/<a href="(\/[^"]*)"([^>]*)>/g)) {
        // 다른 언어로 가는 링크(언어 메뉴, 원문 안내)는 hreflang 을 단다.
        if (/hreflang=/.test(rest!)) continue;
        assert.ok(href!.startsWith(`/${locale}/`), `${localeDir(locale)}${page.file}: ${href} 는 /${locale}/ 로 시작해야 한다`);
      }
    }
  }
});

test("한글이 아닌 페이지에 번역되지 않은 한글이 없다", () => {
  for (const locale of others) {
    for (const page of translatedPages) {
      let html = read(built(locale, page.file));
      for (const allowed of HANGUL_ALLOWED(locale)) html = html.replaceAll(allowed, "");
      const left = html.match(/[가-힣][^<"]{0,30}/);
      assert.equal(left, null, `${localeDir(locale)}${page.file}: 번역되지 않은 글 "${left?.[0]}"`);
    }
  }
});

test("404 는 하나이고 모든 언어 문구가 들어 있다", () => {
  const html = read(resolve(dist, "404.html"));
  for (const locale of LOCALES) {
    assert.ok(html.includes(MESSAGES[locale].notFound.heading), `404: ${locale} 제목`);
    assert.ok(html.includes(`<a class="btn primary" href="${localeBase(locale)}/">`), `404: ${locale} 홈 링크`);
  }
  assert.match(html, /noindex/);
});

test("sitemap 에 모든 언어 주소와 hreflang 대체 링크가 있다", () => {
  const xml = read(resolve(dist, "sitemap.xml"));
  for (const page of PAGES.filter((p) => p.index !== false)) {
    for (const locale of LOCALES) assert.ok(xml.includes(`${localeBase(locale)}${page.path}</loc>`), `sitemap: ${locale} ${page.path}`);
  }
  assert.ok(xml.includes('hreflang="x-default"'));
  assert.ok(!xml.includes("/404"), "404 는 sitemap 에서 뺀다");
});
