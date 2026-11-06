# 사용 버전

확인일: 2026-11-03. 확인 방법: 로컬 설치 버전, `npm view <pkg> dist-tags`, crates.io API(`/api/v1/crates/<name>/versions`, yanked·pre-release 제외).

## 툴체인 (초기 구축 머신: macOS 26.6.2, Apple Silicon)

| 도구 | 버전 | 고정 위치 |
|---|---|---|
| Rust | 1.99.1 | `rust-toolchain.toml` |
| Node.js | v24.21.0 (요구: ≥24) | `package.json` engines |
| pnpm | 10.27.0 | `package.json` packageManager |
| Xcode | 27.2 | — |
| JDK | 시스템 17.0.16, Gradle 데몬은 Corretto 21 자동 내려받기 | `apps/mobile/gradle/gradle-daemon-jvm.properties` |

## 데스크톱 / 웹

| 패키지 | 버전 | 비고 |
|---|---|---|
| `tauri` (crate) | 2.12.5 | 2.x stable 최신. crates.io 최신 태그는 3.0.0-alpha → **사용 안 함** |
| `tauri-build` (crate) | 2.7.4 | |
| `@tauri-apps/cli`, `@tauri-apps/api` | 2.12.4 | npm `latest`. `next` 태그(3.0.0-alpha) 사용 안 함 |
| `tauri-plugin-dialog` (crate), `@tauri-apps/plugin-dialog` | 2.8.4 | 파일 열기 대화상자. Apache-2.0 OR MIT |
| `react`, `react-dom` | 19.3.1 | |
| `vite` | 8.3.5 | |
| `@vitejs/plugin-react` | 6.2.1 | |
| `typescript` | 7.0.2 | |

모든 JS 의존성은 정확한 버전으로 고정(`.npmrc` `save-exact`)하고 `pnpm-lock.yaml`, Cargo 의존성은 `=` 고정과 `Cargo.lock` 으로 재현한다.

## 엔진 외부 구성요소

| 구성요소 | 버전 | 고정 위치 | 받는 방법 |
|---|---|---|---|
| PDFium 사전 빌드 ([bblanchon/pdfium-binaries](https://github.com/bblanchon/pdfium-binaries)) | chromium/8125 | `scripts/fetch-pdfium.mjs` (타깃별 SHA-256) | `node scripts/fetch-pdfium.mjs [rust-target]` |
| `pdfium-render` (crate) | =0.9.5 (feature `pdfium_8125`) | `engine/crates/papy-pdf/Cargo.toml` | crates.io |
| rhwp ([edwardkim/rhwp](https://github.com/edwardkim/rhwp)) | v0.8.7 (`1a76570e833917d15817415a53c09ad61ab3203f`) | `scripts/fetch-rhwp.mjs` | `node scripts/fetch-rhwp.mjs` (sparse, 약 47MB). 받은 뒤 `engine/patches/rhwp/*.patch` 를 이름 순서로 적용: 0001 큰 표 조판 속도(결과 같음), 0002·0003 DOCX·DOC 변환 도움 함수(새 함수만), 0004 PDF 에서 읽을 수 없는 그림만 빼기, 0005 PDF 글꼴 처리 속도(결과 같음), 0006 PDF 에서 글 꺼내기·찾기(쪽 그림 배율), 0007 붙여넣은 표 칸 안 겹친 표 줄 나누기·높이, 0008 붙여넣은 문단 여백 HWPX 왕복, 0009 붙여넣기 장평(`font-stretch`), 0010 붙여넣은 표의 병합 칸만 있는 열 너비. 각 패치 머리에 이유를 적는다 |
| Apache POI 문서 모음(Apache-2.0, 회귀 점검용) | `7f4d36854109efa17152b27a5fb1b2c7d842c314` `test-data/document` 의 .doc·.docx | `scripts/fetch-corpus.mjs` | `node scripts/fetch-corpus.mjs poi` (→ `tests/corpus/poi-documents/`, git 제외) |
| `cfb` (crate) | 0.15 | `engine/crates/papy-office/Cargo.toml` | crates.io (DOC 의 OLE 복합 파일 읽기) |
| `resvg` (crate) | =0.45.1 | `engine/crates/papy-hwp/Cargo.toml` | crates.io |
| Noto Sans KR Regular (OFL-1.1) | rhwp v0.8.7 저장소 사본 | `scripts/fetch-rhwp.mjs` | rhwp 받을 때 함께 |
| rhwp 샘플 문서 모음(MIT, 회귀 점검용) | rhwp v0.8.7 (`1a76570e`) `samples/` | `scripts/fetch-corpus.mjs` | `node scripts/fetch-corpus.mjs` (약 700MB → `tests/corpus/rhwp-samples/`, git 제외) |
| rhwp-studio + @rhwp/core 0.8.7 WASM (MIT) | rhwp v0.8.7 (`1a76570e`), npm sha512 고정 | `scripts/fetch-rhwp-studio.mjs` | `node scripts/fetch-rhwp-studio.mjs` (빌드 결과 약 41MB → `apps/desktop/public/studio/`) |
| @rhwp/editor SDK (MIT) | rhwp v0.8.7 `npm/editor` | `apps/desktop/src/vendor/rhwp-editor/` (패치 표시 `Papy:`) | 직접 복사 |

두 스크립트를 한 번에: `pnpm engine:vendor`. 결과는 `engine/vendor/`(gitignore)에 들어간다. 엔진·데스크톱 빌드 전에 필요하다.

## 모바일 (템플릿 그대로)

| 항목 | 버전 |
|---|---|
| Gradle | 9.7.1 |
| Kotlin | 2.4.20 |
| Android Gradle Plugin | 9.3.1 |
| SKIE | 0.10.15 |
| Compose BOM | 2026.09.00 |

## 업데이트 규칙

- Tauri 는 2.x stable 만. alpha/beta/RC 금지.
- 버전을 올리면 이 문서와 lockfile 을 같은 PR 에서 갱신한다.
