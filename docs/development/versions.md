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
| rhwp ([edwardkim/rhwp](https://github.com/edwardkim/rhwp)) | v0.8.7 (`1a76570e833917d15817415a53c09ad61ab3203f`) | `scripts/fetch-rhwp.mjs` | `node scripts/fetch-rhwp.mjs` (sparse, 약 47MB) |
| `resvg` (crate) | =0.45.1 | `engine/crates/papy-hwp/Cargo.toml` | crates.io |

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
