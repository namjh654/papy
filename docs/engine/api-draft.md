# 엔진 API 초안 (Phase 1 구현 기준)

구현: `engine/crates/papy-core` (파사드), `papy-render` (어댑터 계약·렌더 타입), `papy-model` (문서 타입·오류).
PDF·HWPX 어댑터를 붙이면서 바뀔 수 있다. 바뀌면 이 문서와 앱을 같은 PR 에서 고친다.

## 호출 흐름

```rust
let engine = Engine::new(Limits::default());           // 어댑터 등록
let id = engine.open(DocumentSource::Path(path), OpenOptions::default())?;
let info = engine.info(id)?;                            // 포맷·페이지 수·크기·기능
let bitmap = engine.render_page(id, 0, &RenderRequest::with_scale(2.0))?;  // RGBA8
let text = engine.extract_text(id, 0)?;
let outline = engine.outline(id)?;
engine.close(id)?;
```

| 기획서 함수 | 구현 | 비고 |
|---|---|---|
| `open_document` | `Engine::open(DocumentSource, OpenOptions) -> SessionId` | 경로 또는 메모리 버퍼. 앞 128바이트로 포맷 판별 |
| `get_document_info` | `Engine::info` | 본문 미포함 |
| `get_page_count` | `Engine::page_count` | |
| `render_page` | `Engine::render_page(id, page, &RenderRequest)` | 배율·배경·취소 토큰 |
| `extract_text` | `Engine::extract_text(id, page)` | 페이지 단위. 범위는 호출자가 반복 |
| `search_text` | `Engine::find_text(id, query, max, &CancelToken) -> Vec<TextMatch>` | 쪽마다 꺼낸 글자에서 찾는다(모든 형식이 같은 규칙). 대소문자·공백 종류 무시, 앞뒤 글자 붙임. 쪽마다 잠금을 풀어 그리기와 번갈아 돈다 |
| `get_outline` | `Engine::outline` | PDF 책갈피, HWP/HWPX 개요 문단(번호 포함), DOCX 제목 스타일·`w:outlineLvl` |
| `close_document` | `Engine::close` | |
| `save_document` | `Engine::save(id, SaveFormat)` | `Capabilities::save_formats` 로 지원 형식 표시 |

## 규칙

- **오류**: `Error { code: ErrorCode, message }`. `ErrorCode` 숫자는 고정(추가만). 메시지에 경로·본문 금지. `io::Error` 는 종류만 남긴다.
- **핸들**: `SessionId` 는 0 이 아닌 u64, 재사용하지 않는다. 닫힌 핸들 → `SessionNotFound`.
- **소유권**: `Bitmap` 은 호출자가 소유(Vec). C ABI 에서는 엔진 할당 버퍼 + 해제 함수 쌍으로 노출 예정.
- **스레드**: `Engine: Send + Sync`. 세션마다 잠금 — 다른 문서는 병렬, 같은 문서는 직렬. 스레드 비안전 엔진(PDFium)은 어댑터가 전용 스레드로 감싼다.
- **취소**: `CancelToken` 을 열기·렌더에 넘긴다. 엔진은 어댑터 호출 전, 어댑터는 긴 작업 중간에 확인한다.
- **상한** (`Limits` 기본값): 파일 1 GiB, 동시 문서 64, 페이지 100,000, 렌더 8192×8192 px, 배율 0.05~16. 측정 후 조정.
- **진행률**: 아직 없음. 큰 문서 열기·검색에서 필요해지면 `RenderRequest`/`OpenOptions` 에 콜백을 추가한다.
- **캐시**: 아직 없음. 페이지 비트맵 캐시는 앱 쪽(화면 크기 기준) 또는 `papy-render` 에 상한과 함께 둔다 — PDF PoC 측정 후 결정.
