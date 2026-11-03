# 플랫폼 연결 방식

| 대상 | 연결 | 상태 |
|---|---|---|
| Desktop (Windows/macOS) | `papy-core` 를 Rust 의존성으로 직접 사용 → Tauri command → `@tauri-apps/api` `invoke` | 연결됨 (`engine_version` 만) |
| Android | Rust → Android `.so` → JNI 또는 C ABI 래퍼 → Kotlin (`androidMain`) | 미연결 |
| iOS | Rust → 정적/동적 라이브러리 → C 헤더 + Kotlin/Native C Interop, 또는 UniFFI (`iosMain`) | 미연결, 방식 미확정 |
| Web | Rust 엔진 WASM 빌드 | 미검증 — 외부 문서 엔진의 WASM 호환성 PoC 필요 |

## 원칙

- C ABI 는 함수 호출 규약이며 JNI 와 같은 기술이 아니다. Android 는 JNI 로 C ABI 함수를 부르거나 별도 C 래퍼를 둔다.
- KMP 공통 코드(`commonMain`)에서 네이티브 함수를 직접 부른다고 가정하지 않는다. `expect`/`actual` 또는 인터페이스로
  `androidMain`·`iosMain` 구현을 분리한다.
- C ABI 표면은 작게 유지한다: 불투명 핸들, 명시적 오류 코드, 호출자/엔진 중 누가 메모리를 해제하는지 함수마다 문서화.
- 초기 저장소 단계에서는 모바일에 Rust FFI 를 연결하지 않는다. 엔진 API 안정화 뒤 진행한다.

## 현재 `papy-ffi` 표면

| 함수 | 반환 | 소유권 |
|---|---|---|
| `papy_engine_version()` | NUL 종료 문자열 | 정적 수명, 호출자는 해제하지 않음 |
