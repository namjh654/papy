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

## 판매·고지 점검 (2026-11-09)

저장소 소유자가 PolyForm NC 를 유지하고 나중에 직접 판매(상용 라이선스)하기로 정했다. 저작권자는 같은 코드를 다른 조건으로 줄 수
있으므로 NC 와 상용을 함께 제공하는 이중 라이선스가 된다. 확인한 것:

| 구성요소 | 조건 | 판매 영향 | 적용한 곳 |
|---|---|---|---|
| Papy 코드 | 지금까지 커밋 작성자는 모두 소유자 본인(이름·주소 3개) | 재라이선스에 걸림 없음 | `CONTRIBUTING.md` 에 기여물 재라이선스 권리 조항 추가(앞으로 외부 PR 대비) |
| 이전 Apache-2.0 공개분(`cf10438`~`f2a21d0`) | 그때 받은 사람에게는 Apache-2.0 이 남을 수 있음 | 판매 자체는 막지 않음(그 코드는 남이 상업적으로도 쓸 수 있을 뿐) | 기록만 |
| rhwp v0.8.7 | MIT — 저작권 줄과 허락 문구를 사본에 포함 | 상업적 사용·수정·판매 허용. 수정은 패치로 하므로 고친 파일도 MIT 고지 유지 | `THIRD_PARTY_NOTICES`(이미), `NOTICE` |
| 한컴 HWP 공개 문서(rhwp 가 참고) | 개발 결과물의 화면·매뉴얼·도움말·소스에 "본 제품은 한컴의 HWP 문서 파일(.hwp) 공개 문서를 참고하여 개발하였습니다." 기재. 상업적 이용 제한 문구 없음. 결과물 저작권은 개발자 | 고지만 지키면 판매 가능 | 데스크톱 정보 화면, Android·iOS 라이선스 화면, 웹사이트 바닥글(사용법·도움말 포함 모든 쪽), README, `NOTICE`, `papy-hwp` 소스, 고지 파일 머리 |
| 상표(한글·한컴·HWP·HWPX, Microsoft Word) | 이름은 "HWP 파일을 연다"처럼 호환 설명에만, 제휴로 보이지 않게 | 제품 이름·로고에 쓰지 않는다 | 위와 같은 곳에 상표·비제휴 문구 |
| PDFium(데스크톱) | BSD-3-Clause(고지 포함, Google·기여자 이름으로 홍보 금지) + Apache-2.0(라이선스 사본·NOTICE 전달) + 번들 18개 | 상업적 배포 허용 | `THIRD_PARTY_NOTICES`(이미 18개 전문), `NOTICE` |
| PDFium 안 libjpeg-turbo(IJG) | 제품 문서에 "This software is based in part on the work of the Independent JPEG Group." | 허용 | 데스크톱 정보 화면, README, `NOTICE`, 고지 파일 머리 |
| PDFium 안 FreeType(FTL) | 문서 어딘가에 FreeType 사용을 밝힘(권장 문구) | 허용 | 위와 같음 |
| 글꼴(OFL·GUST) | 글꼴만 따로 팔지 않으면 앱과 함께 판매 가능 | 허용 | 이미 |
| 모바일 Firebase·소셜 SDK | `google-services.json` 이 있을 때만 켜짐(지금 없음) | 켜면 Google 약관·개인정보 고지 필요, "서버 업로드 없음" 문구와 충돌 | 켜기 전 다시 검토 |

남은 결정: 상용 라이선스 문서(가격·범위·지원)와 판매 창구. 법률 자문을 받는 것을 권한다.

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
| 모바일 (`papy-ffi`, `apps/mobile`) | Rust 전이 의존성 281개(Android 대상, 2026-11-04): GPL/AGPL/LGPL 없음. 약한 copyleft 는 UniFFI 0.32(MPL-2.0, `uniffi*` crate 7개, 수정 없이 사용 — 파일 단위 조건). Kotlin 쪽 JNA 5.19.1(Apache-2.0 OR LGPL-2.1 → Apache-2.0 선택). PDF 는 운영체제 렌더러라 PDFium 을 넣지 않는다. 앱 화면의 라이선스 고지는 모바일 배포 전에 넣는다(ADR 0006). |
| 데스크톱 Rust (`src-tauri`) | 전이 포함 402 crate. GPL/AGPL 없음(PolyForm NC 와 결합 가능). 대부분 MIT/Apache-2.0 계열. 약한 copyleft: MPL-2.0 5개(`cssparser`, `cssparser-macros`, `dtoa-short`, `option-ext`, `selectors` — Tauri/wry 경유, 파일 단위 copyleft). |
| JS 직접 의존성 | react·react-dom·vite·@vitejs/plugin-react·@types/react: MIT / typescript: Apache-2.0 / @tauri-apps/*: Apache-2.0 OR MIT |
| 모바일 | 템플릿 의존성(Ktor, Koin, SKIE, Firebase, 소셜 로그인 SDK 등) 미검토 — 모바일 단계 시작 시 정리 |

## 미작성 / 후속

- HWP 고급 편집기(rhwp-studio, ADR 0005): studio·@rhwp/core WASM·@rhwp/editor SDK 모두 MIT. 번들 의존성 canvaskit-wasm(BSD-3-Clause)·@noble/hashes(MIT)·pixelmatch(ISC)·pngjs(MIT). 글꼴은 OFL-1.1(Noto·나눔·Pretendard·고운·D2Coding·스포카 한 산스·Source Han Serif)과 GUST(Latin Modern Math)만 넣는다. studio FONTS.md 에 "무료 배포"로만 적힌 Cafe24·행복고딕은 재배포 조건이 분명하지 않아 빼고, 함초롬체 CDN(비상업 조건·외부 접속)은 `RHWP_DISABLE_EXTERNAL_WEBFONTS=1` 로 끈다.
- `THIRD_PARTY_NOTICES`: `node scripts/third-party-notices.mjs` 가 데스크톱에 들어가는 Rust crate(개발 의존성 제외)·화면 JS 의존성·PDFium·rhwp·Noto Sans KR 의 라이선스 본문을 모아 앱 리소스 `licenses/THIRD_PARTY_NOTICES.txt` 로 넣는다(같은 본문은 한 번만). CI 데스크톱·릴리스 빌드가 매번 만든다. 라이선스 파일을 싣지 않은 crate(2026-11-03 검토 44개: 모두 MIT 를 고를 수 있는 이중·삼중 라이선스이거나 BSD-3-Clause 1개·MPL-2.0 1개)는 SPDX 표준 본문을 Cargo 저작자 이름으로 채워 싣는다(MIT 선택 가능하면 MIT, BSD-3 는 BSD-3 본문). MPL-2.0 은 같은 전문을 실은 다른 crate 를 가리키고, 모든 Rust crate 에 crates.io 소스 주소를 적는다(MPL 의 소스 제공 안내). 결과: 본문 없는 구성요소 0개. 앱의 시작 화면 "정보 · 오픈소스 라이선스"에서 볼 수 있다.
- SBOM: 후보 — Rust `cargo-cyclonedx` 또는 `cargo-about`(고지 생성), JS `pnpm licenses list` / CycloneDX npm, Gradle CycloneDX 플러그인. 도입 시 라이선스·사용법 확인 후 결정.
