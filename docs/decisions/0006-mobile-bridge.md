# 0006. 모바일 엔진 연결: UniFFI, PDF 는 운영체제 렌더러

- 상태: **승인 (2026-11-04)** — 사용자가 1단계 범위(보기 전용)·연결 방식(UniFFI)·템플릿 기능 처리(화면에서만 빼기)를 골랐다
- 관련: [platform-bridges.md](../architecture/platform-bridges.md), [0003 PDF 엔진](0003-pdf-engine.md)

## 결정

1. **연결은 UniFFI 0.32**(Mozilla, MPL-2.0 — 파일 단위 조건이라 PolyForm NC 와 함께 쓸 수 있다).
   `papy-ffi` 가 `PapyEngine` 객체(열기·쪽 크기·쪽 그리기·글자·닫기)와 `PapyError` 를 내보내고,
   Kotlin(JNA)·Swift 바인딩은 `scripts/build-mobile-engine.mjs bindings` 로 만들어 **커밋한다**(앱 빌드가 Rust 없이도 컴파일되게).
   라이브러리(`libpapy_ffi.so`, `PapyFFI.xcframework`)와 한글 글꼴은 같은 스크립트가 만들고 git 에 넣지 않는다.
2. **PDF 는 운영체제 렌더러**: Android `PdfRenderer`, iOS `PDFKit`. 모바일에 PDFium(약 6MB×ABI)을 넣지 않는다.
   Rust 엔진은 HWP·HWPX·DOCX 를 맡는다. 암호 PDF 는 Android 15+ 에서만 열린다(그 미만은 "지원하지 않음").
   PDF 주석·쪽 편집을 모바일에 넣을 때 PDFium 을 다시 검토한다.
3. **공유 코드(KMP `feature:documents`)는 상태만**: 열기·암호·오류·쪽 크기·지금 쪽·글자 보기·최근 문서.
   `DocumentEngine` 인터페이스는 막는 호출이며 suspend·예외를 쓰지 않는다(Swift 가 구현하기 쉽게).
   **쪽 그리기는 플랫폼 화면이 직접 한다** — 큰 픽셀 버퍼가 Kotlin/Native 를 오가지 않게.
4. **템플릿 기능은 화면에서만 뺀다**: 앱 시작 화면을 `DocumentsNavHost`(Android)·`DocumentsRoot`(iOS)로 바꾸고,
   로그인·탭·채팅 등 템플릿 모듈 코드는 그대로 둔다(나중에 지울 수 있다).
5. 파일 참조: Android 는 content URI + `takePersistableUriPermission`, iOS 는 보안 범위 북마크(base64).
   같은 파일이 다른 참조로 오면(Android 파일 앱 "최근"/"다운로드") 최근 목록은 이름·형식으로 합친다.

## 확인 (2026-11-04, macOS arm64)

| 항목 | 결과 |
|---|---|
| Rust 교차 빌드 | aarch64/x86_64-linux-android(NDK 27.1, API 24), aarch64-apple-ios(-sim) 성공. `.so` 19.5MB(arm64, 기호 제거) |
| 단위 테스트 | `papy-ffi` 3개, `feature:documents` 6개, `templateCheck` 통과 |
| Android 에뮬레이터(API 35) | HWP·HWPX·PDF·DOCX 열기·그리기, 한글 글꼴, 쪽 번호, 글자 보기, 최근 문서(재설치 뒤에도 유지), 파일 고르기(형식 거르기) |
| iOS 시뮬레이터(iPhone 16e) | HWP·HWPX·PDF·DOCX 열기·그리기(디버그 실행 인자 `-papyOpen` 으로 앱 Documents 의 파일을 열어 확인) |
| 확인 못 함 | iOS 파일 고르기 창(fileImporter)·"Papy 로 열기", 두 손가락 확대 손동작, 실제 기기, 큰 문서 메모리 |

## 2단계 (2026-11-04)

| 항목 | 내용 |
|---|---|
| 확대 후 다시 그리기 | 손을 떼고 250ms 뒤 확대 배율로 보이는 쪽만 다시 그린다(그동안 이전 그림 유지). 쪽당 최대 1,200만 픽셀 |
| 문단 편집(HWP·HWPX·DOCX) | 편집 모드에서 문단을 누르면 표시되고 편집 창이 열린다 → 적용 → 되돌리기 → 원래 파일에 저장(Android content URI "wt", iOS 조정된 쓰기). 저장하지 않고 나가면 묻는다. PDF 는 보기만 |
| 엔진 API | `paragraph_at`·`replace_paragraph`(되돌리기 단계 수)·`undo`·`save`·`page_count` |
| Android 릴리스 | R8 keep 규칙(JNA·UniFFI), ABI 제한, 16KB 정렬, 쓰지 않는 권한 제거 — [mobile-release.md](../development/mobile-release.md) |
| 확인 | 에뮬레이터: R8 릴리스 빌드에서 HWP 편집·저장(저장 파일을 엔진으로 다시 열기), DOCX 편집·저장, 나가기 확인 창, 두 번 눌러 확대 후 선명함. 시뮬레이터: 디버그 실행 인자 `-papyEditSmoke` 로 DOCX 편집·저장(파일에 반영 확인) |

## 3단계 (2026-11-04)

| 항목 | 내용 |
|---|---|
| 커서 편집 | 편집 모드: 누르면 커서, 두 번 누르면 단어 고르기, 길게 누르면 문단 고치기 창. 숨긴 입력칸으로 키보드 글자를 받는다(조합 중인 한글은 커서 아래에 띄우고 조합이 끝나면 넣는다). 바뀐 만큼만 엔진에 보내 글자가 겹치지 않게 한다. 하드웨어 키보드 화살표 |
| 글자 모양 | 도구 막대: 굵게·기울임·밑줄·취소선, 크기 ±1pt, 색 3가지, 범위 선택(누른 자리까지 늘리기), 커서 좌우, 문단 고치기. 선택이 없으면 커서가 든 단어에 적용 |
| 서식 지키는 문단 바꾸기 | 앞뒤로 같은 글자는 두고 바뀐 가운데만 지우고 넣는다(문단 안 개체가 있으면 통째로) |
| 되돌리기 | 엔진의 되돌리기 단계 수(`undo_depth`)로 "한 동작"을 묶는다(이어 친 글자는 한 단계). 저장 뒤 고치거나 되돌리면 저장하지 않은 변경으로 본다 |
| PDF 로 내보내기 | HWP·HWPX·DOCX → PDF(rhwp, 글자 선택 가능). 앱 한글 글꼴(Noto Sans KR)을 넣어 모바일에서도 글자가 빠지지 않는다. Android: 저장할 곳 고르기(SAF), iOS: 공유·파일에 저장 창. 데스크톱은 저장 메뉴의 "다른 형식으로 내보내기 → PDF" |
| DOCX 도형 | 사각형·둥근 사각형·타원·선·글상자(글자 포함) 읽기·쓰기, 위치·배치·채우기·선 색. 묶음 도형·차트·SmartArt·VML 은 빠진 것으로 센다 |
| 확인 | 엔진 테스트(커서·서식 보존·PDF 글꼴·도형 왕복), KMP 테스트 11개, 에뮬레이터(입력·지우기·굵게·크기·저장 파일 확인·PDF 내보내기와 글꼴), 시뮬레이터(`-papyEditSmoke -papyExportSmoke`: 커서 입력·굵게·문단 고치기·저장·PDF 공유 창) |

## 알려진 점

- 실제 한글 키보드 입력(조합)은 기기에서 더 확인해야 한다(에뮬레이터는 adb 로 영문만 넣어 봤다).
- 표 칸 안·글상자 안 글자는 아직 커서로 고칠 수 없다(본문만). 표·그림 편집은 다음 단계.
- DOCX 는 Papy 가 다시 쓰므로 옮기지 못한 요소(묶음 도형·차트 등)는 저장하면 빠진다(열 때 알림).
- 템플릿 모듈 정리: 사용자 승인으로 2026-11-04 에 지웠다(기능 모듈 10개, UI 테스트, 소셜·Firebase SDK, 안 쓰는 문자열 약 350개). 남은 것은 core:common·core:data·feature:documents.
- 모바일 앱용 제3자 라이선스 고지(UniFFI·JNA·Noto Sans KR 등)는 배포 전에 앱 화면에 넣는다.
