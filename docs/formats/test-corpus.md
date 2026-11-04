# 테스트 문서 코퍼스 구성안

원칙: **배포 권리가 확인된 문서만** `tests/fixtures` 에 커밋하고 출처를 기록한다. 권리가 불명확하거나 개인정보가 있는 실제 문서는
`tests/private/`(커밋 제외)나 비공개 저장소에서 관리한다.

## 공개 코퍼스 (커밋 가능)

| 분류 | 내용 | 확보 방법 |
|---|---|---|
| PDF 기본 | 1쪽 텍스트, 다국어(한글·CJK·RTL), 벡터 도형, 이미지(JPEG/JPX/JBIG2), 폼, 주석, 목차, 회전 페이지, 다양한 페이지 크기 | 자체 제작(LibreOffice/Typst/스크립트로 생성) — "자체 제작"으로 기록 |
| PDF 규격·회귀 | 공개 라이선스 테스트 모음 | 각 파일 라이선스 확인 후 일부만 (예: pdf.js 테스트 중 라이선스 명시 파일, PDF Association 공개 샘플) |
| PDF 대용량 | 500쪽 이상 | 스크립트 생성 |
| HWPX 기본 | 문단 서식, 표(병합·쪽 넘김), 그림, 머리말/꼬리말, 각주, 다단, 수식 | 자체 제작(한컴오피스·HOP 등으로 저장) |
| HWP 5.x | HWPX 기본과 같은 내용을 HWP 로 저장 | 자체 제작 |
| 공공 문서 | 공공누리 1유형(출처표시) 표시가 있는 공문서·서식 | 공공누리 유형 확인 후, 출처·유형 기록 |
| 손상·악성 | 잘린 파일, 잘못된 xref, 압축 폭탄 ZIP, `../` 경로 항목, 거대 이미지 헤더, 깊은 중첩, 레코드 크기 위조 HWP | 스크립트로 생성(재현 스크립트도 함께 커밋) |

## 기록 형식

`tests/README.md` 의 표(파일·출처 URL·라이선스/근거·확인일)를 채운다. 생성 스크립트로 만든 파일은 스크립트 경로를 적는다.

## 회귀 테스트 방식

- 기능: 열기 성공/오류 코드, 페이지 수, 페이지 크기, 텍스트 추출 결과(골든 텍스트).
- 렌더: 기준 PNG 와 픽셀 차이 비율 비교(허용치 정의, OS 별 폰트 차이는 별도 기준).
- 안전: 손상·악성 파일은 패닉·무한 루프·상한 초과 할당 없이 정해진 `ErrorCode` 로 끝나야 한다(시간 제한 포함).
- fuzz: `cargo fuzz` 로 `detect_format`, HWP 레코드 파서, HWPX ZIP/XML 진입점(어댑터 도입 후).

## 문서 모음 회귀 테스트

### 손상 파일 (기본 `cargo test`, CI 3개 OS)

`engine/crates/papy-core/tests/damaged.rs`: 엔진으로 HWP·HWPX·DOCX·PDF 를 만든 뒤 앞부분만 남기기·바이트 뒤집기·0 덮기·뒤에
쓰레기 붙이기로 42가지씩 망가뜨려 연다(168개, 재현 가능한 의사 난수). 패닉 없이 오류로 끝나거나, 열리면 첫 쪽 그리기·글 꺼내기·
목차·그리지 못한 개체 확인이 패닉 없이 끝나야 하고, 하나에 20초를 넘기면 실패한다.

### 무작위 손상·장시간 반복 (손으로 실행, `tests/stress.rs`)

```bash
cd engine
PAPY_FUZZ_ITERS=50000 PAPY_FUZZ_SEED=1 PAPY_FUZZ_OUT=/tmp/papy-fuzz \
  cargo test --release -p papy-core --test stress random_damage -- --ignored --nocapture
PAPY_SOAK_ROUNDS=300 cargo test --release -p papy-core --test stress soak -- --ignored --nocapture
```

- `random_damage`: `cargo fuzz`(nightly) 대신 쓰는 stable 무작위 손상 점검. 시드로 정해지는 손상(바이트 뒤집기·경계값 덮기·
  덩어리 지우기·되풀이·끼우기·자르기)을 1~4가지 겹친다. HWPX·DOCX 는 절반을 ZIP 안 XML 을 망가뜨린 뒤 다시 묶어 해석기까지 닿게 한다.
  실패한 입력은 `PAPY_FUZZ_OUT` 에 저장한다(같은 시드로 다시 돌리면 같은 입력).
- `soak`: 열기·모든 쪽 그리기·찾기·목차·편집·되돌리기·모든 형식 저장·닫기를 되풀이하며 메모리(`ps` 의 RSS)가 계속 늘지 않는지 본다.

### rhwp 샘플 모음 (손으로 실행)

```bash
node scripts/fetch-corpus.mjs     # rhwp v0.8.7 samples/(MIT, 약 700MB) → tests/corpus/rhwp-samples/ (git 제외)
cd engine && PAPY_CORPUS=tests/corpus/rhwp-samples PAPY_CORPUS_GOLDEN=tests/corpus/rhwp-samples.golden.tsv \
  PAPY_PDFIUM_LIBRARY=$PWD/vendor/pdfium/<target>/libpdfium.dylib \
  cargo test --release -p papy-core --test corpus -- --ignored --nocapture
```

GitHub Actions 에서는 `corpus` 워크플로를 손으로 실행한다(macOS, 기본은 첫 쪽만 그림). 폴더 안 PDF·HWP·HWPX·DOCX 를 모두 열고 쪽을
1배로 그린 뒤 글을 꺼낸다. 실패(열기 Internal 오류·그리기·글 꺼내기·패닉)가 있거나 기준값과 다르면 테스트가 실패한다. 문서 내용은
출력하지 않는다.

- **기준값** `tests/corpus/rhwp-samples.golden.tsv`(커밋): 문서마다 형식·쪽 수·쪽 크기 해시·글 해시(FNV-1a)·못 옮긴 요소 수, 열지
  못하는 문서는 오류 코드. 조판·변환이 바뀌면 여기서 드러난다. 의도한 변화면 `PAPY_CORPUS_UPDATE=1` 로 다시 만들고 차이를 PR 에 적는다.
  rhwp 를 올릴 때는 `scripts/fetch-corpus.mjs` 의 커밋도 같이 올린다.
- `PAPY_CORPUS_RENDER=all|first|none`, `PAPY_CORPUS_ROUNDTRIP=1`(같은 형식으로 저장 → 다시 열어 쪽 수·글 비교, 차이는 개수만 출력),
  `PAPY_CORPUS_JOBS`(기본 CPU 수, 시간을 잴 때는 1). 상대 경로는 저장소 루트 기준.
- 출력: 문서마다 열기·그리기 시간, 목차 항목 수, 그리지 못한 개체(차트·OLE 자리표시, 데이터 없는 그림) 수. 끝에 **첫 쪽 표시 시간**
  (열기 + 첫 쪽 그리기, 배율 1)의 형식별 중앙값·90%·99%·최대. 시간은 `PAPY_CORPUS_JOBS=1` 로 잰다.
- 문서 모음에 DOCX 가 없다. DOCX 는 아직 엔진 테스트(생성 문서)로만 본다 — 공개 라이선스 DOCX 모음을 찾아 넣을 것.

### 기록

| 날짜 | 문서 모음 | 결과 |
|---|---|---|
| 2026-11-03 | rhwp v0.8.7 `samples/` 13개(HWP 12·HWPX 1, 224쪽) — macOS Apple Silicon, release | 실패 0. 대부분 쪽당 약 0.1초, 가장 느린 쪽 0.34초. 115쪽 문서 열기 0.57초·전체 그리기 11.7초 |
| 2026-11-04 (2) | 같은 모음, 첫 쪽만, 동시 1 | 실패 0. **첫 쪽 표시 시간**(ms, 중앙값·90%·99%·최대): HWPX 16·56·140·870, HWP 19·80·624·67,029, PDF 3·17·26·81, 전체 15·69·234. 목차 있는 문서 25개(목차 읽기는 모두 0.1초 미만). 그리지 못한 개체가 있는 문서 37개 — 모두 그림 데이터가 없는 그림(결재 문서 서명 칸 등, 빈 그림 자리로 그려진다), 차트·OLE 자리표시는 0 |
| 2026-11-04 (2) | 무작위 손상 50,000번 × 시드 3개 + 20,000번, 장시간 반복 300번 | 실패·시간 초과 0(가장 느린 것 58 ms). rhwp 패닉 1종(`get_page_info_native`, 구역 없는 문서에서 범위 밖 색인)이 나오지만 엔진이 잡아 오류로 돌린다 — 업스트림에 알릴 것. 장시간: 메모리 46.7→57.2 MB 에서 멈춤(160번째 이후 그대로, 누수 없음) |
| 2026-11-04 | rhwp v0.8.7 `samples/` 전체 1,268개(HWP·HWPX·PDF 63, 16,320쪽) — macOS Apple Silicon, release, 동시 12, 모든 쪽 + 저장 왕복 | 실패 0, 4분 49초. 처음 돌렸을 때 HWPX 61개(`mimetype` 압축·뒤·없음)와 HWP 3.x 27개를 "지원하지 않는 형식"으로 거절했다 → 고쳐서 암호 문서 3개 말고 모두 열린다. 저장 왕복에서 글이 바뀌는 문서 13개(모두 HWP 3.x, 아래) |

### 알려진 차이 (2026-11-04)

- **저장 왕복**: 13개 모두 HWP 3.x 문서다. HWP 5.x 로 쓰면 개체 자리(U+FFFC)가 빠지고(그림·도형), 양식 문서
  (`issue5699/20099369_yeongwol_forms.hwp` 등)는 글상자 글도 사라진다. HWP 3.x → HWP 5.x 변환(rhwp)의 한계 — 업스트림에 알릴 것.
  HWP 5.x·HWPX 문서는 저장 왕복 차이가 0 이다. 그래서 HWP 3.x 는 **보기 전용**으로 연다(편집·HWP/HWPX/DOCX 저장 막음, PDF·텍스트
  내보내기만) — `papy-core/tests/fixtures.rs`.
- **느린 문서**: `issue2063_huge_cellbreak_table.hwp`(161쪽)은 열기만 71초, `task2070/…시장구조조사.hwp`(314쪽)은 열기 11초 — rhwp 쪽
  나누기(조판)가 느리다. 나머지는 쪽당 수십 ms.
