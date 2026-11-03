# 0005. HWP/HWPX 편집기: rhwp-studio(@rhwp/editor) 도입 검토

- 상태: **승인 (2026-11-03) — B 안 PoC 완료, HWP/HWPX "고급 편집"으로 도입**
- 조사일: 2026-11-03 (rhwp v0.8.7 커밋 `1a76570e` 소스 정적 분석, npm 레지스트리)

## 배경

Papy 는 지금 HWP/HWPX 편집을 직접 만든 편집 화면(커서·입력·서식, `apps/desktop/src/editor`)으로 한다.
엔진은 rhwp 의 편집 API 를 쓰지만, 표 셀·그림·머리말/꼬리말·각주·찾아 바꾸기 같은 편집 화면은 아직 없다.
rhwp 저장소에는 같은 엔진으로 만든 완성형 웹 편집기 **rhwp-studio** 와, 그것을 페이지에 넣는 SDK
**@rhwp/editor** 가 있다.

## 조사 결과

| 항목 | 내용 |
|---|---|
| 구성 | rhwp-studio = TypeScript 약 11.6만 줄(321개 파일) + rhwp WASM. @rhwp/editor = iframe 으로 studio 를 띄우고 `postMessage`/`MessageChannel` 로 조종하는 SDK(`loadFile`, `exportBytes`, `commands.execute`, HwpCtrl 호환 API, undo) |
| 라이선스 | studio·SDK·@rhwp/core 모두 MIT. studio 의존성: canvaskit-wasm(BSD-3-Clause), @noble/hashes(MIT), pixelmatch(ISC), pngjs(MIT). 번들 글꼴: Noto Sans/Serif KR·Pretendard·D2Coding(OFL). **함초롬체 CDN 은 비상업 조건 + 외부 접속** → `RHWP_DISABLE_EXTERNAL_WEBFONTS=1` 빌드로 끈다 |
| 기능 | 메뉴·툴바·상태 표시줄, 표·셀 편집, 개체 선택, 머리말/꼬리말, 찾기, 클립보드, 한글 입력기(compositionstart/end 처리), undo 계약, 암호 HWP, 저장 시 내용 손실 보고. e2e 테스트 수십 개 |
| 크기 | WASM 11.8MB(`@rhwp/core` 0.8.7), 런타임 글꼴 약 21MB, studio 정적 자원 약 8MB → **설치 파일 +40MB 안팎**(현재 앱 약 3MB + PDFium 약 6MB). canvaskit 렌더러를 넣으면 +수십 MB(선택 사항, 기본 canvas2d) |
| 실행 환경 제약 | ① SDK·studio 둘 다 부모 origin 이 `http:`/`https:` 일 때만 연결(`embed/protocol.ts` `isUsableParentOrigin`). Tauri 는 Windows 만 `http://tauri.localhost`, **macOS·Linux 는 `tauri://localhost`** 라 그대로는 거절된다. ② 파일 열기·저장은 File System Access API(Chromium 전용) — macOS WKWebView·Linux WebKitGTK 에는 없어 내려받기 방식으로 떨어진다 → 파일 입출력은 Papy 호스트가 맡아야 한다 |
| 엔진 이중화 | 보기·PDF 내보내기·변환은 네이티브 rhwp(Rust), 편집은 WASM rhwp 가 된다. 같은 커밋으로 고정하면 조판은 같다. 편집 결과는 `exportBytes()` 로 받아 호스트가 저장하고 네이티브 세션을 다시 연다 |
| 디자인 | 한컴 오피스 풍 UI. `chrome:{ menu:false, statusbar:false }` 로 줄일 수 있지만 Papy 디자인과 완전히 같지는 않다 |
| 모바일 | WebView 기반이라 KMP(Android WebView·iOS WKWebView)에서도 같은 편집기를 쓸 수 있다(iOS 는 origin 제약 동일) |

## 선택지

| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| A. 자체 편집 화면 유지·확장 | 지금 방식으로 표·그림·머리말 편집을 계속 만든다 | 디자인 일관, 설치 파일 작음, 엔진 하나 | studio 수준까지 수개월 |
| **B. studio 를 HWP 편집 모드로 도입 (추천)** | studio 를 `RHWP_DISABLE_EXTERNAL_WEBFONTS=1` 로 빌드해 앱에 넣고(오프라인), HWP/HWPX 의 편집 모드에서만 iframe 으로 띄운다. 열기·저장·다른 이름 저장·변환은 Papy 호스트가 하고 SDK 의 `loadFile`/`exportBytes` 로 주고받는다. origin 제약은 `isUsableParentOrigin` 이 `tauri:` 를 허용하도록 **한 줄 패치**(fetch 스크립트에서 적용, 업스트림에 제안) | 표·셀·개체·머리말까지 바로 편집, rhwp 팀의 테스트·개선을 그대로 받음 | 설치 파일 +40MB, 엔진 이중화, 패치 유지, 편집 화면 디자인이 다름 |
| C. 온라인 studio 사용 | SDK 기본값(github.io)을 그대로 쓴다 | 가장 쉽다 | 오프라인 우선 원칙 위반, 문서가 외부 페이지로 간다 → **채택 불가** |

## 추천

**B 를 단계적으로.** 보기 모드·PDF·DOCX 와 간단한 글자 편집은 지금 Papy 화면을 유지하고,
HWP/HWPX 에서 "고급 편집(표·개체 포함)" 으로 studio 를 연다. PoC 로 아래를 확인한 뒤 승인 여부를 정한다.

## PoC 결과 (2026-11-03, macOS 15 · Apple Silicon, 디버그 빌드)

구현: `scripts/fetch-rhwp-studio.mjs` 가 엔진과 같은 커밋의 studio 를 받아 오프라인으로 빌드해 `apps/desktop/public/studio/` 에 넣는다.
SDK(`@rhwp/editor`)는 `apps/desktop/src/vendor/rhwp-editor/` 에 들여왔다. HWP/HWPX 편집 모드의 서식 막대에 **고급 편집** 버튼이 생기고,
누르면 studio 가 창 전체에 뜬다. 엔진이 문서를 바이트로 내보내 `loadFile` 하고, **적용**하면 `exportHwp`/`exportHwpx` 결과를 엔진이 새 세션으로 연다(파일은 Papy 의 저장으로만 쓴다).

| 확인 항목 | 결과 |
|---|---|
| 빌드 | `npm ci --ignore-scripts` + vite build 약 15초(캐시 있을 때). 설치 스크립트 실행 없음. `@rhwp/core` WASM 은 npm 무결성(sha512) 확인 |
| 크기 | **41MB** (WASM 11MB, CanvasKit WASM 7MB, 글꼴 약 21MB, JS·이미지). 예제 문서·서비스 워커·PWA 매니페스트 제외 |
| 외부 요청 | 번들에 CDN 글꼴 주소가 데이터로 남아 있지만 `RHWP_DISABLE_EXTERNAL_WEBFONTS=1` 빌드에서는 오프라인 목록만 쓴다. 앱 CSP 가 `connect-src`·`font-src` 를 `'self'` 로 막아 이중으로 차단 |
| 글꼴 라이선스 | OFL·GUST 글꼴만 넣는다. FONTS.md 에 "무료 배포"로만 적힌 Cafe24·행복고딕은 빼고(대체 글꼴로 표시) 고지 파일에 FONTS.md·OFL 을 싣는다 |
| 연결(macOS `tauri://localhost`) | studio `isUsableParentOrigin` 한 줄 패치 + SDK 의 같은 출처 허용 패치로 **연결 성공**. 191개 명령(표·머리말·각주·그림·찾아 바꾸기 등) 노출 |
| 왕복 | HWP(6쪽)·HWPX(10쪽) 모두 엔진 → studio → 엔진 쪽 수 같음, 변경 없을 때 첫 쪽 글 동일 |
| 실제 입력 | iframe 의 입력란으로 글자 입력 → studio `dirty` → 내보내기 → 엔진 재열기에서 HWP 첫 쪽 글자 수 +4 확인 |
| 시간 | studio 부팅 약 0.3초, 문서 넣기 0.4~0.65초, 편집 후 내보내기 0.8~0.9초 |
| 암호 문서 | 엔진이 같은 암호로 다시 암호화해 넘기므로 studio 에서 암호를 다시 묻는다(개선 과제) |

남은 확인
- Windows(`http://tauri.localhost`)·Linux(WebKitGTK) 실제 창 연결 — 코드상 Windows 는 http 라 패치 없이도 되고 Linux 는 macOS 와 같은 `tauri:`
- 한글 입력기 조합 입력(사람이 직접), 큰 문서의 메모리
- IPC: 편집 결과가 JSON 숫자 배열로 올 수 있어 호스트가 둘 다 받는다. 큰 문서에서 느리면 임시 파일·채널로 바꾼다
- 업스트림에 `tauri:` 허용을 제안해 패치를 없앤다
