# Phase 5 — Push 설계 (DEC-048)

제공자 없이 동작하는 공통 뼈대 + FCM 기본 제공자. 설정 파일이 없으면 NoOp 으로 빌드·실행.

## 흐름

```text
[플랫폼 SDK]                          [KMP core:data/push]                        [플랫폼 UI]
FCM onNewToken ───────────▶ PushManager.onNewToken ─▶ PushRepository(저장·로그인 중이면 서버 등록)
알림 탭(data) ────────────▶ PushManager.onNotificationOpened ─▶ PushMessageParser ─▶ pendingDeepLink(StateFlow)
                                                                                   └▶ 루트가 관찰 → DeepLink → Route 이동 → consumeDeepLink()
권한 결과 ────────────────▶ PushManager.onPermissionChanged ─▶ permission(StateFlow)
SessionRepository.start/restore ─▶ SessionListener.onSessionStarted  → 저장된 토큰 서버 등록
SessionRepository.logout ────────▶ SessionListener.onSessionEnding   → 서버 해제 + PushProvider.deleteToken()
```

- 서버 계약(가정, D6 와 함께 확인): `POST /api/push/tokens {token, platform, provider}`, `DELETE /api/push/tokens/{token}`. 실패해도 앱 흐름은 막지 않는다.
- DeepLink: `scheme://path?x=y` 또는 `https://host/path?x=y` → `DeepLink(path, params)`. 푸시 data 는 `link`(URI) 우선, 없으면 `screen` + 나머지 키.
- 플랫폼 라우터: Android `navigation/DeepLinkRouter.kt`, iOS `navigation/deeplink.router.swift` — path → Route 표. `new_feature.py` 가 새 feature path 를 추가.

## 공개 API (추가만, 기존 시그니처 변경 없음)

| Kotlin | 모듈 | Swift(SKIE) 노출 |
|---|---|---|
| `data class DeepLink(path, params)` + `DeepLink.parse(uri)` | core:common `navigation/` | `DeepLink`, `DeepLink.companion.parse(uri:)`, `params: [String: String]` |
| `interface PushProvider { id; isEnabled; deleteToken() }` | core:data `push/` | Swift 프로토콜 `PushProvider` — iOS `FcmPushProvider: NSObject, PushProvider` 가 구현 |
| `object NoOpPushProvider` | core:data | `NoOpPushProvider.shared` |
| `enum class PushPermission { NotDetermined, Granted, Denied }` | core:data | Swift enum `.notDetermined/.granted/.denied` |
| `class PushManager` — `onNewToken(token)`, `onNotificationOpened(data)`, `onPermissionChanged(permission)`, `pendingDeepLink: StateFlow<DeepLink?>`, `permission: StateFlow<PushPermission>`, `consumeDeepLink()`, `provider` | core:data | 비 suspend 함수(내부 앱 스코프에서 실행) → Swift 에서 바로 호출. `pendingDeepLink` 은 `SkieSwiftOptionalStateFlow<DeepLink>` |
| `data class PushMessage` + `PushMessageParser.parse(data, title, body)` | core:data | Swift 에서 직접 쓰지 않음 |
| `interface SessionListener` + `SessionRepository(…, listeners = emptyList())` | core:data | 없음 (생성자 기본값 추가) |
| `initKoin(apiConfig, pushProvider = NoOpPushProvider, …)`, `initAndroidKoin(…, pushProvider = NoOpPushProvider, …)` | shared | — |
| `IOSAppKoin.initialize(appEnv, pushProvider)` (기존 `initialize(appEnv)` 유지), `IOSAppGraph.pushManager()` | shared | `IOSAppKoin.shared.initialize(appEnv:pushProvider:)`, `IOSAppGraph.shared.pushManager()` |

## 플랫폼

| | Android | iOS |
|---|---|---|
| SDK | `firebase-bom` + `firebase-messaging`, `com.google.gms.google-services` 는 `androidApp/google-services.json` 이 있을 때만 적용 | `firebase-ios-sdk`(SPM, FirebaseMessaging), 코드는 `#if canImport(FirebaseMessaging)` |
| 켜짐 조건 | `FirebaseApp.getApps(context)` 가 비어 있지 않음 | `GoogleService-Info.plist` 가 번들에 있음 → `FirebaseApp.configure()` |
| 없을 때 | `NoOpPushProvider` | `NoOpPushProvider.shared` |
| 권한 | Android 13+ `POST_NOTIFICATIONS` — 메인 진입 시 1회 요청 | `UNUserNotificationCenter.requestAuthorization` — 메인 진입 시 |
| 채널/표시 | 기본 채널 `default` (ElsaApplication), foreground 는 서비스가 직접 알림 표시 | `willPresent` → banner·list·sound |
| 탭 | 알림 PendingIntent/시스템 알림 extras → MainActivity → `onNotificationOpened` | `didReceive response` → `onNotificationOpened` |
