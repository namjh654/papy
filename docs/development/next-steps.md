# 이어서 할 일 (2026-11-13 갱신)

다른 세션·다른 모델이 이어받아도 이 파일만 보고 진행할 수 있게 쓴 작업 순서표다. 방향은 [ADR 0008](../decisions/0008-product-order-and-engines.md)을 따른다.

- **문서 3종**(한글 HWP·HWPX, 워드 DOC·DOCX, PDF)을 먼저 끝내고 안정화한 뒤 **배포**한다.
- 그다음 **PPT·PPTX**, 그다음 **XLS·XLSX**를 단계별로 더한다.
- 변환 오차는 앱에서 고지한다. 왕복 쪽 수를 원본과 똑같이 맞추는 작업은 하지 않는다.

위에서부터 차례로 한다. 끝낸 항목은 ~~취소선~~과 날짜를 남긴다. 단계가 다 끝나면 내용을 [roadmap.md](roadmap.md)로 옮긴다.

---

## 작업 규칙 (꼭 지킬 것)

- **보고와 문서는 한국어로** 쓴다. 사용자는 한국어 요약 보고를 원한다.
- **커밋·푸시는 사용자가 시킬 때만** 한다. main에 바로 하지 않고 기능 브랜치에서 PR로 낸다.
  - **병합은 사용자가 한다.** 자동 모드는 `gh pr merge`를 거부한다.
  - force push는 막혀 있다. 실수는 고치는 커밋을 하나 더 해서 바로잡는다.
  - 커밋 메시지 끝에는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, PR 본문 끝에는 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`를 붙인다.
    다른 모델이 이어받으면 그 모델 이름으로 바꾼다.
- **`v*` 태그를 올리면 바로 공개 릴리스가 빌드된다**(`.github/workflows/release.yml`, 설치 파일이 GitHub Release에 올라간다).
  태그 이름과 시점은 반드시 사용자에게 먼저 묻는다. `-`가 들어간 태그(`v0.4.0-rc.1`)는 시험판(prerelease)이 된다.
- 서식 맞추기는 `cargo fmt -p papy-hwp -p papy-office -p papy-core` 등 **우리 crate만** 한다.
  `cargo fmt --all`은 vendor(rhwp)까지 바꾸므로 금지다.
- rhwp는 직접 고치지 않는다. `engine/patches/rhwp/NNNN-*.patch`를 고친 뒤 받아 오기를 다시 한다. 만드는 법은 아래 "rhwp 패치"에 있다.
- 손실이나 버그를 고칠 때마다 회귀 테스트를 붙인다. 왕복 손실이면 `papy-hwp`의 `docx_round_trip_keeps_structure` 등에 넣는다.

## 환경 준비 (macOS, 저장소 루트에서)

```bash
export PATH="$HOME/.cargo/bin:$PATH"
pnpm engine:vendor                 # rhwp v0.9.0 + 패치, PDFium, rhwp-studio, 글꼴
node scripts/fetch-corpus.mjs      # 문서 모음(rhwp 샘플·POI, git 제외)
cd engine
export PAPY_PDFIUM_LIBRARY=$PWD/vendor/pdfium/aarch64-apple-darwin/libpdfium.dylib
cargo test --workspace
cargo clippy -p papy-hwp -p papy-office -p papy-core --all-targets -- -D warnings
```

자주 쓰는 점검(모두 `engine/`에서):

| 점검 | 명령 | 비고 |
|---|---|---|
| 시험지 12개 DOCX 왕복 쪽 수 | `./target/release/examples/convert_matrix <목록> docx` | 목록은 `tests/corpus/rhwp-samples/exam*.hwp` 12개 경로. 빌드: `cargo build --release -p papy-core --example convert_matrix`. 결과 열은 `src_pages`·`pages`·`keep` |
| 문서 모음 기준값 | `PAPY_CORPUS=tests/corpus/rhwp-samples PAPY_CORPUS_GOLDEN=tests/corpus/rhwp-samples.golden.tsv cargo test --release -p papy-core --test corpus -- --ignored --nocapture` | 경로는 저장소 루트 기준이다. POI는 `poi-documents`로 바꾼다. 의도한 변화면 `PAPY_CORPUS_UPDATE=1`로 다시 쓰고 diff를 확인한다 |
| 왕복 손실 점검 | `cargo run --release -p papy-core --example roundtrip_audit -- ../tests/corpus/roundtrip-audit-rhwp.txt` | POI는 `roundtrip-audit-poi.txt`. 결과의 `# ` 줄에 요소별 원본 합 → 결과 합 |
| 쪽 배치 비교 | rhwp `dump_page_items(None)`(쪽·단마다 문단 높이·vpos) | 원본과 왕복 결과를 같은 문단끼리 맞대어 어디서 어긋나는지 찾는다 |

## rhwp 패치

- 패치는 `engine/patches/rhwp/`에 있다(0002~0025, 0001·0013·0020은 0.9.0에서 업스트림이 고쳐 뺐다).
  0023: 만들 수 없는 링크 누름 영역은 건너뜀(PDF 내보내기 전체 실패 방지). 0024: Batch 중 재조판·쪽 나누기를 끝으로 미룸(여는 시간). 0025: 큰 표 행 자르기에서 칸마다 표 전체를 훑지 않음(0.8.7의 0001을 대신함).
- `scripts/fetch-rhwp.mjs`가 v0.9.0을 sparse로 받아 패치를 차례로 적용한다.
- 패치를 고칠 때:
  1. 깨끗한 v0.9.0 사본에 앞 패치들을 적용한다.
  2. 고칠 파일을 수정한다.
  3. `git diff`로 그 패치 파일을 다시 만든다.
  4. `pnpm engine:vendor`로 다시 받아 확인한다.
- vendor 폴더를 직접 고친 내용은 다시 받기를 하면 **사라진다**.

---

## 1단계 — 문서 3종 마무리 (지금)

### 1-1. ~~미커밋 작업 정리 → PR #31 갱신~~ (2026-11-11 완료)
브랜치 `feature/rhwp-0.9`(PR #31)에 커밋·푸시했다.

- rhwp 패치 0018: foreign_layout 문서의 종이 기준 자리차지 표는 저장 vpos를 쓰지 않고 본문을 침범하는 하단으로만 맞춘다(exam_kor 25 → 24쪽).
- `papy:fontspace="0"`: 원본이 "글꼴에 어울리는 빈칸"을 쓰지 않았으면 다시 열 때 켜지 않는다.
  켤 때는 빈 문서 틀 글자 모양의 원시 레코드를 지워 HWP로 저장해도 설정이 남게 했다.
- 어울림 표(`papy:wrap="square"`)를 떠 있는 표로 쓰고 되읽는다. 표를 품은 문단의 단 나누기는 표 앞으로 옮긴다.
- 확인: `cargo test --workspace`, clippy, 문서 모음 기준값(rhwp 1,268개·POI 290개, 변화 없음), 시험지 12개(exam_kor만 25 → 24, 나머지 그대로).
- exam_kor(원본 20쪽, 지금 24쪽)는 **더 맞추지 않는다**(ADR 0008). 깨진 배치(겹침·잘림)가 보이면 그것만 고친다.

### 1-2. PR 병합 (사용자)
PR #30(`feature/fidelity-exam-kor`)과 #31(`feature/rhwp-0.9`)을 병합한다. 병합된 브랜치는 지워도 된다(사용자 허락함).

### 1-3. ~~변환 오차 고지 (앱)~~ (2026-11-11 완료, 브랜치 `feature/conversion-notice`)
- 데스크톱(`apps/desktop/src/convert.ts`, `App.tsx`):
  - 한글 ↔ 워드로 저장·내보내기 전에 확인 창을 띄운다("계속", "다시 묻지 않고 계속", "취소").
  - Word 문서를 열면 안내 띠를 띄운다("다시 보지 않기", "닫기"). 고른 값은 이 기기 앱 저장소에만 남는다.
- 모바일(`ViewerUiState.showLayoutNotice`): Word 문서를 열면 안내를 띄운다(옮기지 못한 요소 안내가 먼저, 한 번에 하나).
  iOS 쪽은 코드만 넣었고 **Xcode 빌드는 아직 확인하지 않았다**.
- 웹사이트 사용법(한·영)과 `docs/formats/word.md` "알려진 한계"에 같은 내용을 넣었다.
- 남은 확인: 데스크톱 앱을 실제로 띄워 확인 창·안내 띠 보기, iOS 빌드.

### 1-4. ~~rhwp 버전 맞추기~~ (2026-11-11 완료, 브랜치 `feature/rhwp-studio-0.9`)
- rhwp-studio·@rhwp/core WASM·SDK 사본(`apps/desktop/src/vendor/rhwp-editor`)·문서 모음을 모두 v0.9.0(`c9095c5f`)으로 올렸다.
- studio 빌드 결과가 41 → 65MB로 커졌다(WASM 11.8 → 26.8MB). 설치 파일도 그만큼 커진다.
- 문서 모음: rhwp 샘플 1,268 → 1,606개. 겹치는 1,259개 가운데 기준값이 바뀐 것은 업스트림이 파일을 바꾼 PDF 2개뿐이다.
- 점검: 헤드리스 Chrome에서 SDK로 studio를 띄워 HWP를 넣고 HWP·HWPX로 내보낸 것을 네이티브 엔진이 같은 쪽 수로 열었다([ADR 0005](../decisions/0005-rhwp-studio-editor.md)).
- 남은 확인: studio 안에서 실제 입력(칸에 글자·행 넣기), 데스크톱 창(Tauri)에서 고급 편집 열기 — 2단계 수동 확인 때 같이 본다.

### 1-5. ~~남은 손실~~ (2026-11-11 정리, 브랜치 `feature/stabilize-0.4`)
- ~~그림 자르기(`a:srcRect` ↔ 한글 그림 자르기)~~: 원본 그림 픽셀 크기를 읽어(`papy-office/src/image_size.rs`) 양방향으로 옮긴다.
- ~~확장 바탕쪽(마지막 쪽)~~: 마지막 쪽 바탕쪽 개체를 구역 끝에 놓는다(`papy-hwp/src/masters.rs`).
- ~~DOCX에서 연 문서를 HWP·HWPX로 저장했다 다시 열면 쪽이 느는 문제~~. 원인 세 가지를 고쳤다.
  - 조판 표식(다른 형식에서 옮긴 문서)이 저장되지 않았다 → 쓰이지 않는 글꼴 이름(`Papy Layout Word`)으로 글꼴 목록에 남기고 열 때 되살린다.
  - 빈 문단 글자 크기가 HWPX에서 빠졌다 → 줄 높이에 맞는 글자 모양을 준다(`pin_empty_paragraph_sizes`).
    이 때문에 DOC·DOCX를 **열 때**도 빈 줄이 원본 크기로 조판되어 POI 22개 문서의 줄 배치가 바뀌었다(쪽 수는 4개가 1쪽씩 줆, 글 내용은 같음). 기준값을 다시 만들었다.
  - 저장 글줄이 없으면 머리 표 높이가 통째로 더해졌다 → 패치 0018 보강.
  - 결과: 시험지 1~4쪽 견본·과학은 다시 열어도 같은 쪽 수, exam_eng·exam_kor만 ±1쪽.
- ~~머리말·꼬리말 안 번호 목록~~ → 한글 문단 번호로 옮긴다.
  남김(rhwp 제약): 각주·글상자 안 목록(번호 글자로 남음), 여러 문단을 감싸는 누름틀(글만 남음).
- ~~성능~~: 패치 0024. 각주 많은 DOCX(`bug65649.docx`) 2,375 → 1,248 ms, 도형 57개 DOC(`Bug61268.doc`) 2,753 → 2,072 ms. 결과는 패치 전과 같다.
- 주의: rhwp 0.9 배치 모드는 `paginate`를 미룬다. 배치 안에서 쪽·줄 자리를 묻지 말 것(그림 자리는 `shapes::measure`로 배치 전에 잰다).
  Papy 표시 글자는 U+E0xx를 쓴다(0.9가 U+E0BC~를 옛한글로 바꾼다).

## 2단계 — 안정화

완료 기준:
- 문서 모음(rhwp 샘플·POI·PDF) 열기·그리기·PDF 내보내기 실패 0, 크래시 0
- 기준값 테스트 통과
- 무작위 손상·장시간 반복 테스트 실패 0
- 세 데스크톱 OS와 두 모바일 OS에서 실제 문서를 수동으로 확인

1. 0.9로 올린 뒤 회귀를 다시 잰다:
   - ~~무작위 손상 반복(fuzz 대용)과 장시간 반복~~(2026-11-12): 17만 번·300번 실패 0, 누수 없음. 결과는 [performance.md](performance.md).
   - ~~첫 쪽 표시 시간~~(2026-11-12): 중앙값 5~20 ms. 큰 표 문서 38초는 패치 0025로 4.4초(픽셀 해시 전후 같음).
   - ~~PDF 내보내기 전체 점검~~(2026-11-11): rhwp 샘플 1,606개·POI 290개, PDF 내보내기·저장 왕복 실패 0.
     0.9에서 깨졌던 1건(`한글문서파일형식_5.0_revision1.3.hwp`)은 패치 0023으로 고쳤다.
     같은 형식 저장 왕복에서 쪽 수가 달라지는 문서(실패로 세지 않음): `80168_regulatory_analysis.hwp` 157 → 159,
     `issue1891/80168_regulatory_analysis.hwpx` 156 → 159, `bug59058.docx` 73 → 70. 나머지 8개는 쪽 수는 같고 글만 다르다.
     2026-11-12 확인: 세 문서 모두 **내용 손실 없음**(공백을 빼고 이은 글이 같다. HWP는 쪽 번호 꼬리말만, HWPX는 쪽마다 반복되는 표 머리줄이 한 번 더).
     다시 열 때 rhwp가 저장된 줄 자리를 다르게 써서 쪽 나눔이 바뀌는 것으로 보인다 — **남음**(원인: rhwp 저장·조판 내부).
2. Windows(Windows PC에서):
   - 데스크톱으로 실제 문서 확인, 한글 입력기, rhwp-studio 창·입력기
   - Word 기준 쪽 수 측정(`scripts/word-pages.ps1`)
   - 누름틀 문서(`field-01.hwp` → DOCX → HWP)가 한글에서 열리는지
3. Linux: 데스크톱 실제 문서 확인, rhwp-studio 창.
4. 모바일: Android 릴리스 준비(R8·16KB 페이지·ABI·권한), iOS 실기기 확인. iOS 시뮬레이터 빌드는 통과(2026-11-11, 변환 고지 포함). 문서 3종의 보기·편집·PDF 내보내기를 확인한다.
5. ~~성능 리포트~~(2026-11-12): [performance.md](performance.md). 기획서 6.1 목표값과의 비교는 남음(기획서가 저장소에 없음).

## 3단계 — 배포

2026-11-11 사용자 결정: 먼저 **v0.4.0-rc.1 시험판**을 낸다(GitHub Release에 prerelease로 올라간다). 설치 파일을 받아 확인한 뒤 문제가 없으면 v0.4.0 정식판을 낸다.
2026-11-12 사용자 결정: 수정판 시험판(rc.2)은 내지 않는다. 문서 안정화를 마치면 **v0.4.0 정식판을 코드 서명 없이** 올린다. **모바일은 보류**한다(모바일 버전·스토어는 그대로 둔다).

1. ~~버전을 0.4.0-rc.1로 올림~~(데스크톱 `package.json`·`tauri.conf.json`·`Cargo.toml`, 엔진 `Cargo.toml`, `release.yml` 릴리스 글).
   모바일 버전과 웹사이트·README의 "안정판 v0.3.4" 문구는 정식판 때 바꾼다(iOS 버전 번호는 숫자만 된다).
2. PR 병합(#30 → #31 → #32 → #33 → 버전 PR) 뒤 main에 `v0.4.0-rc.1` 태그를 올린다(`release.yml`이 Windows·macOS·Linux 설치 파일을 만든다).
3. 시험판 확인(2단계 수동 확인과 같이 한다): 설치 파일을 받아서 설치, 문서 3종 열기·편집·저장·PDF 내보내기, 변환 고지 창·안내 띠, 고급 편집(studio 0.9).
   2026-11-12: macOS dmg를 받아 열고 앱을 띄워 10초 동안 죽지 않음을 확인했다(약 100 MB). 화면 캡처 권한이 없어 창 안 확인은 못 했다 — **남음(사용자)**.
4. ~~정식판 v0.4.0~~(2026-11-12): 데스크톱·엔진 버전 0.4.0, 릴리스 글·README·웹사이트 공지 갱신, `v0.4.0` 태그.
   코드 서명은 하지 않았다([code-signing.md](code-signing.md)). 모바일 버전은 보류라 그대로다.

## 배포 뒤 정리 (2026-11-13)

- ~~v0.4.1~~: 앱 아이콘을 웹사이트 로고와 같게 바꿨다(`apps/desktop/src-tauri/icons/source/`). 기능은 v0.4.0 과 같다.
- 무료 설치 경로(유료 서명은 보류, 사용자 결정):
  - ~~macOS 터미널 설치~~: `curl -fsSL https://papy-omega.vercel.app/install.sh | sh`. curl 로 받으면 격리 표시가 없어 경고 없이 열린다.
  - winget: `namjh654.Papy` 0.4.0 을 microsoft/winget-pkgs#478076 로 냈다(CLA 동의함). 검토를 기다리는 중이다.
    병합되면 설치 방법 페이지에 `winget install namjh654.Papy` 를 넣고 0.4.1 PR 을 낸다([packaging/winget](../../packaging/winget/README.md)).
- ~~웹 통계~~: 다운로드 수는 `/stats/`, 방문 수는 Vercel Analytics(대시보드에서 **Enable** 필요, `website/README.md`).

## AI 플러그인 (순서 미정, [ADR 0009](../decisions/0009-ai-plugin.md))

2026-11-13 사용자 결정: 선택 설치하는 AI 플러그인을 만든다. 앱 안 대화창과 MCP 서버를 함께 두고, Claude·ChatGPT·Gemini 셋 다 연결한다.
PPT 단계 앞에 할지 뒤에 할지는 사용자가 정한다.

1. `papy-ai` 뼈대, Papy 도구(읽기·찾기·구조·제안·내보내기), 단독 MCP 서버(stdio). Claude 데스크톱·Gemini CLI 로 확인한다.
2. 앱 대화창과 API 키 연결(Anthropic·OpenAI·Gemini, 키는 OS 키체인). 제안 미리보기·적용·되돌리기, 토큰 사용량을 보여 준다.
3. 플러그인 설치 관리(받기·ed25519 서명 확인·지우기), CI 빌드·서명, 외부 전송 확인 창, 웹 개인정보·FAQ 고지.
4. Ollama(로컬) 연결.
5. ACP: Claude Code·Gemini CLI·Codex CLI 연결. 각 회사의 허용 범위를 먼저 확인한다.
6. 앱 연결 MCP(`--attach`, 앱에 열린 문서 다루기).

## 4단계 — PPT·PPTX (배포 뒤)

[ADR 0007](../decisions/0007-xlsx-pptx.md) 1안: 슬라이드 하나를 한 쪽으로(쪽 크기 = 슬라이드 크기), 도형을 쪽 기준 개체로 옮겨 rhwp로 그린다.

1. 시험 문서 모음을 만든다: Apache POI test-data(Apache-2.0)의 pptx와 직접 만든 문서. `tests/README.md`에 출처를 적는다.
2. PPTX 보기 PoC(`papy-office` `pptx_to_document`):
   - 형식은 `ppt/presentation.xml`로 판별한다.
   - 마스터·레이아웃 상속, 글머리표, 배경을 옮긴다.
   - 차트·SmartArt·효과·애니메이션은 `dropped_objects`로 센다.
   - 완료 기준: 문서 모음 실패 0, 슬라이드 수·글자 일치.
3. 앱을 연결한다:
   - 데스크톱: 여는 확장자(`OPENABLE`), 파일 연결, 대화상자 필터
   - 모바일: MIME, intent-filter, UTType
   - 둘 다 썸네일·찾기·PDF 내보내기
4. PPT(97–2003 이진) 읽기(PPTX 뒤).
5. 글상자 글자 편집(필요하면). PPTX 다시 쓰기는 손실이 커서 맨 뒤로 미룬다.

## 5단계 — XLS·XLSX (PPT 뒤)

ADR 0007 XLSX 안: 시트 모델과, 엔진이 그리는 격자 조각을 쓴다. **PDF 내보내기는 지원하지 않는다**(ADR 0008).

1. 엔진 PoC: IronCalc와 calamine을 견준다(열기 시간·메모리·서식·수식 결과). 엔진 선택 ADR을 승인받는다.
2. `Workbook` API(시트·열 너비·행 높이·칸·병합)와 `render_tile`(tiny-skia + 지금의 글꼴)을 만든다.
3. 데스크톱 화면: 시트 탭, 행·열 머리글, 틀 고정, 조각 스크롤, 칸 선택·복사.
4. 모바일 화면(조각 확대·이동, 시트 고르기)과 `papy-ffi`를 연결한다.
5. 편집: 칸 값·수식 고치기, 저장, 되돌리기. XLS(97–2003)는 그 뒤에 한다.
