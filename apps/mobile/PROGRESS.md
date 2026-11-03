# PROGRESS

> 기준 시점: 2026-09-30 · 템플릿 v0.2 · 브랜치 `ocra` (분석 시작 2026-09-29 `e96934e`)
> 구조와 전략은 [ARCHITECTURE.md](ARCHITECTURE.md) 참조. 이 문서는 진행 상태·완료 조건·위험·결정 사항을 관리한다.
> 개발 방식: Orca (ocra = 공통 로직·병합·점검, Android/iOS 워크트리 = UI·연결, 자동/수동 모드) — [.orca/README.md](.orca/README.md). 확정된 결정은 [.orca/DECISIONS.md](.orca/DECISIONS.md).

---

## 1. 현재 상태 — 템플릿 v0.4.12 (2026-10-27)

**템플릿 v0.4.12** (태그 `v0.4.12`): v0.3 + Phase 17~30 — 소셜 로그인, 메시지, 커뮤니티, 로컬 DB·오프라인, 버전 정책·공지,
프로필·1:1 대화·문의, 앱 잠금·알림 설정, `newFeature --db`·기능 켜고 끄기, 접근성 1차, 파일 받기·보관·화면 캡처 막기. 남은 것은 실기기·서버가 필요한 Phase 23(§3).
확인: `orca.sh check`, 전체 KMP 테스트, `uitest all` Android 20개·iOS 19개(18 통과·1 건너뜀). 아래 표는 v0.3 때 기록.

### v0.3 (2026-10-01)

**템플릿 v0.3 완료** (태그 `v0.3`, `phase-16`). v0.2 + Phase 13~16: 상세 화면 패턴·공지사항·약관·비밀번호 재설정·탈퇴·프로필(업로드)·
권한·온보딩·알림함·검색, 로컬 목업 서버, iOS UI 흐름 테스트. 결정은 전부 확정(DEC-001~102).
확인하지 못한 것·서버 가정·보류한 버전은 [docs/known-limitations.md](docs/known-limitations.md) 한 곳.

| 확인 항목 | 명령 / 방법 | 결과 (2026-10-01 mac) |
|---|---|---|
| 템플릿 점검 + KMP 테스트 | `scripts/orca.sh check --env dev\|staging\|prod` (templateCheck·전 모듈 테스트·Android assemble·Kotlin iOS·xcodebuild) | 3환경 통과 |
| iOS 화면 흐름 | `scripts/orca.sh uitest` (목업 서버 + XCUITest: 온보딩·가입·재설정·로그인·공지·예시·알림함·검색·권한·프로필·링크·테마·세션 만료·탈퇴) | 14개 통과(2026-10-06 재확인) |
| Android 화면 흐름 | `scripts/orca.sh uitest android` (목업 서버 + UiAutomator: 온보딩·가입·재설정·로그인·공지·예시·알림함·검색·권한·프로필·링크·테마·세션 만료·뒤로가기 종료·탈퇴) | 15개 통과(2026-10-04 windows, 2026-10-06 mac) |
| 테마 | 기본 시스템 설정, 앱에서 라이트/다크 고정 → 시스템 복귀 (iOS 시뮬레이터, uitest `test12_theme`) | 통과 |
| 로그 마스킹 | 네트워크 로그의 Authorization `***`, 토큰 값 없음 | 확인 |

구현된 기능: 온보딩 → 스플래시(세션 복원) → 로그인·회원가입(약관)·비밀번호 재설정 → 하단 탭(홈·예시·설정) · 공지사항·알림함·검색·권한 ·
프로필 편집(사진 업로드)·회원 탈퇴 · 웹뷰 · 오프라인 안내 · 업데이트 안내 · 외부 링크 · 푸시(설정 시), 양 플랫폼 Native UI.


### 다음 액션 (2026-10-04 기준)

템플릿 작업은 v0.3 에서 끝났다. 아래는 **새 앱을 시작할 때** 또는 **외부 조건이 갖춰졌을 때** 할 일이다(우선순위 순).

| # | 할 일 | 필요한 것 | 어디서 |
|---|---|---|---|
| 1 | 새 앱으로 복사 → `renameApp` → API 주소·Firebase·디자인 | 앱 이름·패키지·번들 ID·Team ID | [docs/template-setup.md](docs/template-setup.md) |
| 2 | 서버 계약 확정 → `Sample*Api` 를 `Remote*Api` 로 바꾸고 DTO 를 맞춘다 | 실제 API 명세 | template-setup §2 표, 로컬 목업 서버로 먼저 확인 |
| 3 | Firebase 프로젝트 연결 → 푸시 수신·알림 탭 이동·Crashlytics 확인 | `google-services.json`·`GoogleService-Info.plist`, 실기기(APNs) | [docs/push.md](docs/push.md) |
| 4 | CI 켜기 | GitHub 결제·사용 한도 정리, `ubuntu-latest` 가 2026-10-19 부터 Ubuntu 26 으로 바뀌는 것 확인, macOS 러너 비용 | [docs/ci.md](docs/ci.md) |
| 5 | 실기기 확인: iOS 권한 "설정 열기"·오프라인 안내, 스토어 배포 후 인앱 리뷰·업데이트 | 실기기, 내부 테스트 트랙 / TestFlight | [docs/known-limitations.md](docs/known-limitations.md) |
| 6 | ~~Android 화면 흐름 자동 테스트~~ — **완료(2026-10-04)** | | `scripts/orca.sh uitest android` (15개) |
| 7 | 보류한 버전 다시 보기: AGP 9.4.x·Gradle 9.8(Kotlin 호환표), Firebase 토큰 API → 설치 ID 전환 | 호환표 갱신, 실제 Firebase | known-limitations §버전 |

GitHub: 설명·토픽 설정, 릴리스 v0.1~v0.3 작성(2026-10-04). 기본 브랜치 `main` 은 `ocra` 와 같은 커밋으로 맞춘다(작업은 `ocra` 에서).

### v0.2 기록 (2026-09-30)

v0.1(Phase 0~11 + 마무리) + Phase 12 버전 업그레이드. 그때의 확인 표는 `.orca/reports/` 의 v0.2 보고서.

### v0.2 이후 (2026-09-30 windows)
- [x] CI 를 끄고 예시·문서만 남김 — `docs/ci.md` (DEC-096)
- [x] `orca.sh check`: mac 이 아니어도 iOS Kotlin(iosMain) 컴파일 확인 (DEC-097)
- [x] 라이브러리·컴포넌트 적용 점검(소스 기준) — `.orca/reports/AUDIT-20260930-libraries-components.md`. 코드 변경 없음
- [x] Android `prodRelease`(R8) 실행 확인(2026-09-30 windows, 디버그 키·로컬 목업): 로그인 → 탭 → 설정 → 갤러리(날짜·시간·사진 2장, Coil 미리보기). 임시 평문 허용은 커밋 안 함
- [x] `orca.sh`: 새 워크트리에 `local.properties` 복사, 임시 워크트리 정리 시 긴 경로 허용(Windows)
- [x] 결정 검토 묶음 — `.orca/reports/DECISION-REVIEW-20260930.md`
- [x] mac 확인 — `.orca/notes/mac-next.md` (2026-10-01)

### v0.1 마무리 (Phase 11 이후, DEC-088~)
- [x] 다크 모드: 테마 결정 `ThemeMode.isDark`(KMP 한 곳 + 단위 테스트), 기본값·저장값 없음 → 시스템. Android 는 윈도우(시작 화면)·본문·시스템 바를 같은 기준으로(uiMode + Android 12+ 앱별 야간 모드), iOS 는 창 단위 적용 + 첫 화면 전 저장값 읽기
- [x] Android 백업 복원으로 옛 테마가 되살아나 "시스템 라이트인데 다크"로 보이던 경로 차단(저장소를 백업·기기 이전에서 제외)
- [x] 확인 매트릭스: 테마·언어·상태바/웹뷰 다크·백업 복원·자동 로그인(Android)·오프라인(Android)·업데이트 재표시
- [x] Python 잔재 점검: 추적 `.py` 0개, scripts·docs·.github·CLAUDE.md 언급 없음, CHANGELOG 정리
- [x] orca: 임시 워크트리 `tmp`·`merge tmp`(성공 시 자동 정리)·`preflight`, checkpoint·status 경고
- [x] docs/known-limitations.md, CHANGELOG v0.1

---

## 2. 완료된 작업

### 기존 코드 (분석 시점까지)
- [x] KMP `:core` + `:androidApp` + `iosApp`(Xcode) 3분할, 공통 Navigator 제거 후 플랫폼 Native Navigation 채택
- [x] Koin 공통 진입점 `initKoin(platformModule, apiConfig)`, Android/iOS 플랫폼 모듈 주입
- [x] SKIE 적용: `StateFlow` → `SkieSwiftStateFlow`, sealed Event → `onEnum(of:)`
- [x] iOS ViewModel 수명 관리(`SharedViewModel`/`ViewModelOwner` + `dispose()`)
- [x] Network 계층: `BaseApi` / `HttpTransport` / `KtorClient`(OkHttp·Darwin) / `AppError` 정규화 / Bearer 자동 첨부
- [x] Storage 계층: `LocalStorage` 계약 + DataStore/NSUserDefaults 구현 + 도메인 래퍼 4종
- [x] Auth: Login/Signup API·Repository·ViewModel, `SessionRepository`(start/restore/logout), `AuthStore`/`UserStore`
- [x] 환경 분리: `.env.*` → Android flavor(dev/staging/production), iOS 구성(Debug/Staging/Release), `generateEnvironment`
- [x] `SessionRepositoryTest` (commonTest, Fake 저장소)
- [x] README를 현재 구조 기준으로 최신화

### Orca 오케스트레이션 도입 (2026-09-29)
- [x] `scripts/orca.sh` (status/mode/setup/sync/checkpoint/check/refresh/merge/gate/decide/approve/rework/reopen/report)
- [x] `.orca/` 기록 체계: config(모드), DECISIONS.md(DEC-001~005), SYNC.md(머신 체크포인트), notes/(워크트리 요청), reports/
- [x] `CLAUDE.md`, `/orca` 스킬, `.gitattributes`(스크립트 LF), Windows 래퍼 `scripts/orca.cmd`

### Phase 0 — 분석·문서화 (2026-09-29)
- [x] 전체 저장소 분석(Gradle/KMP/Android/iOS/공통 코드/UI/Navigation/Network/Storage/Auth/Push/Design System/테스트/CI/라이브러리)
- [x] `ARCHITECTURE.md` 작성 (현재·목표 구조, 모듈, 의존성, 경계, 전략, Phase)
- [x] `PROGRESS.md` 작성
- 코드 변경 없음

---

## 3. 예정된 Phase와 완료 조건

**모든 Phase의 공통 완료 조건**
- `./gradlew :core:allTests` (또는 모듈 분리 후 전체 테스트) 통과
- `./gradlew :androidApp:assembleDevDebug` 성공
- iOS `xcodebuild` (Debug, simulator) 성공
- 기존 기능(Splash → Login → Signup → Home → Logout) 양 플랫폼 수동 확인
- ARCHITECTURE.md / PROGRESS.md / README 갱신
- 다음 Phase는 사용자 승인 후 시작

### Phase 1 — 기반 정리 (2026-09-29 windows, iOS mac 검증 완료 2026-09-30)
범위: 동작 변경 없는 정리 작업.
- [x] `docs/architecture.md`, `structure.txt`, `CHANGELOG.md`의 구버전 내용 정리(ARCHITECTURE.md로 대체 또는 링크) — structure.txt 삭제, docs/architecture.md 는 링크만, CHANGELOG 재작성
- [x] Compose BOM/아티팩트를 version catalog로 이동, 미사용 catalog 항목(`appcompat`, `core-ktx`) 정리
- [x] `core/build.gradle.kts`의 `iosX64` 주석 불일치, 불필요한 `androidResources { enable = true }` 검토 — 주석 수정, 비활성 (DEC-031)
- [x] 빈 `core/ui`, `core/util` 처리 방침 확정 — 삭제 (DEC-030)
- [x] 공유 Xcode scheme 커밋(CI용) — iOS: mac 검증 완료 (Phase 8 에서 scheme 3개로 대체)
- [x] CI 최소 파이프라인: 공통 테스트 + Android assemble + iOS xcodebuild — GitHub Actions `.github/workflows/ci.yml` (DEC-029) → 2026-09-30 끄고 예시만 남김 `docs/ci.md` (DEC-096). 잡 결과는 push 후 GitHub 에서 확인(이 PC 에 gh 없음)
- [ ] (보류, 사용자 결정) lifecycle beta → 안정 버전, Koin 업그레이드 — 기준선 라이브러리 (DEC-032)

완료 조건: 공통 조건 + CI에서 3개 잡 녹색.

### Phase 2 — 모듈 분리 (2026-09-29 windows, iOS mac 검증 완료 2026-09-30)
범위: 모듈 분리 + 공개 계약 정리(사용자 승인, DEC-033~043). 전/후: `.orca/notes/phase2.md`, 규칙: [docs/conventions.md](docs/conventions.md).
- [x] `build-logic` convention plugin — `papy.kmp.library`, `papy.kmp.feature` (Android app 용은 모듈이 하나라 두지 않음) (DEC-039)
- [x] `core:common`(에러·UiState/UiEvent·BaseViewModel/StatefulViewModel), `core:data`(network·storage·serialization·session) 분리 — `core:network/storage/auth/testing` 대신 (DEC-033)
- [x] `feature:auth`(login·signup·splash), `feature:home` 분리, ViewModel 은 `StatefulViewModel` (DEC-035)
- [x] `shared` umbrella — framework 이름 `Core` 유지, 4개 모듈 export, Koin 진입점 `initKoin(apiConfig)`·`IOSAppGraph` 이동 (DEC-034/037)
- [x] Json 설정 단일화(`AppJson`), core:data 내부 구현 internal, lifecycle 2.10.0·Koin 4.2.2 (DEC-038/043)
- [x] ~~`FakeLocalStorage`를 `core:testing`으로 이동~~ — core:testing 을 두지 않음(DEC-033). 테스트 Fake 는 각 모듈 commonTest
- [x] **mac 검증**(2026-09-30): `scripts/orca.sh check` + `.orca/notes/ios.md` Phase 2 항목 (export·Swift 타입 이름·실행 흐름)

완료 조건: 공통 조건 + ARCHITECTURE 2.3 의존성 규칙 위반 없음(commonMain에 UI 의존 없음, feature 간 의존 없음) + iOS에서 SKIE 기능(Flow, onEnum) 동일 동작.

### Phase 3 — Network·Auth·Storage 강화 (2026-09-29 windows, iOS mac 검증 완료 2026-09-30)
- [x] `HttpTimeout`, debug `Logging`(Authorization 마스킹, production 제외), query 인코딩, `Json` 단일화(`AppJson`, Phase 2), Content-Type 중복 정리 + ContentNegotiation 제거 (DEC-044)
- [x] Android/iOS의 `ApiConfig` 생성 경로 단일화 — 양쪽 `ApiConfig.from(BuildEnvironment.from(APP_ENV))`, Android `BuildConfig.API_BASE_URL` 제거
- [x] 401 → `SessionRepository.expire()` → `AuthStore.Unauthenticated` (refresh 재발급은 서버 계약 D6 확정 후) (DEC-045)
- [x] 양 플랫폼 루트가 `AuthStore.status`를 관찰해 메인 → 로그인 전환 (Android `AndroidNavHost`, iOS `AuthStatusObserver`)
- [x] `SecureStorage` 계약 + Android Keystore / iOS Keychain 구현, `TokenStorage` 이전, 평문 토큰 migration (DEC-046)
- [x] `useMock` 플래그를 DI 교체 방식으로 대체 — Api interface + Koin (DEC-047)
- [x] 테스트: BaseApi 5(가짜 HttpTransport — MockEngine 의존성 대신), 401/expire(SessionRepository 4), LoginRepository 4, SignupRepository 2, TokenStorage migration 4
- [x] **기기 확인** (2026-09-30 windows, 에뮬레이터 API 33 + 로컬 목업 서버): 토큰은 `papy.secure.preferences_pb` 에 암호문으로만 있고 앱 저장소 전체에 평문 토큰 없음. 자동 로그인 재실행 복원. 목록 요청 401 → refresh 401 → 로그인 화면, 토큰·프로필 삭제 (`RemoteExampleApi` 로 잠시 바꿔 확인, 커밋 안 함)
- [x] **mac 검증**(2026-09-30): `.orca/notes/ios.md` Phase 3 (Keychain cinterop 컴파일·동작, 마이그레이션, 401 전환)

완료 조건: 공통 조건 + 토큰이 평문 저장소에 남지 않음 확인 + 401 응답 시 양 플랫폼이 로그인 화면으로 전환.

### Phase 4 — Design System (1차 2026-09-29, 마무리 2026-09-29 windows)
- [x] 토큰 원본(JSON, semantic 이름, light/dark) 정의 — `design/tokens.json`
- [x] 생성: Kotlin(Compose) / Swift 산출 — Gradle 대신 `scripts/design_tokens.py` (DEC-006)
- [x] Android `PapyTheme`(토큰 + Material3 매핑, 다크 모드) / iOS 동적 색 토큰
- [x] Android Manifest 윈도우 테마 정리 — `Theme.Papy`(values / values-night) + 토큰 생성 `papy_colors.xml` 로 다크 모드 시작 배경
- [x] 스타일 방향 적용 (DEC-017): tokens.json 라이트/다크 조정, 채움형 입력·무테 카드·하단 CTA·큰 제목 헤더, 4개 화면 + 갤러리
- [x] 기본 컴포넌트 1차: Button, TextField/SelectField, Checkbox/Switch(Toggle)/Radio, Date·TimePicker, ImagePicker, Alert, Toast, InlineMessage, Loading, Card, (Android) Scaffold — 양 플랫폼 동일 이름·의미 ([design/README.md](design/README.md))
- [x] 키보드 처리 (Android PapyFormColumn / iOS PapyFormScrollView) (DEC-011)
- [x] 기존 4개 화면 적용 + 컴포넌트 갤러리 화면
- [x] Android 빌드 확인 (windows: `orca.sh check` — core 테스트 + assembleDevDebug + lint)
- [x] **mac 에서 `scripts/orca.sh check` 로 iOS 빌드 확인** (2026-09-30 mac 이후 매번 통과) (확인 항목: `.orca/notes/ios.md`)
- [x] Android 갤러리 수동 확인 (2026-09-30 windows, 에뮬레이터 API 33): 전 구간 스크롤, 다이얼로그, 토스트, 시간 선택(10:08 AM), 날짜 선택(Sep 15, 2026), 다크 전환 뒤 값 유지, 사진 2장 선택(2/3)
- [x] iOS 갤러리 확인 (2026-10-01 mac 시뮬레이터): 라이트·다크(시스템), 키보드 위로 스크롤·완료 버튼, 날짜(2026년 9월 15일)·시간(오전 10:20), 포토피커 여러 장, 토스트·다이얼로그. 테마: 기본 시스템 설정 → 앱에서 라이트 고정 → 시스템 설정 복귀 확인

완료 조건: 공통 조건 + 화면 코드에 하드코딩 색·radius·spacing 없음 + 토큰 한 값을 바꾸면 양 플랫폼에 동시 반영됨을 확인 + 다크 모드 동작.

### Phase 5 — Push (2026-09-29 windows, Phase 6 다음 — DEC-048/049/053, iOS mac 검증 완료 2026-09-30)
- [x] 공통 푸시(core:data `push/`): `PushProvider`/`NoOpPushProvider`, `PushMessageParser`(link·screen), `PushTokenApi`(가정 계약), `PushRepository`(SessionListener — 로그인·복원 시 등록, 로그아웃 직전 해제+deleteToken), `PushManager`(pendingDeepLink·permission) — [docs/push.md](docs/push.md)
- [x] `DeepLink` 계약(core:common) + 플랫폼 `DeepLinkRouter`(Android·iOS 같은 path 표, new_feature.py 가 추가)
- [x] Android: `FcmPushProvider`(google-services.json 있을 때만, 없으면 NoOp — 가짜 설정 파일로 플러그인 적용 경로도 빌드 확인), `PapyMessagingService`, 채널 `default`, `POST_NOTIFICATIONS`, 알림 탭 라우팅(singleTop)
- [x] iOS: Firebase SPM(FirebaseMessaging, pbxproj 손편집), `PushSetup`/`FcmPushProvider`(`#if canImport` + plist 있을 때만), `AppDelegate`(foreground 표시·탭), `PushObserver`(권한·DeepLink), `UIBackgroundModes` — mac 검증 완료
- [x] 로그인 시 토큰 등록 / 로그아웃 시 해제 (테스트: PushRepository 4, Parser 3, PushManager 2, DeepLink 4)
- [x] 설정 파일 `.gitignore`, 절차 [docs/template-setup.md §3](docs/template-setup.md)
- [ ] **실기기 확인**: 설정 파일을 넣고 foreground/background/종료 상태 수신 및 탭 이동 (Firebase 프로젝트 필요)
- [x] **mac 검증**(2026-09-30): `.orca/notes/ios.md` Phase 5 (SPM 해석, Swift 컴파일, 권한·탭 이동)

완료 조건: 공통 조건 + 양 플랫폼 실기기에서 foreground/background/종료 상태 수신 및 탭 시 올바른 화면 이동 + 파서/라우터 공통 테스트. → 공통 테스트·Android 빌드 완료, 실기기·iOS 는 Firebase 설정·mac 필요.

### Phase 6 — 테스트·현지화·템플릿화 (2026-09-29 windows, Phase 5 보다 먼저 — DEC-049, iOS mac 검증 완료 2026-09-30)
- [x] ViewModel 테스트(상태·이벤트) — Login 5·Signup 4·Splash 2·Home 1 + 생성 feature 3. 커버리지 목표: 모든 ViewModel·Repository 는 commonTest 1개 이상(arch_check 가 검사), Turbine·Kover 미도입 (DEC-050)
- [x] UI 문자열 → `strings.xml` / `Localizable.xcstrings`(ko 기본 + en), KMP 에러 메시지 → `ErrorCode` 매핑, 로케일 포맷 `PapyFormat`, 키 일치 검사 `scripts/strings_check.py` (Phase 4 와 함께 선행, DEC-018~020)
- [x] 새 기능 추가: `scripts/new_feature.py`(KMP·등록·Android/iOS 화면·경로·문자열 생성) + 기준 예시 `:feature:example`(홈 메뉴 진입점) — 생성 그대로 check·lint 통과 (DEC-051)
- [x] 템플릿 치환: [docs/template-setup.md](docs/template-setup.md)(앱 이름·패키지·번들 ID·Team ID → API URL → Firebase → 디자인 → 예시 정리) + `scripts/rename_app.py` — 임시 워크트리에서 rename → new_feature → check 통과 확인
- [x] 아키텍처 규칙 자동 검사 `scripts/arch_check.py` (feature 간 의존, core→feature, commonMain UI import, 테스트 존재, feature 등록) — orca check·CI
- [ ] (옵션) Android Compose UI 테스트, iOS XCTest 타깃 — 보류
- [x] **mac 검증**(2026-09-30): `.orca/notes/ios.md` Phase 6 (ExampleScreen, 생성 템플릿 Swift 컴파일)

완료 조건: 공통 조건 + 가이드대로 신규 feature 1개를 추가해 양 플랫폼 동작 확인. → Android 빌드·테스트까지 확인, 실행·iOS 는 기기/mac 확인 대기.

---

### Phase 7 — Python 제거, Kotlin 대체 (2026-09-29 windows)
- [x] `scripts/*.py` 5개 삭제 → build-logic `:tooling`(순수 Kotlin) + 루트 태스크 `templateCheck`·`generateDesignTokens`·`checkDesignTokens`·`checkStrings`·`checkArchitecture` (DEC-056)
- [x] `checkArchitecture` 는 Gradle 프로젝트 의존 그래프로 검사 (위반 주입 시 `:feature:home 가 다른 feature 에 의존` 검출 확인)
- [x] `newFeature`·`renameApp` 은 루트 빌드 밖 `./gradlew -p build-logic :tooling:run --args="…"` (DEC-057)
- [x] orca check·CI·docs(template-setup, conventions, design/README, README)·플랫폼 scaffold 주석을 새 명령으로
- [x] 검증: 토큰 생성물 바이트 동일(헤더 생성 명령 한 줄만 변경), Python/Kotlin newFeature 결과 동일(출처 주석만 다름), 임시 워크트리 renameApp → newFeature → check 통과

### Phase 8 — 환경 분리 dev / staging / prod (2026-09-29 windows, iOS mac 검증 완료 2026-09-30)
- [x] `env/common·dev·staging·prod.properties`(공개) + `env/<env>.local.properties`(비밀, gitignore) + `.example` (DEC-058)
- [x] KMP: build-logic 이 선택된 환경으로 `AppConfig` 생성, core 는 값만 사용, `initKoin(pushProvider)` 등 진입점 정리 (DEC-059)
- [x] Android: flavor dev/staging/prod, applicationId·앱 이름 접미사(동시 설치), flavor 별 google-services(없으면 NoOp), prodRelease 에만 R8·로그 제거, 서명 자리, 환경 불일치 방지 (DEC-060) — assembleDevDebug·assembleStagingDebug·assembleProdRelease 빌드 확인
- [x] iOS: Build Configuration 6개, scheme 3개, `Env.generated.xcconfig`, 번들 ID·표시 이름 접미사, 환경별 GoogleService-Info.plist 복사, Kotlin framework 에 APP_ENV 전달 (DEC-061) — mac 검증 완료
- [x] `orca.sh check --env`, CI Android 3환경(prodRelease 포함)·iOS scheme `iosApp-dev`, renameApp 을 env 구조에 맞춤 (임시 워크트리 rename → newFeature → check dev/staging 통과)
- [x] [docs/environments.md](docs/environments.md), template-setup §2·§3, push.md, README
- [x] **mac 검증**(2026-09-30): `.orca/notes/ios.md` Phase 8 (pbxproj 손편집, xcconfig 와일드카드 조건, scheme 3개 동시 설치)
- [ ] (보류, 라이브러리) AGP 9.0.1 의 R8 이 Kotlin 2.4 메타데이터 경고 — R8 버전 올림은 승인 필요

### Phase 9 — 앱 기본 패턴 (2026-09-30 mac, DEC-067~076)
- [x] 목록 공통 패턴: core:common `Paginator`·`ListState`·`Page`(라이브러리 없음) + Android/iOS `PapyPagedList`(당겨서 새로고침·다음 페이지·빈/오류/다음 페이지 실패) — 예시 탭 3페이지 확인 (DEC-070)
- [x] 하단 탭: Android `MainTab`+`PapyNavigationBar`, iOS `MainTab`+`TabView`(탭별 스택), 탭 추가 절차 docs/conventions.md §5 (DEC-071)
- [x] 설정 화면(feature:settings): 로그아웃·언어·테마·앱 버전·환경(dev/staging 만) + 약관·개인정보 웹뷰 (DEC-072)
- [x] 네트워크 상태 `NetworkMonitor`(직접 구현) + `PapyOfflineBanner`, 웹뷰 공통 화면 (DEC-073) — Android 에뮬레이터에서 오프라인 띠 확인, iOS 는 시뮬레이터에서 네트워크를 끌 수 없어 실기기 확인 필요
- [x] DEC-045 토큰 재발급: `TokenRefresher` + BaseApi 1회 재시도, 동시 요청 재발급 1회, 실패 시 세션 만료, `auth.refreshPath` 비우면 바로 만료 (DEC-067/068) — iOS 실기 흐름(401→재발급→재시도, 거절→로그인)·Ktor 로그 Authorization 마스킹 확인
- [x] Turbine·Kover 도입, ViewModel 테스트 Turbine 정리, CI 커버리지 리포트 (DEC-074)
- [x] newFeature: 목록 패턴 화면 생성 + `--tab` 옵션 — 임시 워크트리에서 `notice`·`board --tab` 생성 → check(Android·iOS) 통과
- [x] DEC-053: Firebase 를 안 쓰는 앱의 패키지 빼기 절차 docs/push.md — iOS 는 SPM 제거 상태로 빌드 확인
- [x] debug 전용 localhost 평문 허용(로컬 목업 서버) docs/environments.md (DEC-075)

### Phase 10 — 운영·릴리스 (2026-09-30 mac, DEC-077~083)
- [x] 버전 한 곳(env `app.versionName`·`app.versionCode`) → Android flavor · iOS xcconfig · KMP AppConfig (DEC-077)
- [x] 강제·선택 업데이트: `AppUpdateManager` + 가정 API + `SampleAppVersionApi`, Android `AppUpdateEffect`(Play In-App Update), iOS `.appUpdateAlert` — 양 플랫폼 목업 서버로 선택("나중에" 기억)·강제(닫기 불가) 확인 (DEC-081)
- [x] Kermit 로깅: 환경별 수준, prod 는 크래시 리포터로만, Ktor 로그 연결 (DEC-078)
- [x] 크래시·이벤트 인터페이스 + Firebase Crashlytics·Analytics(설정 파일 있을 때만), 사용자 id 연결 (DEC-079)
- [x] 인앱 리뷰(Play / StoreKit)·인앱 업데이트(Play) 인터페이스와 구현, 설정 "앱 평가하기" (DEC-082)
- [x] 진입점 `PlatformServices` 로 통합 (DEC-080)
- [x] docs/release.md(서명·환경별 릴리스 빌드·버전 올리기·로그·업데이트·리뷰·체크리스트) — prodRelease R8 APK 실행 확인 (DEC-083)
- [ ] Firebase 실제 프로젝트로 Crashlytics·Analytics 수신 확인(설정 파일 필요), Play/App Store 에서 인앱 리뷰·업데이트 실제 표시 확인(스토어 배포 필요)

### Phase 11 — 리팩토링·컴포넌트화 마무리 (2026-09-30 mac, DEC-084~087)
- [x] 변경 목록을 구현 전에 결정으로 기록 (DEC-084)
- [x] 반복 UI 통합: `PapyScrollScreen`·`PapyMenuCard`(양 플랫폼), iOS `AuthStatusObserver` → `FlowState`, design/README.md 대응표 갱신
- [x] 라이브러리: catalog 전 항목 사용 확인(제거 대상 없음), `:shared` framework bundleId 지정(Kotlin 경고 제거)
- [x] 네이밍·배치: conventions.md 폴더 표 갱신 + templateCheck [6](KMP package=디렉터리, Android·iOS 파일/화면 배치, 문자열 snake_case)
- [x] iOS Team ID 한 곳(Config.xcconfig) (DEC-086)
- [x] 문서: 낡은 docs 3개 삭제, 서버 계약 표 한 곳, ARCHITECTURE Gap·Phase 기록, PROGRESS 위험표
- [x] 템플릿 전 과정: 임시 워크트리 renameApp → 예시 제거 → newFeature(일반·--tab) → check dev·staging·prod(Android·iOS) + 시뮬레이터 실행, template-setup.md 보완 (DEC-087)
- [x] 버전 업그레이드는 하지 않음 — Phase 12 제안 표(보고서)

### Phase 12 — 버전 업그레이드 → v0.2 (2026-09-30 mac, DEC-093·094)
- [x] 최신 안정 버전·호환 관계 조회(Kotlin↔SKIE↔AGP↔Gradle), 적용 가능/불가 표 — 보고서·known-limitations §4
- [x] Kotlin 2.4.20 + SKIE 0.10.15
- [x] AGP 9.3.1 + Gradle 9.7.1 + compileSdk 37 + lifecycle 2.11.0 (AGP 9.4.1·Gradle 9.8.0 은 Kotlin 호환표 밖이라 보류)
- [x] Compose BOM 2026.09.00
- [x] 단계마다 check dev·staging·prod(Android·iOS) + 에뮬레이터·시뮬레이터 실행, 마지막에 `bundleProdRelease`
- [ ] Firebase deprecated API 정리 — v0.2 제외(서버 계약·실제 Firebase 필요, DEC-094)

### Phase 13 — core 보강 이식 + 상세 화면 패턴 + 공지사항 (2026-10-01 windows, iOS mac 검증 완료 2026-10-01)
- [x] core 이식(kmp-decompose 와 같은 코드): `BaseApi.upload`(multipart), `PermissionController`(Android·iOS 구현), `RelativeTime`(kotlinx-datetime), `Paginator.update/restart/clear`, `UserModel.avatarUrl`·`SessionRepository.updateUser`, `ErrorCode` 4개, 온보딩 저장 키, `DevicePlatform` (DEC-098)
- [x] **버그 수정 2건**: ① 로그아웃해도 자동 로그인 값이 남던 문제(`SessionRepository.logout` 순서) ② Android 에서 알림·링크의 딥링크를 열면 앱이 종료되던 문제 — 확장 함수 `NavController.navigate(deepLink)` 가 멤버 `navigate(route)` 에 가려져 있었다 → `openDeepLink` 로 이름 변경 (DEC-099)
- [x] 외부 링크로 앱 열기: `papy://<path>` (Android intent-filter, iOS CFBundleURLTypes·`onOpenURL`) → `PushManager.openLink`
- [x] 상세 화면 기준 `feature/example/detail`: id 로 불러오기, `PapyContentState`(양 플랫폼), 수정(즐겨찾기) → `ExampleRepository.changes` → 목록이 그 항목만 교체
- [x] `:feature:notice` 목록 + 상세, 홈 메뉴, 딥링크 `notice`, `notice/<id>`, `example/<id>`
- [x] 디자인 시스템(양 플랫폼): `PapyContentState`, `PapyBadge`, 상대 시간 표시(`RelativeTime.label()` / `PapyFormat.relative`)
- [x] 확인: check dev·staging·prod(iOS Kotlin 컴파일 포함), 에뮬레이터 — 공지 목록·상세, 즐겨찾기 → 목록 배지, 링크 3종(실행 중·탭·꺼진 상태)
- [x] **mac**(2026-10-01): Swift 컴파일(오류 1건 수정: `RelativeTimeCompanion.shared`)·실행 — 새 파일 2개(`example.detail.screen.swift`, `notice.detail.screen.swift`), 고친 파일(`example.screen`, `notice.screen`, `home.screen`, `app.navigation`, `app.route`, `deeplink.router`, `papy.states`, `papy.format`, `ui.error.text`), Info.plist URL scheme

### Phase 14 — 약관·비밀번호 재설정·탈퇴·프로필(업로드) (2026-10-01 windows, iOS mac 검증 완료 2026-10-01)
- [x] 약관 동의(`feature/auth/terms`) → 회원가입(선택 동의 값 전달, 가입은 약관부터), 비밀번호 규칙 `PasswordPolicy`(8자)
- [x] 비밀번호 재설정(`feature/auth/reset`): 한 화면 두 단계, 둘째 단계의 뒤로가기는 첫 단계로(Android `BackHandler`, iOS 뒤로 버튼 교체)
- [x] 이전 화면에 결과 알리기: `LoginNoticeStore`(가입·재설정 완료 → 로그인 화면 토스트) — 플랫폼 코드 없이 양쪽 같게
- [x] 회원 탈퇴(`feature/settings/withdraw`): 안내·비밀번호·확인 체크·확인 다이얼로그 → 세션·저장한 아이디 삭제
- [x] 프로필 편집(`feature/settings/profile`): 사진 선택 → 저장 때 업로드(multipart) → `SessionRepository.updateUser`, 저장 전 나가기 확인
- [x] 디자인 시스템(양 플랫폼): `PapyRemoteImage`·`PapyAvatar`(Android Coil + coil-network-okhttp, iOS AsyncImage)
- [x] 확인: check 3환경, 에뮬레이터 — 약관 → 가입 → 로그인 토스트, 재설정 단계 뒤로가기, 프로필 사진 업로드(Bearer)·저장·설정 반영·나가기 확인, 탈퇴
- [x] **mac**(2026-10-01): Swift 새 파일 5개(`terms.screen`, `password.reset.screen`, `withdraw.screen`, `profile.screen`, `papy.remote.image`)와 고친 파일(`login.screen`, `signup.screen`, `settings.screen`, `app.navigation`, `app.route`) 컴파일·실행

### Phase 15 — 권한·온보딩·알림함·검색·뒤로가기 재확인 (2026-10-01 windows, iOS mac 검증 완료 2026-10-01)
- [x] 권한 패턴 `:feature:permission`: 왜 필요한지 → 요청 → 거부 시 설정 안내, 화면이 다시 보일 때마다 재확인(Android `LifecycleResumeEffect`, iOS `scenePhase`). `MainActivity` 가 권한 창 연결, manifest·Info.plist 에 카메라·위치 예시
- [x] 온보딩 `feature/auth/onboarding`: 첫 실행 때 한 번(스플래시가 분기), `PapyPageIndicator`(양 플랫폼)
- [x] 알림함 `:feature:inbox`: 읽음 처리(먼저 화면 반영), 연결 화면은 `PushManager.openLink` 로(알림 탭과 같은 길)
- [x] 검색 `:feature:search`: 입력이 멈추면 자동 검색, 늦은 결과 버림, 최근 검색어
- [x] Android 탭 루트의 뒤로가기: 다른 탭 → 홈 탭, 홈 탭 → "한 번 더 누르면 종료"(2초) — `AndroidNavHost` 의 `BackHandler`
- [x] 확인: check 3환경, 에뮬레이터 — 온보딩(첫 실행·뒤로가기), 알림 → 공지 상세, 모두 읽음, 검색 → 예시 상세, 카메라 거부 → 설정에서 허용 후 돌아오면 "허용됨", 종료 재확인
- [x] **mac**(2026-10-01): Swift 새 파일(`onboarding.screen`, `papy.page.indicator`)과 다시 쓴 파일(`permission.screen`, `inbox.screen`, `search.screen`), 고친 파일(`splash.screen`, `home.screen`, `app.navigation`, `app.route`), Info.plist 사용 목적 문구, iOS 권한 창 3종

### Phase 16 — 결정 확정·목업 서버·iOS UI 테스트 (2026-10-01 mac, DEC-101)
- [x] 검토대기 결정 전부 확정(사용자 일괄 승인)
- [x] 로컬 목업 서버 `mockServer`(build-logic tooling, JDK HttpServer — 라이브러리 추가 없음): 서버 계약대로 응답, `/__mock/expire`·`reset`
- [x] iOS UI 흐름 테스트 `iosApp/UITests`(xcodegen 정의 + 생성물 커밋, 설치된 앱을 번들 ID 로 조작) + `scripts/orca.sh uitest [--sim] [--only]`
- [x] 확인: uitest 14개 통과(iPhone 18 Pro 시뮬레이터, 새로 설치 상태), 끝나면 목업 서버 종료·env/dev.local.properties 원복

### Phase 17 — Android UI 흐름 테스트 (2026-10-04 windows, DEC-103)
- [x] `:androidUiTest`(`com.android.test`, 대상 `:androidApp` dev): UiAutomator 로 설치된 앱을 밖에서 조작 — 앱 코드에 테스트용 태그·의존성을 넣지 않는다
- [x] 라이브러리(사용자 승인 2026-10-04): androidx.test runner 1.7.0, ext-junit 1.3.0, uiautomator 2.4.0 — 테스트 모듈에만
- [x] `scripts/orca.sh uitest android [--port] [--only]`: 목업 서버 → `adb reverse` → env/dev.local.properties 잠시 교체 → `connectedDebugAndroidTest` → 서버 종료·원복
- [x] 흐름 15개: 온보딩·가입·재설정·로그인·공지·예시·알림함·검색·권한·프로필·링크·테마·세션 만료·뒤로가기 종료·탈퇴. 영어 문구로 찾는다(앱 언어를 en-US 로 고정), 실패하면 화면 글자를 `adb logcat -s AppFlowTest` 에 남긴다
- [x] 확인: 에뮬레이터(API 33) 15개 통과, 끝나면 목업 서버 종료·env 원복
- [x] **mac**(2026-10-06): check 3환경, `uitest android` 15개(Pixel 9 API 35, 기기 언어 한국어), `uitest` 14개(iPhone 18 Pro). 고친 것: 권한 창 거부 버튼·사진 선택기를 id 로 찾기, uitest 스크립트가 조용히 끝나던 두 경우(`grep` 종료코드)

### Phase 18 — 소셜 로그인 → v0.4.1 (완료·승인 2026-10-26, DEC-105~109·119·124)
흐름: 로그인 화면 버튼 → **플랫폼**이 SDK 로 인증(Activity·ViewController 필요) → 받은 토큰을 KMP 에 넘김 → KMP 가 서버와 교환 → `SessionRepository.start()` (아이디 로그인과 같은 길). 키가 없는 제공자는 NoOp — 버튼이 안 보이고 빌드·실행은 같다(Push 와 같은 방식).
- [x] 18-0 디자인 동등성(DEC-112·114·117·118, 2026-10-22 mac): `orca.sh capture`(두 플랫폼 인증 화면 7개를 한국어·같은 이름으로 찍고 `build/capture/index.html` 에 나란히, 기기 글자 크기·화면 크기·굵은 글씨·iOS 글자 크기를 기본값으로 맞춘 뒤 복원), [컴포넌트 명세](design/components.md), Pretendard 글꼴, 아이콘 원본 하나(Lucide → Android VectorDrawable·iOS 에셋, `PapyIcon`), Android 한국어 어절 줄바꿈, 약관 목록 정렬, iOS 뒤로 버튼 통일(`PapyBackButton`)
- [x] 18-0 중 발견한 버그(DEC-115·116): 틀린 비밀번호에 '인증이 만료되었어요'가 나오던 문제 → `ErrorCode.InvalidCredentials`, 로그인 실패 뒤 비밀번호만 비움(KMP 한 곳)
- [x] 18-1 KMP(2026-10-22 mac, DEC-105~109): core:data `social/` — `SocialProvider`(kakao·google·apple·naver), `SocialCredential`(토큰·인가 코드·nonce), `SocialAuthProvider`(로그아웃 때 SDK 정리, `PlatformServices(socialAuth:)`, 기본 NoOp), `SocialConfig`(env 키 → 보일 제공자, 애플은 iOS 만, `social.mock`), `UserModel.provider`, `ErrorCode.SocialLoginFailed`. feature:auth `social/` — `SocialLoginApi`(`POST /api/auth/social` 가정), `SocialLoginRepository`(교환 → `SessionRepository.start`, 4xx → SocialLoginFailed). `LoginViewModel`: `socialProviders`, `socialLoginTapped` → `StartSocialSignIn` 이벤트(mock 이면 바로 교환) → 플랫폼이 `socialCredentialReceived`·`socialSignInCancelled`·`socialSignInFailed`, 신규 사용자는 `SocialSignedUp`. env: 키는 `<env>.local.properties`(.example 에 목록), dev 는 `social.mock=true`. 목업 서버 `/api/auth/social`(token `reject` → 401, `new…` → 신규). 테스트: SocialAuth 4·SocialLoginRepository 7·LoginViewModel 소셜 7. 플랫폼은 오류 문구·새 이벤트만 처리(버튼·SDK 는 18-2·18-3)
  - Swift 노출(DEC-026): `SocialProvider.kakao` 등 enum, `SocialCredential(provider:token:authorizationCode:nonce:)`(인자 모두 전달), `PlatformServices(…, socialAuth:)`, `NoOpSocialAuthProvider.shared`, `viewModel.socialLoginTapped(provider:)`·`socialCredentialReceived(credential:)`·`socialSignInCancelled()`·`socialSignInFailed()`, 이벤트 `onEnum(of:)` 의 `.startSocialSignIn(let e)`(`e.provider`)·`.socialSignedUp`
- [x] 18-2 Android(2026-10-23 mac, DEC-119): 카카오 v2-user 2.26.0(카카오톡 → 실패 시 카카오계정, 사용자 취소면 넘기지 않음)·구글 Credential Manager 1.6.0 + googleid 1.2.1(ID 토큰 + nonce)·네이버 oauth 5.12.0(`NidOAuth`) — `social/AndroidSocialSdk`, 로그아웃 시 SDK 정리 `AndroidSocialAuthProvider`, 카카오 리다이렉트 Activity(키 없으면 꺼짐, manifest placeholder). 로그인 화면 `PapySocialLoginSection`("또는" + 각 사 색·문구 버튼, 로고는 앱마다 `SocialBrandLogo` 에 연결), 브랜드 색 토큰. 확인: check(빌드·lint), uitest android 16개(새 `test13b_socialLogin` dev mock 포함), prodRelease R8 빌드. **실제 SDK 로그인은 키·콘솔 등록이 없어 미확인**
- [x] 18-3 iOS(2026-10-23 mac, DEC-119): 카카오 kakao-ios-sdk 2.29.0·구글 GoogleSignIn 10.1.0·네이버 NidThirdPartyLogin 5.2.1(SPM)·애플 AuthenticationServices — `social/ios.social.sdk.swift`, `onOpenURL` 은 소셜 먼저(키 없는 SDK 는 건너뜀), URL 스킴은 env → `Social.local.generated.xcconfig`, 로그인 화면 `PapySocialLoginSection`. **실제 SDK 로그인은 키·콘솔 등록이 없어 미확인**
- [x] 18-4 탈퇴·신규 가입(2026-10-23 mac): 소셜 사용자 탈퇴는 비밀번호 없이 확인 체크만(`WithdrawUiState.requiresPassword`), 서버 탈퇴 뒤 `SocialAuthProvider.unlink`(카카오 연결 끊기·네이버 연동 해제·구글 자격 증명 삭제, 애플은 서버가 폐기). 처음 가입한 소셜 사용자는 약관 화면(동의 모드) → `SocialConsentViewModel`(`POST /api/auth/social/agreements`) → 메인, 뒤로 = 로그아웃. 미리보기 `TermsContent`·소셜 버튼(DEC-122). UI 테스트 `test13c`(두 플랫폼, 목업은 네이버 = 신규)
- [x] 18-5 확인(2026-10-26 mac): `orca.sh check`(KMP 테스트·아키텍처·Android 빌드), iOS 빌드, `uitest all` — Android 18개·iOS 17개(16 통과·1 건너뜀) 모두 통과. 소셜 흐름 test13b(로그인)·test13c(신규 가입 동의·비밀번호 없는 탈퇴). iOS 테스트 안정화(test03 은 `toLogin` 으로 시작, 토스트는 `papy.toast` 식별자). 실제 SDK 로그인은 known-limitations §2
- [x] 18-6 문서: template-setup §3-1(키·콘솔·iOS URL 스킴·애플 capability·신규 가입·탈퇴), conventions §4-10(미리보기 먼저), known-limitations(§1 #5~7 가정, §2 실제 SDK 미확인)

완료 조건: 공통 조건 + 키가 없는 상태에서 기존 15·14개 UI 흐름 그대로 통과 + 목업 서버로 소셜 신규·기존 사용자 흐름 양 플랫폼 확인 + 제공자 하나를 env 로 끄면 버튼만 사라짐 + 비교 페이지에서 로그인·소셜 화면이 명세와 맞음.

### Phase 19 — 메시지(채팅) UI → v0.4.2 (완료·승인 2026-10-26, DEC-111·125~128)
화면 구성·동작은 텔레그램처럼, 코드·이름·로고는 쓰지 않는다(GPL, DEC-111). 자체 UI + KMP 공유 로직으로 두 플랫폼이 같은 구조·규칙으로 그린다. 새 모듈 `:feature:chat`(떼어낼 수 있게, DEC-110).
- [x] 19-0 공통 UI 보강(2026-10-26 mac, 양 플랫폼 빌드 확인, 미리보기·갤러리): 바텀시트 `PapyBottomSheet`, 목록 행 `PapyListItem`(앞 아이콘·아바타/제목·부제/뒤 요소), 액션 메뉴 `PapyActionSheet`(바텀시트 위 선택지, 위험 항목), Android 화면 전환 통일(쌓이는 화면 가로 밀기·탭 전환 페이드 — iOS 기본 동작과 맞춤)
- [x] 19-1 KMP(2026-10-26 mac, `newFeature chat --tab`): 대화 목록·대화방 상태, 메시지 묶기(같은 보낸이 연속·시간 간격), 날짜 구분선, 전송 상태(보내는 중·실패·재전송)·낙관적 전송, 읽음 표시, 위로 이전 메시지 불러오기(`Paginator` 재사용), 실시간 수신 계약(D19) — 테스트: 묶기·정렬·전송 상태 전이·중복 수신
- [x] 19-2 디자인 시스템(양 플랫폼, 미리보기 먼저 DEC-122): 말풍선(내 것/상대·묶음 모양·사진·파일 말풍선), 날짜 구분선, 입력창(키보드에 붙음·여러 줄·첨부 버튼), 첨부 메뉴(바텀시트: 사진·카메라·파일), 맨 아래로 이동 버튼, 길게 눌러 메뉴 — 바텀시트·목록 행·액션 메뉴는 19-0 것을 쓴다
- [x] 19-2b 네이티브 첨부 연결(DEC-125): 파일(Android SAF `OpenDocument` / iOS `fileImporter`), 사진(Android Photo Picker / iOS `PhotosPicker`), 카메라 촬영(Android `TakePicture` + FileProvider / iOS `UIImagePickerController` 카메라, 권한 안내는 기존 권한 흐름) → KMP 에 `Attachment`(이름·종류·크기·바이트) 로 넘겨 업로드
- [x] 19-3 Android(ocra-android)·19-4 iOS(ocra-ios): 아래부터 쌓이는 목록, 새 메시지 때 위치 유지, 밀어서 답장(선택), 키보드·안전 영역 처리
- [x] 19-5 목업 서버(2026-10-26, curl 로 확인): 대화·메시지·첨부 업로드 API + 실시간 흉내(DEC-126). 목업 서버 대상 UI 흐름 테스트(uitest)는 하지 않는다(DEC-125)
- [x] ~~19-6 아이콘 원본 하나~~ — 18-0 에서 완료(DEC-118). 메시지 화면에 필요한 아이콘만 `design/icons` 에 추가
- [x] 19-7 문서: 서버 계약(가정)·known-limitations·conventions, 모듈 빼는 방법(template-setup)

완료 조건: 공통 조건 + 목업 서버로 보내기·받기·실패 재전송·이전 메시지 불러오기 양 플랫폼 확인 + 같은 대화 데이터를 두 플랫폼이 같은 묶음·구분선으로 표시(비교 페이지) + `:feature:chat` 을 빼도 빌드·기존 흐름 통과.

### Phase 20 — 커뮤니티 게시판 → v0.4.3 (완료·승인 2026-10-26, DEC-129)
떼어낼 수 있는 모듈 `:feature:community`(newFeature --tab, DEC-110). 화면은 각자(Compose/SwiftUI), 로직·규칙은 KMP, 디자인·흐름은 공통(DEC-112·127).
- [x] 20-1 KMP: 글 목록(최신순·페이징)·상세(본문·사진·댓글)·작성·수정·삭제(내 글만), 좋아요(낙관적·되돌리기), 댓글(목록·작성·삭제), 신고(사유 선택), 차단(차단한 사용자의 글·댓글 숨김, 차단 목록·해제). 사진 첨부는 19 의 `AttachmentInput` 을 core 로 옮겨 같이 쓴다 — 테스트: 좋아요 전이·차단 필터·권한(내 글)·작성 검증
- [x] 20-2 디자인 시스템(미리보기 먼저): 글 카드(작성자·시간·본문 3줄·사진 썸네일·좋아요·댓글 수), 좋아요 버튼, 댓글 행, 사진 묶음(1~4장 격자)
- [x] 20-3 Android·20-4 iOS: 목록(당겨서 새로고침·끝에서 다음)·상세·작성(사진 최대 4장, 하단 고정 등록), 더보기 메뉴(내 글: 수정·삭제 / 남의 글: 신고·차단 — `PapyActionSheet`), 신고 사유 시트, 설정 → 차단한 사용자
- [x] 20-5 목업 서버·예시 API: 글·댓글·좋아요·신고·차단(가정 계약)
- [x] 20-6 문서: 서버 계약(가정)·App Store 1.2(신고·차단·연락처, 부적절 콘텐츠 필터는 서버)·모듈 빼는 방법·known-limitations
- 확인: KMP 테스트·미리보기·빌드, Phase 끝 `uitest all` 한 번(DEC-120)

### Phase 21 — 공통 UI 2차·알림 연결 → v0.4.4 (완료 2026-10-26)
- [x] 21-1 디자인 시스템(양 플랫폼, 미리보기·갤러리): 칩 `PapyChip`(선택·지우기), 세그먼트 탭 `PapySegmentedTabs`, 스켈레톤 `PapySkeleton`(반짝임, 목록 첫 로딩 자리), 검색창 `PapySearchBar`(아이콘·지우기·검색 키)
- [x] 21-2 적용: 검색 화면(검색창·최근 검색어 칩), 커뮤니티 "전체 · 내 글" 세그먼트(KMP 필터), 메시지·커뮤니티 목록 첫 로딩 스켈레톤
- [x] 21-3 딥링크: `chat/{id}`(대화방, 이름은 KMP 가 불러온다)·`community/{id}`(글 상세) — 푸시 `screen`/`link` 로도 열린다
- [x] 21-4 탭 배지: 메시지 탭에 안 읽은 수(KMP `ChatRepository.unreadTotal`, 방을 열면 그 방만큼 줄어든다)
- [x] 21-5 디자인 비교 캡처(`orca.sh capture`)를 메시지·커뮤니티까지(목록·대화방·글 목록·글 상세)
- [x] 21-6 문서·테스트: components.md, push.md(딥링크 path), KMP 테스트(필터·안 읽은 수·제목 불러오기)

### Phase 22 — 채팅 고도화 → v0.4.5 (완료 2026-10-26)
- [x] 22-1 KMP: 답장(인용 — `Message.replyTo` 미리보기, 보내기에 replyToId), 입력 중 표시(실시간 `typing` 이벤트·폴링 응답 `peerTyping`, 5초 뒤 사라짐, 내가 입력하면 3초마다 한 번 알림), 대화방 메시지 검색(`GET …/messages/search?q=`, 결과 목록·누르면 그 메시지로) — 테스트
- [x] 22-2 디자인 시스템: 인용 블록(말풍선 안·입력창 위 답장 막대), 입력 중 점 3개, 사진 전체 화면(확대·축소·닫기)
- [x] 22-3 Android·22-4 iOS: 길게 눌러 "답장"·밀어서 답장, 인용 누르면 원문으로, 사진 누르면 전체 화면, 상단 검색(결과 목록)
- [x] 22-5 목업 서버·예시 API: 답장·입력 중·검색, 문서(서버 계약 #8 확장)
- [x] 확인(21·22 공통, 2026-10-26): KMP 테스트·미리보기·빌드, 디자인 비교 캡처 11화면, `uitest all` 두 플랫폼 통과(DEC-120)

### Phase 24 — 로컬 DB·오프라인 → v0.4.6 (완료·승인 2026-10-26, DEC-130·131)
- [x] 24-1 KMP 기반: SQLDelight 2.4.1, `papy.kmp.database`(모듈 → `XDatabase`·`<ns>.db`, 스키마 변경 확인), core:data `database/`(`SqlDriverFactory` Android·iOS, `databaseDispatcher`, `LocalCache`·`LocalCacheCleaner` — 로그아웃·다른 사용자 로그인 때 정리), `Paginator(cache = …)`(저장된 첫 페이지 먼저, 오프라인이면 그대로 + 짧은 오류), iOS framework `-lsqlite3`
- [x] 24-2 채팅: `ChatCache`(`Chat.sq` — 대화 목록 첫 페이지·방별 최근 200개·글 메시지 대기열), 대화방은 저장된 메시지 먼저·못 보낸 글은 다시 열 때 전송·재연결(`NetworkMonitor`) 때 실패 메시지 전송, 오프라인이면 저장된 메시지 유지 — 테스트(저장소 5·대화방 4)
- [x] 24-3 커뮤니티: `CommunityCache`(`Community.sq` — 전체 목록 첫 페이지, 수정·삭제·차단 반영) — 테스트
- [x] 24-4 iOS 이미지 캐시: Nuke 13.2.0(SPM) `LazyImage` 를 `PapyRemoteImage`(placeholder·contentMode)·채팅 사진·전체 화면에, `PapyImagePipeline`(디스크 200MB) — AsyncImage 는 디코딩 캐시가 없어 스크롤마다 다시 받음(DEC-131)
- [x] 24-5 문서: [docs/local-db.md](docs/local-db.md)(구조·새 기능에 DB 넣기·스키마 변경·저장 목록·이미지 캐시), ARCHITECTURE·template-setup·known-limitations

### Phase 27 — 버전 정책·운영 공지 → v0.4.7 (완료·승인 2026-10-26, DEC-132)
- [x] 27-1 KMP: 최신과 major·minor 가 다르면 강제, patch 만 다르면 권장(나중에), `minimumVersion` 은 그대로 강제. 같은 API 의 `notice`(점검 = 닫기 없음, 공지 = 확인 후 같은 id 안 보임) — 테스트
- [x] 27-2 Android(`AppUpdateEffect`)·iOS(`.appUpdateAlert`): 공지·점검 안내(업데이트 안내가 없을 때, 점검은 닫히지 않음), 문구 ko/en. iOS `FlowState` 에 nil 가능 StateFlow 연결 추가
- [x] 27-3 문서: release.md §5(버전 정책 예시·공지 계약)
- [x] 확인(24·27 공통, 2026-10-26): `orca.sh check`(KMP 테스트·아키텍처·Android 빌드), KMP iOS 테스트, iOS 빌드, `uitest all` — Android 18개·iOS 17개(16 통과·1 건너뜀) 통과(DEC-120)

### Phase 25 — 사람·프로필·문의 → v0.4.8 (완료·승인 2026-10-26, DEC-133)
- [x] 25-1 사용자 프로필(KMP `feature:community` `user/`): `UserProfile`(이름·사진·소개·글 수·isMe), `UserProfileViewModel`(프로필 + 그 사람의 글 페이징·좋아요·차단 → 닫기·삭제 반영), API `GET /api/users/{id}`·`…/posts`. 두 플랫폼: 글 카드·상세의 글쓴이, 댓글 아바타를 누르면 프로필, 프로필 카드(`UserProfileCard`)·더보기 → 차단 — 테스트 7
- [x] 25-2 1:1 대화 시작: `ChatApi.direct`(`POST /api/chats/direct`), `ChatRoomViewModel(peerUserId)` 가 처음 불러올 때 대화를 찾거나 만든다(실시간은 그 뒤 시작, 정해지기 전 보내기 막음). Android `ChatRoom(peerUserId)`, iOS `.directChat`·`IOSAppGraph.directChatRoomViewModel(userId:name:)` — 테스트 2
- [x] 25-3 문의하기(`feature:settings` `inquiry/`): 종류 4개·내용(2000자)·답장 이메일(계정 이메일), 앱 버전·플랫폼을 붙여 `POST /api/inquiries`, 보내면 안내 후 닫기. 설정 "앱 정보"에 행(App Store 1.2 연락 수단). 목업 서버 204 — 테스트 8, UI 흐름 `test12b_inquiry`(두 플랫폼)
- [x] 25-4 문서: known-limitations §1 #10, components.md(글쓴이 누르기·프로필·문의), template-setup(모듈 빼기 경로)
- [x] 확인(2026-10-26): `orca.sh check`, KMP iOS 테스트, iOS 빌드, `uitest all` — Android 19개·iOS 18개(17 통과·1 건너뜀) 통과. 첫 실행에서 찾은 문의하기 버그 2개(iOS 칩이 지우기 버튼으로 묶임, Android 안내를 닫으면 두 번 뒤로) 수정

### Phase 26 — 앱 잠금·알림 설정 → v0.4.9 (완료·승인 2026-10-26, DEC-134)
- [x] 26-1 앱 잠금: KMP core:data `security/AppLock`(켜면 새로 열 때·30초 넘게 백그라운드에 있다 돌아올 때 잠근다, 로그인한 동안만, 다시 로그인하면 풀린 채, 로그아웃하면 끈다 — SessionListener) — 테스트 4. Android `security/DeviceAuth`(androidx.biometric 1.1.0, 생체 또는 기기 PIN, MainActivity → FragmentActivity)·`AppLockGate`(onStop·onStart, 잠금 화면, 뒤로 = 앱 내리기). iOS `security/`(LocalAuthentication `.deviceOwnerAuthentication`, scenePhase, `NSFaceIDUsageDescription`). 설정 "알림·보안"의 앱 잠금 스위치(켤 때 먼저 본인 확인, 잠금 수단이 없는 기기는 숨김), 아이콘 `lock`
- [x] 26-2 알림 설정: `feature:settings` `notification/` — 채팅·댓글·공지·광고성 스위치(낙관적, 실패하면 되돌림), `GET/PUT /api/push/preferences`, 광고성 동의·철회 날짜 안내(정보통신망법 제50조), 기기 알림이 꺼져 있으면 안내·설정 열기(돌아오면 다시 확인) — 테스트 8. 공통 `PapySettingsSwitchRow`/`PapySettingsToggleRow`. 목업 서버 저장(reset 으로 처음 값). UI 흐름 `test12c_notificationSettings`
- [x] 26-3 문서: known-limitations(#11 계약, 서버가 종류별로 거른다, 실기기 생체 인증 미확인), components.md
- [x] 확인(2026-10-26): `orca.sh check`, KMP iOS 테스트, iOS 빌드, `uitest all` — Android 20개·iOS 19개(18 통과·1 건너뜀) 통과. 실제 생체 인증 잠금은 실기기 확인 필요(known-limitations §2)

### Phase 28 — 생성기 DB 옵션·기능 켜고 끄기 → v0.4.10 (완료·승인 2026-10-26, DEC-135)
- [x] 28-1 `newFeature <name> --db`: `papy.kmp.database` + `<Name>.sq`(목록 첫 페이지) + `cache/<Name>Cache`(LocalCache, Koin bind) + 저장소가 첫 페이지 저장·`cachedItems` + `Paginator(cache = …)` + 테스트용 메모리 DB(commonTest expect, androidHostTest JDBC, iosTest native). 확인: 임시 워크트리에서 `newFeature memo --db` 생성 → Android host·iOS 테스트·templateCheck·Android 컴파일 통과. 캐시의 디스패처는 `CoroutineContext`(테스트는 EmptyCoroutineContext — Unconfined 로 바꾸면 상태 순서가 달라졌다)
- [x] 28-2 기능 켜고 끄기: core:data `flags/FeatureFlags`·`FeatureKey`(버전 확인 응답 `features`, 마지막 값 저장, 없으면 기본값), 첫 사용처 대화방 첨부 버튼(`chat_attachments`, 두 플랫폼에서 숨김) — 테스트 5
- [x] 28-3 문서: local-db.md(생성기 한 번), release.md §5(features), conventions
- [x] 확인: `orca.sh check`(아키텍처 규칙에서 `core.data.feature` 패키지가 기능 모듈로 읽혀 `core.data.flags` 로 바꿈), KMP iOS 테스트 — 최종 `uitest all` 은 29 와 함께

### Phase 29 — 접근성 1차·최종 검수 → v0.4.11 (완료·승인 2026-10-26)
- [x] 29-1 화면 읽기 점검(두 플랫폼): 아이콘만 있는 버튼은 모두 이름이 있다(스캔으로 확인). 큰 제목·설정 섹션 제목을 제목으로 알림(Android `heading()`, iOS `.isHeader`), 누르는 카드·행·칩·글쓴이·인용에 Android `Role.Button`, 글쓰기 사진 빼기 버튼 누르는 영역 24 → 40(보이는 원은 24)
- [x] 29-2 최종 검수(2026-10-26): `orca.sh check`, 전체 KMP iOS 테스트, iOS 빌드, `uitest all` — Android 20개·iOS 19개(18 통과·1 건너뜀) 통과, 디자인 비교 캡처
- 남음(실기기·사람 확인): VoiceOver·TalkBack 으로 실제로 읽어 보기, 가장 큰 글자에서 모든 화면 — 23 으로


### Phase 30 — 파일 받기·보관·화면 캡처 막기 → v0.4.12 (완료·승인 2026-10-27, DEC-136~138)
- [x] 30-1 파일 받기·보관: core:data `files/FileDownloads`(조각 받기 Ktor·okio, `.part` → 이름 바꾸기, 같은 주소 한 번, 목록은 LocalStorage, 로그아웃하면 지움 — LocalCache), 토큰은 우리 서버 주소에만. 채팅 파일 말풍선: 누르면 받고(진행 표시) 연다·"저장됨", 설정 › 저장한 파일(열기·공유·지우기·모두 지우기). Android FileProvider(`files/downloads`)·공유 시트, iOS QuickLook·UIActivityViewController. 목업 서버 예시 PDF(`/__mock/files/guide.pdf`) — 테스트 10, UI 흐름 `test12d_fileDownload`
- [x] 30-2 화면 캡처 막기: env `security.blockCapture`(기본 false). Android FLAG_SECURE(스크린샷·녹화 검게), 앱 잠금이면 최근 앱 화면 가림(13+ setRecentsScreenshotEnabled). iOS 는 막을 수 없어 녹화·미러링 중 가림 + 스크린샷 안내, 앱 전환 화면 가림
- [x] 30-3 문서: environments.md, local-db.md §5, known-limitations
- 결정: 앱 내 결제(IAP)는 수수료 때문에 템플릿에서 뺀다(DEC-138)
- [x] 확인(2026-10-27): `orca.sh check`, 전체 KMP iOS 테스트, `uitest all` 에서 실패한 4개를 고쳐 그 테스트만 다시 — Android test12d·13·13b·13c, iOS test12d·13 통과. 찾은 것: ① 앱 버그 — 대화방을 다시 열 때 저장된 메시지를 받은 목록으로 바꾸면 iOS 스크롤이 중간에 남았다 → 바꾼 뒤 맨 아래로(KMP, 테스트 추가) ② 테스트 — test12d 가 설정 탭을 내려 둔 채 끝나 다음 테스트가 프로필을 못 찾음, iOS QuickLook 은 테스트가 볼 수 없는 별도 화면

### 이후 예정 (2026-10-26 기준, 순서는 Phase 마다 다시 확인)
| Phase | 버전 | 내용 |
|---|---|---|
| 23 1차 완성·실기기(보류 — 실기기·서버 준비 후) | v0.5.0 | 실서버 연동 가이드 정리, 실기기 확인(소셜 SDK·카메라·푸시·WebSocket), 접근성(VoiceOver·TalkBack·큰 글자) 점검, CI 켜기 |

## 4. 현재 발견된 위험 요소

| # | 위험 | 근거 | 영향 | 대응 Phase |
|---|---|---|---|---|
| R1 | ~~토큰 평문 저장~~ 해소(Phase 3, DEC-046) | `TokenStorage` → DataStore / NSUserDefaults. Android `allowBackup="true"` | 백업·루팅 기기에서 토큰 노출 | 3 |
| R2 | ~~401/토큰 만료 미처리~~ 해소(Phase 3, DEC-045) | `AppError.isUnauthorized` 정의만 있고 사용처 없음. 루트 전환은 화면 Event에만 의존 | 만료 후 앱이 로그인 상태로 남음 | 3 |
| R3 | 서버 계약 전부 가정(로그인 필드 `id` vs `username` 포함) — DEC-055 | 계약 표 docs/template-setup.md §2 | 실서버 연동 시 수정 필요 | 서버 확정 시 (KMP 안에서만 수정) |
| R4 | ~~사전 릴리스 의존성~~ 해소(lifecycle 2.10.0, DEC-043) | `lifecycle 2.11.0-beta01` (core `api` 노출) | 안정화 전 API 변경 | 1 (안정 버전 확인) |
| R5 | SKIE ↔ Kotlin 버전 결합 | SKIE 0.10.14가 지원하는 Kotlin 범위 밖으로 Kotlin 업그레이드 시 iOS 빌드 실패 | Kotlin 업그레이드 제약 | 상시 |
| R6 | ~~Koin 구버전~~ 해소(4.2.2, DEC-038) | Koin 4.0.3 + 최신 lifecycle/AGP 9 조합 | `koinViewModel` 호환 이슈 가능 | 1 |
| R7 | ~~환경 URL 생성 경로 이중화~~ 해소(Phase 3, Phase 8 에서 env/ 단일 원본) | Android는 `BuildConfig.API_BASE_URL`, iOS는 `GeneratedEnvironment` | 한쪽만 수정되는 불일치 | 3 |
| R8 | ~~Content-Type 중복 설정~~ 해소(Phase 3) | `ApiHeaders.json()` 헤더 + `KtorClient`의 `contentType()` | 엔진별 중복 헤더/예외 가능(미검증) | 3 |
| R9 | ~~query 미인코딩~~ 해소(Phase 3) | `BaseApi.request`의 수동 `joinToString` | 특수문자 파라미터 오류 | 3 |
| R10 | ~~iOS 서명/번들 ID 분산~~ 해소(Phase 6·8·11) — TEAM_ID 는 Config.xcconfig 한 곳(pbxproj 는 $(TEAM_ID)), 번들 ID·환경 접미사는 xcconfig·renameApp | | | |
| R11 | ~~테스트 공백~~ 해소(Phase 6·9) — 모든 ViewModel·Repository 테스트, Turbine·Kover, CI. UI 자동 테스트는 없음 | | | |
| R12 | ~~문서 불일치~~ 해소(Phase 1) | `docs/architecture.md`, `structure.txt`, `CHANGELOG.md`가 제거된 Navigator/파일명 기준 | 신규 참여자 오해 | 1 |
| R13 | ~~사용자 문자열이 KMP에 하드코딩~~ 해소 (DEC-019) | `ErrorCode`/`UiError` 로 대체 | — | 완료 |
| R14 | ~~Release 최적화 미설정~~ prodRelease R8 (Phase 8) | `isMinifyEnabled = false` | APK 크기·난독화 없음 | 6 |
| R15 | ~~`.env.*` 커밋됨~~ env/ 공개·비밀 분리 (Phase 8) | `.gitignore`는 `.env`만 제외. 현재는 URL만 포함 | 향후 비밀값 추가 시 유출 | 1 (규칙 명시) |
| R16 | ~~iOS framework 이름~~ `Core` 확정(DEC-034, 2026-09-30) | `Core` → `Shared` 변경 시 모든 Swift `import` 수정 | 일시적 iOS 빌드 깨짐 | 2 |

---

## 5. 결정이 필요한 사항

> 결정되면 `scripts/orca.sh decide` 로 [.orca/DECISIONS.md](.orca/DECISIONS.md) 에 DEC 로 기록하고 이 표의 해당 행에 DEC 번호를 적는다.

| # | 결정 | 선택지 | 권장 | 필요 시점 |
|---|---|---|---|---|
| D1 | 모듈 분리 수준 → **DEC-033** | (a) `:core` 유지 + 패키지 정리 (b) ARCHITECTURE 2.2 전체 분리 (c) core/feature 2~3개만 | (b)를 Phase 2에서 단계적으로. 템플릿 목적상 경계가 모듈로 강제되는 편이 유리 | Phase 2 전 |
| D2 | iOS framework 이름/형태 → **DEC-034**(Core 유지) | `Core` 유지 vs `Shared` umbrella, dynamic vs static | `Shared` umbrella, static 검토(dynamic 유지도 무방) | Phase 2 전 |
| D3 | Design Token 원본 형식 | (a) JSON + Gradle 생성 (b) Kotlin 상수 → SKIE 노출 (c) Style Dictionary 등 외부 도구 | (a): 기존 `generateEnvironment` 패턴과 일관, Swift에서 Kotlin 타입 변환 불필요 | Phase 4 전 |
| D4 | 생성 토큰 커밋 여부 | 커밋 vs 빌드 시 생성 | iOS 빌드 단순화를 위해 커밋 + CI에서 최신 여부 검사 | Phase 4 전 |
| D5 | Push 제공자 → **DEC-048**(뼈대 + FCM 기본, 설정 없으면 NoOp) | FCM(양 플랫폼) vs Android FCM + iOS APNs 직접 | 서버 구현 확인 후 결정 | Phase 5 전 |
| D6 | 서버 인증 계약 | refresh token 지원 여부, 로그인 요청 필드(`id` vs `username`), 로그아웃 API | 서버 팀 확인 | Phase 3 전 |
| D7 | 보안 저장소 구현 → **DEC-046**(직접 구현) | 직접 구현(Keystore/Keychain) vs 라이브러리(KVault, multiplatform-settings 등) | 의존성 최소화를 위해 직접 구현 또는 검증된 라이브러리 1개 | Phase 3 전 |
| D8 | 사용자 문자열 책임 | KMP 문자열 유지 vs 플랫폼 리소스 + 에러 코드 | 플랫폼 리소스 + 에러 코드 → **DEC-018, DEC-019** | 결정됨 |
| D9 | CI 플랫폼 → **DEC-029**(GitHub Actions) | 사내 GitLab(`origin`) vs GitHub(`github`) | 주 저장소가 `origin`이면 GitLab CI (macOS runner 필요) | Phase 1 |
| D10 | 사전 릴리스 의존성 허용 → **DEC-038/043**(안정판) | lifecycle beta 유지 vs 안정 버전 | 템플릿이므로 안정 버전 | Phase 1 |
| D11 | ViewModel 테스트 도구 → **DEC-050**(coroutines-test 만) | coroutines-test만 vs Turbine 추가 | Turbine 추가 | Phase 3/6 |
| D12 | 템플릿 식별자 → `renameApp`(build-logic, Phase 7 에서 Python 대체) + docs/template-setup.md | `papy`/`io.github.namjh654.papy` 유지 vs 치환 가능한 템플릿 이름 | 템플릿화 시점(Phase 6)에 치환 가이드/스크립트 제공 | Phase 6 |
| D13 | 소셜 제공자 범위 → **DEC-105**(4개 모두, 네이버 포함) | 카카오·애플·구글·네이버 전부 vs 일부 | 카카오·애플·구글 기본, 네이버 선택. 다른 소셜 로그인을 넣는 iOS 앱은 Apple 로그인도 요구됨(App Store 가이드라인) | Phase 18 전 |
| D14 | SDK 라이브러리 추가(기준선 밖) → **DEC-106** | 카카오 SDK v2, androidx.credentials + googleid, 네이버 OAuth / iOS KakaoSDK·GoogleSignIn·NidThirdPartyLogin SPM, Apple 은 OS 기본 | 각 사 공식 SDK. 버전은 착수 때 최신 안정판·호환 조회 | Phase 18 전 |
| D15 | core 공개 계약 변경 → **DEC-107** | `PlatformServices`에 `SocialAuthProvider` 추가, `UserModel.provider`, LoginUiState 에 제공자 목록, `SocialLoginRepository` 신설 — Swift 노출 형태 포함 | 아이디 로그인 계약은 그대로 두고 추가만 | Phase 18 전 |
| D16 | UI 흐름 테스트 방식 → **DEC-108**(a) | (a) dev 전용 가짜 제공자(env `social.mock=true`)로 SDK 없이 목업 서버와 흐름 확인 (b) 소셜은 단위 테스트만 | (a) — 앱 코드에 테스트 태그를 넣지 않는 원칙(DEC-103)은 유지, env 값으로만 켬 | Phase 18 전 |
| D17 | 서버 계약(가정) → **DEC-109** | `POST /api/auth/social {provider, token, authorizationCode?, nonce?}` → `{accessToken, refreshToken, user, isNewUser}` / Android Apple 로그인(웹) 포함 여부 | 가정 계약 + 목업 서버, Android Apple 로그인은 제외 | Phase 18 전 |
| D18 | 공용 글꼴 (DEC-112 검토 항목) → **DEC-117**(Pretendard) | (a) 시스템 글꼴 유지(iOS SF Pro·Android Roboto) (b) 상업 이용 가능한 글꼴(예: Pretendard, OFL) 양 플랫폼 포함 | (b) — 글자 모양 차이가 가장 크게 줄어든다. 앱 크기 증가·라이선스 고지 확인 | Phase 18-0 |
| D19 | 메시지 실시간 전송 방식 → **DEC-126**(Ktor WebSocket + 폴링 대체) | WebSocket 직접(Ktor 클라이언트, 기존 의존성) vs 외부 서비스(Firebase·Supabase Realtime 등) vs 폴링 | 서버 계획에 따라. 템플릿 기본은 Ktor WebSocket + 재연결, 서버 없을 때 폴링 대체 | Phase 19 전 |
| D20 | 메시지 서버 계약(가정) → **DEC-125**(가정 계약 + 목업 서버) | 대화 목록·메시지 페이지·보내기(클라이언트 id 로 중복 방지)·읽음·실시간 이벤트 형식 | 가정 계약 + 목업 서버(DEC-055 방식) | Phase 19 전 |
