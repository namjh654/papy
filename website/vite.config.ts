import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig, type Plugin } from "vite";
import { PAGES, REPO_URL, SITE_URL, VERIFICATION } from "./site.config.ts";

const root = import.meta.dirname;
const partial = (name: string) => readFileSync(resolve(root, "partials", name), "utf8");
const escape = (s: string) => s.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");

/** 페이지마다 공통 head(SEO 메타)·헤더·푸터를 넣고, sitemap.xml·robots.txt 를 만든다. */
function site(): Plugin {
  return {
    name: "papy-site",
    transformIndexHtml: {
      // 다른 HTML 처리보다 먼저 partial 을 넣어야 Vite 가 그 안의 CSS·아이콘 경로도 처리한다.
      order: "pre",
      handler(html, ctx) {
      const file = ctx.filename.slice(root.length + 1).replace(/\\/g, "/");
      const page = PAGES.find((p) => p.file === file);
      if (!page) return html;
      const url = SITE_URL + page.path;
      const verify = [
        VERIFICATION.google && `<meta name="google-site-verification" content="${escape(VERIFICATION.google)}" />`,
        VERIFICATION.naver && `<meta name="naver-site-verification" content="${escape(VERIFICATION.naver)}" />`,
      ]
        .filter(Boolean)
        .join("\n    ");
      const nav = PAGES.filter((p) => p.nav)
        .map((p) => `<a href="${p.path}"${p === page ? ' aria-current="page"' : ""}>${p.nav}</a>`)
        .join("");
      const vars: Record<string, string> = {
        "{{head}}": partial("head.html"),
        "{{header}}": partial("header.html"),
        "{{footer}}": partial("footer.html"),
        "{{nav}}": nav,
        "{{verification}}": verify,
        "{{robots}}": page.index === false ? "noindex" : "index, follow",
        "{{title}}": escape(page.title),
        "{{description}}": escape(page.description),
        "{{url}}": url,
        "{{site}}": SITE_URL,
        "{{repo}}": REPO_URL,
        "{{year}}": String(new Date().getFullYear()),
      };
      // partial 안의 변수까지 바뀌도록 두 번 치환한다.
      for (let i = 0; i < 2; i++) html = html.replace(/\{\{\w+\}\}/g, (k) => vars[k] ?? k);
      return html;
      },
    },
    generateBundle() {
      const today = new Date().toISOString().slice(0, 10);
      const urls = PAGES.filter((p) => p.index !== false).map((p) => `  <url><loc>${SITE_URL}${p.path}</loc><lastmod>${today}</lastmod></url>`).join("\n");
      this.emitFile({
        type: "asset",
        fileName: "sitemap.xml",
        source: `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`,
      });
      this.emitFile({
        type: "asset",
        fileName: "robots.txt",
        source: `User-agent: *\nAllow: /\n\nSitemap: ${SITE_URL}/sitemap.xml\n`,
      });
    },
  };
}

export default defineConfig({
  plugins: [site()],
  build: {
    rollupOptions: {
      // 입력 이름: "index.html" → "home", "download/index.html" → "download"
      input: Object.fromEntries(
        PAGES.map((p) => [p.file.replace(/(\/index)?\.html$/, "").replace(/\//g, "-") || "home", resolve(root, p.file)]),
      ),
    },
  },
});
