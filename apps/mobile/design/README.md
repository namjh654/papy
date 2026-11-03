# Design System

토큰 원본은 `design/tokens.json` 하나. 컴포넌트는 양 플랫폼 Native 구현이며 **이름·파라미터 의미가 같다** (DEC-006, DEC-007).
수치 기준은 [컴포넌트 명세](components.md)(DEC-112), 두 플랫폼 비교는 `scripts/orca.sh capture` → `build/capture/index.html`.

## 토큰 변경

```bash
# 1) design/tokens.json 수정  2) 생성  3) 생성물까지 커밋
./gradlew generateDesignTokens      # 생성 (JDK 만 필요)
./gradlew checkDesignTokens         # 최신 확인 — templateCheck·orca check·CI 에서 자동 실행
```

| 생성물 | 사용 |
|---|---|
| `androidApp/.../designsystem/token/PapyTokens.kt` | `PapyTheme.colors.primary`, `PapyTheme.typography.body`, `PapySpacing.lg`, `PapyRadius.md`, `PapySize.controlHeight` |
| `androidApp/src/main/res/values(-night)/papy_colors.xml` | 윈도우 테마(시작 배경) 전용 `@color/papy_background` 등. Compose 코드는 사용하지 않음 |
| `iosApp/iosApp/designsystem/token/papy.tokens.swift` | `PapyColor.primary`, `.papyFont(PapyFont.body)`, `PapySpacing.lg`, `PapyRadius.md`, `PapySize.controlHeight` |

화면·컴포넌트 코드에 hex 색, 숫자 radius/spacing 을 직접 쓰지 않는다. 다크 모드는 토큰의 `dark` 값으로 자동 전환된다.

## 글꼴·아이콘 (DEC-117·118)

| 원본 | 생성물 | 사용 |
|---|---|---|
| `design/fonts/Pretendard-*.otf` (Regular·Medium·SemiBold·Bold) | `androidApp/src/main/res/font/pretendard_*.otf`, `iosApp/iosApp/Resources/Fonts/` | 토큰 타이포그래피가 굵기별 파일을 쓴다(Android `PapyFontFamily`, iOS `PapyFont.*.fontName`). iOS 는 앱 시작 때 `PapyFont.registerFonts()` |
| `design/icons/<이름>.svg` (Lucide 선 아이콘, 24×24) | Android `res/drawable/papy_ic_<이름>.xml`, iOS `Assets.xcassets/Icons/papy_ic_<이름>` | Android `painterResource(R.drawable.papy_ic_<이름>)`, iOS `Image("papy_ic_<이름>")` — 색은 tint/foregroundStyle |

- 아이콘 추가: [Lucide](https://lucide.dev) 에서 SVG 를 받아 `design/icons/<이름>.svg` 로 넣고 `./gradlew generateDesignTokens`. 지원 요소는 path·circle·line·rect·polyline·polygon(`SvgIcon`)
- 뒤로 가기·목록 화살표처럼 **플랫폼 관례 아이콘은 넣지 않는다**(Android `papy_ic_arrow_back`·`papy_ic_chevron_right`, iOS SF Symbol `chevron.*`)
- 한국어 줄바꿈: Android 타이포그래피는 어절 단위(`LineBreak.WordBreak.Phrase`, Android 13+) — iOS 기본과 같게
- 출처·라이선스: Pretendard — SIL Open Font License 1.1 (`design/fonts/OFL.txt`, 글꼴 파일 수정 금지·글꼴만 따로 판매 금지), Lucide — ISC (`design/icons/LICENSE.txt`). 앱의 오픈소스 고지에 두 항목을 넣는다

## 참고한 디자인 (DEC-114)

코드·파일은 가져오지 않고 값·배치만 참고한다: shadcn/ui·React Native Reusables(MIT) — 무채색 + 포인트 색 하나, 얇은 테두리, 단순한 로그인 구성 / Material 3 Design Kit(CC BY 4.0, Google) — 상태 규칙(눌림·비활성·오류·포커스) / 기존 DEC-017 의 채움형 입력·하단 CTA·큰 제목.

## 스타일 방향 (DEC-017)

토스 앱의 **레이아웃·간격·타이포 위계·컴포넌트 형태만** 참고한다. 로고·브랜드 색·일러스트는 쓰지 않는다(색은 자체 팔레트).
캡처는 `design/reference/` 에 둔다(현재 비어 있음 — 캡처를 넣으면 이 원칙을 다시 맞춘다).

| 항목 | 규칙 |
|---|---|
| 화면 배경 | 입력·흐름 화면은 `background`(흰색/다크 거의 검정), 목록·대시보드는 `backgroundGrouped` 위에 `surface` 카드 |
| 제목 | 상단 좌측 정렬 큰 제목 `headline`(26 bold), 설명은 `body` + `onSurfaceVariant`. 제목과 본문 사이 `PapySpacing.xl`~`section` |
| 좌우 여백 | `PapySize.screenPadding`(24) |
| 주요 버튼 | 전폭 `controlHeight`(56), radius `lg`, 화면 하단 고정(CTA). 보조 동작은 텍스트/회색(`secondary` 스타일) 버튼 |
| 입력칸 | 테두리 없는 채움형(`fill`), radius `md`, 포커스 시 `primary` 테두리, 오류 시 `error` 테두리 + 아래 문구 |
| 카드 | 테두리·그림자 없이 `surface` + radius `xl`, 내부 여백 `xl` |
| 목록 | 당겨서 새로고침, 끝에서 다음 페이지(`PapyPagedList`), 비었을 때 가운데 문구, 오류는 가운데 + 다시 시도 |
| 하단 탭 | 3~5개, 아이콘 + 짧은 라벨, 선택 탭은 `onSurface`, 나머지 `onSurfaceTertiary` |
| 목록 행 | 높이 `listRowHeight`(64), 구분은 여백 우선, 필요하면 `divider` |
| 텍스트 위계 | 제목 `headline`/`title` → 강조 `subtitle`/`bodyStrong` → 본문 `body` → 보조 `bodySmall`/`caption` + `onSurfaceTertiary` |

## 컴포넌트 대응표

| 용도 | Android (Compose) | iOS (SwiftUI) |
|---|---|---|
| 테마 | `PapyTheme { }` (MainActivity) | 불필요 (동적 색) |
| 버튼 | `PapyButton(text, onClick, style, size, enabled, isLoading, fullWidth)` | `PapyButton(title, style:, size:, enabled:, isLoading:, fullWidth:) { }` |
| 텍스트 입력 | `PapyTextField(value, onValueChange, label, placeholder, helperText, errorText, isSecure, maxLength, imeAction, onImeAction)` | `PapyTextField(label, text:, placeholder:, helperText:, errorText:, isSecure:, maxLength:, submitAction:, onSubmit:)` |
| 선택 필드 | `PapySelectField` | `PapySelectField` |
| 체크박스 | `PapyCheckbox` | `PapyCheckbox` |
| 스위치 | `PapySwitch` | `PapyToggle` |
| 라디오 | `PapyRadioGroup` | `PapyRadioGroup` |
| 날짜 | `PapyDatePickerField` (UTC 자정 millis) | `PapyDatePickerField` (`Date?`) |
| 시간 | `PapyTimePickerField` (`PapyTime`) | `PapyTimePickerField` (`Date?` 의 시/분) |
| 이미지 선택 | `PapyImagePicker` (`List<Uri>`, 포토피커) | `PapyImagePicker` (`[PapyPickedImage]`, PhotosPicker) |
| 확인 다이얼로그 | `PapyAlertDialog(alert: PapyAlert?)` | `.papyAlert($alert)` |
| 토스트 | `rememberPapyToastState()` + `toast.show()` | `.papyToast($toast)` |
| 인라인 알림 | `PapyInlineMessage(type = PapyMessageType.Error)` | `PapyInlineMessage(type: .error)` |
| 로딩 | `PapyLoadingIndicator`, `PapyLoadingOverlay` | `PapyLoadingIndicator`, `.papyLoadingOverlay()` |
| 카드 | `PapyCard` | `PapyCard` |
| 화면 틀 | `PapyScaffold(title, onBack, toastState, containerColor)` | NavigationStack + `.papyScreenBackground(grouped:)` |
| 큰 제목 | `PapyScreenHeader(title, description)` | `PapyScreenHeader(title:description:)` |
| 키보드 대응 폼 | `PapyFormColumn(bottomBar = { CTA }) { }` | `PapyFormScrollView { } bottomBar: { CTA }` |
| 오류 문구 | `uiError.message()`, 이벤트 처리 안에서는 `uiError.message(context)` | `uiError.localizedMessage` |
| 스크롤 화면 틀 | `PapyScrollScreen(grouped, verticalArrangement) { }` | `PapyScrollScreen(grouped:spacing:) { }` |
| 메뉴 카드(→ 다른 화면) | `PapyMenuCard(title, onClick, description)` | `PapyMenuCard(title:description:action:)` |
| 목록(페이징) | `PapyPagedList(state, key, emptyTitle, onRefresh, onLoadMore, onRetry, onRetryLoadMore, header) { item -> }` | `PapyPagedList(state:id:emptyTitle:onRefresh:onLoadMore:onRetry:onRetryLoadMore:) { header } row: { item in }` |
| 빈 화면 | `PapyEmptyState(title, message, actionText, onAction)` | `PapyEmptyState(title:message:actionTitle:action:)` |
| 전체 오류(다시 시도) | `PapyErrorState(message, onRetry)` | `PapyErrorState(message:onRetry:)` |
| 한 건 불러오기(내용·로딩·오류) | `PapyContentState(value, isLoading, error, onRetry) { item -> }` | `PapyContentState(value:isLoading:error:onRetry:) { item in }` |
| 짧은 표시(새 글 등) | `PapyBadge(text, type)` | `PapyBadge(text:type:)` |
| 상대 시간(3일 전) | `RelativeTime.of(millis).label()` | `PapyFormat.relative(epochMillis:locale:)` |
| 원격 이미지·프로필 사진 | `PapyRemoteImage(url)`, `PapyAvatar(name, url, picked: Uri?)` | `PapyRemoteImage(url:)`, `PapyAvatar(name:url:picked: UIImage?)` |
| 쪽 표시 | `PapyPageIndicator(pageCount, currentPage)` | `PapyPageIndicator(pageCount:currentPage:)` |
| 오프라인 안내 | `PapyOfflineBanner(visible)` (AndroidNavHost 루트) | `PapyOfflineBanner()` (AppNavigation `safeAreaInset`) |
| 설정 묶음·행 | `PapySettingsSection(title) { PapySettingsRow(title, value, onClick, destructive); PapySettingsDivider() }` | `PapySettingsSection(title:) { PapySettingsRow(title:value:destructive:action:); PapySettingsDivider() }` |
| 하나 고르기(바텀시트) | `PapyOptionSheet(title, options, selected, label, onSelect, onDismiss)` | `.papyOptionSheet(isPresented:title:options:selected:label:onSelect:)` |
| 하단 탭 | `PapyNavigationBar(items: List<PapyTabItem>)` + `navigation/MainTab.kt` | `TabView` + `Tab` + `navigation/main.tab.swift` |
| 전역 상태 관찰 | `collectAsStateWithLifecycle()` | `FlowState(stateFlow)` (`@StateObject`) |
| 새로고침 끝 기다리기 | — (`PullToRefreshBox` 가 `isRefreshing` 을 본다) | `await model.waitUntil { !$0.list.isRefreshing }` |
| 날짜·시간·숫자 | `PapyFormat.date/dateLong/time/number/percent(…, currentLocale)` | `PapyFormat.date/dateLong/time/number/percent(…, locale:)` |

## 키보드 처리

- **Android**: `enableEdgeToEdge()` + `adjustResize` + 루트 `safeDrawing` padding → 입력 화면은 `PapyFormColumn` (스크롤, 빈 곳 탭 시 닫힘). `PapyTextField` 는 포커스 시 키보드 위로 스크롤된다. 직접 만든 입력에는 `Modifier.bringIntoViewOnFocus()`.
- **iOS**: `PapyFormScrollView` 안의 `PapyTextField` 는 자동 등록되어 포커스 시 스크롤, `.next` 로 다음 필드 이동, 스크롤/빈 곳 탭 시 닫힘, 키보드 위 '완료' 버튼(NavigationStack 안).

## 다국어 (DEC-018, DEC-019)

- 기본 언어 **ko**, 추가 **en**. 언어는 기기 설정을 따르고, Android 13+ 는 앱별 언어(`res/xml/locales_config.xml`), iOS 는 설정 앱에서 바꿀 수 있다.
- 문자열은 플랫폼 리소스에 **같은 키**로 둔다.
  - Android: `res/values/strings.xml`(ko) + `res/values-en/strings.xml` — 인자 `%1$s`, `%1$d`
  - iOS: `iosApp/iosApp/Localizable.xcstrings` (sourceLanguage ko, en) — 인자 `%1$@`, `%1$lld`. iOS 전용 키 허용(예: `common_done`)
  - `./gradlew checkStrings` 가 키·언어·인자 개수 일치를 검사한다 (`templateCheck`·`orca check`·CI 에서 자동 실행).
- KMP(core) 는 문구를 갖지 않는다. 오류는 `UiError(code: ErrorCode, statusCode)` 로 내보내고 플랫폼이 `error_*` 키로 매핑한다. `ErrorCode` 를 추가하면 양 플랫폼 매핑과 `error_*` 문자열을 함께 추가한다.
- 날짜·시간·숫자는 `PapyFormat` 으로만 표시한다(패턴 문자열 금지). 기기 12/24시간 설정을 따른다.
- 새 화면: 문자열 하드코딩 금지 → 키 추가(ko·en 양쪽, 양 플랫폼) → `./gradlew checkStrings` 통과.
- 언어 추가: Android `values-<lang>/strings.xml` + `locales_config.xml`, iOS xcstrings 에 언어 추가, `./gradlew checkStrings` 의 검사 언어 목록 갱신.

## 확인

홈 → **컴포넌트 갤러리** 에서 모든 컴포넌트를 양 플랫폼에서 같은 순서로 확인할 수 있다. 새 컴포넌트는 갤러리에도 추가한다.
