# API 문서

엔진 공개 API(Rust, C ABI)와 데스크톱 Tauri command 목록을 둔다. API 가 안정되면 `cargo doc` 산출물과 함께 정리한다.

| Tauri command | 엔진 호출 | 반환 |
|---|---|---|
| `engine_version` | `papy_core::ENGINE_VERSION` | 문자열 |
