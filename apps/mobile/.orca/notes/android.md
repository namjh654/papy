# Android 워크트리 노트 (ocra-android)

> 이 워크트리가 수정할 수 있는 파일: `androidApp/**`, 이 파일.
> 공통 로직(core) 변경이 필요하면 직접 고치지 말고 아래에 요청을 남긴다 → ocra 가 처리 후 `orca.sh refresh`.
> 결정이 필요한 사항도 여기에 `[결정제안]` 으로 남기면 ocra 가 DECISIONS.md 에 기록한다.

## 요청 (ocra 에게)

<!-- - [ ] YYYY-MM-DD core: 필요한 변경 / 이유 -->

## 작업 로그

- 2026-09-29 디자인 시스템 1차: ElsaTheme(토큰 매핑) + 컴포넌트(Button/TextField/SelectField/Checkbox/Switch/RadioGroup/Date·TimePicker/ImagePicker/Alert/Toast/InlineMessage/Loading/Card/Scaffold) + 키보드 처리(ElsaFormColumn, bringIntoViewOnFocus), 컴포넌트 갤러리, Login/Signup/Home/Splash 적용. 기존 theme/·AppCard 제거.
  - 이 환경에서는 Android SDK 미설치로 빌드 불가 → Compose Desktop 1.8.2 + 스텁으로 Kotlin 타입 검사만 통과. mac/windows 에서 `orca.sh check android` 필요.
- 2026-09-29 (windows) 1차 작업 실제 빌드 확인: `assembleDevDebug` + lint 통과.
- 2026-09-29 (windows) Phase 4 마무리 + 다국어 기반:
  - 다크 모드 시작 번쩍임: `Theme.Elsa`(values = Light 부모, values-night = 다크 부모) + windowBackground `@color/elsa_background`(토큰 생성물).
  - 스타일(DEC-017): 채움형 입력칸, 무테 카드(radius xl), 큰 버튼 radius lg, `ElsaScreenHeader`, `ElsaFormColumn(bottomBar=)` 하단 CTA, `ElsaScaffold(containerColor=)`.
  - 다국어(DEC-018/019): `values/strings.xml`(ko 기본) + `values-en`, `xml/locales_config.xml`(앱별 언어), `UiError.message()`(ErrorCode 매핑), `ElsaFormat`(로케일 날짜·시간·숫자). 화면·컴포넌트 문자열 하드코딩 제거.
  - Login/Signup/Home/Splash/갤러리 반영. 홈은 backgroundGrouped + 카드 메뉴 + 로케일 날짜.
- 2026-09-29 (windows) Phase 3:
  - ApiConfig 생성 경로 단일화: `ApiConfig.from(BuildEnvironment.from(BuildConfig.APP_ENV))` — `BuildConfig.API_BASE_URL` 제거(URL 은 core:data GeneratedEnvironment).
  - 루트(AndroidNavHost)가 `AuthStore.status` 를 관찰해 메인 화면에서 Unauthenticated(401 만료 등)가 되면 `navigateToLogin()`.
- 2026-09-29 (windows) Phase 6: 홈 메뉴에 예시 기능 카드(navigateToExample), 메뉴 카드를 MenuCard 로 공통화. ExampleScreen·경로는 scripts/new_feature.py 생성물(ocra).
- 2026-09-29 (windows) Phase 5 푸시: FcmPushProvider(google-services.json 있을 때만, 없으면 NoOp), ElsaMessagingService(토큰·foreground 알림), NotificationChannels(default), MainActivity(singleTop, 알림 탭 extras → PushManager), PushEffects(로그인 후 POST_NOTIFICATIONS 요청, pendingDeepLink → DeepLinkRouter), 알림 아이콘 elsa_ic_notification.
