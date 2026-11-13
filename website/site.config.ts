/** 사이트 공통 값. 도메인을 바꾸면 VITE_SITE_URL 만 바꾼다(.env 또는 Vercel 환경 변수). */
import type { PageId } from "./i18n/index.ts";

export const SITE_URL = (process.env.VITE_SITE_URL ?? "https://papy-omega.vercel.app").replace(/\/$/, "");

export const REPO_URL = "https://github.com/namjh654/papy";

/** 검색엔진 소유 확인 값. Google Search Console / 네이버 서치어드바이저에서 받은 content 값을 환경 변수로 넣는다. */
export const VERIFICATION = {
  google: process.env.VITE_GOOGLE_SITE_VERIFICATION ?? "",
  naver: process.env.VITE_NAVER_SITE_VERIFICATION ?? "",
};

export interface Page {
  id: PageId;
  /** 기본 언어의 페이지 파일(website/ 기준). 다른 언어는 앞에 "<언어>/" 가 붙는다 */
  file: string;
  /** 기본 언어의 공개 경로. 다른 언어는 앞에 "/<언어>" 가 붙는다 */
  path: string;
  /** false 면 sitemap 에서 빼고 noindex 로 둔다 */
  index?: boolean;
  /** true 면 언어별 파일 없이 하나만 둔다(모든 언어 문구를 한 페이지에 싣는다). Vercel 의 404 는 하나뿐이다 */
  shared?: boolean;
}

/** 페이지 목록. 순서대로 상단 메뉴에 나온다(메뉴 이름은 i18n/<언어>.ts 의 pages.<id>.nav). */
export const PAGES: Page[] = [
  { id: "home", file: "index.html", path: "/" },
  { id: "download", file: "download/index.html", path: "/download/" },
  { id: "install", file: "install/index.html", path: "/install/" },
  { id: "usage", file: "usage/index.html", path: "/usage/" },
  { id: "developers", file: "developers/index.html", path: "/developers/" },
  { id: "contact", file: "contact/index.html", path: "/contact/" },
  { id: "privacy", file: "privacy/index.html", path: "/privacy/" },
  { id: "stats", file: "stats/index.html", path: "/stats/" },
  { id: "notFound", file: "404.html", path: "/404", index: false, shared: true },
];
