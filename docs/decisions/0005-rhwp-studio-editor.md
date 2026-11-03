# 0005. HWP/HWPX 편집기: rhwp-studio(@rhwp/editor) 도입 검토

- 상태: **제안 — PoC 실행 승인 대기**
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

## PoC 확인 항목 (실행 승인 필요)

이 세션에서 studio 빌드(외부 코드 `npm ci`·`vite build` 실행)는 자동 모드 안전장치가 막았다. 저장소 소유자가 승인하면:

1. `scripts/fetch-rhwp-studio.mjs`: rhwp-studio·assets 를 같은 커밋으로 sparse 받기 + `@rhwp/core@0.8.7` WASM(무결성 해시 고정) → `npm ci --ignore-scripts` → `RHWP_DISABLE_EXTERNAL_WEBFONTS=1 vite build --base=./`
2. 빌드 결과 크기, 외부 네트워크 요청이 0 인지(CSP `connect-src 'self'` 로 강제)
3. macOS(tauri://)·Windows(http://tauri.localhost)·Linux 에서 iframe 연결(패치 전후), 한글 입력기 입력
4. `loadFile` → 편집 → `exportBytes` → 호스트 저장 → 네이티브 엔진 재열기 왕복
5. 첫 표시 시간·메모리(WASM 초기화 포함)
