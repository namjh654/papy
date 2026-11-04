# 모바일 배포 준비 (Android 중심)

상태: 2026-11-04 점검. 데스크톱·웹·모바일을 한 번에 배포하기로 했다(사용자 결정). 앱 서명·스토어 등록은 사용자가 한다.

## 이미 된 것

| 항목 | 내용 |
|---|---|
| R8(코드 축소·난독화)·리소스 축소 | `prodRelease` 에만 켜진다(`build-logic/.../AndroidEnvironmentsPlugin.kt`). `proguard-android-optimize.txt` + `androidApp/proguard-rules.pro` |
| 엔진 keep 규칙 | JNA·UniFFI 바인딩은 리플렉션을 쓰므로 `com.sun.jna.**`, `uniffi.**` 를 남긴다. R8 빌드로 에뮬레이터에서 HWP(Rust)·PDF 열기 확인 |
| 로그 제거 | 릴리스에서 `Log.v/d/i` 제거(w/e 는 남김) |
| 엔진 크기 | Rust release: 기호 제거 + LTO(fat) + codegen-units 1 → arm64 `.so` 19.5MB → 15.0MB |
| ABI | `arm64-v8a`, `x86_64` 만(엔진을 이 둘만 만든다). 32비트 기기는 설치되지 않는다(설치되면 문서를 못 연다) |
| 16KB 페이지 | Android 15+ 대상 앱 요구사항. 엔진을 `-z max-page-size=16384` 로 링크한다(`scripts/build-mobile-engine.mjs`) |
| 권한 | 알림·카메라·위치 권한 제거(템플릿 예시). 남은 것: INTERNET·ACCESS_NETWORK_STATE(core:data 네트워크 코드가 남아 있음 — 문서는 밖으로 보내지 않는다), DYNAMIC_RECEIVER_NOT_EXPORTED(androidx 자동) |

## 배포 전에 해야 할 것

| # | 할 일 | 누가 | 비고 |
|---|---|---|---|
| 1 | 업로드 키(keystore) 만들기 → `env/release-signing.local.properties` 또는 CI 비밀값(`ANDROID_KEYSTORE_FILE` 등) | 사용자 | 키 파일·비밀번호는 커밋하지 않는다. Play App Signing 사용 권장 |
| 2 | 엔진 빌드: `node scripts/fetch-rhwp.mjs` → `node scripts/build-mobile-engine.mjs android` | 빌드 | release 는 LTO 로 ABI 당 약 6분 |
| 3 | App Bundle 빌드: `./gradlew -Papp.env=prod :androidApp:bundleProdRelease` | 빌드 | Play 가 ABI·화면 밀도별로 나눠 내려준다(기기당 엔진 1개만) |
| 4 | 템플릿 모듈 정리 | 완료 | 2026-11-04. APK 42.9→40.2MB, AAB 29.9→24.8MB, dex 6.8→3.4MB |
| 5 | 앱 안 오픈소스 라이선스 화면(UniFFI·JNA·rhwp·Noto Sans KR 등) | 개발 | MIT·Apache·OFL 고지 의무 |
| 6 | 개인정보처리방침 URL | 완료 | env `url.privacy` 를 `https://papy-omega.vercel.app/privacy/` 로 바꿨다. 스토어 등록에도 이 주소를 쓴다 |
| 7 | 앱 아이콘·이름 확인(지금 템플릿 아이콘일 수 있음) | 사용자 확인 | `res/mipmap-*` |
| 8 | Play Console: 앱 만들기, 데이터 보안(수집 없음), 콘텐츠 등급, 스토어 등록 정보·스크린샷 | 사용자 | 문서는 기기 밖으로 나가지 않는다 |
| 9 | 실제 기기 시험(파일 앱·Google Drive 의 파일 열기·저장, 큰 문서) | 사용자·개발 | 에뮬레이터에서만 확인했다 |
| 10 | versionCode 올리기(`env/common.properties`) | 배포 때마다 | |

## iOS

| 할 일 | 비고 |
|---|---|
| Apple Developer 계정·서명(TEAM_ID) | 앱 배포·코드 서명은 사용자가 정한 대로 뒤로 미뤘다 |
| 엔진: `node scripts/build-mobile-engine.mjs ios` → Xcode 아카이브 | xcframework 는 git 제외 |
| "Papy 로 열기"(문서 형식 등록, `CFBundleDocumentTypes`) | 아직 없음 — 파일 앱에서 Papy 로 바로 열기 |
| 실제 기기 시험 | 시뮬레이터에서만 확인 |
