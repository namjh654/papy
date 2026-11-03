# apps/web — 브라우저 뷰어

Vite + React + TypeScript 정적 SPA([결정 0001](../../docs/decisions/0001-web-framework.md)). **아직 문서 기능 없음.**
엔진 연결은 Rust 엔진·문서 라이브러리의 WASM 빌드 가능성을 PoC 로 확인한 뒤 진행하며, 그 전까지 데스크톱과 기능이 같다고 표시하지 않는다.

```bash
pnpm install            # 저장소 루트에서
pnpm web:dev            # 개발 서버
pnpm --filter @papy/web typecheck
pnpm --filter @papy/web build   # → apps/web/dist
```

검증 결과(2026-11-03, macOS): typecheck·build 통과.
