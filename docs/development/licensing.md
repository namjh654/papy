# 라이선스 검토

## 프로젝트 라이선스

- 현재 루트 `LICENSE`: Apache-2.0 (GitHub 저장소 생성 시 추가됨).
- **최종 확정 전.** PDF·HWP·Office 엔진 선택에 따라 배포 의무가 바뀔 수 있어(예: AGPL 엔진을 정적 링크하면 전체 소스 공개 의무),
  외부 엔진 결정과 함께 확정한다.

## 외부 라이브러리 도입 체크리스트

도입 PR 마다 아래를 채운다. 하나라도 불명확하면 의존성으로 추가하지 않는다.

- [ ] 정확한 버전과 그 버전의 라이선스(SPDX), 공식 저장소에서 확인한 날짜와 URL
- [ ] 라이선스 계열: 허용형(MIT/Apache/BSD) / 약한 copyleft(MPL·LGPL) / 강한 copyleft(GPL·AGPL) / 상용·이중
- [ ] 링크 방식(정적/동적)에 따른 의무 — 특히 iOS 는 정적 링크가 사실상 강제됨
- [ ] 소스 공개 의무 범위(파일 단위·라이브러리 단위·전체 앱)
- [ ] 저작권 고지·NOTICE 파일 요구사항 → `THIRD_PARTY_NOTICES` 반영
- [ ] 앱 스토어(App Store/Play/Microsoft Store) 배포와 충돌 여부
- [ ] 상용 배포·재배포 조건, 특허 조항
- [ ] 번들되는 폰트·데이터 파일의 별도 라이선스
- [ ] 지원 플랫폼(Windows/macOS/Android/iOS/WASM)과 빌드 가능성
- [ ] 유지보수 상태(최근 릴리스, 이슈 대응)

## 현재 의존성 현황 (2026-11-03)

| 범위 | 내용 |
|---|---|
| 엔진 (`engine/`) | 외부 의존성 없음 |
| 데스크톱 Rust (`src-tauri`) | 전이 포함 402 crate. 대부분 MIT/Apache-2.0 계열. 약한 copyleft: MPL-2.0 5개(`cssparser`, `cssparser-macros`, `dtoa-short`, `option-ext`, `selectors` — Tauri/wry 경유, 파일 단위 copyleft). GPL/AGPL 없음 |
| JS 직접 의존성 | react·react-dom·vite·@vitejs/plugin-react·@types/react: MIT / typescript: Apache-2.0 / @tauri-apps/*: Apache-2.0 OR MIT |
| 모바일 | 템플릿 의존성(Ktor, Koin, SKIE, Firebase, 소셜 로그인 SDK 등) 미검토 — 모바일 단계 시작 시 정리 |

## 미작성 / 후속

- `THIRD_PARTY_NOTICES`: 첫 배포 산출물 전에 생성.
- SBOM: 후보 — Rust `cargo-cyclonedx` 또는 `cargo-about`(고지 생성), JS `pnpm licenses list` / CycloneDX npm, Gradle CycloneDX 플러그인. 도입 시 라이선스·사용법 확인 후 결정.
