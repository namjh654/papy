# 이어서 할 일 (2026-11-09 갱신)

Windows 에서 하던 변환 품질 작업을 macOS 에서 잇기 위한 메모. 끝나면 이 파일을 지우거나 [roadmap.md](roadmap.md) 로 옮긴다.

## 지금 상태

- 브랜치 `feature/windows-fidelity`(원격에 올림, PR 열림 — main 병합은 사용자가).
- 우선순위(사용자와 정함): **① 버그를 찾아 재발하지 않게**(DOCX↔HWPX 내용·구조 100%, 쪽 수는 가깝게) → ② DOC·HWP 변환 문제·성능
  → ③ XLSX·PPTX. 코드 서명(로드맵 항목 2)은 보류.
- 원칙: 손실을 고칠 때마다 회귀 테스트를 붙인다. 왕복 손실은 `papy-hwp` 의 `docx_round_trip_keeps_structure` 원본 DOCX 에
  요소를 더하고 기대 수를 적는다(기본 `cargo test`, CI).

## macOS 준비

```bash
git fetch && git switch feature/windows-fidelity && git pull
node scripts/fetch-rhwp.mjs          # rhwp v0.8.7 + engine/patches/rhwp 0001~0017 적용
node scripts/fetch-corpus.mjs        # rhwp 샘플·POI 문서(git 제외)
cd engine && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings
```

## 왕복 손실 점검 돌리기

```bash
cd engine
cargo run --release -p papy-core --example roundtrip_audit -- ../tests/corpus/roundtrip-audit-rhwp.txt > /tmp/audit-rhwp.tsv
cargo run --release -p papy-core --example roundtrip_audit -- ../tests/corpus/roundtrip-audit-poi.txt  > /tmp/audit-poi.tsv
grep '^# ' /tmp/audit-rhwp.tsv     # 경로·요소마다 바뀐 문서 수·원본 합·결과 합
```

목록 파일은 Windows 에서 쓴 것과 같은 문서(rhwp 샘플 80개, POI 245개). 2026-11-08 마지막 결과(기준선, 한글 원본의 `docx` 경로 =
HWP/HWPX → DOCX → 다시 열기; `요소 바뀐문서/전체 원본합→결과합`):

- 표·칸: `tables 5/80 46→42`, `cells 5/80 1032→916`, `tables.in_note 2/80 8→0`, `tables.floating 48/80 109→581`
- 글: `chars.caption 14/80 1078→0`, `chars.textbox 5/80 883→78`, `chars.bold 7/80 12702→9823`, `chars.colored 4/80 446→11`
- 개체: `pictures.in_note 3/80 64→0`, `pictures.in_textbox 4/80 8→0`, `shapes 13/80 89→40`, `shapes.in_cell 5/80 5→0`,
  `shapes.in_footer 1/80 2→0`, `shapes.groups 7/80 8→0`(묶음은 풀림 — 의도)
- 그 밖: `fields 16/80 46→0`, `page_numbers 21/80 30→0`, `para.list 12/80 219→0`, `bookmarks 1/80 1→0`,
  `equations 5/80 5966→0`(그림으로 — 의도), `sections 10/80 28→10`(쪽 나누기로 — 의도)
- Word 원본(POI, `hwpx` 경로 = DOC/DOCX → HWPX → 다시 열기): `comments 3/245 5→0` 만 남음

## 2026-11-09 macOS 에서 한 것

①의 1~9를 모두 손봤다(회귀 테스트: `docx_round_trip_keeps_structure`, papy-office `pictures_captions_inline_tables_links_and_memos_are_written`·
`tab_stops_short_rows_and_cell_tails_are_read_and_written`). 한글 원본 `docx` 경로 결과(시작 → 지금, `원본합→결과합`):

- 캡션 `1078→0` → `451→459`, 글상자 글 `883→78` → `392→376`, 떠 있는 표 `109→581` → `54→35`, 주석 속 표 `8→0` → 같음
- 굵게 `12702→9823` → `7918→7902`, 크기 11pt·12pt 차이 → 같음, 칸 속 도형 `5→0` → `1→0`
- 필드 `46→0` → `36→6`(누름틀·계산식·빈 주소 링크만), 책갈피 `1→0` → 같음, 쪽 번호 `30→0` → `19→6`
- 일부러 다른 것은 그대로: 목록 `219→0`(번호 글자로), 수식 → 그림(그래서 `pictures` 가 늘어 보인다), 묶음 도형 풀림, 구역 → 쪽 나누기
- Word 원본(POI): Bug51170 머리말 문단 하나(첫 쪽 머리말 미지원), bug65649 하이퍼링크 하나(HWPX → DOCX, rhwp HWPX 쓰기)만 남음
- 성능: 한글 80개 DOCX 다시 열기 23.3s → 13.0s, Word 245개 열기 23s → 6s(bug59058.docx 12.9s → 0.78s). 붙여넣은 뒤
  손질을 rhwp 배치 모드로 묶고, 각주 HTML 을 한 번에 붙여넣는다.
- 2단 시험지: 한글 원본의 다단 구역은 아래 여백을 본문 줄 간격 여분만큼 줄여 쓴다(`papy:hwpBottom` 에 원래 값, 다시 열면 되돌림).
  탭 위치는 이제 왕복한다(rhwp 패치 0017).

## 남은 일

1. ~~목록 번호~~(2026-11-09 완료: 본문·칸 목록은 한글 문단 번호로, `para.list 219→0` → `10→0`). 남은 10개는 머리말·주석·글상자 안 목록.
2. ~~한글·영문 글꼴~~(2026-11-09 완료: `w:ascii` 를 라틴 글꼴로 따로, `fonts` 차이 29 → 13 문서). 남은 것: 언어별 장평·자간·상대 크기(Word 에 자리 없음).
3. 첫 쪽 머리말(Bug51170 — 한글에 첫 쪽 전용 머리말이 없어 형식 차이로 둔다), HWPX → DOCX 하이퍼링크 하나(bug65649),
   누름틀·계산식 필드(`fields 36→6`). 누름틀 방법: 쓰기는 `field_spans`(write.rs)에서 ClickHere 를 Word 내용 컨트롤
   (`w:sdt`, `w:tag="papy-clickhere"`, 안내문은 `w:alias`)로 감싸고, 읽기는 그 범위에 표시 글자를 둔 뒤 papy-hwp 에서
   rhwp `insert_click_here_field_at`(빈 누름틀)을 넣고 `field_ranges` 끝을 글 끝으로 늘린다. 컨트롤 자리(raw slot)를
   직접 고치므로 한글에서 열리는지 꼭 확인해야 한다.
4. 칸 속 도형 1개, 글상자 속 도형·주석 속 도형(`shapes.in_*`).
5. 성능 나머지: 머리말·글상자마다 문서 복제·`set_document` 하는 비용(바꾸기 위험이 커서 미룸).
6. Papy 로 다시 연 exam_eng 은 10쪽(원본 8쪽, 영문 글꼴 고친 뒤 14 → 10). 원인: rhwp 쪽 나누기가 한글이 저장한 줄 배치(원본)는
   줄 높이만, 붙여넣어 다시 나눈 문단은 줄 높이 + 줄 간격으로 쌓아 왼쪽 단이 한 줄 넘친다(`RHWP_DIAG_FLOW=1` 로 보면 한 줄에
   원본 15.3px, 다시 연 것 21.8px). 그림은 둘 다 같은 간격으로 그린다 — rhwp `renderer/typeset` 의 저장 배치 경로와 맞춰야 한다.

## Windows 에서만 하는 것 (다음에 Windows 로 갈 때)

- **먼저:** 2단 시험지 아래 여백 줄이기가 Word 에서 단 넘침을 막는지 잰다 — `exam*.hwp` 12개를 이 브랜치와 그 앞(3755ecf)으로 각각 DOCX 로
  저장해 쪽 수 비교(원본 exam_eng 8쪽). macOS Word 자동화(AppleScript)는 문서를 열지 못해(창이 뜨지 않음) 못 쟀다.
- Word 기준 쪽 수 측정: `scripts/word-pages.ps1`(Microsoft Word 필요, [test-corpus.md](../formats/test-corpus.md)).
  macOS 에서는 LibreOffice 점검(같은 문서)으로 대신 본다.
- 로드맵 항목 1(Windows 데스크톱 실제 문서 확인)·항목 3(Windows 한글 입력기).

## 그다음

- ② DOC·HWP 변환: 쪽 높이에 맞춘 양식 표.
- ③ XLSX·PPTX 지원.
