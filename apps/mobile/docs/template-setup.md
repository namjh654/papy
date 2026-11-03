# 템플릿으로 새 앱 시작하기

이 저장소를 복사해 새 앱을 만들 때 **위에서부터 순서대로** 따라 한다. 각 단계 끝에 `scripts/orca.sh check` 로 확인한다.
규칙(이름·패키지·파일 배치)은 [conventions.md](conventions.md).

## 0. 준비

| 도구 | 버전 | 비고 |
|---|---|---|
| JDK | 17 이상 (Android Studio 내장 jbr 권장, JDK 25 확인됨) | `JAVA_HOME` |
| Android Studio | AGP 9.0 지원 버전, SDK 36 | `local.properties` 의 `sdk.dir` |
| Xcode | 16 이상 (mac) | iOS 빌드·서명 |

```bash
git clone <이 템플릿> my-app && cd my-app
rm -rf .git && git init          # 템플릿 이력 없이 시작 (이력을 남기려면 생략)
rm -rf .orca/reports             # 템플릿 작업 보고서 (Orca 를 안 쓰면 .orca 전체 삭제 가능)
```

## 1. 앱 이름 · 패키지명 · 번들 ID · Team ID

`renameApp` 도구가 한 번에 바꾼다(JDK 만 필요, 루트 빌드 밖에서 실행 — DEC-057). 먼저 `--dry-run` 으로 바뀔 곳을 본다.

```bash
./gradlew -p build-logic :tooling:run --args="renameApp --package com.acme.shop --app-name 'Acme Shop' --dry-run"
./gradlew -p build-logic :tooling:run --args="renameApp --package com.acme.shop --app-name 'Acme Shop' --bundle-id com.acme.shop --team-id ABCDE12345"
scripts/orca.sh check
```

| 항목 | 바뀌는 곳 |
|---|---|
| 패키지 `cloud.elsa.app` | 모든 `src/*/kotlin/cloud/elsa/app` 디렉터리 이동, Kotlin `package`/`import`, Android `applicationId`·`namespace`(모듈별 자동), 스크립트 경로 |
| 앱 이름 | Android `app_name`(`res/values/strings.xml`), iOS `PRODUCT_NAME`(`iosApp/Configuration/Config.xcconfig`), `rootProject.name` |
| iOS 번들 ID | `Config.xcconfig` 의 `PRODUCT_BUNDLE_IDENTIFIER` |
| Team ID | `Config.xcconfig` 의 `TEAM_ID`, `project.pbxproj` 의 `DEVELOPMENT_TEAM = "$(TEAM_ID)"` |
| 저장소 이름 | Keystore alias·Keychain service·UserDefaults suite 는 새 패키지 기준으로 바뀐다. Android DataStore 파일 이름(`elsa.*.preferences_pb`)은 그대로 — 백업 제외 규칙(`res/xml/data_extraction_rules.xml`)과 맞아야 하므로 바꾸려면 둘 다 바꾼다 |

직접 할 것
- 스크립트가 마지막에 출력하는 남은 흔적 수정 — `README.md` 제목, `env/common.properties` 주석, `docs/environments.md` 의 앱 이름 예시
- 앱 아이콘: Android `androidApp/src/main/res/mipmap-*`, iOS `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`
- (선택) 디자인 시스템 접두어 `Elsa*`, convention plugin id `elsa.kmp.*` — 바꾸려면 IDE 의 이름 변경 기능을 쓴다. 바꾸지 않아도 동작한다
- dev·staging·prod 앱은 이미 한 기기에 함께 설치된다(Android applicationId·iOS 번들 ID 에 env `app.idSuffix` 접미사). 추가 작업 없음
- 버전은 `env/common.properties` 의 `app.versionName`·`app.versionCode` 한 곳 ([release.md](release.md) §1)

> 이미 배포한 앱에는 쓰지 않는다. 패키지·번들 ID·저장소 이름이 바뀌면 기존 설치와 이어지지 않는다.

## 2. API URL · 환경 (dev / staging / prod)

환경별 값의 원본은 `env/` 폴더다 — 자세한 규칙은 [environments.md](environments.md).

```properties
# env/common.properties   (공통)
app.name=Acme Shop
# env/dev.properties       (환경별 공개 값 — staging·prod 도 같은 키)
app.nameSuffix=Dev
app.idSuffix=.dev
api.baseUrl=https://api.dev.acme.com
network.log=true
```

1. `env/dev.properties`·`staging.properties`·`prod.properties` 의 `api.baseUrl` 을 실제 서버로 바꾼다.
2. 비밀 값이 있으면 `env/<env>.local.properties.example` 에 키를 적고(커밋), 값은 `env/<env>.local.properties`(커밋 금지)에 넣는다.
3. 앱 이름·ID 접미사를 바꿨으면 `./gradlew generateEnvConfig` 로 iOS xcconfig 를 다시 만들고 커밋한다.
4. `scripts/orca.sh check` (dev), `scripts/orca.sh check --env prod` 등으로 확인한다.

- 세 앱(`… Dev`, `… Stg`, 이름 그대로)을 한 기기에 함께 설치할 수 있다(Android applicationId·iOS 번들 ID 접미사).
- 네트워크 로그는 env 의 `network.log` 로 켠다(Authorization 은 가려진다). prod 는 false.
- 함께 볼 키: `auth.refreshPath`(401 재발급, 비우면 바로 로그아웃), `url.terms`·`url.privacy`(설정 화면 웹뷰), `update.checkPath`·`store.appStoreId`(업데이트 안내), `log.level`·`log.console` — [environments.md](environments.md) "앱 동작 키"
- 서버 없이 로컬 목업으로 돌리려면 `env/dev.local.properties` 에 `api.baseUrl=http://127.0.0.1:<port>` ([environments.md](environments.md))
- 서버 계약(로그인·회원가입·토큰 만료·푸시 토큰)은 **가정**이다(D6, DEC-055). 서버가 정해지면 아래 위치만 바꾼다.

| 계약 | 지금 가정 | 인터페이스(호출) | DTO(요청·응답 모델) | 목업·테스트 대역 |
|---|---|---|---|---|
| 로그인 | `POST /api/auth/login` `{id, password}` → `accessToken`/`token`, `refreshToken`, `user{id, displayName, username, email}`, `displayName` | `feature/auth/.../login/LoginApi.kt` (`LoginApi`, `RemoteLoginApi`) | `login/LoginRequest.kt`, `login/LoginResponse.kt`, 응답→세션 변환은 `login/LoginRepository.kt` 의 `authenticate()` | `feature/auth/src/commonTest/.../Fakes.kt` 의 `FakeLoginApi` (서버 없이 실행하려면 `di/AuthModule.kt` 에서 `single<LoginApi> { … }` 교체) |
| 소셜 로그인 | `POST /api/auth/social` `{provider, token, authorizationCode?, nonce?}` → `accessToken`, `refreshToken`, `user{id, displayName, email}`, `isNewUser` | `feature/auth/.../social/SocialLoginApi.kt` (`SocialLoginApi`, `RemoteSocialLoginApi`) | `social/SocialLoginApi.kt`, 응답→세션은 `social/SocialLoginRepository.kt` | `Fakes.kt` 의 `FakeSocialLoginApi`, 로컬 목업 서버(token `reject` → 401, `new…` → 신규 사용자) |
| 회원가입 | `POST /api/signup` `{username, password, passwordConfirmation}` → `{success, message}` | `signup/SignupApi.kt` | `signup/SignupRequest.kt`, `signup/SignupResponse.kt` | `SignupRepositoryTest`·`SignupViewModelTest` 의 익명 `SignupApi` |
| 토큰 만료·재발급 | 401 → `POST {auth.refreshPath} {refreshToken}` → `{accessToken\|token, refreshToken?}` → 원래 요청 1회 재시도. 동시 401 은 재발급 1회. 거절(4xx)·토큰 없음·재시도도 401 → 세션 만료, 네트워크·5xx 는 세션 유지. `auth.refreshPath` 를 비우면 재발급 없이 바로 만료 (DEC-045) | `core/data/.../session/TokenRefresher.kt` (`TokenRefreshApi`, `RemoteTokenRefreshApi`, `TokenRefresher`), 재시도는 `network/BaseApi.kt`, 경로는 env `auth.refreshPath` | 같은 파일의 `TokenRefreshRequest`, `TokenRefreshResponse` | `TokenRefresherTest`, `BaseApiTest` |
| 목록(예시) | `GET /api/example?page=N` → `{items: [{id, title, subtitle?}], nextPage}` (nextPage 없으면 마지막) | `feature/example/.../ExampleApi.kt` (`RemoteExampleApi`, 기본은 `SampleExampleApi`) | 같은 파일의 `ExamplePageResponse` → `Page<T>` | `ExampleViewModelTest` 의 `FakeApi` |
| 로그아웃 API | 서버 호출 없음(로컬 삭제만) | `core/data/.../session/SessionRepository.kt` 의 `logout()` (필요하면 `SessionListener` 로 추가) | — | `SessionRepositoryTest` |
| 푸시 토큰 | `POST /api/push/tokens {token, platform, provider}`, `DELETE /api/push/tokens/{token}` | `core/data/.../push/PushTokenApi.kt` (`PushTokenApi`, `RemotePushTokenApi`) | 같은 파일의 `PushTokenRequest` | `PushRepositoryTest` 의 `RecordingPushApi` |
| 푸시 data | `link` 또는 `screen`+params, `title`, `body` | `core/data/.../push/PushMessage.kt` (`PushMessageParser`) | — (문자열 Map) | `PushMessageParserTest` |
| 버전 확인(업데이트) | `GET {update.checkPath}?platform=android\|ios` → `{minimumVersion?, latestVersion?, storeUrl?}` ([release.md](release.md) §5) | `core/data/.../update/AppUpdate.kt` (`AppVersionApi`, `RemoteAppVersionApi`) | 같은 파일의 `AppVersionResponse` | `AppUpdateManagerTest`, 목업 `SampleAppVersionApi` |

바꾸는 순서: DTO 필드 → Remote 구현의 endpoint → Repository 변환 로직 → 해당 테스트 기대값 → `scripts/orca.sh check`.
플랫폼 코드·Swift 는 바뀌지 않는다(서버 계약은 KMP 안에서 끝난다).

## 3. Firebase (푸시) 설정

같은 설정 파일로 크래시(Crashlytics)·이벤트(Analytics)도 켜진다. Firebase 를 안 쓰면 [push.md](push.md) "Firebase 를 쓰지 않는 앱".

푸시는 설정 파일이 **없어도 빌드·실행된다**(푸시만 꺼진 상태). 설정 파일은 커밋하지 않는다(`.gitignore`).
자세한 구조와 동작은 [push.md](push.md) (Phase 5).

1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트를 만든다.
2. Android 앱 추가 — 환경마다 등록한다(`com.acme.shop.dev`, `com.acme.shop.staging`, `com.acme.shop`) → 환경별 `google-services.json` 을 `androidApp/src/<env>/google-services.json` 에 둔다(dev·staging·prod). 한 파일에 세 앱을 모두 넣었다면 `androidApp/google-services.json` 하나로도 된다. 파일이 없는 환경은 푸시만 꺼진다.
3. iOS 앱 추가 — 환경마다 번들 ID(`…​.dev`, `…​.staging`, 접미사 없음) → `iosApp/Configuration/Firebase/<env>/GoogleService-Info.plist` 에 둔다. 빌드 단계가 현재 Build Configuration 의 환경에 맞는 파일을 앱에 넣는다.
4. APNs 인증 키: Apple Developer → Certificates, Identifiers & Profiles → Keys → **Apple Push Notifications service (APNs)** 키(.p8) 생성 → Firebase 프로젝트 설정 → Cloud Messaging → Apple 앱 구성에 키 ID·Team ID 와 함께 업로드.
5. Xcode: 타깃 → Signing & Capabilities → **Push Notifications**, **Background Modes › Remote notifications** 추가.
6. CI 에서 푸시까지 빌드하려면 두 파일을 저장소 비밀 변수(base64)로 넣고 빌드 전에 복원한다(선택).

FCM 대신 APNs 를 직접 쓰려면 iOS 의 `PushProvider` 구현체만 바꾼다([push.md](push.md)).

## 3-1. 소셜 로그인 (카카오·구글·애플·네이버, DEC-105~119)

키를 넣은 제공자만 로그인 화면에 버튼이 생긴다. 키는 환경마다 `env/<env>.local.properties`(커밋 금지, 목록은 `.example`)에 넣는다.
dev 는 `social.mock=true` 라 키 없이도 버튼이 모두 보이고 로컬 목업 서버와 흐름을 확인할 수 있다(실제 SDK 는 부르지 않음). 키를 넣고 실제 SDK 로 확인할 때는 dev 의 `social.mock` 을 `false` 로 바꾼다.

| 제공자 | env 키 | 콘솔에서 할 일 |
|---|---|---|
| 카카오 | `social.kakao.appKey`(네이티브 앱 키) | [Kakao Developers](https://developers.kakao.com) 앱 → 플랫폼 Android: 패키지명(환경별 `…​.dev` 등)·**키 해시**(디버그·릴리스 서명 각각), iOS: 번들 ID. 카카오 로그인 활성화. Android 리다이렉트 Activity 는 키가 있으면 빌드가 켠다 |
| 구글 | `social.google.webClientId`(Android, **웹** 클라이언트 ID), `social.google.iosClientId` | [Google Cloud Console](https://console.cloud.google.com/apis/credentials) → OAuth 클라이언트: 웹(서버가 ID 토큰 검증에 쓰는 값을 Android 에 넣는다), Android(패키지명 + 서명 **SHA-1**, 환경별), iOS(번들 ID) |
| 네이버 | `social.naver.clientId`, `social.naver.clientSecret` | [네이버 개발자센터](https://developers.naver.com/apps) → 애플리케이션 → 네이버 로그인, Android 패키지명·iOS URL Scheme 등록 |
| 애플 | `social.apple.enabled=true`(공개 값, iOS 만) | Apple Developer → Identifier 에 **Sign in with Apple**, Xcode Signing & Capabilities 에 추가 |

- **로고**: 템플릿에는 각 사 로고를 넣지 않았다(DEC-119). 각 사 가이드에서 공식 로고를 받아 Android `res/drawable/social_logo_<provider>` 로 넣고 `ElsaSocialButton.kt` 의 `SocialBrandLogo` 에서 연결한다. 버튼 색·문구는 가이드대로 되어 있다 — [카카오](https://developers.kakao.com/docs/latest/ko/kakaologin/design-guide), [구글](https://developers.google.com/identity/branding-guidelines), [네이버](https://developers.naver.com/docs/login/bi/bi.md), [애플](https://developer.apple.com/design/human-interface-guidelines/sign-in-with-apple)
- **서버**: 서버가 각 사 토큰을 검증하고 우리 토큰을 돌려준다(가정 계약 `POST /api/auth/social` — 위 표). 구글·애플은 ID 토큰과 nonce, 카카오·네이버는 access token 을 보낸다
- **iOS 설정**: 키를 넣고 `./gradlew generateEnvConfig`(빌드 때도 돈다)를 하면 `iosApp/Configuration/Social.local.generated.xcconfig`(커밋 안 함)가 생기고 Info.plist 의 URL 스킴이 채워진다 — 카카오 `kakao<앱 키>`, 구글 역순 클라이언트 ID(`com.googleusercontent.apps.…`), 네이버 `naverlogin-<번들 ID>`(네이버 콘솔의 iOS URL Scheme 에 이 값을 넣는다). 애플은 Xcode → Signing & Capabilities → **Sign in with Apple** 추가(템플릿은 entitlement 를 켜 두지 않았다 — 팀 서명이 필요하다)
- **처음 가입한 소셜 사용자**: 서버가 `isNewUser=true` 를 주면 로그인 대신 약관 동의 화면이 열린다(같은 약관 화면, 회원가입과 같은 필수·선택). 동의하면 `POST /api/auth/social/agreements` 후 메인, 뒤로 가면 로그아웃하고 로그인으로
- **탈퇴**: 소셜 사용자는 비밀번호 칸 없이 확인 체크만 받는다. 서버 탈퇴 뒤 SDK 연결을 끊는다(애플은 서버가 토큰 폐기) — 가정은 [known-limitations.md](known-limitations.md) §1 #6·#7
- **목업**: dev 목업 서버는 네이버를 처음 가입한 사용자로 응답한다(동의 흐름 확인용, UI 테스트 `test13c`)
- 쓰지 않는 제공자는 키를 비워 두면 된다. 소셜 로그인을 통째로 빼려면 키를 모두 비우고(버튼 없음) Android `build.gradle.kts` 의 소셜 SDK 의존성과 `social/` 패키지를 지운다

## 4. 디자인

- 색·타이포·간격·radius 원본: `design/tokens.json` → `./gradlew generateDesignTokens` 로 Kotlin/Swift/XML 생성(생성물 커밋). 규칙은 [design/README.md](../design/README.md).
- 스타일 방향(큰 제목, 넓은 여백, 채움형 입력칸, 무테 카드, 하단 고정 CTA, 바텀시트)은 유지하고 **자체 팔레트**로 바꾼다.

## 5. 예시 기능 정리

`:feature:example` 은 `newFeature --tab` 과 같은 모양의 기준 예시(목록 패턴, "예시" 탭)다. 참고용으로 남겨도 되고, 지우려면
(2026-09-30 renameApp 직후 상태에서 이 순서로 지우고 check 통과 확인):

1. 삭제: `feature/example/`, `androidApp/.../screen/example/`, `iosApp/iosApp/view/example/`
2. KMP 등록: `settings.gradle.kts`(include), `shared/build.gradle.kts`(exported), `AppKoin.kt`(import·`exampleModule`), `IOSAppGraph.kt`(import·`exampleViewModel()`)
3. Android: `AndroidRoute.kt`(Example), `MainTab.kt`(Example 탭), `AndroidNavHost.kt`(import·`composable<AndroidRoute.Example>`·홈의 `onExampleClick`), `AndroidNavigator.kt`(`navigateToExample`), `DeepLinkRouter.kt`("example"), `HomeScreen.kt`(`onExampleClick` 파라미터·예시 `ElsaMenuCard`)
4. iOS: `main.tab.swift`(`case example`·title·icon), `app.navigation.swift`(`root(for:)` 의 `.example`·홈의 `onExampleTap`), `deeplink.router.swift`("example"), `home.screen.swift`(`onExampleTap`·예시 `ElsaMenuCard`)
5. 문자열: `example_*`, `home_menu_example*`, `tab_example` 키를 `strings.xml`(ko/en)·`Localizable.xcstrings` 에서 삭제
6. `scripts/orca.sh check` — 누락이 있으면 `./gradlew templateCheck`(아키텍처·문자열)·컴파일 오류가 알려 준다

## 5-1. 메시지 기능 정리 (`:feature:chat`, 19)

쓰지 않으면 `:feature:example` 과 같은 순서로 지운다: `feature/chat/`, `androidApp/.../screen/chat/`, `iosApp/iosApp/view/chat/`·`iosApp/iosApp/attachment/`,
KMP 등록(`settings.gradle.kts`·`shared/build.gradle.kts`·`AppKoin.kt`·`IOSAppGraph.kt` 의 chat 두 줄 — 로컬 DB `chat.db` 는 모듈과 같이 사라진다), Android `AndroidRoute`(Chat·ChatRoom)·`MainTab.Chat`·`AndroidNavHost`·`AndroidNavigator`(`navigateToChat`·`navigateToChatRoom`·`navigateToDirectChat` — 프로필의 "메시지 보내기"도 같이 뺀다)·`DeepLinkRouter`("chat"),
iOS `main.tab.swift`(`.chat`)·`app.route.swift`(`.chatRoom`·`.directChat`)·`app.navigation.swift`·`deeplink.router.swift`, 문자열 `chat_*`·`tab_chat`(`attach_*` 는 갤러리도 쓴다), Android 매니페스트 FileProvider·`res/xml/file_paths.xml`(카메라 첨부 전용), env `chat.websocketUrl`.
서버 연동: `ChatModule.kt` 에서 `RemoteChatApi` + `myId` 를 로그인 사용자로(주석 참고), 계약은 [known-limitations.md](known-limitations.md) §1 #8.

## 5-2. 커뮤니티 기능 정리 (`:feature:community`, 20)

쓰지 않으면 메시지와 같은 순서로 지운다: `feature/community/`, `androidApp/.../screen/community/`·`screen/blocked/`, `iosApp/iosApp/view/community/`·`view/blocked/`,
KMP 등록(`settings.gradle.kts`·`shared/build.gradle.kts`·`AppKoin.kt`·`IOSAppGraph.kt` 의 community 줄), Android `AndroidRoute`(Community·PostDetail·PostEditor·BlockedUsers·UserProfile)·`MainTab.Community`·`AndroidNavHost`·`AndroidNavigator`·`DeepLinkRouter`("community"),
iOS `main.tab.swift`·`app.route.swift`(`.postDetail`·`.postEditor`·`.blockedUsers`·`.userProfile`), 화면 `screen/user/`·`view/user/`·`app.navigation.swift`·`deeplink.router.swift`, 설정의 "차단한 사용자" 행(두 플랫폼), 문자열 `community_*`·`blocked_*`·`settings_blocked_users`·`tab_community`.
메시지와 커뮤니티를 모두 지우면 `attachment/`(두 플랫폼)·core `media/AttachmentInput` 도 지울 수 있다.
서버 연동: `CommunityModule.kt` 에서 `RemoteCommunityApi`, 계약은 [known-limitations.md](known-limitations.md) §1 #9. 앱을 내기 전 App Store 1.2(사용자 콘텐츠)를 확인한다.

## 6. 첫 기능 추가

```bash
./gradlew -p build-logic :tooling:run --args="newFeature notice --title-ko 공지사항 --title-en Notices"      # 탭 위에 여는 화면
./gradlew -p build-logic :tooling:run --args="newFeature board --title-ko 게시판 --title-en Board --tab"    # 하단 탭
scripts/orca.sh check
```

생성 직후 서버 없이(Sample Api) 목록 화면(당겨서 새로고침·다음 페이지)이 동작한다. 진입점 연결, 서버 연동(Remote Api 로 교체), 화면 다듬기는 [conventions.md](conventions.md) §4.

## 7. 확인

- `scripts/orca.sh check [--env dev|staging|prod]` — 디자인 토큰·문자열 키·아키텍처·환경 설정 + 전 모듈 테스트 + Android 빌드 (mac 이면 iOS 빌드까지)
- mac: Xcode 에서 `iosApp-dev` / `iosApp-staging` / `iosApp-prod` scheme 실행, 라이트/다크·en 전환
- CI 는 켜져 있지 않다. GitHub Actions 예시와 켜는 방법은 [ci.md](ci.md)
- 릴리스(서명·환경별 빌드·버전 올리기)는 [release.md](release.md)
