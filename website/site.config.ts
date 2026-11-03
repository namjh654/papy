/** 사이트 공통 값. 도메인을 바꾸면 VITE_SITE_URL 만 바꾼다(.env 또는 Vercel 환경 변수). */
export const SITE_URL = (process.env.VITE_SITE_URL ?? "https://papy.vercel.app").replace(/\/$/, "");

export const REPO_URL = "https://github.com/namjh654/papy";

/** 검색엔진 소유 확인 값. Google Search Console / 네이버 서치어드바이저에서 받은 content 값을 환경 변수로 넣는다. */
export const VERIFICATION = {
  google: process.env.VITE_GOOGLE_SITE_VERIFICATION ?? "",
  naver: process.env.VITE_NAVER_SITE_VERIFICATION ?? "",
};

export interface Page {
  /** 빌드 입력 HTML (website/ 기준) */
  file: string;
  /** 공개 경로 */
  path: string;
  title: string;
  description: string;
  /** 상단 메뉴 표시 이름. 없으면 메뉴에 넣지 않는다 */
  nav?: string;
  /** false 면 sitemap 에서 빼고 noindex 로 둔다 */
  index?: boolean;
}

export const PAGES: Page[] = [
  {
    file: "index.html",
    path: "/",
    title: "Papy (페이피) — 무료·오프라인 PDF·HWPX 문서 뷰어",
    description:
      "Papy(페이피)는 PDF와 한글(HWP/HWPX) 문서를 우선 지원하는 무료 오픈소스 문서 뷰어입니다. 회원가입·광고·서버 업로드 없이 내 컴퓨터에서 바로 엽니다. Windows·macOS·Linux 지원 예정.",
    nav: "소개",
  },
  {
    file: "download/index.html",
    path: "/download/",
    title: "다운로드 — Papy (페이피)",
    description: "Papy 문서 뷰어를 Windows, macOS, Linux 용으로 무료로 내려받으세요. 모든 설치 파일은 GitHub Releases 에서 공개 빌드됩니다.",
    nav: "다운로드",
  },
  {
    file: "install/index.html",
    path: "/install/",
    title: "설치 방법 — Papy (페이피)",
    description: "Windows(.msi/.exe), macOS(.dmg), Linux(.AppImage/.deb) 별 Papy 설치 방법과 보안 경고가 뜰 때 여는 방법을 안내합니다.",
    nav: "설치 방법",
  },
  {
    file: "usage/index.html",
    path: "/usage/",
    title: "사용법 — Papy (페이피)",
    description: "Papy 로 문서를 여는 방법, 화면 구성, 지원 포맷과 현재 개발 상태를 안내합니다.",
    nav: "사용법",
  },
  {
    file: "privacy/index.html",
    path: "/privacy/",
    title: "개인정보 처리방침 — Papy (페이피)",
    description: "Papy 는 계정·광고·추적 없이 동작하며 문서를 서버로 보내지 않습니다. 앱과 이 웹사이트가 처리하는 정보를 설명합니다.",
  },
  {
    file: "404.html",
    path: "/404",
    title: "페이지를 찾을 수 없습니다 — Papy (페이피)",
    description: "요청한 페이지가 없습니다.",
    index: false,
  },
];
