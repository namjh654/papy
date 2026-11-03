# 기여 가이드

Papy 는 Issue → Branch → Pull Request → Review → Merge 흐름으로 개발합니다.

## 브랜치

- `main`: 항상 빌드 가능한 상태. 직접 push 하지 않습니다.
- 작업 브랜치: `feature/*`, `fix/*`, `docs/*`
- 큰 변경은 작은 PR 로 나눕니다. 강제 push 로 공유 이력을 덮어쓰지 않습니다.

## PR 전에 확인

변경한 영역의 검증을 로컬에서 실행하고, 결과를 PR 본문에 적습니다. CI 도 같은 명령을 실행합니다.

| 영역 | 명령 |
|---|---|
| engine | `cd engine && cargo fmt --all --check && cargo clippy --workspace --all-targets -- -D warnings && cargo test --workspace` |
| desktop | `pnpm --filter @papy/desktop typecheck` · `cd apps/desktop/src-tauri && cargo clippy --all-targets -- -D warnings` · `pnpm --filter @papy/desktop tauri build` |
| web | `pnpm --filter @papy/web typecheck && pnpm --filter @papy/web build` |
| mobile | `cd apps/mobile && scripts/orca.sh check` |

엔진 공개 API 를 바꾸면 그 API 를 쓰는 모든 앱(desktop, 이후 mobile·web)을 함께 검증합니다.

## 규칙

- 엔진(`engine/**`)은 UI 프레임워크(Tauri, React, Compose, SwiftUI) 타입에 의존하지 않습니다.
- 앱에 문서 파싱·레이아웃 로직을 넣지 않습니다. 필요한 기능은 엔진 API 로 추가합니다.
- 문서 본문, 추출 텍스트, 사용자 파일 경로를 로그에 남기지 않습니다.
- 새 외부 의존성(특히 C/C++ 엔진)은 라이선스·재배포 조건을 확인하고 [docs/development/licensing.md](docs/development/licensing.md)에 기록한 뒤 PR 에서 승인받습니다.
- 중요한 기술 결정은 [docs/decisions](docs/decisions)에 ADR 로 남깁니다.
- 테스트 문서는 배포 권리가 확인된 파일만 `tests/fixtures` 에 커밋하고 출처를 기록합니다([tests/README.md](tests/README.md)).
- 비밀값·서명키·인증서·`.env` 를 커밋하지 않습니다.
