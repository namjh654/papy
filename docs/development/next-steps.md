# 이어서 할 일 (2026-11-10 갱신)

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
node scripts/fetch-rhwp.mjs          # rhwp v0.9.0 + engine/patches/rhwp/*.patch 적용
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
3. ~~누름틀~~(2026-11-09 완료: 한글 누름틀 ↔ Word 글 콘텐츠 컨트롤 `w:sdt`. field-01.hwp 누름틀 `11→1` → `11→11`(글상자 안 5개 포함),
   한글 원본 80개 `fields` 36→6 가운데 누름틀이 남은 것은 여러 문단을 감싸는 9개뿐). 붙여넣은 문단은 탭을 원시 위치 1 로 세어 rhwp 가
   누름틀 자리를 셀 수 없었다 — papy-hwp `fields.rs` 가 탭을 8 로 다시 센다. 한글 프로그램에서 열리는지는 아직 확인하지 않았다(Windows).
   남은 것: 첫 쪽 머리말(Bug51170 — 한글에 첫 쪽 전용 머리말이 없어 형식 차이로 둔다), HWPX → DOCX 하이퍼링크 하나(bug65649),
   여러 문단을 감싸는 누름틀(블록 `w:sdt` 로 쓸 수는 있으나 rhwp 쪽 문단 넘는 필드 넣기가 없다).
4. ~~계산식 필드~~(2026-11-09 완료: `fields` 한글 원본 80개 `27→6` → `11→6`. 남은 것은 원본에 잘못 적힌 링크·빈 주소 링크·범위 책갈피).
   ~~그림만 든 글상자~~(2026-11-09 완료: 글자 없이 그림·표만 든 글상자를 버리던 것, `shapes.textboxes` 7 → 3 문서).
   남은 도형: 칸 속 묶음 도형 1개(묶음은 일부러 푼다), 주석 속 도형 2개·글상자 속 도형 1개(조각 HTML 에 도형 자리가 없다).
5. 성능 나머지: 머리말·글상자마다 문서 복제·`set_document` 하는 비용(바꾸기 위험이 커서 미룸).
6. ~~Papy 로 다시 연 exam_eng~~(2026-11-09 완료: 10 → **8쪽**, 원본과 같다. 2단 시험지 exam-kor-1p·2p·3p 도 원본과 같아지고 4p 6 → 5,
   exam_kor 27 → 26). 고친 것: 영문 빈칸을 반각으로 재던 것(DOCX 에서 연 글자에 "글꼴에 어울리는 빈칸"),
   한글·영문 장평·자간·상대 크기가 한 값으로 합쳐지던 것(DOCX 런을 언어 묶음별로 나눠 씀), 최소 공백(`papy:condense`, rhwp 패치 0019),
   줄 채움이 탭 정지를 모르던 것(rhwp 패치 0020 — 편집한 한글 문단에도 쓰인다), 배수 줄 간격 버림(143% → 142%),
   빈 문서가 HWP5 로 만들어져 패치 0018 이 꺼지던 것(문서 출처에 "다른 형식에서 옮김"). 남은 것: exam_kor 26쪽(원본 20).
   2026-11-10: 지문 상자(문단 테두리, rhwp 0021)·글자처럼 놓은 글상자(안 여백, 줄 자리)를 고쳤으나 쪽 수는 그대로다. exam_kor 는
   rhwp 줄 나눔이 한컴 저장 줄보다 길게 접는 것(원본 HWP 도 rhwp 가 다시 나누면 쪽이 는다)이라 rhwp 조판 쪽 일이다.
   "글꼴에 어울리는 빈칸"을 원래 값(꺼짐)으로 옮기면 오히려 exam_eng 8 → 11쪽이라 켠 채로 둔다.
7. ~~바탕쪽이 DOCX 로 통째로 빠지던 것~~(2026-11-10: 양쪽·홀수·짝수 바탕쪽을 Word 머리말 안 떠 있는 개체로 쓰고,
   Papy 로 다시 열면 바탕쪽으로 되돌린다. 틀 표 칸 쪽 번호도 쪽마다 맞게). 남은 것: 마지막 쪽 등 확장 바탕쪽("확인 사항" 상자),
   그림 자르기(`a:srcRect` ↔ 한글 그림 자르기 — 시험지 머리 "국어 영역" 그림이 잘리지 않고 통째로 나온다),
   DOCX 에서 연 문서를 HWP 로 저장했다 다시 열면 쪽이 하나 느는 것(exam-kor-4p 5 → 6, 바탕쪽과 무관).
8. 2026-11-10 rhwp **v0.9.0** 으로 올림(패치 0001·0013·0020 은 업스트림이 고쳐 뺌, 0022 새로 — 옮긴 문서의 묶음 빈칸 폭).
   exam_eng 8쪽 그대로. 0.9 에서 쪽이 는 것: exam_kor 26 → 28(원본 20), exam_science 4 → 7, exam_social 3 → 5(원본 4).
   0.8.7 은 떠 있는 개체 위에 글이 겹쳐 쪽이 적었고, 0.9 는 글을 개체 둘레로 흘린다 — 작은 넘침 뒤 강제 쪽 나누기로 쪽이 는다.
   다음에 볼 것: 그 넘침이 Papy 가 쓰는 개체 크기·바깥 여백 때문인지 rhwp 조판 때문인지(쪽마다 넘친 문단 높이 비교).
   rhwp-studio·@rhwp/core WASM·샘플 문서 모음은 아직 0.8.7(따로 올림). 0.9 배치 모드는 `paginate` 를 미루므로 배치 안에서
   쪽·줄 자리를 묻지 말 것(그림 자리는 `shapes::measure` 로 배치 전에 잰다). Papy 표시 글자는 U+E0xx(0.9 가 U+E0BC~ 를 옛한글로 바꾼다).

## Windows 에서만 하는 것 (다음에 Windows 로 갈 때)

- **먼저:** 2단 시험지 아래 여백 줄이기가 Word 에서 단 넘침을 막는지 잰다 — `exam*.hwp` 12개를 이 브랜치와 그 앞(3755ecf)으로 각각 DOCX 로
  저장해 쪽 수 비교(원본 exam_eng 8쪽). macOS Word 자동화(AppleScript)는 문서를 열지 못해(창이 뜨지 않음) 못 쟀다.
- Word 기준 쪽 수 측정: `scripts/word-pages.ps1`(Microsoft Word 필요, [test-corpus.md](../formats/test-corpus.md)).
  macOS 에서는 LibreOffice 점검(같은 문서)으로 대신 본다.
- DOCX 로 저장했다 다시 연 누름틀 문서(`field-01.hwp` → DOCX → HWP)가 한글에서 열리고 누름틀이 동작하는지.
- 로드맵 항목 1(Windows 데스크톱 실제 문서 확인)·항목 3(Windows 한글 입력기).

## 그다음

- ② DOC·HWP 변환: 쪽 높이에 맞춘 양식 표.
- ③ XLSX·PPTX 지원.
