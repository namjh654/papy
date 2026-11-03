# iOS 워크트리 노트 (ocra-ios)

> 이 워크트리가 수정할 수 있는 파일: `iosApp/**`, 이 파일.
> 공통 로직(core) 변경이 필요하면 직접 고치지 말고 아래에 요청을 남긴다 → ocra 가 처리 후 `orca.sh refresh`.
> 결정이 필요한 사항도 여기에 `[결정제안]` 으로 남기면 ocra 가 DECISIONS.md 에 기록한다.

## 요청 (ocra 에게)

<!-- - [ ] YYYY-MM-DD core: 필요한 변경 / 이유 -->

## 작업 로그

- 2026-09-29 디자인 시스템 1차: 토큰 기반 컴포넌트(ElsaButton/TextField/SelectField/Checkbox/Toggle/RadioGroup/Date·TimePickerField/ImagePicker/Alert/Toast/InlineMessage/Loading/Card) + 키보드 처리(ElsaFormScrollView + ElsaFormCoordinator: 포커스 필드 스크롤, 다음 필드 이동, 탭/스크롤로 닫기, 키보드 '완료' 툴바), 컴포넌트 갤러리(Home → MainRoute.componentGallery), Login/Signup/Home/Splash 적용. AppCard 제거.
  - 이 환경에는 Swift 툴체인이 없어 컴파일 미검증 → mac 에서 `orca.sh check ios` 필요.
- 2026-09-29 (windows, **mac 빌드 대기**) Phase 4 마무리 + 다국어 기반 — Windows 에서 작성, Swift 컴파일 미검증:
  - 스타일(DEC-017): 채움형 필드 배경(포커스/오류만 테두리), 무테 카드(radius xl), 큰 버튼 radius lg, secondary=fill, `ElsaScreenHeader`, `ElsaFormScrollView { } bottomBar: { }`(safeAreaInset 하단 CTA), `.elsaScreenBackground(grouped:)`.
  - 다국어(DEC-018/019): `Localizable.xcstrings`(sourceLanguage ko + en, Android strings.xml 과 같은 키, iOS 전용 `common_done`), `UiError.localizedMessage`(ErrorCode switch), `ElsaFormat`(FormatStyle 로케일 포맷). 화면·컴포넌트 문자열 하드코딩 제거.
  - mac 확인 항목: ① SKIE 가 `ErrorCode` 를 Swift enum(`.credentialsRequired` …)으로 내보내는지 ② `UiError.statusCode`(KotlinInt?) `.int64Value` ③ `ElsaFormScrollView` 제네릭 init 두 개 해석 ④ 설정 앱에서 언어 en 전환 시 문구·날짜 변경 ⑤ 다크 모드.

## mac 검증 (Phase 별, DEC-027)

> 2026-09-30 mac(Xcode 27.0, iPhone 16 Pro 시뮬레이터)에서 Phase 1~8 을 검증했다. 방법:
> `scripts/orca.sh check --env dev|staging|prod`(Kotlin iOS + SKIE + xcodebuild), 시뮬레이터 설치·실행,
> scratchpad 의 임시 XCUITest(설치된 앱을 번들 ID 로 조작) + 로컬 목업 서버(`env/dev.local.properties` 의 `api.baseUrl` 덮어쓰기).
> 참고: `orca.sh check` 는 서명 없이(`CODE_SIGNING_ALLOWED=NO`) 컴파일만 한다. 이 빌드는 entitlement 가 없어
> 시뮬레이터에서 Keychain 이 실패(-34018)하므로 **실행 확인은 Xcode Run 또는 `CODE_SIGN_IDENTITY=-`(ad-hoc) 빌드**로 한다.

### Phase 1 — 기반 정리 — 검증 완료 (공유 scheme `iosApp` 은 Phase 8 에서 `iosApp-<env>` 3개로 대체)
- [x] 공유 scheme 으로 xcodebuild 성공 (`iosApp-dev·staging·prod`, generic iOS Simulator)
- [x] ~~GitHub Actions `iOS xcodebuild` 잡 녹색~~ — CI 를 끄고 예시만 남김(docs/ci.md, DEC-096). 확인 대상 아님

### Phase 2 — 모듈 분리 — 검증 완료
- [x] `orca.sh check` (mac) — `:shared:compileKotlinIosSimulatorArm64` + xcodebuild 성공 (dev·staging·prod)
- [x] `Core.h` 에 모듈 접두어 타입 없음(`Core_data…`/`Feature_…` 0건, Koin 타입만 `CoreKoin_core…` — export 대상 아님)
- [x] Swift 이름 그대로: `LoginViewModel`·`LoginUiState`·`ErrorCode`·`UiError`·`SplashEvent`·`HomeEvent`·`IOSAppGraph`·`IOSAppKoin`·`AuthStatus`·`DeepLink`
- [x] `ViewModelOwner`·`SharedViewModel(..., state: \.uiState)`·`onEnum(of:)` 컴파일, 화면 동작
- [x] 실행: 스플래시 → 로그인 → 홈 → 예시 → 로그아웃, 자동 로그인 복원 (XCUITest)

### Phase 3 — Network·Auth·Storage — 검증 완료
- [x] `IOSSecureStorage` 컴파일
- [x] Keychain: 로그인 → 재실행 → 자동 로그인 복원, 로그아웃 → 재실행 시 로그인 화면 (ad-hoc 서명 빌드)
- [x] 마이그레이션: Keychain 비움 + NSUserDefaults 에 `token.access/refresh` 만 둔 상태 → 복원 성공, defaults 의 토큰 키 삭제
- [x] `AuthStatus` Swift enum·`.onChange(of:)` 컴파일
- [x] 401 → 로그인 전환 (2026-10-01 mac: 프로필 저장 `PUT /api/me` 401 → `POST /api/auth/refresh` 401 → 로그인 화면). Ktor 로그의 Authorization 은 `***` 로만 찍히고 토큰 값은 로그에 없음(2026-10-01 mac, `log stream` 확인)

### Phase 4 — Design System + 다국어 — 검증 완료
- [x] xcodebuild 성공, `ErrorCode` Swift enum·`UiError.statusCode` 컴파일, `ElsaFormScrollView` 제네릭 init
- [x] en(`-AppleLanguages (en)`)·다크 모드: 로그인·홈(날짜 영어)·컴포넌트 갤러리 표시
- [x] 갤러리 조작(키보드 스크롤, PhotosPicker 여러 장, 날짜/시간, 라이트·다크) — 2026-10-01 mac 시뮬레이터

### Phase 5 — Push — 검증 완료 (Firebase 설정·실기기 항목 제외)
- [x] firebase-ios-sdk 12.19.2 SPM 해석(Package.resolved 커밋), FirebaseMessaging 링크
- [x] `GoogleService-Info.plist` 없이 빌드·실행 → NoOp, 로그인·홈 정상
- [x] Swift 컴파일: `FcmPushProvider` 요구사항, `onChange(of: push.pendingDeepLink)`
- [ ] plist 있을 때 FCM 토큰 등록·권한 팝업·알림 탭 이동·foreground 배너 — 실제 Firebase 프로젝트와 실기기 필요
- [ ] Signing & Capabilities › Push Notifications (실기기)
- 경고: `Messaging.deleteToken(completion:)` deprecated(→ `unregister`). 동작이 달라(FCM 설치 해제) 유지, Phase 12 후보

### Phase 13 — 상세 패턴·공지·외부 링크 — 검증 완료 (2026-10-01 mac)
> 2026-10-01 mac(Xcode 27.0, iPhone 18 Pro 시뮬레이터): check dev·staging·prod 통과, 임시 XCUITest + 로컬 목업 서버로 아래 흐름 확인.
> 주의: iOS 27 시뮬레이터에서 `simctl ui appearance` 로 시스템 외형을 바꾸면 PosterBoard 가 죽으며 시뮬레이터가 꺼질 수 있다 — 다크 확인은 미리 바꿔 두고 부팅하거나 앱 테마 설정으로 한다.
> Swift 컴파일 오류는 1건뿐: Kotlin **인터페이스**의 companion 은 Swift 에서 `RelativeTime.companion` 이 아니라 `RelativeTimeCompanion.shared` (49f5634).
- [x] 컴파일: `view/example/example.detail.screen.swift`, `view/notice/notice.detail.screen.swift`(둘 다 `init(id:)` + `StateObject(wrappedValue:)`), `ElsaContentState<Value, Content>`·`ElsaBadge`(elsa.states.swift), `ElsaFormat.relative`(`onEnum(of: RelativeTime…)`, `RelativeTime.companion.since(epochMillis:)`), `ui.error.text.swift` 의 새 case 4개
- [x] 컴파일: `MainRoute.noticeDetail(id:)`·`.exampleDetail(id:)`, `DeepLinkRouter` 의 `<path>/<id>` 분기, `HomeScreen(onNoticeTap:)`, `.onOpenURL`
- [x] 실행: 홈 → 공지사항 → 상세 → 뒤로, 예시 → 상세 → 즐겨찾기 → 목록 배지, `elsa://notice/5`(실행 중·꺼진 상태 → 로그인 뒤 열림), `elsa://settings`
- [x] Info.plist `CFBundleURLTypes`(elsa)

### Phase 14 — 약관·재설정·탈퇴·프로필 — 검증 완료 (2026-10-01 mac)
- [x] 컴파일: `view/terms/terms.screen.swift`(`Agreement` 의 `.service/.privacy/.marketing`·`isRequired`, `state.isChecked(agreement:)`, `state.url(agreement:)`)
- [x] 컴파일: `view/reset/password.reset.screen.swift`(`state.step == .request`, 툴바 뒤로 버튼), `view/withdraw/withdraw.screen.swift`, `view/profile/profile.screen.swift`(`photosPicker`, `IOSAppGraph.shared.bytes(data:)` → `KotlinByteArray`)
- [x] 컴파일: `designsystem/component/elsa.remote.image.swift`, `login.screen`(`.notice(let event)` → `event.notice` switch), `signup.screen`(`init(marketingAgreed:onSignupSucceeded:)`), `settings.screen`, `AppRoute` 새 case 4개
- [x] 실행: 회원가입(약관 → 가입 → 로그인 토스트), 비밀번호 재설정(둘째 단계에서 뒤로 → 첫 단계), 프로필(사진 선택·저장·변경 후 뒤로 → 확인), 탈퇴, 약관 전문 웹뷰(로그인 전)

### Phase 15 — 권한·온보딩·알림함·검색 — 검증 완료 (2026-10-01 mac)
- [x] 컴파일: `view/onboarding/onboarding.screen.swift`(`TabView(selection:)` + `.page`, `Int32` ↔ `Int`), `designsystem/component/elsa.page.indicator.swift`
- [x] 컴파일: `view/permission/permission.screen.swift`(`AppPermission`·`PermissionStatus` enum, `state.status(permission:)`, `status.isGranted`), `view/inbox/inbox.screen.swift`, `view/search/search.screen.swift`(`ElsaTextField(nil, …)`, `ElsaPagedList` 의 빈 header)
- [x] 컴파일: `AppPhase.onboarding`, `SplashScreen(onFirstLaunch:)`, `HomeScreen` 새 콜백 3개
- [x] 실행: 첫 실행 온보딩(밀기·다음·건너뛰기) → 둘째 실행은 로그인, 알림 눌러 공지 상세, 검색 자동 결과·최근 검색어, 권한 창 3종(알림·카메라·위치)과 거부 뒤 "설정 열기" → 돌아오면 상태 갱신
  - 참고: iOS 는 카메라·위치 권한을 설정에서 바꾸면 **앱을 종료**한다(OS 동작) → 돌아오면 새로 시작하며 상태가 맞게 나온다. 시뮬레이터에서 "설정 열기"는 앱 설정이 아니라 설정 첫 화면을 열었다(코드는 표준 `UIApplicationOpenSettingsURLString`) — 실기기에서 한 번 볼 것
- [x] core:data `IOSPermissionController.kt`(Kotlin — Windows 에서 컴파일 확인) 동작, Info.plist `NSCameraUsageDescription`·`NSLocationWhenInUseUsageDescription`

### Phase 6 — 테스트·템플릿화 — 검증 완료
- [x] `ExampleScreen` 컴파일, 홈 → 예시 기능 → Sample 3개, 다크·en
- [x] 임시 워크트리에서 `newFeature notice` → `orca.sh check`(Android + iOS xcodebuild) 통과

### Phase 8 — 환경 분리 — 검증 완료
- [x] Build Configuration 6개·scheme 3개로 빌드, `[config=*-<env>]` 적용
- [x] 세 앱 동시 설치: `…elsaapp.dev`/ELSA Dev, `…elsaapp.staging`/ELSA Stg, `…elsaapp`/ELSA
- [x] Kotlin framework 환경 일치: 각 앱의 Core 바이너리에 해당 환경 baseUrl 만 포함
- [x] Debug-* = debug framework, Release-* = release(`linkReleaseFrameworkIosArm64`)
- [x] `Configuration/Firebase/<env>/GoogleService-Info.plist` 를 넣은 환경만 번들에 복사
- [x] Archive(Release-prod, 서명 없이) 성공
- 경고(무해): `ld: search path …/xcode-frameworks/$(CONFIGURATION)/* not found`, Kotlin `Cannot infer a bundle ID` — Phase 11 정리 후보
