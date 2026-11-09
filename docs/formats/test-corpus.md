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
  `PAPY_CORPUS_JOBS`(기본 CPU 수, 시간을 잴 때는 1), `PAPY_CORPUS_PIXELS=파일`(쪽마다 픽셀 해시 — 조판 엔진을 고칠 때
  전후 두 번 돌려 diff). 상대 경로는 저장소 루트 기준.
- 출력: 문서마다 열기·그리기 시간, 목차 항목 수, 그리지 못한 개체(차트·OLE 자리표시, 데이터 없는 그림) 수. 끝에 **첫 쪽 표시 시간**
  (열기 + 첫 쪽 그리기, 배율 1)의 형식별 중앙값·90%·99%·최대. 시간은 `PAPY_CORPUS_JOBS=1` 로 잰다.
- `PAPY_CORPUS_PDF=1`: PDF 가 아닌 문서를 모두 PDF 로 내보내 `%PDF-` 로 시작하는지 본다(실패는 실패로 센다). 끝에 형식별
  문서 수·쪽 수·쪽당 평균 시간을 찍는다.

### Apache POI Word 문서 모음 (손으로 실행)

```bash
node scripts/fetch-corpus.mjs poi   # Apache POI test-data/document 의 .doc·.docx 290개(Apache-2.0) → tests/corpus/poi-documents/
cd engine && PAPY_CORPUS=tests/corpus/poi-documents PAPY_CORPUS_GOLDEN=tests/corpus/poi-documents.golden.tsv \
  PAPY_CORPUS_RENDER=first cargo test --release -p papy-core --test corpus -- --ignored --nocapture
```

`node scripts/fetch-corpus.mjs` 는 인자가 없으면 모든 모음(rhwp, poi)을 받는다. POI 커밋은 스크립트에 고정한다(`7f4d3685`).
기준값은 `tests/corpus/poi-documents.golden.tsv`. 일부러 망가뜨린 문서·Word 6/95·암호 문서는 정해진 오류 코드로 기록된다.

### 형식 변환 점검 (손으로 실행)

```bash
cd engine && cargo run --release -p papy-core --example convert_matrix -- <문서 경로 목록 파일> [docx,hwp,hwpx,pdf] > 결과.tsv
```

문서마다 DOCX·HWP·HWPX·PDF 로 저장하고 다시 열어 쪽 수와 글 보존율(빈칸 뺀 글자 빈도)을 적는다. PDF 를 다시 열려면
`PAPY_PDFIUM_LIBRARY` 가 필요하다. `PAPY_KOREAN_FONT`(앱에 넣는 한글 글꼴 경로, 같은 폴더 글꼴을 모두 읽음)와 `PAPY_FONTS_BUNDLED_ONLY=1`(시스템 글꼴을 읽지 않음 — 한글 글꼴 없는 모바일·Linux 재현)로 글꼴 환경을 바꿔 볼 수 있다(쪽 수는 글꼴과 무관: rhwp 내장 글자 폭 표로 줄을 나눈다). 수식이 많은 문서는 원문 글에 수식 스크립트가 들어 있어(그림으로 옮겨진다) 보존율이 낮게 나온다.

### 변환 왕복 손실 점검 (기본 `cargo test` + 손으로 실행)

쪽 수가 아니라 **내용·구조가 빠졌는지** 본다. 문서를 다른 형식으로 저장해 다시 열고 구조 요약(`Document::structure`: 문단·글자,
자리별 글자(본문·칸·머리말·꼬리말·주석·글상자·캡션), 굵게·기울임·밑줄·색·크기별 글자, 정렬·목록 문단, 쪽·단 나누기, 표·칸 안 표·
병합 칸, 그림·도형·글상자, 머리말·꼬리말·각주·미주, 필드·책갈피·하이퍼링크·메모, 쪽 설정 mm)을 원본과 비교한다.

```bash
cd engine && cargo run --release -p papy-core --example roundtrip_audit -- <문서 경로 목록 파일> > 결과.tsv
```

경로는 Word 원본이면 `hwpx`·`hwpx>docx`·`docx`, 한글 원본이면 `docx`·`docx>hwpx`·`hwpx`. 바뀐 요소마다 한 줄, 끝에 `#` 줄로
경로·요소마다 바뀐 문서 수를 요약한다. 찾은 손실은 고친 뒤 기본 `cargo test` 의 `docx_round_trip_keeps_structure`(papy-hwp)에
요소를 더해 다시 생기지 않게 한다 — 여러 요소를 담은 DOCX 를 세 경로로 거쳐도 구조 요약이 원본과 같아야 한다.

일부러 다른 것(형식 차이): 한글 구역 여럿 → Word 구역은 쪽 나누기 하나로(점검은 뒤 구역 시작을 쪽 나누기로 센다), 수식 → 그림,
묶음 도형 → 풀린 도형, 한글 "쪽 번호 위치" → 꼬리말의 쪽 번호 문단, 번호 목록 → 번호 글자, Word 메모 → 한글 숨은 설명(점검은 한글
메모·숨은 설명·Word 메모를 모두 `comments` 로 센다). 점검은 머리말 쪽 번호 필드 글자도 쪽 번호로 세고, 글자 크기는 반 pt 로
반올림하며(Word 가 담는 단위), 문서 첫 문단의 쪽 나누기는 세지 않는다(보이지 않고 옮기면 사라진다).

### Word 기준 점검 — LibreOffice (손으로 실행)

```bash
cd engine && PAPY_SOFFICE=/Applications/LibreOffice.app/Contents/MacOS/soffice PAPY_SOFFICE_FONTS=<한글 글꼴 폴더> \
  PAPY_PDFIUM_LIBRARY=… cargo run --release -p papy-core --example convert_matrix -- <목록> docx > 결과.tsv
```

저장한 DOCX 를 LibreOffice 로 PDF 로 바꿔(Word 와 비슷한 조판) 쪽 수·글 보존율을 `docx@lo` 줄로 적는다. Papy 로 다시 연 값만
보면 Papy 의 읽기·쓰기가 서로 맞춰 생기는 사각지대가 있어 함께 본다. LibreOffice 가 한글 글꼴을 찾지 못하면(macOS) 글이 통째로
빠지므로 `PAPY_SOFFICE_FONTS` 로 글꼴을 준다(따로 만든 프로필에 넣는다, 시스템은 건드리지 않는다). 문서마다 60초가 넘으면 멈춘다.
LibreOffice 는 Word 와 같지 않다 — Word 문서(DOCX→DOCX)도 4~6% 쪽 수가 다르게 나오는 정도를 기준선으로 본다.

### Word 기준 점검 — Microsoft Word (Windows, 손으로 실행)

```powershell
cd engine; $env:PAPY_SAVE_DIR="<저장 폴더>"; cargo run --release -p papy-core --example convert_matrix -- <목록> docx > 결과.tsv
Get-ChildItem <저장 폴더>\*.docx | % FullName > docx목록.txt
powershell -ExecutionPolicy Bypass -File ..\scripts\word-pages.ps1 -List docx목록.txt -Out word.tsv
```

`PAPY_SAVE_DIR` 이면 `convert_matrix` 가 저장한 파일을 `<문서 이름>.<대상>` 으로 남기고, `scripts/word-pages.ps1` 이 Word 로 하나씩 열어
쪽 수(`ComputeStatistics`)를 적는다(문서마다 Word 를 따로 띄우고 시간을 넘기면 그 Word 만 닫는다). 원본 쪽 수(`convert_matrix` 의
`src_pages`)와 견준다. DOC·DOCX 원본은 같은 스크립트로 Word 쪽 수를 재서 Papy 가 연 쪽 수와 견준다. Word 가 없는 글꼴은 대체 글꼴로
재므로 한글 글꼴이 설치되지 않은 PC 에서는 줄바꿈이 달라질 수 있다.

### 기록

| 날짜 | 문서 모음 | 결과 |
|---|---|---|
| 2026-11-03 | rhwp v0.8.7 `samples/` 13개(HWP 12·HWPX 1, 224쪽) — macOS Apple Silicon, release | 실패 0. 대부분 쪽당 약 0.1초, 가장 느린 쪽 0.34초. 115쪽 문서 열기 0.57초·전체 그리기 11.7초 |
| 2026-11-04 (3) | 같은 모음, 모든 쪽 + 저장 왕복, 동시 12 — rhwp 표 조판 패치 뒤 | 실패 0, 기준값 같음, 저장 왕복 차이 0. 4분 49초 → **1분 28초**. 쪽마다 픽셀 해시가 패치 전후 같다 |
| 2026-11-05 | Apache POI `test-data/document` 290개(DOC 159·DOCX 131), 첫 쪽만 + 저장 왕복, 동시 12 | 실패 0. DOC 131개 열림(못 연 28개: Word 6/95 14·손상/CFB 아님 11·암호 3). 첫 쪽 표시 시간 중앙값 DOC 15 ms·DOCX 8 ms. 149쪽 DOC 열기 3.6 → 1.35초(쪽 나누기 한 번에 적용) |
| 2026-11-05 (2) | rhwp 샘플 1,205개 + POI 290개, 첫 쪽만 + PDF 내보내기(`PAPY_CORPUS_PDF=1`), 동시 12 | 고치기 전 실패 17개(끝이 잘린 PNG 하나 때문에 문서 전체 `InvalidImage`) → 그 그림만 빼고 **실패 0**(1,447개 내보냄). 쪽당 PDF 시간 약 700 → **50 ms**, rhwp 샘플 전체 952 → 91초. 215쪽 HWP 단독 30 → 6.2초(글꼴 파일 매핑·글꼴 DB 재사용·글꼴 고르기 기억, 결과 바이트 같음). rhwp 기준값 그대로. POI 기준값은 bug65649.docx 하나 바뀜(한 문단에 쪽 나누기가 둘이면 두 번째 표시 글자가 남던 것을 고침) |
| 2026-11-05 (3) | 형식 변환 점검(`convert_matrix`): DOC·DOCX 각 30개 안팎(POI), HWP·HWPX 각 30개(rhwp 샘플) × DOCX·HWP·HWPX·PDF | PDF 를 PDFium 으로 다시 열면 글이 거의 안 꺼내지던 것(DOC·DOCX 약 30%)을 고쳐 **글 보존율 DOC→PDF 98→100%·DOCX→PDF 100%**(쪽 그림 배율, 패치 0006). HWP→DOCX 쪽 수 오차 평균 36.7 → 21.1%(정확히 같은 문서 14 → 18개), 글 보존율 0.910 → 0.944. 같은 형식·한글 형식끼리(HWP↔HWPX)는 그대로 차이 0 |
| 2026-11-05 (4) | rhwp 샘플 1,205개 + POI 290개, 첫 쪽(POI 는 모든 쪽) + 저장 왕복 + PDF, 동시 12 | 실패 0. rhwp 기준값 그대로, 저장 왕복 차이 0. POI 기준값은 쪽 크기·여백·다단과 문단 간격·줄 간격·들여쓰기를 이제 읽어서 291개 중 165개가 바뀜(쪽 수 1131 → 1051). 같은 형식 저장 왕복 차이 31개(v0.3.0 도 30개 — DOCX 저장·다시 열기의 조판 차이) |
| 2026-11-06 | 형식 변환 점검(같은 문서), 쪽 수 오차 줄이기 | **HWP→DOCX 쪽 수 오차 평균 21.1 → 7.7%, HWPX→DOCX 24.1 → 14.4%, DOCX→HWPX 9.3 → 0%**. 원인과 고친 것: 표 칸 안쪽 여백을 읽지 않아 칸이 좁아져 숫자가 접힘, 붙여넣은 표 문단에 15pt 들여쓰기(빈 문서 "본문" 모양), 나란히 놓인 표가 세로로 쌓임(테두리 없는 한 줄 표에 넣음), Word 가 요구하는 표 뒤 빈 문단(1pt 로 쓰고 읽을 때 뺌), 칸 경계가 행마다 다른 양식 표의 열 너비(행별 경계의 가운데 값·가는 열 허용, 패치 0010), 장평(패치 0009), 쪽 기준 떠 있는 표 자리(`w:tblpPr`), 표 왼쪽 자리(`w:tblInd`). POI 같은 형식 저장 왕복 차이 31 → 9개. rhwp 기준값 그대로 |
| 2026-11-06 (2) | 형식 변환 점검(같은 문서) | **HWP→DOCX 7.7 → 6.6%, HWPX→DOCX 14.4 → 13.6%**. 단 나누기 읽기·쓰기(2단 시험지, 패치 0011), 첫 줄 들여쓰기가 왼쪽 여백에 더해지던 것(패치 0012), DOCX 첫 문단 서식이 빠지던 것, 한 문단의 표 여럿 사이 빈 줄, 표 위아래 바깥 여백 0.5mm, 쪽 기준 표 문단 줄 높이. 남은 차이의 큰 몫은 글꼴(설치되지 않은 한글 글꼴의 대체 글꼴 너비)로 줄바꿈이 달라지는 것 |
| 2026-11-06 (3) | Word 기준 점검(LibreOffice 26.8, Noto 글꼴) 첫 측정과 개선 | 처음: HWP→DOCX 쪽 수 오차 36.1%·HWPX 37.5%·DOC 11.9%·DOCX 6.1%(Papy 로 다시 열면 6.6%·13.5%·0%·0% — 사각지대 확인). 원인: 한글 배수 줄 간격을 Word 배수로 옮기면 Word 는 글꼴 고유 줄 높이(한글 글꼴 약 1.3배)의 배수라 줄이 30% 높아짐 → "최소(글자 크기 × %)"로. 그 밖에 번호 목록·미주 번호 모양·쪽 번호 필드·글꼴 표. 뒤: **HWP 21.2%·HWPX 27.7%·DOC 1.9%·DOCX 4.7%**, Papy 다시 열기는 그대로(6.6%·13.5%·0%·0%) |
| 2026-11-08 | Word 기준 점검(Microsoft Word 16, Windows 11): POI DOC 123개·DOCX 111개를 Word 로 연 쪽 수, rhwp 샘플 HWP 31·HWPX 35개 → DOCX 를 Word 로 연 쪽 수 | DOC(Papy 가 연 쪽 수 대 Word): 오차 14.2 → **11.1%**(같은 문서 86 → 91개) — 떠 있는 도형·OLE 미리보기·구역 나누기 종류(이어서·새 단)·단 나누기. DOCX 3.7%(같은 문서 100/111). HWP→DOCX(Word): **HWP 26.8 → 18.7%·HWPX 15.5 → 14.0%** — 구역 안 단 정의 바뀜을 이어지는 구역으로(1단↔2단 문서 14 → 9쪽, 원본 7), 행 높이에서 테두리 굵기 빼기(Word 는 더한다), 130% 보다 좁은 배수 줄 간격은 고정으로, 탭 위치·기본 탭 간격. Papy 다시 열기 HWP 27.4 → 24.6%·HWPX 9.8% 그대로. 남은 큰 차이: 2단 시험지에서 왼쪽 단이 몇 pt 넘쳐 단 나누기가 한 단씩 밀리는 문서(8 → 15쪽), 쪽 높이에 꼭 맞춘 서식 표(글꼴 줄 높이 차이) |
| 2026-11-08 (2) | 변환 왕복 손실 점검(`roundtrip_audit`): rhwp 샘플 80개(HWP·HWPX) × `docx`·`docx>hwpx`·`hwpx`, POI 245개(DOC·DOCX) × `hwpx`·`hwpx>docx`·`docx` | 찾아 고친 손실: 글 없는 칸 안 표가 칸째 사라짐(칸 1059 → 39, 패치 0013), 표 담은 문단의 쪽 나누기(23개 문서), 표 칸 첫 평문이 굵게 굳음(패치 0014), DOCX 머리말·꼬리말의 표·그림·서식이 글자로만(표 소실 문서 → 0), **Word 구역 나누기가 쪽을 나누지 않음**·뒤 구역 머리말 버림, 한글 둘째 구역·칸 속 머리말을 DOCX 에 안 씀, 주 번호 앞뒤 글자가 다시 열 때 글로 들어와 왕복마다 늘어남(POI 글자 수가 다른 문서 11 → 1개), 위치 탭(`w:ptab`). POI 기준값: 구역이 여럿인 DOCX 2개 쪽 수(Headers 1 → 3, bug65649 61 → 64 — 구역 나누기), 머리말 있는 문서 글 해시. rhwp 기준값 그대로. 남은 것: 머리말 속 떠 있는 그림·도형, 주석 속 표·그림, 글상자 속 그림, 글상자·캡션 글자, 메모 |
| 2026-11-04 (2) | 같은 모음, 첫 쪽만, 동시 1 | 실패 0. **첫 쪽 표시 시간**(ms, 중앙값·90%·99%·최대): HWPX 16·56·140·870, HWP 19·80·624·67,029, PDF 3·17·26·81, 전체 15·69·234. 목차 있는 문서 25개(목차 읽기는 모두 0.1초 미만). 그리지 못한 개체가 있는 문서 37개 — 모두 그림 데이터가 없는 그림(결재 문서 서명 칸 등, 빈 그림 자리로 그려진다), 차트·OLE 자리표시는 0 |
| 2026-11-04 (2) | 무작위 손상 50,000번 × 시드 3개 + 20,000번, 장시간 반복 300번 | 실패·시간 초과 0(가장 느린 것 58 ms). rhwp 패닉 1종(`get_page_info_native`, 구역 없는 문서에서 범위 밖 색인)이 나오지만 엔진이 잡아 오류로 돌린다 — 업스트림에 알릴 것. 장시간: 메모리 46.7→57.2 MB 에서 멈춤(160번째 이후 그대로, 누수 없음) |
| 2026-11-04 | rhwp v0.8.7 `samples/` 전체 1,268개(HWP·HWPX·PDF 63, 16,320쪽) — macOS Apple Silicon, release, 동시 12, 모든 쪽 + 저장 왕복 | 실패 0, 4분 49초. 처음 돌렸을 때 HWPX 61개(`mimetype` 압축·뒤·없음)와 HWP 3.x 27개를 "지원하지 않는 형식"으로 거절했다 → 고쳐서 암호 문서 3개 말고 모두 열린다. 저장 왕복에서 글이 바뀌는 문서 13개(모두 HWP 3.x, 아래) |

### 알려진 차이 (2026-11-04)

- **저장 왕복**: 13개 모두 HWP 3.x 문서다. HWP 5.x 로 쓰면 개체 자리(U+FFFC)가 빠지고(그림·도형), 양식 문서
  (`issue5699/20099369_yeongwol_forms.hwp` 등)는 글상자 글도 사라진다. HWP 3.x → HWP 5.x 변환(rhwp)의 한계 — 업스트림에 알릴 것.
  HWP 5.x·HWPX 문서는 저장 왕복 차이가 0 이다. 그래서 HWP 3.x 는 **보기 전용**으로 연다(편집·HWP/HWPX/DOCX 저장 막음, PDF·텍스트
  내보내기만) — `papy-core/tests/fixtures.rs`.
- **느린 문서**(고침, 2026-11-04): rhwp 조판이 큰 표에서 "행마다 모든 셀 훑기"(행 수 × 셀 수)를 쪽마다 되풀이했다.
  `engine/patches/rhwp/0001-table-scan-linear.patch` 로 셀을 한 번만 훑게 바꿨다(결과 같음 — 16,320쪽 픽셀 해시 전후 동일).
  `issue2063_huge_cellbreak_table.hwp`(161쪽) 열기 70.3 → 4.5초·모든 쪽 글 31.9 → 4.8초, `task2070/…시장구조조사.hwp`(314쪽)
  열기 10.6 → 2.5초·모든 쪽 글 30.7 → 1.8초. 전체 모음(모든 쪽 그리기) 215 → 81초. 업스트림에 알릴 것.
