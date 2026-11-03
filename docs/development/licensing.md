# 라이선스 검토

## 프로젝트 라이선스

- **PolyForm Noncommercial 1.0.0** (SPDX `PolyForm-Noncommercial-1.0.0`) — 2026-11-03 저장소 소유자 결정.
  원문: 공식 저장소 `polyformproject/polyform-licenses` 의 `PolyForm-Noncommercial-1.0.0.md` 를 그대로 사용(확인일 2026-11-03).
- 적용 범위: 저장소의 모든 소스와 배포 파일(데스크톱 설치 파일, 이후 모바일 앱·웹 산출물). 서드파티 코드·폰트·바이너리는 각자 라이선스.
- 이전 커밋(`cf10438`~`f2a21d0`)은 Apache-2.0 LICENSE 와 함께 공개됐다. 그 시점 사본을 받은 사람에게는 Apache-2.0 이 계속 유효할 수 있다.
- 조건 요약: 비상업적 목적의 사용·수정·배포 허용, 배포 시 라이선스 전문(또는 URL)과 `Required Notice:` 줄 전달, 특허 소송 시 라이선스 종료,
  위반 통지 후 32일 내 시정하지 않으면 종료.

### 이 라이선스가 주는 제약

| 항목 | 영향 |
|---|---|
| OSI 오픈소스 아님 | "오픈소스"가 아닌 "소스 공개"로 표기. 오픈소스 전용 프로그램·패키지 저장소(일부 배포판 공식 저장소, F-Droid 등)에 올리기 어렵다 |
| 상업적 사용 불가 | 회사 업무용 사용도 상업적 목적에 해당할 수 있다. 기업 사용자에게는 별도 상용 라이선스가 필요 |
| copyleft 서드파티와 결합 | GPL·AGPL 은 상업적 사용 제한을 추가하는 것을 허용하지 않으므로 **GPL/AGPL 구성요소는 함께 배포할 수 없다** (예: MuPDF AGPL 판). LGPL·MPL 은 파일·라이브러리 단위 조건을 지키면 가능 |
| 허용형 서드파티 | MIT·BSD·Apache·Zlib·OFL 등은 고지만 지키면 함께 배포 가능 (PDFium, rhwp, Tauri 등) |
| 외부 기여 | 기여물도 같은 라이선스로 받는다. 향후 상용 라이선스를 따로 팔려면 기여자에게 추가 권리(CLA 등)가 필요 — 결정 필요 |

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
| 엔진 (`engine/`) | PDFium 사전 빌드(BSD-3-Clause + Apache-2.0, 서드파티 고지 18개 — 받은 폴더의 `LICENSE`·`licenses/`), `pdfium-render` 0.9.5(MIT OR Apache-2.0), rhwp v0.8.7(MIT, 고지 `THIRD_PARTY_LICENSES.md`), resvg 0.45.1(Apache-2.0 OR MIT), Noto Sans KR Regular(OFL-1.1, rhwp 저장소 `ttfs/opensource` — PDF 텍스트 상자에 넣고 HWP 대체 글꼴로 씀. OFL 은 글꼴 단독 판매만 금지하고 소프트웨어와 함께 배포·문서에 넣는 것을 허용). rhwp+resvg 전이 의존성 235개 전부 허용형(MIT·Apache·BSD·Zlib·Unicode-3.0·0BSD·CC0·BSL-1.0 등), GPL/AGPL/MPL 없음 (`cargo tree --format {l}`, 2026-11-03). |
| 데스크톱 Rust (`src-tauri`) | 전이 포함 402 crate. GPL/AGPL 없음(PolyForm NC 와 결합 가능). 대부분 MIT/Apache-2.0 계열. 약한 copyleft: MPL-2.0 5개(`cssparser`, `cssparser-macros`, `dtoa-short`, `option-ext`, `selectors` — Tauri/wry 경유, 파일 단위 copyleft). |
| JS 직접 의존성 | react·react-dom·vite·@vitejs/plugin-react·@types/react: MIT / typescript: Apache-2.0 / @tauri-apps/*: Apache-2.0 OR MIT |
| 모바일 | 템플릿 의존성(Ktor, Koin, SKIE, Firebase, 소셜 로그인 SDK 등) 미검토 — 모바일 단계 시작 시 정리 |

## 미작성 / 후속

- `THIRD_PARTY_NOTICES`: `node scripts/third-party-notices.mjs` 가 데스크톱에 들어가는 Rust crate(개발 의존성 제외)·화면 JS 의존성·PDFium·rhwp·Noto Sans KR 의 라이선스 본문을 모아 앱 리소스 `licenses/THIRD_PARTY_NOTICES.txt` 로 넣는다(같은 본문은 한 번만). CI 데스크톱·릴리스 빌드가 매번 만든다. 라이선스 파일을 싣지 않은 crate 는 SPDX 식별자만 적힌다(2026-11-03 기준 44개) — 배포 전 검토.
- SBOM: 후보 — Rust `cargo-cyclonedx` 또는 `cargo-about`(고지 생성), JS `pnpm licenses list` / CycloneDX npm, Gradle CycloneDX 플러그인. 도입 시 라이선스·사용법 확인 후 결정.
