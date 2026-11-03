# 0002. 엔진과 데스크톱 Cargo workspace 분리

- 상태: 제안 (초기 구축에서 적용, 2026-11-03)

## 결정

`engine/Cargo.toml` 을 엔진 전용 workspace 로 두고, `apps/desktop/src-tauri` 는 독립 패키지(빈 `[workspace]`)로 엔진을 path 의존한다.
루트 통합 `Cargo.toml` 은 만들지 않는다. 툴체인은 루트 `rust-toolchain.toml` 로 공통 고정한다.

## 근거

- 엔진 CI(`engine.yml`)가 Tauri·WebView·시스템 GUI 라이브러리 없이 Linux/macOS/Windows 에서 돈다.
- 엔진이 앱에 의존하지 않는다는 규칙이 빌드 구조로도 강제된다.

## 비용

- `Cargo.lock` 이 두 개(`engine/`, `apps/desktop/src-tauri/`)이고 `target/` 도 따로다.
  공통 의존성 버전이 어긋나지 않는지 엔진 의존성 추가 시 확인한다. 불편이 커지면 루트 workspace 로 합치는 것을 재검토한다.
