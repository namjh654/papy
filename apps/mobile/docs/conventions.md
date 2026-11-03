# 규칙: 이름 · 패키지 · 파일 배치

이 템플릿에서 새 코드를 어디에 어떤 이름으로 두는지 정리한 한 페이지다. 구조의 이유는 [ARCHITECTURE.md](../ARCHITECTURE.md), 결정 기록은 [.orca/DECISIONS.md](../.orca/DECISIONS.md).

## 1. 모듈

| 모듈 | 경로 | 패키지 | 들어가는 것 |
|---|---|---|---|
| `:core:common` | `core/common` | `cloud.elsa.app.core.common` | `error/`(AppError·ErrorCode·UiError), `viewmodel/`(BaseViewModel·StatefulViewModel·UiState·UiEvent) |
| `:core:data` | `core/data` | `cloud.elsa.app.core.data` | `network/` `storage/` `serialization/` `session/` `di/` — 여러 feature 가 공유하는 데이터 |
| `:feature:<name>` | `feature/<name>` | `cloud.elsa.app.feature.<name>` | 화면별 `UiState`·`Event`·`ViewModel`·`Repository`·`Api`, `di/<Name>Module.kt` |
| `:shared` | `shared` | `cloud.elsa.app.di` | Koin 진입점(`initKoin`), iOS framework `Core`(모든 모듈 export), `IOSAppGraph` |
| `androidApp` | `androidApp` | `cloud.elsa.app` | Compose 화면·Navigation·디자인 시스템 |
| `iosApp` | `iosApp` | — | SwiftUI 화면·Navigation·디자인 시스템 |

의존 방향: `feature:* → core:data → core:common`, `shared → 전부`, `androidApp → shared`.
- feature 끼리 의존하지 않는다. 둘 이상이 쓰면 `core:data`(데이터) 또는 `core:common`(계약)으로 올린다.
- `commonMain` 은 UI 프레임워크를 모른다(허용: `androidx.lifecycle` ViewModel).
- 모듈 `build.gradle.kts` 는 convention plugin 한 줄로 시작한다: KMP 라이브러리는 `id("elsa.kmp.library")`, feature 는 `id("elsa.kmp.feature")`(core:common·core:data·Koin 포함). namespace 는 경로에서 자동(`:feature:home` → `cloud.elsa.app.feature.home`).

## 2. feature 모듈 안 (KMP)

```text
feature/<name>/src/commonMain/kotlin/cloud/elsa/app/feature/<name>/
├── <screen>/                 화면 하나당 폴더 하나 (예: login/, signup/)
│   ├── <Screen>UiState.kt    data class <Screen>UiState(...) : UiState   (상태 없으면 data object)
│   ├── <Screen>Event.kt      sealed interface <Screen>Event : UiEvent
│   ├── <Screen>ViewModel.kt  class <Screen>ViewModel(...) : StatefulViewModel<<Screen>UiState, <Screen>Event>(...)
│   ├── <Screen>Repository.kt 저장·조합·검증 규칙
│   ├── <Screen>Api.kt        interface <Screen>Api + internal class Remote<Screen>Api(baseApi) — BaseApi 호출만(endpoint 한 곳)
│   └── <Screen>Request.kt / <Screen>Response.kt   @Serializable 전송 모델
└── di/<Name>Module.kt        val <name>Module = module { single { Api/Repository }; factory { ViewModel } }
```

ViewModel 규칙
- 공개 API 는 `val uiState: StateFlow<XUiState> = state`, `val events: Flow<XEvent> = eventFlow`, 입력 `xxxChanged(value)`, 액션 동사(`login()`).
  공개 프로퍼티는 **구체 타입**으로 한 줄씩 선언한다(Swift/SKIE 가 타입을 알도록).
- 비동기는 `launch { }` 안에서. 예외는 던지고 `onError` 에서 `toAppError().toUiError()` 로 UiState 에 담는다.
- 화면 이동은 하지 않는다. `sendEvent(XEvent.Done)` 만 보내고 플랫폼 Navigation 이 결정한다.
- 사용자 문구를 만들지 않는다. `ErrorCode` 만 전달하고 문구는 플랫폼 리소스가 결정한다.

서버 없이 개발·테스트할 때는 플래그(useMock) 대신 Koin 에서 Api 구현을 바꾼다: `single<LoginApi> { FakeLoginApi() }`. 테스트는 feature 의 `commonTest` 에 Fake 를 둔다(core:testing 모듈 없음).

UseCase 는 기본으로 두지 않는다(ViewModel → Repository 직접). 여러 Repository 를 조합하거나 규칙이 있을 때만 `<screen>/<Verb><Noun>UseCase.kt` 로 추가한다.

## 3. 이름

| 대상 | 규칙 | 예 |
|---|---|---|
| Kotlin 파일 | 대표 타입 이름 `PascalCase.kt`, 플랫폼 actual 은 `.android.kt` / `.ios.kt` | `LoginViewModel.kt`, `PlatformHttpClient.ios.kt` |
| Koin 모듈 | `<name>Module` (feature), `dataModule(apiConfig)` | `authModule`, `homeModule` |
| 저장 키 | `<도메인>.<의미>` 소문자 점 표기, `internal object XStorageKey` | `login.auto.login` |
| Android 파일 | `PascalCase.kt`, 화면은 `<Screen>Screen.kt` | `LoginScreen.kt` |
| Android 패키지 | `screen/<screen>/`(화면), `navigation/`(경로·탭·딥링크), `designsystem/`(Elsa*), `component/`(화면 공통 헬퍼), `app/`(Application·Activity·로케일), `push/`, `monitoring/`(Crashlytics·Analytics), `review/`, `update/` | `screen/login/LoginScreen.kt` |
| iOS 파일 | 소문자 점 표기 `<screen>.screen.swift` | `login.screen.swift` |
| iOS 폴더 | `view/<screen>/`(화면), `navigation/`, `designsystem/`, `component/`(SharedViewModel·FlowState 등), `app/`(App·AppDelegate·PlatformSetup), `push/`, `monitoring/`, `review/`, `update/` | `view/login/login.screen.swift` |
| 디자인 컴포넌트 | 양 플랫폼 같은 이름 `Elsa*` (DEC-007) | `ElsaButton`, `ElsaTextField` |
| 문자열 키 | `<화면 또는 영역>_<의미>` snake_case, Android·iOS 같은 키 (DEC-018/021) | `login_title`, `error_network` |

`./gradlew templateCheck`(= orca check·CI)가 배치 규칙을 검사한다 — KMP package 는 디렉터리와 같고, Android 파일은 PascalCase·화면은
`screen/<소문자>/<Name>Screen.kt`, iOS 파일은 소문자 점 표기·`view/` 아래는 `<이름>.screen.swift`, 문자열 키는 snake_case (ArchRules [6]).

## 4. 새 화면·기능을 붙일 때 바꾸는 곳

**새 feature 는 스크립트로 만든다** — 아래 1~6 을 자동으로 하고, 만든 직후 `scripts/orca.sh check` 를 통과한다(Sample Api 로 서버 없이 동작).

```bash
./gradlew -p build-logic :tooling:run --args="newFeature notice --title-ko 공지사항 --title-en Notices"        # 탭 위에 쌓이는 화면
./gradlew -p build-logic :tooling:run --args="newFeature board --title-ko 게시판 --title-en Board --tab"      # 하단 탭으로
./gradlew -p build-logic :tooling:run --args="newFeature memo --title-ko 메모 --title-en Memo --db"            # 로컬 DB(첫 페이지 저장·오프라인, docs/local-db.md)
# --args 끝에 --dry-run 을 붙이면 미리보기. 루트 빌드 밖(build-logic)에서 돌아 settings 를 안전하게 고친다 (DEC-057)
```

생성물은 **목록 패턴**이다: KMP `Paginator`(core:common, 라이브러리 없는 페이징) → `UiState.list: ListState<T>` →
Android/iOS `ElsaPagedList`(당겨서 새로고침·끝에서 다음 페이지·첫 로딩·전체 오류·빈 화면·다음 페이지 실패).
목록이 아닌 화면은 생성 후 UiState 에서 `list` 를 빼고 화면을 바꾼다. 기준 예시는 `:feature:example`(스크립트 생성물 + 예시 탭). 스크립트 없이 손으로 할 때·기존 feature 에 화면을 더할 때의 목록:

1. `feature/<name>/` 모듈(위 2번 구조) + `settings.gradle.kts` 에 `include(":feature:<name>")`
2. `shared/build.gradle.kts` 의 `exported` 에 `":feature:<name>"`, `shared/.../AppKoin.kt` 의 `appModules` 에 `<name>Module`
3. iOS 에서 쓰면 `shared/src/iosMain/.../IOSAppGraph.kt` 에 `fun <screen>ViewModel(): XViewModel = get()`
4. Android: `navigation/AndroidRoute.kt` + `AndroidNavHost.kt` + `screen/<screen>/<Screen>Screen.kt` (`koinViewModel()`, `EventEffect`)
5. iOS: `navigation/app.route.swift` + `app.navigation.swift` + `view/<screen>/<screen>.screen.swift` (`SharedViewModel`, `onEnum(of:)`)
5-1. 딥링크: Android `navigation/DeepLinkRouter.kt`, iOS `navigation/deeplink.router.swift` 에 같은 path 추가 (푸시 `screen`/`link` 로 열림, [push.md](push.md))
6. 문자열: `androidApp/src/main/res/values{,-en}/strings.xml` + `iosApp/iosApp/Localizable.xcstrings` 같은 키 → `./gradlew checkStrings`
7. 설정 항목: 값은 `core:data` `AppSettingsRepository`(+ `SettingStorage` 키), 표시는 `SettingsUiState` 필드, 화면은 `ElsaSettingsRow` 한 줄 (Android `screen/settings/SettingsScreen.kt`, iOS `view/settings/settings.screen.swift`)
8. 웹 페이지(약관·공지): URL 은 env(`url.*`) → `AppConfig`, 화면은 Android `navigateToWeb(url, title)` / iOS `open(.web(url:title:))`
9. 화면은 토스 기반 스타일(큰 제목, 넓은 여백, 채움형 입력칸, 무테 카드, 하단 고정 CTA, 바텀시트), 다크 모드·다국어 처음부터 (DEC-025)
10. **미리보기 먼저 (DEC-122)**: 화면은 ViewModel 을 받는 바깥 함수(`XScreen`)와 상태·콜백만 받는 `XContent` 로 나누고 `XContent` 에 미리보기를 둔다 — Android `@Preview`(라이트·다크 두 개), iOS `#Preview`. 디자인 시스템 컴포넌트도 같다. 예: `TermsContent`, `ElsaSocialLoginSection`. 다듬는 동안은 미리보기로 보고, 에뮬레이터·시뮬레이터는 Phase 끝 `scripts/orca.sh uitest all` 한 번으로 확인한다(DEC-120)

### 상세 화면 (인자가 있는 화면)

기준 코드: `feature/example/detail`(수정 있음), `feature/notice/detail`(읽기 전용).

- 인자는 **id 만** 넘긴다. KMP: `factory { (id: String) -> XDetailViewModel(id, get()) }`.
  Android: `AndroidRoute.XDetail(id)` → `koinViewModel { parametersOf(id) }`. iOS: `MainRoute.xDetail(id:)` → `IOSAppGraph.shared.xDetailViewModel(id:)`(`init(id:)` 에서 `_model = StateObject(wrappedValue: …)`).
- 상태는 `item / isLoading / error`. 화면은 `ElsaContentState` 로 전환한다(양 플랫폼 같은 이름).
- 불러오기 실패는 `onError` → 전체 오류 화면. 수정 실패는 내용을 그대로 두고 이벤트 → 토스트.
- 수정 결과를 목록에 알릴 때는 Repository 의 `changes: SharedFlow<Item>` 를 목록 ViewModel 이 구독해 `paginator.update { … }` 로 그 항목만 바꾼다.
- 딥링크 `<path>/<id>`: Android `DeepLinkRouter.kt`, iOS `deeplink.router.swift` 의 "상세 화면 path" 자리에 한 줄씩.
- Android 의 NavController 확장 함수 이름을 `navigate` 로 짓지 않는다(멤버 `navigate(route)` 가 먼저 선택된다).

### 여러 단계·수정 폼·이전 화면에 결과 알리기

- 짧은 단계(2~3개)는 한 ViewModel 의 상태로 둔다(기준 `feature/auth/reset`). 뒤로가기는 화면이 가로챈다:
  Android `BackHandler(enabled = state.canGoToPreviousStep)`, iOS `.navigationBarBackButtonHidden(…)` + 툴바 버튼.
- 수정 폼(기준 `feature/settings/profile`): `isDirty` 일 때만 저장 버튼을 켜고, 나갈 때 확인 다이얼로그를 띄운다(같은 방법으로 뒤로가기를 가로챈다).
  파일은 화면이 바이트로 넘긴다(Android `contentResolver`, iOS `IOSAppGraph.shared.bytes(data:)`). 업로드는 `baseApi.upload(...)`.
- 이전 화면에 결과를 알릴 때는 같은 feature 의 작은 저장소에 남기고 받는 ViewModel 이 읽는다(기준 `LoginNoticeStore`).
- 되돌릴 수 없는 작업은 확인 체크 + 확인 다이얼로그(`ElsaAlert(isDestructive)`)를 거친다(기준 `feature/settings/withdraw`).
- 인증 상태를 바꾸는 호출(`SessionRepository.logout()`) 뒤에는 코드를 두지 않는다 — 화면이 넘어가며 코루틴이 취소될 수 있다.
- Kotlin 프로퍼티 이름이 Swift 예약어(`required`, `default`, `description` 등)면 Swift 용 다른 이름을 하나 더 둔다(`isRequired`).

### 권한·뒤로가기·모달

**권한**(기준 `feature/permission`, `core/data/.../permission/`)
- ViewModel 은 `PermissionController` 만 쓴다. 순서: ① 왜 필요한지 화면에 먼저 ② `request(permission)` ③ `PermanentlyDenied` 면 확인 다이얼로그 뒤 `openSettings()`.
- **재확인**: 권한에 기대는 화면은 다시 보일 때마다 `viewModel.refresh()` — Android `LifecycleResumeEffect(Unit) { …; onPauseOrDispose { } }`, iOS `.onAppear` + `.onChange(of: scenePhase)`.
- 권한을 추가·삭제할 때 고칠 곳: `AppPermission`, `AndroidPermissionController.manifestNames`, `AndroidManifest.xml`, `IOSPermissionController`, `Info.plist` 사용 목적 문구, 화면 문구. **쓰지 않는 권한은 지운다.**

**뒤로가기(Android) 처리 순서** — 위에 있는 것부터:
1. 떠 있는 다이얼로그·바텀시트 2. 화면의 `BackHandler(enabled = …)`(이전 단계로·저장 전 확인·온보딩 앞 쪽)
3. NavHost(위에 쌓인 화면을 닫는다) 4. 탭 루트: 다른 탭 → 홈 탭 → "한 번 더 누르면 종료"(`AndroidNavHost`)

**모달**
- 다이얼로그·시트의 상태는 그 화면의 `remember`(Android) / `@State`(iOS). 화면이 스택에서 가려지거나 로그인으로 넘어가면 같이 사라진다.
- 한 화면에 다이얼로그는 하나(`alert` 하나). 시트 위에 확인이 필요하면 시트를 닫고 다이얼로그를 띄운다(겹쳐 쌓지 않는다).
- 확인 버튼은 다이얼로그를 닫은 뒤 동작을 부른다(`ElsaAlertDialog`·`.elsaAlert` 가 그렇게 한다).
- 앱 전체 안내(강제 업데이트·오프라인)는 루트에 한 번만 둔다.
- 중복 실행: 요청은 ViewModel 이 `isLoading`/`isSaving` 으로 막고, 화면 이동은 `launchSingleTop`.

**검색·낙관적 갱신**
- 검색(기준 `feature/search`): 입력마다 요청하지 않고 멈춘 뒤 검색한다(`delay` + 이전 Job 취소). 조건이 바뀌면 `paginator.restart()`, 지우면 `paginator.clear()`.
- 틀려도 큰 문제가 없는 값(읽음 표시)은 먼저 화면에 반영하고 서버에 보낸다(기준 `feature/inbox`). 결과를 확인해야 하는 값(저장)은 서버 응답 뒤에 바꾼다.

## 5. 하단 탭 추가·변경

탭은 양 플랫폼에 같은 순서로 둔다. 목록은 Android `navigation/MainTab.kt`(enum), iOS `navigation/main.tab.swift`(enum).
새 feature 를 탭으로 붙이려면 `newFeature <name> --tab` 이 아래를 모두 한다. 기존 화면을 탭으로 올릴 때는 손으로:

1. Android `MainTab` 에 `Name(AndroidRoute.Name, R.string.tab_name, R.drawable.elsa_ic_tab_name)` — 탭 루트 화면은 `onBack` 없이 `AndroidNavHost` 에 등록
2. iOS `MainTab` 에 `case name` + `title`(`"tab_name"`) + `icon`(생성 에셋 `elsa_ic_tab_<name>` — design/icons, DEC-118), `AppNavigation.root(for:)` 에 루트 화면
3. 문자열 `tab_<name>` (Android strings.xml ko/en, iOS Localizable.xcstrings)
4. 아이콘: `design/icons/tab_<name>.svg`(Lucide 24×24 선 아이콘)를 넣고 `./gradlew generateDesignTokens` → Android `R.drawable.elsa_ic_tab_<name>`, iOS 에셋 `"elsa_ic_tab_<name>"` (DEC-118, 그전까지는 `elsa_ic_tab_default`)
5. 딥링크로 탭을 열려면 Android `DeepLinkRouter` 에 `navigateToTab(MainTab.Name)`, iOS `deeplink.router.swift` 에 `.tab(.name)`

- 탭마다 스택을 따로 유지한다(Android `navigateToTab` 의 saveState/restoreState, iOS 탭별 `NavigationStack(path:)`).
- 하단 탭 바는 탭 루트에서만 보인다(Android). 탭 위에 쌓이는 화면(갤러리·웹뷰·상세)은 `AndroidRoute`/`MainRoute` 로 연다.
- 탭은 5개를 넘기지 않는다(플랫폼 가이드). 순서를 바꾸면 양쪽 enum 순서를 같이 바꾼다.

