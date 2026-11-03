import { existsSync, readFileSync } from "node:fs";
import type { IncomingMessage } from "node:http";
import { resolve } from "node:path";
import { defineConfig, loadEnv, type Plugin } from "vite";
import { DEFAULT_LOCALE, LOCALES, MESSAGES, localeBase, localeDir, type Locale } from "./i18n/index.ts";
import { PAGES, REPO_URL, SITE_URL, VERIFICATION, type Page } from "./site.config.ts";

const root = import.meta.dirname;
const partial = (name: string) => readFileSync(resolve(root, "partials", name), "utf8");
const escape = (s: string) => s.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");

/** 페이지·언어별 빌드 입력 파일. shared 페이지는 하나뿐이다. */
const pageFiles = (page: Page) =>
  page.shared ? [{ locale: DEFAULT_LOCALE as Locale, file: page.file }] : LOCALES.map((locale) => ({ locale, file: localeDir(locale) + page.file }));

/** HTML 파일(website/ 기준) → 페이지와 언어 */
function lookup(file: string): { page: Page; locale: Locale } | undefined {
  for (const page of PAGES) {
    const hit = pageFiles(page).find((entry) => entry.file === file);
    if (hit) return { page, locale: hit.locale };
  }
  return undefined;
}

const pageUrl = (page: Page, locale: Locale) => SITE_URL + localeBase(locale) + page.path;

/** "site.name" 같은 점 경로로 문구를 찾는다. */
function message(locale: Locale, path: string): string | undefined {
  let value: unknown = MESSAGES[locale];
  for (const key of path.split(".")) value = (value as Record<string, unknown> | undefined)?.[key];
  return typeof value === "string" ? value : undefined;
}

function render(html: string, page: Page, locale: Locale, file: string): string {
  const t = MESSAGES[locale];
  const text = t.pages[page.id];
  const base = localeBase(locale);
  const url = pageUrl(page, locale);
  const verify = [
    VERIFICATION.google && `<meta name="google-site-verification" content="${escape(VERIFICATION.google)}" />`,
    VERIFICATION.naver && `<meta name="naver-site-verification" content="${escape(VERIFICATION.naver)}" />`,
  ]
    .filter(Boolean)
    .join("\n    ");
  const nav = PAGES.filter((p) => t.pages[p.id].nav)
    .map((p) => `<a href="${base}${p.path}"${p === page ? ' aria-current="page"' : ""}>${escape(t.pages[p.id].nav!)}</a>`)
    .join("");
  // 공유 페이지(404)는 각 언어의 홈으로 보낸다.
  const target = (other: Locale) => localeBase(other) + (page.shared ? "/" : page.path);
  const languages = LOCALES.map(
    (other) =>
      `<a href="${target(other)}" hreflang="${MESSAGES[other].htmlLang}" lang="${MESSAGES[other].htmlLang}"${
        other === locale && !page.shared ? ' aria-current="true"' : ""
      }>${escape(MESSAGES[other].name)}</a>`,
  ).join("");
  const alternates =
    page.index === false
      ? ""
      : [
          ...LOCALES.map((other) => `<link rel="alternate" hreflang="${MESSAGES[other].htmlLang}" href="${pageUrl(page, other)}" />`),
          `<link rel="alternate" hreflang="x-default" href="${pageUrl(page, DEFAULT_LOCALE)}" />`,
        ].join("\n    ");
  const ogAlternates = LOCALES.filter((other) => other !== locale)
    .map((other) => `<meta property="og:locale:alternate" content="${MESSAGES[other].ogLocale}" />`)
    .join("\n    ");
  // 브라우저 스크립트용 문구. type=application/json 이라 실행되지 않으므로 CSP(script-src 'self')에 걸리지 않는다.
  const client = JSON.stringify(t.client).replace(/</g, "\\u003c");
  const notFound = LOCALES.map((other) => {
    const n = MESSAGES[other].notFound;
    const b = localeBase(other);
    return `<section lang="${MESSAGES[other].htmlLang}" class="not-found">
        <h1>${escape(n.heading)}</h1>
        <p class="lead">${escape(n.body)}</p>
        <div class="actions"><a class="btn primary" href="${b}/">${escape(n.home)}</a><a class="btn" href="${b}/download/">${escape(n.download)}</a></div>
      </section>`;
  }).join("\n      ");

  const vars: Record<string, string> = {
    head: partial("head.html"),
    header: partial("header.html"),
    footer: partial("footer.html"),
    lang: t.htmlLang,
    locale,
    base,
    nav,
    languages,
    alternates,
    ogLocale: t.ogLocale,
    ogAlternates,
    i18n: `<script type="application/json" id="papy-i18n">${client}</script>`,
    brandSub: t.site.brandSub ? ` <small>${escape(t.site.brandSub)}</small>` : "",
    notFound,
    verification: verify,
    robots: page.index === false ? "noindex" : "index, follow",
    title: escape(text.title),
    description: escape(text.description),
    url,
    site: SITE_URL,
    repo: REPO_URL,
    edit: `${REPO_URL}/edit/main/website/${file}`,
    year: String(new Date().getFullYear()),
  };
  // partial 안의 변수까지 바뀌도록 두 번 치환한다. {{t.site.name}} 은 문구 파일에서 찾는다.
  for (let i = 0; i < 2; i++) {
    html = html.replace(/\{\{([\w.]+)\}\}/g, (all, key: string) =>
      key.startsWith("t.") ? (escape(message(locale, key.slice(2)) ?? all)) : (vars[key] ?? all),
    );
  }
  const left = html.match(/\{\{[\w.]+\}\}/);
  if (left) throw new Error(`${file}: 바꾸지 못한 자리표시자 ${left[0]}`);
  return html;
}

/** 페이지마다 공통 head(SEO·hreflang)·헤더·푸터·언어 선택을 넣고, sitemap.xml·robots.txt 를 만든다. */
function site(): Plugin {
  return {
    name: "papy-site",
    transformIndexHtml: {
      // 다른 HTML 처리보다 먼저 partial 을 넣어야 Vite 가 그 안의 CSS·아이콘 경로도 처리한다.
      order: "pre",
      handler(html, ctx) {
        const file = ctx.filename.slice(root.length + 1).replace(/\\/g, "/");
        const hit = lookup(file);
        return hit ? render(html, hit.page, hit.locale, file) : html;
      },
    },
    generateBundle() {
      const today = new Date().toISOString().slice(0, 10);
      const urls = PAGES.filter((p) => p.index !== false)
        .flatMap((p) =>
          LOCALES.map((locale) => {
            const links = [...LOCALES.map((other) => [MESSAGES[other].htmlLang, pageUrl(p, other)]), ["x-default", pageUrl(p, DEFAULT_LOCALE)]]
              .map(([lang, href]) => `\n    <xhtml:link rel="alternate" hreflang="${lang}" href="${href}" />`)
              .join("");
            return `  <url>\n    <loc>${pageUrl(p, locale)}</loc>\n    <lastmod>${today}</lastmod>${links}\n  </url>`;
          }),
        )
        .join("\n");
      this.emitFile({
        type: "asset",
        fileName: "sitemap.xml",
        source: `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n${urls}\n</urlset>\n`,
      });
      this.emitFile({
        type: "asset",
        fileName: "robots.txt",
        source: `User-agent: *\nAllow: /\n\nSitemap: ${SITE_URL}/sitemap.xml\n`,
      });
    },
  };
}

/** 서버에서만 쓰는 환경 변수. 브라우저 번들에는 VITE_ 로 시작하는 값만 들어간다. */
const SERVER_ENV = ["RESEND_API_KEY", "CONTACT_TO", "CONTACT_FROM", "ALLOWED_ORIGINS"] as const;

async function toRequest(req: IncomingMessage): Promise<Request> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  const headers = new Headers();
  for (const [key, value] of Object.entries(req.headers)) {
    if (typeof value === "string") headers.set(key, value);
    else if (Array.isArray(value)) value.forEach((v) => headers.append(key, v));
  }
  const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);
  const hasBody = req.method !== "GET" && req.method !== "HEAD";
  return new Request(url, { method: req.method, headers, body: hasBody ? Buffer.concat(chunks) : undefined });
}

/**
 * 개발 서버(pnpm dev)에서 Vercel 함수 api/contact.ts 를 그대로 돌린다. 배포에는 들어가지 않는다.
 * 키는 website/.env.local 에서 읽는다(git 제외). RESEND_API_KEY 가 없으면 실제로 보내지 않고 성공으로 답한다.
 */
function devApi(): Plugin {
  return {
    name: "papy-dev-api",
    apply: "serve",
    configureServer(server) {
      const env = loadEnv(server.config.mode, root, "");
      for (const name of SERVER_ENV) if (env[name] && !process.env[name]) process.env[name] = env[name];
      const dryRun = !process.env.RESEND_API_KEY || !process.env.CONTACT_TO;
      if (dryRun) server.config.logger.warn("[papy] RESEND_API_KEY/CONTACT_TO 없음 — 문의 폼은 메일을 보내지 않고 성공으로 답합니다.");
      server.middlewares.use("/api/contact", async (req, res) => {
        try {
          if (req.method !== "POST") {
            res.statusCode = 405;
            res.end();
            return;
          }
          let response: Response;
          if (dryRun) {
            response = new Response(JSON.stringify({ ok: true }), { headers: { "Content-Type": "application/json" } });
          } else {
            const mod = (await server.ssrLoadModule("/api/contact.ts")) as { POST(r: Request): Promise<Response> };
            response = await mod.POST(await toRequest(req));
          }
          res.statusCode = response.status;
          response.headers.forEach((value, key) => res.setHeader(key, value));
          res.end(Buffer.from(await response.arrayBuffer()));
        } catch {
          // 문의 내용은 로그에 남기지 않는다.
          server.config.logger.error("[papy] /api/contact 처리 실패");
          res.statusCode = 500;
          res.end();
        }
      });
    },
  };
}

export default defineConfig({
  plugins: [site(), devApi()],
  build: {
    rollupOptions: {
      // 입력 이름: "index.html" → "home", "en/download/index.html" → "en-download"
      input: Object.fromEntries(
        PAGES.flatMap(pageFiles).map(({ file }) => {
          const path = resolve(root, file);
          if (!existsSync(path)) throw new Error(`${file} 가 없습니다. 언어를 추가했다면 기본 언어 페이지를 복사해 번역하세요.`);
          return [file.replace(/(\/?index)?\.html$/, "").replace(/\//g, "-") || "home", path];
        }),
      ),
    },
  },
});
