# Papy (페이피)

Open-source cross-platform document viewer and editor.

PDF 와 한국어 문서(HWP/HWPX)를 우선 지원하는 **무료·오프라인 우선** 통합 문서 앱입니다.
회원가입·광고·서버 업로드 없이, 사용자가 선택한 파일만 로컬에서 엽니다.

> **상태: 초기 개발 단계 (v0.0.0).** 저장소 구조와 빌드만 갖춘 상태이며, **아직 어떤 문서 포맷도 열 수 없습니다.**
> 지원 포맷·기능은 실제 테스트 문서와 회귀 테스트로 검증된 범위만 이 README 에 표시합니다.

## 현재 구현 상태

| 영역 | 상태 |
|---|---|
| Rust 엔진 (`engine/`) | workspace·crate 골격, 파일 시그니처 기반 포맷 판별(PDF/HWPX/ZIP/CFB 후보)만 구현 |
| 데스크톱 (`apps/desktop/`) | Tauri 2 앱 골격. 엔진 버전 표시만 함. macOS `.app` 빌드 확인, Windows 는 CI 에서 확인 예정 |
| 웹 (`apps/web/`) | Vite + React 골격. 문서 기능 없음 |
| 모바일 (`apps/mobile/`) | 기존 KMP 템플릿([namjh654/kmp](https://github.com/namjh654/kmp)) 편입. 아직 Papy 기능·엔진 연결 없음 |
| PDF / HWPX / HWP / Office | 미구현. 엔진 선정·라이선스 검토 전 |

## 디렉터리

```
papy/
├── engine/          Rust 문서 엔진 workspace (UI 프레임워크에 의존하지 않음)
├── apps/
│   ├── desktop/     Tauri 2.x stable + React/TypeScript (Windows/macOS)
│   ├── web/         Vite + React/TypeScript 브라우저 뷰어
│   └── mobile/      KMP — Android Compose + iOS SwiftUI
├── packages/        web·desktop 이 실제로 공유하는 JS 코드만 (현재 config)
├── docs/            아키텍처·엔진·포맷·결정 기록·개발 가이드
├── tests/           포맷별 테스트, fixtures(배포 권리가 확인된 문서만)
├── scripts/         빌드·검증 자동화
├── website/         소개·다운로드 사이트 (후속)
└── .github/         CI, Issue/PR 템플릿
```

의존 방향은 항상 **engine → 앱** 입니다. 엔진은 Tauri·React·Compose·SwiftUI 를 참조하지 않습니다. 자세한 구조는 [docs/architecture/overview.md](docs/architecture/overview.md).

## 개발 환경

| 도구 | 버전 (고정 위치) |
|---|---|
| Rust | 1.99.1 (`rust-toolchain.toml`) |
| Node.js | 24 이상 (`package.json` engines) |
| pnpm | 10.27.0 (`packageManager`) |
| Tauri | crate 2.12.5 / CLI·API 2.12.4 (stable) |
| 모바일 | JDK 21(Gradle 데몬, 자동 내려받기), Android SDK, Xcode(macOS) — [apps/mobile/README.md](apps/mobile/README.md) |

Tauri 의 OS 별 선행 조건(Xcode Command Line Tools, Windows 의 WebView2·MSVC Build Tools)은 [Tauri 공식 문서](https://v2.tauri.app/start/prerequisites/)를 따릅니다.
전체 버전 목록은 [docs/development/versions.md](docs/development/versions.md).

## 실행

```bash
pnpm install

# 엔진
cd engine && cargo test --workspace

# 데스크톱 (개발 실행 / 패키징)
pnpm desktop:dev
pnpm --filter @papy/desktop tauri build

# 웹
pnpm web:dev

# 모바일 (apps/mobile)
cd apps/mobile && scripts/orca.sh check
```

## 원칙

- **로컬 우선**: 문서 본문을 외부 서버로 보내지 않습니다. 핵심 열람은 네트워크 없이 동작합니다.
- **무계정·무광고**: 로그인, 광고 SDK, 사용자 추적을 넣지 않습니다.
- **엔진 독립성**: 파싱·모델·렌더링은 Rust 엔진에만 두고 앱은 얇은 어댑터만 가집니다.
- **검증 우선**: 테스트로 확인한 범위만 지원한다고 표시합니다.

## 기여·보안

- 기여: [CONTRIBUTING.md](CONTRIBUTING.md)
- 보안 신고: [SECURITY.md](SECURITY.md)

## 라이선스

저장소 생성 시 추가된 [LICENSE](LICENSE)(Apache-2.0)가 있습니다. 다만 PDF·HWP·Office 엔진 등 **외부 의존성의 라이선스 검토가 끝나지 않아
프로젝트 최종 라이선스는 확정 전**입니다. 검토 결과에 따라 바뀔 수 있습니다 — [docs/development/licensing.md](docs/development/licensing.md).
