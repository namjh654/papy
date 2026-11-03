# 0001. apps/web 프레임워크: Vite + React

- 상태: 제안 (초기 구축에서 적용, 2026-11-03)

## 맥락

브라우저 뷰어는 서버 없이 사용자의 로컬 파일을 브라우저 안에서만 처리해야 한다. 엔진은 이후 WASM 으로 붙일 가능성이 있다.
데스크톱 UI 도 TypeScript 웹 UI 다.

## 결정

Vite 8 + React 19 + TypeScript 의 정적 SPA.

## 근거

- 서버 렌더링이 필요 없다 — 문서 처리는 모두 클라이언트. 정적 산출물은 Vercel 등 어디서나 호스팅된다.
- 데스크톱(Tauri) UI 와 같은 스택이라 `packages/ui`·`packages/types` 공유가 쉽다.
- Vite 는 WASM 모듈 로딩과 Tauri 개발 서버 연동이 공식 문서화돼 있다.

## 대안

- Next.js: SSR/서버 기능이 이 앱에 불필요하고, 정적 내보내기 제약을 따로 관리해야 한다.
- SvelteKit/Solid: 가볍지만 데스크톱과 같은 스택(React)으로 맞추는 이점이 사라진다.
