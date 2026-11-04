# website — 소개·다운로드·설치·사용법 사이트

정적 멀티페이지 사이트(Vite). 페이지마다 완성된 HTML 이 나와 Google·네이버 검색 수집에 유리하다. 쿠키·분석 도구 없음.

| 경로 | 파일 | 내용 |
|---|---|---|
| `/` | `index.html` | 소개, 원칙, 지원 포맷 현황, FAQ (JSON-LD `SoftwareApplication`) |
| `/download/` | `download/index.html` + `src/download.ts` | OS 별 설치 파일 — GitHub Releases 최신 목록을 브라우저에서 읽어 표시, 접속한 OS 강조 |
| `/install/` | `install/index.html` | Windows·macOS·Linux 설치, 서명 없는 앱 보안 경고 해결, 업데이트·삭제 |
| `/usage/` | `usage/index.html` | 사용법. 동작하는 기능만 `사용 가능`, 나머지는 `준비 중` |
| `/privacy/` | `privacy/index.html` | 개인정보 처리방침 |

공통 head(SEO 메타·OG·canonical·hreflang)·헤더·푸터는 `partials/`, 페이지 목록은 `site.config.ts` 의 `PAGES`, 페이지 제목·설명·메뉴·공통 문구는 `i18n/<언어>.ts` 에서 관리한다.
빌드 시 `sitemap.xml`(언어별 대체 링크 포함)·`robots.txt` 를 자동 생성한다(`vite.config.ts`).

## 다국어

| 언어 | 주소 | 페이지 원본 | 문구 |
|---|---|---|---|
| 한국어(기본) | `/`, `/download/` … | `index.html`, `download/index.html` … | `i18n/ko.ts` |
| English | `/en/`, `/en/download/` … | `en/index.html`, `en/download/index.html` … | `i18n/en.ts` |

- 페이지 본문은 언어별 HTML 이고, 여러 페이지가 함께 쓰는 문구(메뉴·푸터·제목·설명·브라우저 스크립트 문구)는 `i18n/<언어>.ts` 에 있다. 모양은 `i18n/types.ts` 가 정해서 키가 빠지면 타입 검사가 실패한다.
- 페이지 안의 사이트 링크는 `{{base}}/download/` 처럼 쓴다(기본 언어는 `""`, 다른 언어는 `/en` 으로 바뀐다). `<html lang="{{lang}}">` 도 그대로 둔다.
- 브라우저 스크립트(`src/*.ts`)는 페이지에 들어간 `<script id="papy-i18n" type="application/json">` 에서 문구를 읽는다(`src/i18n.ts`).
- 404 는 Vercel 에 하나뿐이라 모든 언어 문구를 한 페이지에 싣는다(`i18n/<언어>.ts` 의 `notFound`).
- 언어를 자동으로 바꾸거나 다른 주소로 보내지 않는다. 머리글의 언어 메뉴로 고른다.

### 언어 추가 (예: 일본어 `ja`)

1. `i18n/en.ts` 를 `i18n/ja.ts` 로 복사해 번역한다(`name: "日本語"`, `htmlLang: "ja"`, `ogLocale: "ja_JP"`, `dateLocale: "ja-JP"`).
2. `i18n/index.ts` 의 `MESSAGES` 에 `ja` 를 넣는다(순서 = 언어 메뉴 순서).
3. `en/` 폴더를 `ja/` 로 복사해 본문을 번역한다.
4. `pnpm --filter @papy/website test` — 키 누락, 페이지 누락, 남은 자리표시자, 다른 언어로 새는 링크, 번역 안 된 한글, sitemap·hreflang 을 검사한다.

## 명령

```bash
pnpm --filter @papy/website dev      # http://localhost:5173
pnpm --filter @papy/website build    # → website/dist
pnpm --filter @papy/website test     # 빌드 + 다국어 검사
```

## 환경 변수 (Vercel Project → Settings → Environment Variables)

| 이름 | 용도 | 기본값 |
|---|---|---|
| `VITE_SITE_URL` | canonical·sitemap 의 도메인 | `https://papy-omega.vercel.app` |
| `VITE_GOOGLE_SITE_VERIFICATION` | Google Search Console HTML 태그 `content` 값 | 비어 있음 |
| `VITE_NAVER_SITE_VERIFICATION` | 네이버 서치어드바이저 HTML 태그 `content` 값 | 비어 있음 |
| `RESEND_API_KEY` | 문의 메일 발송(Resend, Sending access 권한). 서버 전용 | 없음 → 발송 실패 |
| `CONTACT_TO` | 문의 메일을 받을 주소. 서버 전용 | 없음 |
| `CONTACT_FROM` | 보내는 사람. 도메인 인증 전에는 비워 둔다 | `onboarding@resend.dev` |

로컬에서는 `.env.example` 을 `.env.local` 로 복사해 채운다(git 제외). `pnpm dev` 가 `api/contact.ts` 를 그대로 실행하며, 키가 없으면 메일을 보내지 않고 성공으로 답한다.

## 배포 (Vercel)

- Vercel 프로젝트 `papy` (https://papy-omega.vercel.app — `papy.vercel.app` 은 이미 다른 사람이 사용 중), Root Directory = `website`, Framework = Vite, Node 24.
- Install: `pnpm install --frozen-lockfile --filter "@papy/website..."`, Build: `pnpm build`, Output: `dist`.
- 저장소 루트 `.vercelignore` 로 앱·엔진 폴더는 올리지 않는다. 보안 헤더·CSP·캐시는 `vercel.json`.
- GitHub `namjh654/papy` 와 연결돼 있다. **main 에 합치면 운영(papy-omega.vercel.app)에 자동 배포**되고, 다른 브랜치·PR 은 미리보기 배포가 만들어진다.
- 급히 손으로 배포해야 할 때만 저장소 루트에서 Vercel CLI 를 쓴다:

  ```bash
  vercel link --yes --project papy   # 처음 한 번 (.vercel/ 는 git 제외)
  vercel deploy --prod --yes          # 운영 배포
  ```

## 검색 등록

1. Google Search Console: URL 접두어 속성으로 사이트 추가 → HTML 태그 방식 → `content` 값을 `VITE_GOOGLE_SITE_VERIFICATION` 에 넣고 재배포 → 확인 → Sitemaps 에 `sitemap.xml` 제출.
2. 네이버 서치어드바이저: 웹마스터 도구 → 사이트 등록 → HTML 태그 방식 → `VITE_NAVER_SITE_VERIFICATION` → 재배포 → 확인 → 요청 → 사이트맵 제출(`sitemap.xml`), RSS 는 없음.
