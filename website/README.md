# website — 소개·다운로드·설치·사용법 사이트

정적 멀티페이지 사이트(Vite). 페이지마다 완성된 HTML 이 나와 Google·네이버 검색 수집에 유리하다. 쿠키·분석 도구 없음.

| 경로 | 파일 | 내용 |
|---|---|---|
| `/` | `index.html` | 소개, 원칙, 지원 포맷 현황, FAQ (JSON-LD `SoftwareApplication`) |
| `/download/` | `download/index.html` + `src/download.ts` | OS 별 설치 파일 — GitHub Releases 최신 목록을 브라우저에서 읽어 표시, 접속한 OS 강조 |
| `/install/` | `install/index.html` | Windows·macOS·Linux 설치, 서명 없는 앱 보안 경고 해결, 업데이트·삭제 |
| `/usage/` | `usage/index.html` | 사용법. 동작하는 기능만 `사용 가능`, 나머지는 `준비 중` |
| `/privacy/` | `privacy/index.html` | 개인정보 처리방침 |

공통 head(SEO 메타·OG·canonical)·헤더·푸터는 `partials/`, 페이지 제목·설명은 `site.config.ts` 의 `PAGES` 한 곳에서 관리한다.
빌드 시 `sitemap.xml`·`robots.txt` 를 자동 생성한다(`vite.config.ts`).

## 명령

```bash
pnpm --filter @papy/website dev      # http://localhost:5173
pnpm --filter @papy/website build    # → website/dist
```

## 환경 변수 (Vercel Project → Settings → Environment Variables)

| 이름 | 용도 | 기본값 |
|---|---|---|
| `VITE_SITE_URL` | canonical·sitemap 의 도메인 | `https://papy-omega.vercel.app` |
| `VITE_GOOGLE_SITE_VERIFICATION` | Google Search Console HTML 태그 `content` 값 | 비어 있음 |
| `VITE_NAVER_SITE_VERIFICATION` | 네이버 서치어드바이저 HTML 태그 `content` 값 | 비어 있음 |

## 배포 (Vercel)

- Vercel 프로젝트 `papy` (https://papy-omega.vercel.app — `papy.vercel.app` 은 이미 다른 사람이 사용 중), Root Directory = `website`, Framework = Vite, Node 24.
- Install: `pnpm install --frozen-lockfile --filter "@papy/website..."`, Build: `pnpm build`, Output: `dist`.
- 저장소 루트 `.vercelignore` 로 앱·엔진 폴더는 올리지 않는다. 보안 헤더·CSP·캐시는 `vercel.json`.

## 검색 등록

1. Google Search Console: URL 접두어 속성으로 사이트 추가 → HTML 태그 방식 → `content` 값을 `VITE_GOOGLE_SITE_VERIFICATION` 에 넣고 재배포 → 확인 → Sitemaps 에 `sitemap.xml` 제출.
2. 네이버 서치어드바이저: 웹마스터 도구 → 사이트 등록 → HTML 태그 방식 → `VITE_NAVER_SITE_VERIFICATION` → 재배포 → 확인 → 요청 → 사이트맵 제출(`sitemap.xml`), RSS 는 없음.
