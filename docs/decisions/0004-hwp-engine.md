# 0004. HWP/HWPX 엔진

- 상태: **승인** (2026-11-03, 저장소 소유자 — "추천대로 rhwp")
- 조사일: 2026-11-03 (GitHub API·저장소 README·Cargo.toml 기준)

## 후보 비교

| 항목 | rhwp | OpenHWP | hwp-rs | 자체 구현 |
|---|---|---|---|---|
| 저장소 | [edwardkim/rhwp](https://github.com/edwardkim/rhwp) | [openhwp/openhwp](https://github.com/openhwp/openhwp) | [hahnlee/hwp-rs](https://github.com/hahnlee/hwp-rs) | — |
| 라이선스 | MIT (서드파티 고지 [THIRD_PARTY_LICENSES.md](https://github.com/edwardkim/rhwp/blob/main/THIRD_PARTY_LICENSES.md), 폰트 OFL-1.1) | MIT | Apache-2.0 | — |
| 최신 / 활동 | v0.8.7 (2026-10-06), 마지막 push 2026-11-02, 기여자 75명, ★4k | 마지막 커밋 2025-12-12 (약 11개월 정체), ★93 | 마지막 push 2022-11-11, crates.io `hwp` 0.2.0 | — |
| HWP 5.x 읽기 | O (암호 문서 포함) | O | O(일부) | 큰 작업 |
| HWPX 읽기/쓰기 | O / O (의미 보존 저장) | O / O | X | 중간 |
| **레이아웃·렌더링** | **O** — 문단·표·다단·머리말/꼬리말·각주·수식·차트, SVG/PNG/PDF 출력, Canvas(WASM) | X (파서·모델만) | X | 가장 큰 작업 |
| 테스트 | Rust 테스트 3,400+, 시각 회귀 CI, fuzz 디렉터리 | 적음 | 적음 | — |
| 배포 형태 | crates.io 미등록 → git 태그 고정 의존 | git 의존 | crates.io | — |
| Rust | 1.93.1, edition 2021 (Papy 1.99.1 과 호환) | — | — | — |
| 무게 | 의존성 많음(암호화 crate 다수, image, rustybuzz 등) | 가벼움 | 가벼움 | — |
| 참고 | 같은 엔진 위에 만든 데스크톱 앱 [HOP](https://github.com/golbin/hop)(MIT)가 이미 있음 | | | |

## rhwp 가 맞는가

맞다고 본다. 근거와 위험은 다음과 같다.

- **근거**: HWP/HWPX 둘 다 "렌더링까지" 되는 유일한 Rust 후보다. 기획서 P0(HWPX 열람)와 HWP 5.x 검증 게이트를 동시에 앞당긴다.
  MIT 라 PolyForm NC 와 결합할 수 있고, 테스트·회귀 체계가 Papy 의 "검증 우선" 원칙과 맞는다.
- **위험**:
  - v1.0 전이라 API 가 바뀔 수 있다 → `papy-hwp` 어댑터 한 곳에서만 rhwp 를 부르고, git **커밋 고정**으로 올린다.
  - crates.io 미등록 → `Cargo.lock` 으로 재현성 확보, 업스트림 변경은 PR 단위로 검토.
  - 의존성이 무겁다 → 바이너리 크기 측정(기획서 6.1), 필요 없는 기능은 feature 로 끌 수 있는지 PoC 에서 확인.
  - 단일 핵심 개발자 의존(공동 유지보수자 2명 있음) → 포크 가능한 MIT, 어댑터 경계로 교체 비용 제한.
- **대안 사용처**: OpenHWP 의 HWPX 쓰기 모델은 편집 단계(기획서 7장 3단계)에서 비교 대상으로 남긴다.

## 승인 시 할 일 (PoC)

1. `papy-hwp` 에 rhwp git 의존성(태그 v0.8.7 커밋 고정), HWPX·HWP `FormatBackend` 구현.
2. rhwp 의 페이지 렌더 결과(PNG/RGBA)를 `Bitmap` 으로 변환, 텍스트 추출.
3. 지원하지 않는 요소는 정보 손실 경고로 노출(기획서 3.2).
4. 코퍼스 회귀 테스트, 폰트 폴백 정책 확인(OFL 폰트 번들 여부).

## 구현 메모 (2026-11-03)

- **받는 방법**: rhwp 저장소는 문서·PDF·샘플 포함 약 5.3GB 라 cargo git 의존성으로 쓰면 CI 마다 전체를 받는다.
  `scripts/fetch-rhwp.mjs` 가 v0.8.7 커밋(`1a76570e`)을 얕게·sparse 로 받아 빌드에 필요한 파일(약 47MB: `Cargo.toml`, `build.rs`,
  `src/`, `crates/`, `saved/blank2010.hwp`, 라이선스)만 `engine/vendor/rhwp` 에 둔다(gitignore). 엔진 워크스페이스는 `exclude = ["vendor"]`.
  rhwp 의 `[patch.crates-io]`(svg2pdf 포크)는 의존성으로 쓸 때 적용되지 않는다 — Papy 는 rhwp 의 PDF 내보내기를 쓰지 않으므로 영향 없다.
- **렌더 경로**: rhwp `DocumentCore::render_page_svg_native`(96 DPI px) → resvg 0.45.1(MIT OR Apache-2.0, rhwp 와 같은 usvg) 로
  `Bitmap` 래스터화. `native-skia` feature(skia-safe 사전 빌드 수십 MB)는 쓰지 않는다. A4 한 쪽 약 5ms(릴리스, M 시리즈).
- **글꼴**: 시스템 글꼴을 한 번 읽어(약 250ms) 쓰고, 문서 글꼴이 없으면 맑은 고딕/Apple SD Gothic Neo/Noto Sans CJK KR 순으로 대체.
  OFL 한글 글꼴 번들 여부는 렌더 품질 비교 후 결정한다.
- **안전**: rhwp 호출은 `catch_unwind` 로 감싸고 패닉 뒤 그 문서는 닫힌 것으로 취급한다. rhwp 오류 원문은 버리고 고정 문구만 낸다.
  SVG 의 외부 파일 참조는 따라가지 않는다. rhwp 가 stderr 에 파서 진단(본문 아님)을 찍는 경우가 있다.
- **테스트 문서**: rhwp 로 빈 문서를 만들어 글자·쪽 나누기·암호를 넣고 HWP/HWPX 로 저장해 쓴다(저작권 없음).
- **미구현**: 목차, 제목 메타데이터, 정보 손실 경고(기획서 3.2), HWPML 판별. (2026-11-04: HWP 3.x 판별·보기 전용 열기, 코퍼스 회귀 완료 — [test-corpus.md](../formats/test-corpus.md))

