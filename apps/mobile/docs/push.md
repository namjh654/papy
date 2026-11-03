# 푸시

기본 제공자는 **FCM**(Android·iOS 모두)이다. Firebase 설정 파일이 **없으면 푸시만 꺼진 상태(NoOp)로 빌드·실행된다** — 템플릿을 받은 직후에도 앱은 정상이다.
설정 절차(파일 위치, APNs 키)는 [template-setup.md §3](template-setup.md#3-firebase-푸시-설정). 결정: DEC-048, DEC-053.

## 구조

```text
                 KMP  core:data/push  (공통, 플랫폼 SDK 모름)
 PushProvider ◀── 플랫폼 구현: Android FcmPushProvider / iOS FcmPushProvider (없으면 NoOpPushProvider)
 PushManager      onNewToken · onNotificationOpened · onPermissionChanged · pendingDeepLink · consumeDeepLink
 PushRepository   토큰 저장, 로그인·세션 복원 때 서버 등록, 로그아웃 직전 서버 해제 + 제공자 토큰 삭제 (SessionListener)
 PushMessageParser  data → 제목·본문·DeepLink
                 KMP  core:common/navigation
 DeepLink(path, params) + parse(uri)
                 플랫폼
 DeepLinkRouter   path → 화면 (Android navigation/DeepLinkRouter.kt, iOS navigation/deeplink.router.swift — 같은 path 표)
```

| 단계 | Android | iOS |
|---|---|---|
| 제공자 선택 | `ElsaApplication`: `FcmPushProvider.createOrNoOp(this)` → `initAndroidKoin(pushProvider = …)` | `iOSApp.init`: `PushSetup.makeProvider()` → `IOSAppKoin.initialize(appEnv:pushProvider:)` |
| 켜짐 조건 | `androidApp/src/<env>/google-services.json`(또는 공통 `androidApp/google-services.json`)이 있으면 google-services 플러그인 적용 → Firebase 초기화. 파일이 없는 flavor 는 경고만 | Firebase SDK(SPM `FirebaseMessaging`) 링크 + `iosApp/Configuration/Firebase/<env>/GoogleService-Info.plist` 를 빌드 단계가 번들에 복사 |
| 토큰 | `FirebaseMessaging.token` + `ElsaMessagingService.onNewToken` → `PushManager.onNewToken` | `MessagingDelegate.didReceiveRegistrationToken` → `onNewToken` |
| 권한 | 로그인 후 `PushEffects` 가 `POST_NOTIFICATIONS` 요청(13+) | 메인 진입 시 `PushObserver.requestPermissionIfNeeded()` → `registerForRemoteNotifications` |
| 표시 | 채널 `default`(`NotificationChannels`). foreground·data-only 는 `ElsaMessagingService` 가 직접 표시 | `AppDelegate.willPresent` → 배너·목록·소리 |
| 탭 | `MainActivity`(singleTop) extras → `onNotificationOpened` | `AppDelegate.didReceive` → `onNotificationOpened` |
| 이동 | `PushEffects` 가 메인 화면에 들어온 뒤 `navController.navigate(deepLink)` | `AppNavigation.openPendingDeepLink()` |

앱이 꺼져 있다가 알림으로 켜지면 DeepLink 는 `pendingDeepLink` 에 남아 있다가 스플래시·로그인을 지나 메인에 들어올 때 열린다.

## 서버와 맞출 계약

토큰 등록 API (가정 — 서버와 정한 뒤 `core/data/.../push/PushTokenApi.kt` 의 endpoint 만 바꾼다):

```http
POST   /api/push/tokens          {"token": "...", "platform": "android|ios", "provider": "fcm"}
DELETE /api/push/tokens/{token}
```

실패해도 로그인·로그아웃은 진행된다(다음 로그인·토큰 갱신 때 다시 등록).

푸시 data (FCM `data` 필드, 모두 문자열):

| 키 | 의미 | 예 |
|---|---|---|
| `link` | 딥링크 URI (가장 우선) | `elsa://example?id=1`, `https://app.acme.com/example?id=1` |
| `screen` | 화면 path, 나머지 키는 params | `{"screen": "example", "id": "1"}` |
| `title`, `body` | data-only 메시지의 표시 문구 | |

path 는 양 플랫폼 `DeepLinkRouter` 표에 있어야 이동한다: `home`, `settings`, `example`(탭 전환), `gallery`, 그리고 `newFeature` 로 만든 feature 이름.
상세 화면은 `<path>/<id>`: `notice/{id}`, `example/{id}`, `chat/{id}`(대화방 — 이름은 앱이 불러온다), `community/{id}`(글 상세) — 예) `elsa://chat/c1`, `{"link": "elsa://community/p3"}` (21-3)

### 서버가 정해지면 바꿀 위치

모든 서버 계약(로그인·재발급·목록·푸시 토큰·푸시 data·버전 확인)의 가정과 바꿀 위치는 한 표로 관리한다 →
[template-setup.md §2 "서버 계약"](template-setup.md#2-api-url--환경-dev--staging--prod). 플랫폼 코드·Swift 는 바뀌지 않는다.

## 제공자 바꾸기 (예: iOS 에서 APNs 직접)

`PushProvider`(`id`, `isEnabled`, `deleteToken()`)를 구현한 클래스를 만들고 제공자 선택 한 곳만 바꾼다.
- iOS: `PushSetup.makeProvider()` 에서 새 클래스를 돌려주고, `AppDelegate` 의 `didRegisterForRemoteNotificationsWithDeviceToken` 에서 토큰(hex)을 `IOSAppGraph.shared.pushManager().onNewToken(token:)` 으로 넘긴다. 서버는 `provider = "apns"` 로 구분한다.
- Android: `FcmPushProvider.createOrNoOp` 대신 새 구현을 `initAndroidKoin(pushProvider = …)` 로 넘긴다.

KMP 쪽(토큰 등록·세션 연동·DeepLink)은 그대로다.

## Firebase 를 쓰지 않는 앱 (패키지 빼기, DEC-053)

설정 파일이 없으면 이미 NoOp 로 동작하지만 SDK 는 앱에 들어간다. 앱 크기·빌드 시간을 줄이려면 뺀다.
푸시·크래시(Crashlytics)·이벤트(Analytics)가 같은 Firebase 를 쓰므로 함께 빠진다(모두 NoOp 로 대체, [release.md](release.md) §4).

**iOS (SPM firebase-ios-sdk)**
1. Xcode → 프로젝트 `iosApp` → **Package Dependencies** → `firebase-ios-sdk` 선택 → `−` (Remove)
2. 타깃 `iosApp` → **General › Frameworks, Libraries, and Embedded Content** 에 `FirebaseMessaging`·`FirebaseCrashlytics`·`FirebaseAnalytics` 가 남아 있으면 삭제
3. 타깃 → **Build Phases** → Run Script `Copy GoogleService-Info.plist` 삭제(선택 — 남겨도 plist 가 없으면 아무것도 안 한다)
4. Swift 코드는 고치지 않는다: `app/platform.setup.swift`·`push/push.setup.swift`·`monitoring/firebase.monitoring.swift` 가 `#if canImport(Firebase…)` 로 감싸 있어 SDK 가 없으면 NoOp 로 컴파일된다
5. `Package.resolved`(`iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/`)의 변경을 커밋 → `scripts/orca.sh check`(mac)
6. APNs 직접 등 다른 제공자를 쓸 거면 위 "제공자 바꾸기"

**Android**
1. `androidApp/build.gradle.kts` 에서 `firebase-bom`·`firebase-messaging`·`firebase-crashlytics`·`firebase-analytics` 의존성과 google-services·crashlytics 조건부 적용 블록 삭제, 루트 `build.gradle.kts`·`gradle/libs.versions.toml` 의 firebase·google-services·crashlytics 항목 삭제
2. `push/FcmPushProvider.kt`·`push/ElsaMessagingService.kt`·`monitoring/FirebaseMonitoring.kt` 삭제, `AndroidManifest.xml` 의 `ElsaMessagingService`·`com.google.firebase.messaging.*` meta-data 삭제
3. `ElsaApplication` 의 `PlatformServices(…)` 에서 pushProvider·crashReporter·analytics 를 빼고(기본 NoOp) `inAppReview` 만 남긴다
4. `scripts/orca.sh check`

KMP(`core:data` push 패키지)는 그대로 둔다 — 제공자가 없으면 토큰 등록을 하지 않는다.

## 확인

- 설정 파일 없이: 앱 실행·로그인·로그아웃 정상, 로그에 `google-services.json 없음 → 푸시 비활성` (Gradle)
- 설정 파일 있을 때: 로그인 → 권한 허용 → dev 로그에 `POST /api/push/tokens` → Firebase 콘솔 "테스트 메시지"로 토큰에 발송 → 앱 종료·백그라운드·foreground 각각에서 표시·탭 이동
- 테스트: `core:common` DeepLinkTest, `core:data` PushRepositoryTest·PushMessageParserTest·PushManagerTest
