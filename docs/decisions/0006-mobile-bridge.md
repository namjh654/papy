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

## 알려진 점

- 처음 그린 너비(화면 × 1.5, 최대 2400px)로만 그리고 확대는 화면에서 늘린다 — 크게 확대하면 흐려진다(다음 단계: 확대 후 다시 그리기).
- 디버그 APK 가 큼(약 90MB — 템플릿 의존성 + 두 ABI). 배포 때 ABI 분리(App Bundle)와 템플릿 모듈 정리를 한다.
- 모바일 앱용 제3자 라이선스 고지(UniFFI·JNA·Noto Sans KR 등)는 배포 전에 앱 화면에 넣는다.
