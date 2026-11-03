# engine — Papy Rust 문서 엔진

UI 프레임워크에 의존하지 않는 독립 Rust workspace.

| crate | 책임 | 현재 |
|---|---|---|
| `papy-core` | 엔진 파사드, 세션 관리, 앱이 호출하는 진입점 | `ENGINE_VERSION`, `detect_format` |
| `papy-model` | 문서·페이지·스타일 모델, 편집 명령 | `DocumentFormat` |
| `papy-render` | 렌더링 추상화, 지연 렌더링·캐시·취소 | 비어 있음 |
| `papy-pdf` | PDF 어댑터 | 시그니처 확인만 |
| `papy-hwp` | HWP/HWPX 어댑터 | HWPX mimetype·CFB 시그니처 확인만 |
| `papy-office` | DOCX/XLSX/PPTX 어댑터(실험) | 비어 있음 |
| `papy-ffi` | C ABI 경계(모바일·기타 호스트) | `papy_engine_version()` |

## 명령

```bash
cargo fmt --all --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
```

검증 결과(2026-11-03, macOS arm64): fmt·clippy 경고 없음, 테스트 6개 통과.

구조와 의존 방향: [docs/architecture/overview.md](../docs/architecture/overview.md)
