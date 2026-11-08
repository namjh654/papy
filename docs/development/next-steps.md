# 이어서 할 일 (2026-11-09, Windows → macOS)

Windows 에서 하던 변환 품질 작업을 macOS 에서 잇기 위한 메모. 끝나면 이 파일을 지우거나 [roadmap.md](roadmap.md) 로 옮긴다.

## 지금 상태

- 브랜치 `feature/windows-fidelity`(원격에 올림, PR 아직 없음). 마지막 커밋: 변환 왕복 손실 점검과 머리말·구역 버그 고치기.
- 우선순위(사용자와 정함): **① 버그를 찾아 재발하지 않게**(DOCX↔HWPX 내용·구조 100%, 쪽 수는 가깝게) → ② DOC·HWP 변환 문제·성능
  → ③ XLSX·PPTX. 코드 서명(로드맵 항목 2)은 보류.
- 원칙: 손실을 고칠 때마다 회귀 테스트를 붙인다. 왕복 손실은 `papy-hwp` 의 `docx_round_trip_keeps_structure` 원본 DOCX 에
  요소를 더하고 기대 수를 적는다(기본 `cargo test`, CI).

## macOS 준비

```bash
git fetch && git switch feature/windows-fidelity && git pull
node scripts/fetch-rhwp.mjs          # rhwp v0.8.7 + engine/patches/rhwp 0001~0014 적용
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

## 남은 일 (① 버그·재발 방지)

순서는 영향이 큰 것부터. 문서 이름은 rhwp 샘플(`tests/corpus/rhwp-samples` 아래 어딘가, 목록 파일 참고).

1. **글상자·캡션 글자**(`chars.caption 1078→0`, `chars.textbox 883→78`): HWP → DOCX 에서 캡션·글상자 글이 본문으로 가거나 빠진다.
   `papy-office/src/write.rs` 의 캡션·글상자 쓰기와 `read.rs`·`shapes.rs` 의 `wps:txbx` 읽기를 본다.
2. **떠 있는 표 수**(`tables.floating 109→581`): DOCX 를 다시 열면 글자처럼 놓인 표가 떠 있는 표로 바뀐다. 쪽 수에도 영향. 붙여넣은
   표의 `treat_as_char` 가 어디서 정해지는지(rhwp `html_table_import.rs`, `split_tables_across_pages`) 본다.
3. **주석 속 표·그림**(`tables.in_note`, `pictures.in_note` — 3-09월_교육_통합_2022.hwp, 3-10월_교육_통합_2022.hwpx):
   DOCX 각주는 글자만 읽는다(`parse_footnotes` → `Vec<String>`). 머리말과 같은 방법으로: 각주 부분을 `part_to_html`(read.rs)로
   바꾸고, papy-hwp `notes.rs` 에서 본문 끝에 붙여넣은 뒤 그 문단들을 각주로 옮긴다(`header_footer_html` 참고).
4. **머리말 속 떠 있는 그림·도형**(156742029_prosecutor_transfer_list.hwp, hf_cross_section_inherit.hwp): 머리말 HTML 은 도형
   표시(`SHAPE_MARK`)를 빼고 빠진 것으로 센다(read.rs 머리말 처리). 머리말 부분의 `part.shapes` 를 넘겨 papy-hwp `shapes::apply`
   를 머리말 문단에도 적용한다.
5. **굵게·색·글자 크기·글꼴**(`chars.bold`, `chars.colored`, `chars.size.*`, `fonts` — 교육_통합 문서들): 어느 자리(칸·본문)의
   어느 런인지 먼저 좁힌다. 크기는 반 pt 반올림 의심.
6. **글상자·칸·주석 속 그림·도형**(`pictures.in_textbox`, `shapes.in_cell` 등): 쓰기가 그 자리 개체를 빼는지 확인.
7. **메모**(Word 원본 `comments 5→0`, 한글 → DOCX 도): DOCX 메모를 읽어 HWP 숨은 설명/메모로 넣고, DOCX 로 쓸 때 `comments.xml`.
8. **필드·책갈피·목록·쪽 번호를 다시 열 때**(`fields`, `bookmarks`, `para.list`, `page_numbers`): 목록은 번호 글자로 읽는 것이
   지금 설계(번호 목록을 rhwp 문단 번호로 읽는 것은 큰 일). `page_numbers` 는 다시 열면 머리말 필드 글자(U+0015)라 점검이 세지
   않는 것 — 점검(`papy-hwp/src/audit.rs`)에서 그 글자도 쪽 번호로 세게 고치면 실제 손실만 남는다.
9. 작은 것: Bug51170.docx 머리말 빈 문단 하나 차이(그림만 있는 머리말).

## Windows 에서만 하는 것 (다음에 Windows 로 갈 때)

- Word 기준 쪽 수 측정: `scripts/word-pages.ps1`(Microsoft Word 필요, [test-corpus.md](../formats/test-corpus.md)).
  macOS 에서는 LibreOffice 점검(같은 문서)으로 대신 본다.
- 로드맵 항목 1(Windows 데스크톱 실제 문서 확인)·항목 3(Windows 한글 입력기).

## 그다음

- ② DOC·HWP 변환 문제·성능: 2단 시험지 단 넘침(exam_eng, 왼쪽 단 7pt 넘쳐 단 나누기가 한 단씩 밀림), Papy 로 다시 열 때 문단 탭
  위치 읽기, 쪽 높이에 맞춘 양식 표.
- ③ XLSX·PPTX 지원.
