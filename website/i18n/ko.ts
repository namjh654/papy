import type { Messages } from "./types.ts";

export const ko: Messages = {
  name: "한국어",
  htmlLang: "ko",
  ogLocale: "ko_KR",
  dateLocale: "ko-KR",
  site: {
    name: "Papy (페이피)",
    brandSub: "페이피",
    skip: "본문 바로가기",
    home: "Papy 홈",
    mainNav: "주 메뉴",
    language: "언어",
    licenseNote: "비상업적 용도 무료",
    github: "GitHub",
    editPage: "이 페이지 수정 제안",
    privacy: "개인정보 처리방침",
  },
  pages: {
    home: {
      title: "Papy (페이피) — 무료·오프라인 PDF·HWP·DOCX 문서 편집기",
      description:
        "Papy(페이피)는 PDF·한글(HWP/HWPX)·Word(DOCX) 문서를 보고 편집하는 소스 공개 문서 앱으로, 개인·비상업 용도는 무료입니다. 보기·편집 모드 전환, 저장·형식 변환을 회원가입·광고·서버 업로드 없이 내 컴퓨터에서 합니다. Windows·macOS·Linux.",
      nav: "소개",
    },
    download: {
      title: "다운로드 — Papy (페이피)",
      description: "Papy 문서 뷰어를 Windows, macOS, Linux 용으로 무료로 내려받으세요. 모든 설치 파일은 GitHub Releases 에서 공개 빌드됩니다.",
      nav: "다운로드",
    },
    install: {
      title: "설치 방법 — Papy (페이피)",
      description: "Windows(.msi/.exe), macOS(.dmg), Linux(.AppImage/.deb) 별 Papy 설치 방법과 보안 경고가 뜰 때 여는 방법을 안내합니다.",
      nav: "설치 방법",
    },
    usage: {
      title: "사용법 — Papy (페이피)",
      description: "Papy 로 문서를 열고, 편집 모드로 고치고, 저장·다른 이름으로 저장·형식 변환하는 방법과 지원 범위를 안내합니다.",
      nav: "사용법",
    },
    developers: {
      title: "개발자 — Papy (페이피) 기여하기",
      description: "Papy 소스 코드에 기여하는 방법: GitHub 저장소, 이슈 등록, Fork·브랜치·Pull Request 절차, 개발 환경과 저장소 구조를 안내합니다.",
      nav: "개발자",
    },
    contact: {
      title: "문의 — Papy (페이피)",
      description: "Papy 개발자에게 버그 신고, 기능 제안, 사용·상업적 이용 문의를 바로 보낼 수 있습니다.",
      nav: "문의",
    },
    privacy: {
      title: "개인정보 처리방침 — Papy (페이피)",
      description: "Papy 는 계정·광고·추적 없이 동작하며 문서를 서버로 보내지 않습니다. 앱과 이 웹사이트가 처리하는 정보를 설명합니다.",
    },
    notFound: {
      title: "페이지를 찾을 수 없습니다 — Papy (페이피)",
      description: "요청한 페이지가 없습니다.",
    },
  },
  notFound: {
    heading: "페이지를 찾을 수 없습니다",
    body: "주소가 바뀌었거나 없는 페이지입니다.",
    home: "홈으로",
    download: "다운로드",
  },
  client: {
    contact: {
      sent: "문의를 보냈습니다. 확인 후 적어 주신 이메일로 답장드리겠습니다.",
      failed: "보내지 못했습니다. 잠시 후 다시 시도하거나 GitHub Issues 를 이용해 주세요.",
      errors: {
        forbidden: "허용되지 않은 요청입니다.",
        badRequest: "요청 형식이 올바르지 않습니다.",
        type: "문의 유형을 골라 주세요.",
        email: "답장 받을 이메일 주소를 확인해 주세요.",
        message: "내용을 {min}자 이상 적어 주세요.",
      },
    },
    download: {
      latest: "최신 버전: {name}{date}",
      prerelease: " (미리보기 빌드 — 아직 문서를 열 수 없습니다)",
      none: "아직 공개된 설치 파일이 없습니다. 첫 빌드가 올라오면 이곳에 자동으로 표시됩니다. {link}",
      failed: "버전 정보를 불러오지 못했습니다. {link}에서 직접 받아 주세요.",
      viewOnGithub: "GitHub Releases 에서 보기",
      labels: {
        exe: "설치 프로그램 (.exe, 권장)",
        msi: "MSI 패키지",
        dmgArm: "Apple Silicon (.dmg)",
        dmgIntel: "Intel (.dmg)",
        dmgUniversal: "Universal (.dmg)",
        appImage: "AppImage",
        deb: "Debian/Ubuntu (.deb)",
        rpm: "Fedora/openSUSE (.rpm)",
      },
    },
    developers: {
      empty: "지금은 없습니다.",
    },
  },
};
