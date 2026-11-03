# Phase 2 — 모듈 분리: 최종 구성과 공개 계약 전/후

> 결정: DEC-033(D1) · DEC-034(D2) · DEC-035(공개 계약) · DEC-036(UseCase) · DEC-037(Koin) · DEC-038(버전) · DEC-039(build-logic)

## 모듈 구성

```text
build-logic/            convention plugin: elsa.kmp.library, elsa.kmp.feature
core/common  :core:common   cloud.elsa.app.core.common
  error/       AppError, toAppError(), ErrorCode, UiError
  viewmodel/   BaseViewModel(비제네릭), StatefulViewModel<S: UiState, E: UiEvent>, UiState, UiEvent
               iosMain: BaseViewModel.dispose()
core/data    :core:data     cloud.elsa.app.core.data
  network/       ApiConfig, BuildEnvironment, BaseApi, HttpTransport, KtorClient, ApiHeaders, AccessTokenProvider
  serialization/ AppJson (Json 설정 단일 정의)
  storage/       LocalStorage(+Android DataStore / iOS NSUserDefaults 구현), Login/Token/User/SettingStorage
  session/       SessionRepository, AuthSession, AuthStore/AuthStatus, UserStore, UserModel
  di/            dataModule(apiConfig)  (플랫폼 저장소는 expect/actual platformStorageModule)
feature/auth :feature:auth  cloud.elsa.app.feature.auth
  login/ signup/ splash/  (Api·Request·Response·Repository·UiState·Event·ViewModel)
  di/            authModule
feature/home :feature:home  cloud.elsa.app.feature.home
  HomeUiState, HomeEvent, HomeViewModel, di/homeModule
shared       :shared        cloud.elsa.app.di   ← iOS framework "Core" (위 4개 모듈 export)
  initKoin(apiConfig), appModules(apiConfig)
  androidMain: initAndroidKoin(context, apiConfig, appModules)
  iosMain:     IOSAppKoin.initialize(appEnv), IOSAppGraph.*ViewModel()
```

의존 방향: `feature:* → core:data → core:common`, `shared → 전부(api/export)`, `androidApp → shared`. feature 끼리 의존 금지.

## 공개 계약 전/후

| 항목 | 전 | 후 | Swift(SKIE) |
|---|---|---|---|
| 에러 | `cloud.elsa.app.network.{AppError, ErrorCode, UiError, toAppError}` | `cloud.elsa.app.core.common.error.*` | 이름 동일 (`ErrorCode` enum, `UiError`) |
| ViewModel 베이스 | `cloud.elsa.app.base.BaseViewModel` | `cloud.elsa.app.core.common.viewmodel.BaseViewModel` (비제네릭 유지) + `StatefulViewModel<S, E>` | `ViewModelOwner<VM: BaseViewModel>` 그대로 동작. `dispose()` 동일 |
| UiState/UiEvent | 각 data class / sealed interface | `UiState` / `UiEvent` 구현 추가 | 프로토콜 채택만 추가, `onEnum(of:)` 동일 |
| ViewModel 공개 API | `uiState: StateFlow<XUiState>`, `events: Flow<XEvent>`, 입력/액션 함수 | 동일 (StatefulViewModel 이 보관, 각 VM 이 구체 타입으로 노출) | `SkieSwiftStateFlow<LoginUiState>` 등 동일 |
| Splash | `cloud.elsa.app.feature.splash.*` | `cloud.elsa.app.feature.auth.splash.*`, `SplashUiState`(data object) 추가 | `SplashEvent` 동일 |
| 네트워크/설정 | `cloud.elsa.app.network.{ApiConfig, BuildEnvironment, BaseApi, …}` | `cloud.elsa.app.core.data.network.*` | Swift 에서 직접 사용 안 함 |
| 저장소·세션 | `cloud.elsa.app.{storage, store, repository.session, domain.model.user}` | `cloud.elsa.app.core.data.{storage, session}` | `UserModel`, `AuthStatus` 이름 동일 |
| Koin 진입점 | `initKoin(platformModule, apiConfig, appDeclaration)` | `initKoin(apiConfig, appDeclaration)` | `IOSAppKoin.shared.initialize(appEnv:)` 동일 |
| Android 진입점 | `initAndroidKoin(context, apiConfig, appModules)` | 동일 | — |
| Gradle | `:core` (framework Core) | `:shared` (framework Core) | Xcode 빌드 단계: `:shared:embedAndSignAppleFrameworkForXcode` |

저장 데이터: DataStore 파일(`elsa.storage.preferences_pb`)·NSUserDefaults suite(`cloud.elsa.app.storage`)·키 이름 변경 없음 → 기존 데이터 유지.
