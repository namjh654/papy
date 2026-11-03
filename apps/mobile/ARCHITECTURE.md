# ARCHITECTURE

> 기준 시점: 2026-09-29 · 브랜치 `ocra` (`e96934e`)
> 이 문서는 **현재 구조 분석**과 **목표 KMP Template 구조**를 정의한다. 진행 상황과 Phase별 완료 조건은 [PROGRESS.md](PROGRESS.md)를 따른다.
>
> 구버전 `docs/architecture.md`·`structure.txt` 는 Phase 1 에서 정리했다. 구조의 기준 문서는 이 파일이다.

---

## 1. 현재 Architecture

### 1.1 한눈에 보기

Phase 2(2026-09-29)에서 모듈을 분리했다. 이름·패키지·파일 배치 규칙은 [docs/conventions.md](docs/conventions.md).

```text
build-logic/   papy.kmp.library · papy.kmp.feature (convention plugin)

:feature:auth  login · signup · splash  → Api · Request/Response · Repository · UiState · Event · ViewModel, authModule
:feature:home  HomeUiState · HomeViewModel (홈 탭), homeModule
:feature:example  목록 패턴 기준 예시(Paginator → ListState, newFeature 생성물), exampleModule
:feature:settings 설정 탭: 테마·언어·환경·약관·로그아웃, settingsModule
      │ (feature 끼리 의존 없음)
:core:data     network/  BaseApi · HttpTransport · KtorClient(internal) · ApiConfig · ApiConfig(AppConfig 기본값)
               storage/  LocalStorage 계약 + Login/Token/User/Setting 래퍼, Android DataStore · iOS NSUserDefaults 구현
               database/ 로컬 DB(SQLDelight) 드라이버·LocalCache 정리 — 테이블은 기능 모듈 .sq (docs/local-db.md, DEC-130)
               serialization/ AppJson(단일 Json 설정)
               session/  SessionRepository · AuthStore(AuthStatus) · UserStore · UserModel · TokenRefresher(401 재발급, DEC-045)
               settings/ AppSettingsRepository(ThemeMode·AppLanguage)   connectivity/ NetworkMonitor(Android·iOS 직접 구현)
               push/     PushManager · PushRepository · PushProvider(NoOp 기본)
               di/       dataModule() + platformDataModule(expect/actual)
      │
:core:common   error/ AppError · ErrorCode · UiError · toAppError
               viewmodel/ BaseViewModel(비제네릭, iOS dispose) · StatefulViewModel<S: UiState, E: UiEvent> · UiState · UiEvent
               paging/ Page · ListState · Paginator (라이브러리 없는 페이징)   navigation/ DeepLink

:shared        initKoin(apiConfig) · initAndroidKoin · IOSAppKoin · IOSAppGraph
               iOS framework "Core" (dynamic, SKIE) = 위 4개 모듈 export
      ├──────────────────────────────────────────┐
:androidApp (Compose)                        iosApp (SwiftUI, Xcode)
Navigation Compose type-safe route           NavigationStack + AppPhase
하단 탭 MainTab + PapyNavigationBar          TabView(Tab) + MainTab, 탭별 스택
koinViewModel() · EventEffect                SharedViewModel/ViewModelOwner (SKIE)
designsystem/ (PapyTheme, Papy*)              designsystem/ (Papy*)
```

현재 구조는 이미 **"KMP = Business Layer, UI·Navigation = Native"** 원칙을 따르고 있다. 목표 방향과 가장 큰 차이는 **모듈 분리, Design Token, Push, 보안 저장소, 토큰 갱신/401 처리, 테스트·CI**다.

### 1.2 Gradle / Kotlin / KMP 설정

| 항목 | 값 | 비고 |
|---|---|---|
| Gradle | 9.7.1 | configuration-cache, build cache, parallel 활성 |
| Daemon JVM | Amazon Corretto 21 (`gradle-daemon-jvm.properties`) | |
| AGP | 9.3.1 | `com.android.kotlin.multiplatform.library`(신규 KMP Android 플러그인) 사용, androidApp은 AGP 내장 Kotlin |
| Kotlin | 2.4.20 | serialization / compose compiler 플러그인 동일 버전 |
| JVM target | 11 | core, androidApp 모두 |
| KMP targets | `androidLibrary`, `iosArm64`, `iosSimulatorArm64` | `iosX64` 미지원(Intel Mac 시뮬레이터 제외, 주석 정리 — Phase 1) |
| iOS framework | `Core`, dynamic(`isStatic = false`), `:shared` 가 생성·export | `:shared:embedAndSignAppleFrameworkForXcode` 빌드 스크립트 연동 (DEC-034) |
| SKIE | 0.10.15 | FlowInterop, SwiftUI observing preview 활성, DefaultArgumentInterop 비활성 |
| Android SDK | compile 37, target 36, min 24 | |
| iOS | Deployment target 18.2, Swift 5.0 | Debug / Staging / Release 구성 |
| 환경 | `env/common·dev·staging·prod.properties` (+ `.local` 비밀) | build-logic 이 KMP `AppConfig`·Android flavor·iOS `Env.generated.xcconfig` 생성 (Phase 8, DEC-058, docs/environments.md) |
| Version catalog | `gradle/libs.versions.toml` | Compose BOM 포함 전부 catalog (Phase 1) |
| Convention plugin | `build-logic` — `papy.kmp.library`, `papy.kmp.feature` | namespace 는 Gradle 경로에서 자동 (Phase 2, DEC-039) |

### 1.3 라이브러리와 버전

| 영역 | 라이브러리 | 버전 | 사용처 |
|---|---|---|---|
| Coroutines | kotlinx-coroutines-core / -test | 1.11.0 | KMP |
| Serialization | kotlinx-serialization-json | 1.10.0 | KMP, androidApp(route) |
| Network | Ktor client core / logging / okhttp / darwin | 3.5.0 | core:data |
| Storage | androidx.datastore(core-okio, preferences-core), Okio | 1.2.1 / 3.16.0 | core:data androidMain |
| ViewModel | org.jetbrains.androidx.lifecycle:lifecycle-viewmodel | 2.10.0 | core:common (api) — DEC-043 |
| DI | Koin core / android / androidx-compose | 4.2.2 | KMP, androidApp |
| Android UI | activity-compose 1.13.0, lifecycle-runtime/viewmodel-compose 2.10.0, navigation-compose 2.9.4, Compose BOM 2026.01.00, Material3, Coil 3.3.0 | | androidApp |
| Push | Firebase BOM 34.19.0(messaging), google-services 4.5.0 / firebase-ios-sdk 12.19.2(SPM) | | 설정 파일 있을 때만 (DEC-048) |
| iOS interop | SKIE | 0.10.14 | :shared → Swift |
| Test | kotlin-test, coroutines-test, Turbine 1.2.1 | | commonTest (DEC-065) |
| Coverage | Kover 0.9.11 | | 루트 `koverHtmlReport` (DEC-065) |

### 1.4 영역별 현황

| 영역 | 현재 상태 | 평가 |
|---|---|---|
| **UI 구조** | Android `screen/*` Compose, iOS `view/*` SwiftUI. 화면은 ViewModel의 `uiState`를 구독하고 입력/액션 메서드를 직접 호출 | 원칙에 맞음. 문자열·간격·색이 하드코딩 |
| **Navigation** | Android: `AndroidRoute`(@Serializable) + `NavHost` + `AndroidNavigator` 확장 함수. iOS: `AppPhase`(splash/auth/main) 루트 교체 + auth `NavigationStack(path:)`. ViewModel은 Event만 발행 | 원칙에 맞음. 인증 상태 변화(401 등)에 따른 루트 전환 없음, 딥링크 없음 |
| **Network** | `BaseApi`(URL 조합, Bearer 자동 첨부, JSON 인코딩/디코딩) → `HttpTransport`(String 기반) → `KtorClient` → 엔진 expect/actual. 모든 예외는 `AppError`로 정규화 | 잘 설계됨(테스트 가능한 경계). timeout·logging·retry·refresh·query 인코딩 없음 |
| **Local Storage** | `LocalStorage`(String KV, suspend) + 타입 확장 + 도메인 래퍼 4종. Android DataStore, iOS NSUserDefaults 전용 suite | 잘 설계됨. 토큰이 평문 저장, 변경 관찰(Flow) 없음 |
| **Authentication** | `LoginRepository` → `SessionRepository.start/restore/logout` → Storage + `AuthStore`/`UserStore`. 아이디 저장·자동 로그인. Splash에서 세션 복원 | 핵심 흐름 완성, 테스트 3건. 토큰 만료/갱신, 401 시 세션 종료 없음 |
| **Push Notification** | 없음 (`SettingStorage.PUSH` 플래그만 존재) | 신규 |
| **Design System** | Android `PapyTheme` = Material3 기본값(색/타입/모양 미정의), iOS 테마 없음. `AppCard` 양쪽에 있으나 수치 불일치(Android 기본 Card vs iOS radius 24) | 사실상 없음. Token 체계 신규 |
| **DI** | Koin. `initKoin(platformModule, apiConfig)` 공통 진입점, Android `koinViewModel()`, iOS `IOSAppGraph` + `SharedViewModel`(@StateObject, deinit에서 `dispose()`) | 잘 정리됨. ViewModel을 `factory`로 등록 |
| **테스트** | `core/commonTest` 1개 파일 3케이스(통과). androidApp·iOS 테스트 타깃 없음 | 부족 |
| **Build / CI** | CI 설정 없음. remote 2개(`origin` 사내 GitLab, `github`) | 신규 |
| **빈 디렉터리** | ~~`core/ui`, `core/util`~~ Phase 1 에서 삭제. 공통 UI 코드는 두지 않고(UI=Native), 유틸은 Phase 2 `core:common` 에 둔다 | 해소 |

### 1.5 현재 구조에서 유지할 것 (재작성 금지 대상)

- `BaseApi` / `HttpTransport` / `KtorClient` / `AppError` / `toAppError()` 계층
- `LocalStorage` 계약 + 타입 확장 + 도메인 Storage 래퍼 패턴
- `SessionRepository` ↔ `AuthStore`/`UserStore` 단일 책임 구조와 테스트
- ViewModel = `StateFlow<UiState>` + `Channel` 기반 `Flow<Event>` 패턴
- Android `EventEffect`, `AndroidNavigator`(back stack 규칙 집중)
- iOS `SharedViewModel` / `ViewModelOwner` + SKIE `onEnum(of:)` exhaustive 분기
- `env/` 단일 소스 + build-logic 코드 생성(AppConfig·Android flavor·iOS xcconfig) 방식 (Phase 8)
- `initKoin(platformModule, …)` + 플랫폼 모듈 주입 방식

이들은 **이동(모듈/패키지 재배치)** 은 하되 로직은 유지한다.

---

## 2. 목표 Architecture

### 2.1 원칙

1. **KMP는 비즈니스 로직과 공통 계약을 공유한다.** Repository, UseCase(필요 시), ViewModel(Presentation State), Network, Storage, Auth, Push 처리 로직, Design Token의 *의미 체계*.
2. **UI는 Native다.** Android = Jetpack Compose, iOS = SwiftUI. `commonMain`에 Composable/View 구현을 두지 않는다 (Compose Multiplatform UI 미사용).
3. **공통 Component는 계약·상태·Token만 공유한다.** 예: `ButtonVariant`, `TextFieldState`, `ToastMessage` 같은 모델은 공유 가능, 렌더링은 플랫폼.
4. **플랫폼 고유 기능은 Native API로 구현하고, KMP에는 인터페이스만 둔다.** (보안 저장소, 푸시 등록, 권한, 파일, 생체인증 등)
5. **Navigation은 플랫폼이 소유한다.** KMP는 "무엇이 일어났는가(Event)"와 "어디로 가야 하는가의 의미(DeepLink/Destination 계약)"만 제공한다.
6. **의존성은 한 방향으로만 흐른다.** app → feature → core, 플랫폼 구현은 계약에 의존.

### 2.2 목표 Module 구조

Gradle 모듈 수는 템플릿 사용성과 빌드 비용의 균형을 위해 **"작게 시작해서 경계가 굳으면 쪼갠다"** 를 따른다.

> **Phase 2 결정(DEC-033/034):** 아래 초기 안보다 적게 나눴다 — `core:common` · `core:data`(network·storage·auth 세션·serialization 을 패키지로) · `feature:auth`(splash 포함) · `feature:home` · `shared`(framework 이름은 `Core` 유지). `core:testing`·레이어별 모듈은 두지 않는다. 현재 구성은 1.1 이 기준이고, 아래는 초기 목표안(참고용)이다.

```text
root
├── build-logic/                       # Convention plugins (kmp-library, android-app, …)
├── shared/                            # iOS umbrella framework "Shared" (export core/feature), Koin 진입점
├── core/
│   ├── common/                        # AppError, Result/LoadState, DispatcherProvider, Logger, BaseViewModel
│   ├── network/                       # ApiConfig, BaseApi, HttpTransport, KtorClient, 인증 인터셉트, env 생성
│   ├── storage/                       # LocalStorage, SecureStorage 계약 + Android/iOS 구현
│   ├── auth/                          # SessionRepository, AuthStore, UserStore, Token/User Storage, 토큰 갱신
│   ├── push/                          # PushTokenRepository, PushMessage 파서, 등록 API, 권한 상태 계약
│   ├── designsystem-tokens/           # 토큰 원본(JSON) + Kotlin/Swift 생성 태스크 (UI 코드 없음)
│   └── testing/                       # Fake(LocalStorage, HttpTransport, Clock), 테스트 유틸
├── feature/
│   ├── auth/                          # login · signup : Api, Repository, UiState, Event, ViewModel
│   ├── splash/
│   └── home/
├── androidApp/
│   ├── app/ navigation/               # Application, MainActivity, NavHost, Route
│   ├── designsystem/                  # PapyTheme(Compose) ← 생성된 토큰, 공통 Composable
│   ├── feature/<name>/                # Screen (Compose)
│   └── push/                          # FirebaseMessagingService 등 Native 구현
└── iosApp/
    ├── App/ Navigation/               # App, AppNavigation, Route
    ├── DesignSystem/                  # PapyTheme(SwiftUI) ← 생성된 토큰, 공통 View
    ├── Features/<Name>/               # Screen (SwiftUI)
    ├── Bridge/                        # SharedViewModel, ViewModelOwner
    └── Push/                          # UNUserNotificationCenter / APNs Native 구현
```

androidApp/iosApp 내부는 **패키지/폴더 분리**로 시작하고, Android 화면이 많아질 때만 `:android:feature:*` 모듈화를 검토한다.

### 2.3 의존성 방향

```text
           androidApp                      iosApp
               │                              │ (import Shared)
               ▼                              ▼
          feature:*  ◀──────────────  shared (umbrella, export)
               │                              │
               ▼                              ▼
   core:auth  core:push  core:designsystem-tokens
       │         │
       ▼         ▼
   core:network  core:storage
          \       /
           ▼     ▼
          core:common

core:testing ── testImplementation only
```

규칙:
- `core:*`는 `feature:*`를 모른다. `feature:*`끼리는 서로 의존하지 않는다(공유가 필요하면 core로 내린다).
- `commonMain`은 Android/iOS UI 프레임워크(Compose UI, SwiftUI, UIKit)에 의존하지 않는다. 허용 예외: `androidx.lifecycle:lifecycle-viewmodel`(KMP).
- 플랫폼 구현(`androidMain`/`iosMain`/Swift)은 `commonMain` 계약에만 의존하고 Koin 플랫폼 모듈로 주입한다.
- Route/NavController/NavigationStack 타입은 app 레이어에만 존재한다.

### 2.4 Native UI / Shared Logic 경계

| 구분 | KMP (commonMain) | Android (Compose) | iOS (SwiftUI) |
|---|---|---|---|
| 화면 상태 | `UiState` data class, `Event` sealed interface | `collectAsStateWithLifecycle` | `SharedViewModel.state` (SKIE) |
| 사용자 입력 | ViewModel public 메서드 (`usernameChanged`, `login`) | 직접 호출 | 직접 호출 |
| 화면 이동 | Event 발행만 | `EventEffect` → `NavController` | `for await` + `onEnum(of:)` → path/phase |
| 인증 게이트 | `AuthStore.status: StateFlow<AuthStatus>` | 루트에서 관찰 → 그래프 교체 | 루트에서 관찰 → `AppPhase` 교체 |
| 문자열 | 에러 **코드/타입** (`AppError`) | `strings.xml` 로 매핑 | `Localizable.xcstrings` 로 매핑 |
| 디자인 | Token **의미·값**(생성 원본) | `PapyTheme`/CompositionLocal | `PapyTheme`/Environment |
| 권한·푸시·보안 저장소 | 인터페이스 + 상태 모델 | Native 구현 | Native 구현 |
| ViewModel 수명 | `viewModelScope` | `koinViewModel()` (NavBackStackEntry) | `@StateObject` + `dispose()` |

**사용자 노출 문자열 정책(DEC-018/019):** KMP 는 `ErrorCode`/`UiError` 만 주고, 플랫폼이 `strings.xml`/`Localizable.xcstrings` 로 매핑한다.

---

## 3. 영역별 전략

### 3.1 Design System 전략

- **단일 원본:** `core/designsystem-tokens/tokens/*.json` (색, 타이포, spacing, radius, elevation, motion). 이름은 **semantic** 계층으로 통일한다.
  - primitive: `blue.500`, `gray.100` …
  - semantic: `color.bg.surface`, `color.text.primary`, `color.border.default`, `color.status.error`, `space.md`, `radius.card`, `type.title.lg`
  - light/dark 두 모드 값을 함께 정의
- **생성:** 기존 `generateEnvironment`와 같은 방식의 Gradle 태스크로
  - Android: `PapyTokens.kt` (Compose `Color`, `Dp`, `TextStyle`) → `PapyTheme`의 `ColorScheme`/CompositionLocal 에 매핑
  - iOS: `PapyTokens.swift` (`Color`, `CGFloat`, `Font`) → `EnvironmentValues`/`ShapeStyle` 확장으로 노출
  - 생성 결과는 커밋할지 빌드 시 생성할지 결정 필요(iOS는 Xcode 빌드 전 생성 필요)
- **공통 Component:** 계약만 KMP 또는 문서로 공유(예: `ButtonStyle = Primary | Secondary | Text`, `size`, `isLoading`). 구현은 `PapyButton`(Compose) / `PapyButton`(SwiftUI)로 동일 이름·동일 파라미터 의미를 유지한다. 1차 대상: Button, TextField, SecureField, Card, Checkbox/Toggle, LoadingIndicator, ErrorText, TopBar.
- Material3/SwiftUI 기본 컴포넌트를 감싸되 플랫폼 룩앤필(예: iOS `Toggle`, Android `Checkbox`)은 유지한다. **의미(token)가 같고 모양은 플랫폼답게**가 기준이다.

### 3.2 Navigation 전략

- **플랫폼 소유 유지.** Android: Navigation Compose type-safe route(현행). iOS: `NavigationStack(path:)` + 루트 `AppPhase`(현행).
- **인증 게이트를 상태 기반으로:** 루트가 `AuthStore.status`를 관찰해 `Unauthenticated`가 되면 auth 그래프로 교체. 화면 Event(`LoggedOut`)에만 의존하지 않아 401/토큰 만료/다른 기기 로그아웃도 처리된다.
- **그래프 분리:** Android는 `navigation<AuthGraph>` / `navigation<MainGraph>` 중첩 그래프, iOS는 phase별 `NavigationStack`. 탭이 생기면 Android `NavigationBar`, iOS `TabView` 각 탭마다 독립 스택.
- **딥링크/푸시 라우팅 계약만 공유:** `commonMain`에 `DeepLink` sealed 모델과 `DeepLinkParser(url|pushPayload) → DeepLink?`를 두고, 각 플랫폼이 `DeepLink → Route`로 변환한다. Route 타입 자체는 공유하지 않는다.
- 공통 Navigator(과거 `AppNavigator`) 재도입은 하지 않는다.

### 3.3 Network 전략

현행 `BaseApi → HttpTransport → KtorClient` 유지, 다음을 보강한다.

| 항목 | 방향 |
|---|---|
| Timeout | `HttpTimeout` 플러그인 (connect/request/socket), `ApiConfig`에서 주입 |
| Logging | debug 빌드에서만 Ktor `Logging`, Authorization 헤더 마스킹 |
| Query | Ktor `URLBuilder`/`parameters`로 인코딩 (현재 수동 `joinToString`, 인코딩 없음) |
| JSON | `Json` 인스턴스를 DI 싱글톤 하나로 (현재 `BaseApi`, `installJsonSerialization`, `UserStorage` 3곳) |
| Content-Type | `ApiHeaders.json()`과 `KtorClient.contentType()` 중복 설정 정리 |
| 인증 | `AccessTokenProvider` 유지 + 401 시 `TokenRefresher`(refresh 지원 시) → 실패하면 `SessionRepository.expire()` → `AuthStore = Unauthenticated` |
| 에러 | `AppError.Http`에 서버 에러 바디 파싱(코드/메시지) 확장 포인트 |
| 환경 | `ApiConfig` 단일 경로화: Android도 `GeneratedEnvironment` 또는 둘 다 BuildConfig/Info.plist로 통일 |
| 테스트 | `FakeHttpTransport` + Ktor `MockEngine` |

### 3.4 Storage 전략

- `LocalStorage`(비민감 KV) 유지: Android DataStore, iOS NSUserDefaults suite.
- **`SecureStorage` 계약 추가:** 토큰 전용. Android = Keystore 기반 암호화(+DataStore), iOS = Keychain. `TokenStorage`만 `SecureStorage`로 이동하고 나머지 래퍼는 그대로.
- 관찰 필요 시 `LocalStorage.observeString(key): Flow<String?>` 추가(설정 화면 등). 기존 API는 변경하지 않는다.
- 구조화 데이터/오프라인 캐시가 필요해지면 SQLDelight 또는 Room KMP를 별도 `core:database`로 도입(현재 범위 아님).
- 키 마이그레이션: 저장소 교체 시 최초 실행에서 기존 평문 토큰을 이전 후 삭제하는 1회성 migration.

### 3.5 Push 전략

```text
KMP core:push
  PushTokenRepository   토큰 수신 → 서버 등록/해제 (로그인/로그아웃 연동)
  PushMessage           payload(Map<String,String>) → 도메인 모델 파싱
  PushRouter            PushMessage → DeepLink
  PushPermissionState   NotDetermined / Granted / Denied (상태 계약)
  PushSettings          SettingStorage.PUSH 이동

Android (Native)                         iOS (Native)
  FirebaseMessagingService                 UNUserNotificationCenterDelegate
  onNewToken → PushTokenRepository         didRegisterForRemoteNotifications → PushTokenRepository
  POST_NOTIFICATIONS 권한(API 33+)          requestAuthorization
  NotificationChannel · 알림 표시            foreground 표시 옵션
  클릭 Intent → DeepLink → NavController    알림 응답 → DeepLink → AppNavigation
```

- 제공자(FCM 단일 vs Android FCM + iOS APNs 직접)와 서버 등록 API 계약은 결정 필요.
- 알림 UI·권한 요청 UX·채널 설정은 반드시 Native.

### 3.6 Auth 전략

- 현행 `LoginRepository` / `SignupRepository` / `SessionRepository` 유지.
- 추가: 토큰 만료 처리(401 → expire), refresh token 흐름(서버 지원 시), 로그아웃 시 서버 로그아웃/푸시 토큰 해제 훅, `AuthStore` 기반 루트 전환.
- `useMock` 플래그는 생성자 boolean 대신 `FakeLoginApi` 등 DI 교체(dev 환경 또는 테스트)로 대체.

### 3.7 Testing 전략

| 레벨 | 위치 | 도구 | 대상 |
|---|---|---|---|
| Unit (공통) | `commonTest` | kotlin-test, coroutines-test, (Turbine 도입 검토) | Repository, Session, Storage 래퍼, AppError 매핑, ViewModel 상태/이벤트 |
| Network | `commonTest` | Ktor `MockEngine`, `FakeHttpTransport` | BaseApi URL/헤더/에러/401 흐름 |
| 플랫폼 구현 | `androidHostTest`, `iosSimulatorArm64Test` | | DataStore/NSUserDefaults/Keychain 구현 |
| Android UI | `androidApp/src/androidTest` | Compose UI Test | 주요 화면 렌더링/상호작용 (선택) |
| iOS UI | `iosAppTests` / `iosAppUITests` | XCTest | ViewModel 브리지, 주요 흐름 (선택) |
| 아키텍처 | CI | Gradle dependency 검사(또는 Konsist) | commonMain에 UI 의존성 금지, feature 간 의존 금지 |

공통 Fake는 `core:testing`에 모은다(현재 `FakeLocalStorage`가 테스트 파일에 private으로 있음).

### 3.8 Build / CI 전략

- `build-logic` convention plugin으로 KMP 라이브러리 설정(targets, jvmTarget, SKIE 여부) 공통화.
- Compose BOM·Compose 아티팩트를 version catalog로 이동, 미사용 항목 정리.
- CI 파이프라인(플랫폼 결정 필요: 사내 GitLab `origin` vs GitHub):
  1. `./gradlew :core:allTests` (host + iosSimulatorArm64, macOS runner)
  2. `./gradlew :androidApp:assembleDevDebug` + lint
  3. `xcodebuild -scheme iosApp -configuration Debug -sdk iphonesimulator build`
  4. (옵션) ktlint/detekt, SwiftLint
- 릴리스 서명·환경별 빌드·버전 올리기는 [docs/release.md](docs/release.md) (Fastlane 등 배포 자동화는 없음).

---

## 4. 현재 → 목표 Gap 요약 (2026-09-30, Phase 11 기준)

Phase 0 에서 정한 목표(§2)는 Phase 1~11 에서 모두 반영했다. 남은 것은 외부 조건이 필요한 항목이다.

| 영역 | 상태 | 남은 일 |
|---|---|---|
| 모듈 | core:common·core:data·feature:*·:shared, build-logic convention | — |
| Network·Auth | timeout·로그·인코딩·401 재발급(TokenRefresher)·SecureStorage·AuthStore 루트 전환 | 서버 계약 확정(DEC-055 가정) |
| Design System | 토큰 원본·생성, 양 플랫폼 컴포넌트(design/README.md 대응표) | design/reference 캡처가 생기면 재조정 |
| 앱 패턴 | 하단 탭·목록 페이징·설정·웹뷰·오프라인·업데이트 안내 | — |
| Push·운영 | FCM·Crashlytics·Analytics(설정 파일 있을 때만), Kermit, 인앱 리뷰·업데이트 | 실제 Firebase·스토어 배포로 수신 확인 |
| 테스트·CI | 모든 ViewModel·Repository 테스트(Turbine), Kover 리포트, CI 는 예시만(docs/ci.md — 켜져 있지 않음) | UI 테스트는 없음(수동·임시 XCUITest) |
| 템플릿화 | renameApp·newFeature(--tab)·templateCheck(아키텍처·배치·문자열·토큰·env) | — |
| 버전 | AGP 9.3.1·Gradle 9.7.1·Kotlin 2.4.20·SKIE 0.10.15·lifecycle 2.11.0·Compose BOM 2026.09.00 (Phase 12, v0.2) | AGP 9.4·Gradle 9.8 은 Kotlin 호환표에 들어오면, Firebase FID 등록은 서버 계약 확정 후 (known-limitations §4) |

---

## 5. Phase 기록

세부 완료 조건과 상태는 [PROGRESS.md](PROGRESS.md)에서 관리한다.

| Phase | 이름 | 핵심 내용 |
|---|---|---|
| 0 | 분석·문서화 | 현재 문서 |
| 1 | 기반 정리 | 문서 동기화, catalog, build-logic, CI |
| 2 | 모듈 분리 | core:common·core:data·feature:*·:shared umbrella |
| 3 | Network·Auth·Storage | timeout·로그·401, SecureStorage, AuthStore 루트 전환 |
| 4 | Design System | 토큰 원본·생성, 양 플랫폼 컴포넌트, 다국어 |
| 5 | Push | FCM(설정 있을 때만), DeepLink |
| 6 | 테스트·템플릿화 | ViewModel 테스트, newFeature, renameApp |
| 7 | Python 제거 | 도구를 build-logic Kotlin 으로 |
| 8 | 환경 분리 | env/ dev·staging·prod |
| 9 | 앱 기본 패턴 | 목록 페이징·하단 탭·설정·네트워크 상태·웹뷰·토큰 재발급·Turbine/Kover |
| 10 | 운영·릴리스 | 버전 한 곳·업데이트 안내·Kermit·Crashlytics/Analytics·인앱 리뷰/업데이트·release.md |
| 11 | 리팩토링·컴포넌트화 | 반복 UI 통합, 배치 규칙 검사, 문서 정리, 템플릿 전 과정 검증 |

각 Phase는 **빌드 통과(Android assemble, iOS Kotlin compile/xcodebuild) + 기존 테스트 통과**를 공통 종료 조건으로 하며, 다음 Phase는 명시적 승인 후 시작한다.
