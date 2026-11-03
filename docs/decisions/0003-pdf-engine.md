# 0003. PDF 엔진

- 상태: **승인** (2026-11-03, 저장소 소유자 — 추천안 채택)
- 조사일: 2026-11-03 (출처는 각 항목의 공식 저장소·사이트)

## 전제

Papy 는 PolyForm Noncommercial 1.0.0 으로 배포한다([licensing.md](../development/licensing.md)).
따라서 **GPL·AGPL 구성요소는 함께 배포할 수 없다** — 상업적 사용 금지라는 추가 제한을 GPL 계열이 허용하지 않기 때문이다.

## 후보 비교

| 항목 | PDFium | MuPDF | hayro | pdf.js |
|---|---|---|---|---|
| 라이선스 | BSD-3-Clause + Apache-2.0 ([LICENSE](https://pdfium.googlesource.com/pdfium/+/refs/heads/main/LICENSE)) | AGPL-3.0 **또는** Artifex 유료 상용 ([GitHub](https://github.com/ArtifexSoftware/mupdf)) | Apache-2.0 OR MIT ([GitHub](https://github.com/LaurenzV/hayro)) | Apache-2.0 ([GitHub](https://github.com/mozilla/pdf.js)) |
| PolyForm NC 와 결합 | 가능 | AGPL 판 **불가** / 상용 라이선스 구매 시 가능 | 가능 | 가능 |
| 쓰는 곳 | Chrome·Edge 의 PDF 뷰어 | SumatraPDF, 다수 상용 뷰어 | (신규) | Firefox |
| 언어 / Rust 연결 | C++ / `pdfium-render` 0.9.5 (MIT OR Apache-2.0, 2026-11-01) | C / `mupdf` crate 0.8.0 (AGPL-3.0, 2026-06-22) | 순수 Rust 0.9.0 (2026-11-02) | JavaScript — 엔진 독립성·모바일 공유에 불리 |
| 렌더링 품질·호환성 | 매우 높음(브라우저급, 폼·주석·폰트) | 매우 높음, 일반적으로 가장 빠름 | 개발 중 — 실사용 검증 부족 | 높음, 네이티브보다 느림 |
| 텍스트 추출·검색 | 지원(`FPDFText_*`) | 지원(구조화 텍스트 우수) | 제한적 | 지원 |
| 편집(주석·페이지) | 주석·페이지 편집·저장 API 있음 | 풍부 | 없음 | 주석 일부 |
| Windows/macOS/Linux | 사전 빌드 제공 | 소스 빌드 | Rust 빌드 그대로 | — |
| Android/iOS/WASM | 사전 빌드 제공(android arm/arm64/x86/x64, ios device/simulator/catalyst, wasm) | 소스 빌드(공식 Android/iOS SDK 는 상용) | 그대로 빌드 | 웹 전용 |
| 바이너리 크기 | 3~6 MB(플랫폼별, [pdfium-binaries](https://github.com/bblanchon/pdfium-binaries) chromium/8125, 2026-10-26) | 수 MB(폰트 포함 시 증가) | 작음 | — |
| 스레드 | 스레드 안전 아님 → 전용 워커 스레드 하나로 직렬화 | 컨텍스트별 사용 | Rust 안전 | — |
| 동봉 서드파티 | 18개 고지(abseil, FreeType(FTL 선택), ICU, libjpeg-turbo, libpng, lcms, openjpeg, zlib, agg, harfbuzz, highway, simdutf 등) — 모두 허용형 | 자체 포함(동일 계열) | Rust crate 들 | — |

상용으로 쓸 수 있는 유료 SDK(Apryse·Foxit·Nutrient 등)도 품질은 최상위이나 소스 비공개·배포 단위 과금이라
"무료 소스 공개 앱"으로 재배포하는 Papy 모델과 맞지 않아 후보에서 뺐다.

## 제안

**PDFium + `pdfium-render`, 플랫폼별 사전 빌드 동적 라이브러리를 앱에 함께 배포.**

- 품질·호환성이 최상위권이면서, 상업적 이용 가능한 허용형 라이선스라 Papy 의 어떤 라이선스 정책과도 충돌하지 않는다.
- 데스크톱 3종 + Android/iOS + WASM 사전 빌드가 있어 기획서의 모바일·웹 확장 경로가 열린다.
- MuPDF 는 성능이 가장 좋지만 AGPL 판은 PolyForm NC 와 함께 배포할 수 없다. 쓰려면 Artifex 상용 라이선스(유료, 가격 비공개)가 필요하다.
- hayro 는 라이선스·이식성은 이상적이지만 아직 실사용 검증이 부족하다 — PDFium 렌더 결과와 비교하는 보조 후보로 지켜본다.

## 승인 시 할 일 (PoC)

1. `papy-pdf` 에 `pdfium-render` 의존성(정확 버전 고정), `FormatBackend` 구현: 열기·페이지 수·크기·렌더·텍스트·목차.
2. PDFium 호출은 전용 스레드 하나로 직렬화(스레드 비안전 대응).
3. 빌드 스크립트: 플랫폼별 PDFium 바이너리를 고정 버전으로 내려받아 Tauri `resources` 로 번들, `THIRD_PARTY_NOTICES` 에 18개 고지 포함.
4. 테스트 코퍼스로 회귀 테스트(아래 test-corpus.md), 성능 측정(기획서 6.1).
