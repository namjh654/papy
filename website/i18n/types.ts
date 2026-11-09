// 사이트 문구의 모양. 모든 언어 파일(ko.ts, en.ts …)이 이 모양을 그대로 채워야 타입 검사를 통과한다.
// 페이지 본문은 언어별 HTML(website/<언어>/…)에 있고, 여기에는 여러 페이지가 함께 쓰는 문구만 둔다.

/** site.config.ts 의 PAGES 와 같은 id */
export type PageId = "home" | "download" | "install" | "usage" | "developers" | "contact" | "privacy" | "notFound";

export interface PageText {
  title: string;
  description: string;
  /** 상단 메뉴 이름. 없으면 메뉴에 넣지 않는다 */
  nav?: string;
}

/** 브라우저 스크립트(src/*.ts)가 쓰는 문구. 페이지에 JSON 으로 들어간다. {name} 은 자리표시자 */
export interface ClientText {
  contact: {
    sent: string;
    failed: string;
    errors: {
      forbidden: string;
      badRequest: string;
      type: string;
      email: string;
      /** {min} = 최소 글자 수 */
      message: string;
    };
  };
  download: {
    /** {name} 버전 이름, {date} 날짜 */
    latest: string;
    prerelease: string;
    /** {link} = Releases 링크 */
    none: string;
    failed: string;
    viewOnGithub: string;
    labels: {
      exe: string;
      msi: string;
      dmgArm: string;
      dmgIntel: string;
      dmgUniversal: string;
      appImage: string;
      deb: string;
      rpm: string;
    };
  };
  developers: {
    empty: string;
  };
}

export interface Messages {
  /** 언어 선택 메뉴에 그 언어로 쓰는 이름 (예: "English", "한국어") */
  name: string;
  /** <html lang>, hreflang 값 (BCP 47) */
  htmlLang: string;
  /** og:locale (예: ko_KR) */
  ogLocale: string;
  /** 날짜 표시에 쓰는 로캘 (예: ko-KR) */
  dateLocale: string;
  site: {
    name: string;
    /** 로고 옆 작은 글씨. 없으면 "" */
    brandSub: string;
    skip: string;
    home: string;
    mainNav: string;
    language: string;
    licenseNote: string;
    legal: string;
    github: string;
    editPage: string;
    privacy: string;
  };
  pages: Record<PageId, PageText>;
  /** 404 페이지(모든 언어가 한 페이지에 함께 나온다) */
  notFound: { heading: string; body: string; home: string; download: string };
  client: ClientText;
}
