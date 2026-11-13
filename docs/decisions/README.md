# 결정 기록 (ADR)

중요한 기술·제품 결정을 `NNNN-제목.md` 로 남긴다. 형식: 상태(제안/승인/대체됨), 맥락, 결정, 대안, 결과.

| 번호 | 제목 | 상태 |
|---|---|---|
| [0001](0001-web-framework.md) | apps/web 프레임워크: Vite + React | 제안 |
| [0002](0002-cargo-workspaces.md) | 엔진과 데스크톱 Cargo workspace 분리 | 제안 |
| [0003](0003-pdf-engine.md) | PDF 엔진: PDFium + pdfium-render | 승인 (2026-11-03) |
| [0004](0004-hwp-engine.md) | HWP/HWPX 엔진: rhwp | 승인 (2026-11-03) |
| [0005](0005-rhwp-studio-editor.md) | HWP/HWPX 편집기: rhwp-studio 도입 검토 | 승인 (2026-11-03) |
| [0006](0006-mobile-bridge.md) | 모바일 엔진 연결: UniFFI, PDF 는 운영체제 렌더러 | 승인 (2026-11-04) |
| [0007](0007-xlsx-pptx.md) | XLSX·PPTX 지원 방식(PPTX 는 쪽 변환, XLSX 는 시트 모델·조각 그리기) | 보류 → 순서는 0008 (문서 3종·배포 뒤) |
| [0008](0008-product-order-and-engines.md) | 제품 순서(문서 3종 → 배포 → PPT → 엑셀)와 형식별 엔진 구조(rhwp 하나 + 호환 규칙, 엑셀만 별도) | 승인 (2026-11-11) |
| [0009](0009-ai-plugin.md) | AI 플러그인: 앱 안 대화창 + MCP 서버, Claude·ChatGPT·Gemini(API 키·로컬·ACP) | 제안 (2026-11-13) |
| — | 프로젝트 라이선스: PolyForm Noncommercial 1.0.0 | 승인 (2026-11-03, [licensing.md](../development/licensing.md)) |
