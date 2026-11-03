# Papy (페이피)

Source-available cross-platform document viewer and editor (PolyForm Noncommercial 1.0.0).

PDF 와 한국어 문서(HWP/HWPX)를 우선 지원하는 **비상업 용도 무료·오프라인 우선** 통합 문서 앱입니다.
회원가입·광고·서버 업로드 없이, 사용자가 선택한 파일만 로컬에서 엽니다.

> **상태: 초기 개발 단계 (v0.0.0).** 저장소 구조와 빌드만 갖춘 상태이며, **아직 어떤 문서 포맷도 열 수 없습니다.**
> 지원 포맷·기능은 실제 테스트 문서와 회귀 테스트로 검증된 범위만 이 README 에 표시합니다.

## 현재 구현 상태

| 영역 | 상태 |
|---|---|
| Rust 엔진 (`engine/`) | workspace·crate 골격, 파일 시그니처 기반 포맷 판별(PDF/HWPX/ZIP/CFB 후보)만 구현 |
| 데스크톱 (`apps/desktop/`) | Tauri 2 앱 골격. 엔진 버전 표시만 함. macOS·Windows·Linux 패키징을 CI 에서 빌드, `v*` 태그로 GitHub Release 배포 |
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
├── website/         소개·다운로드·설치·사용법 사이트 (Vercel)
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

소스 코드와 배포 파일(설치 프로그램·바이너리) 모두 [PolyForm Noncommercial 1.0.0](LICENSE)
(SPDX: `PolyForm-Noncommercial-1.0.0`)을 따릅니다.

- 허용: 개인·학습·연구·취미·비영리 단체·공공기관 등 **비상업적 목적**의 사용, 수정, 재배포
- 불허: 상업적 목적의 사용 — 별도 라이선스가 필요하면 [문의](https://papy-omega.vercel.app/contact/)
- 재배포 시 `LICENSE` 전문(또는 URL)과 `Required Notice:` 줄을 함께 전달해야 합니다.
- OSI 가 승인한 오픈소스 라이선스가 아니므로 Papy 는 "소스 공개(source-available)" 프로젝트입니다.
- 포함된 서드파티 구성요소는 각자의 라이선스를 따릅니다 — [docs/development/licensing.md](docs/development/licensing.md).
