# 플랫폼 연결 방식

| 대상 | 연결 | 상태 |
|---|---|---|
| Desktop (Windows/macOS) | `papy-core` 를 Rust 의존성으로 직접 사용 → Tauri command → `@tauri-apps/api` `invoke` | 연결됨 (`engine_version` 만) |
| Android | Rust → `libpapy_ffi.so` → UniFFI Kotlin(JNA) → `AndroidDocumentEngine`(androidApp). PDF 는 `PdfRenderer` | 연결됨 (보기, [ADR 0006](../decisions/0006-mobile-bridge.md)) |
| iOS | Rust → `PapyFFI.xcframework`(정적) → UniFFI Swift → `PapyDocumentEngine`(iosApp, KMP `DocumentEngine` 구현). PDF 는 PDFKit | 연결됨 (보기, ADR 0006) |
| Web | Rust 엔진 WASM 빌드 | 미검증 — 외부 문서 엔진의 WASM 호환성 PoC 필요 |

## 원칙

- C ABI 는 함수 호출 규약이며 JNI 와 같은 기술이 아니다. Android 는 JNI 로 C ABI 함수를 부르거나 별도 C 래퍼를 둔다.
- KMP 공통 코드(`commonMain`)에서 네이티브 함수를 직접 부른다고 가정하지 않는다. `expect`/`actual` 또는 인터페이스로
  `androidMain`·`iosMain` 구현을 분리한다.
- C ABI 표면은 작게 유지한다: 불투명 핸들, 명시적 오류 코드, 호출자/엔진 중 누가 메모리를 해제하는지 함수마다 문서화.
- 모바일 빌드: `node scripts/build-mobile-engine.mjs all` (바인딩·Android `.so`·iOS xcframework·한글 글꼴).

## 현재 `papy-ffi` 표면

| 항목 | 내용 |
|---|---|
| `PapyEngine(koreanFont)` | 엔진 객체(UniFFI). `open(bytes, password)` → `DocumentSummary`, `pageSize`, `renderPage(scale)` → RGBA, `extractText`, `close` |
| `PapyError` | 종류별 오류(`PasswordRequired` 등) + `detail`(개발자용) |
| `engine_version()` / `papy_engine_version()` | 버전. C 함수는 NUL 종료 정적 문자열(해제하지 않음) |
