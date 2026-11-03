# 릴리스 — 버전 · 서명 · 환경별 빌드 · 운영 도구

> 환경(dev/staging/prod)은 [environments.md](environments.md), 푸시·Firebase 는 [push.md](push.md).

## 1. 버전 올리기 (한 곳)

`env/common.properties` 만 고친다. Android·iOS·KMP 가 모두 여기서 읽는다.

```properties
app.versionName=1.1.0   # 사용자에게 보이는 버전(semver). Android versionName · iOS MARKETING_VERSION · AppConfig.APP_VERSION_NAME
app.versionCode=2       # 스토어 빌드 번호. 올릴 때마다 +1. Android versionCode · iOS CURRENT_PROJECT_VERSION
```

1. 두 값을 올린다(스토어는 같은 versionCode 를 다시 받지 않는다)
2. `./gradlew generateEnvConfig` → `iosApp/Configuration/Env.generated.xcconfig` 가 바뀌면 함께 커밋
3. `scripts/orca.sh check --env prod` → 커밋 → 태그 `vX.Y.Z`
- dev·staging Android 앱은 versionName 에 `-dev`/`-staging` 이 붙는다(설정 화면 앱 버전에서 보인다)
- 강제·선택 업데이트 판단(§5)은 versionName 을 숫자로 비교한다

## 2. 서명

**Android** — 아래 둘 중 하나가 있으면 release 에 서명한다. 없으면 unsigned 로 빌드된다(CI 기본).

| 방법 | 값 |
|---|---|
| 파일(로컬) | `env/release-signing.local.properties` (`.example` 복사, 커밋 금지): `storeFile`, `storePassword`, `keyAlias`, `keyPassword` |
| 환경변수(CI) | `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` |

키스토어 만들기: `keytool -genkeypair -v -keystore release.jks -alias release -keyalg RSA -keysize 4096 -validity 10000`.
Play 앱 서명(Play App Signing)을 쓰면 이 키는 업로드 키다. 키 파일·비밀번호는 저장소 밖에 보관한다.

**iOS** — `iosApp/Configuration/Config.xcconfig` 의 `TEAM_ID` 를 채우고 Xcode › Signing & Capabilities 에서 Automatically manage signing
(또는 CI 에서 App Store Connect API 키 + `-allowProvisioningUpdates`). 번들 ID 는 `PRODUCT_BUNDLE_IDENTIFIER` + 환경 접미사(prod 는 없음).

## 3. 환경별 릴리스 빌드

| 대상 | 명령 | 결과물 |
|---|---|---|
| Android prod (스토어) | `./gradlew -Papp.env=prod :androidApp:bundleProdRelease` | `androidApp/build/outputs/bundle/prodRelease/*.aab` |
| Android prod APK | `./gradlew -Papp.env=prod :androidApp:assembleProdRelease` | `androidApp/build/outputs/apk/prod/release/` |
| Android staging (내부 배포) | `./gradlew -Papp.env=staging :androidApp:assembleStagingRelease` | `…/apk/staging/release/` |
| iOS prod | `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp-prod -configuration Release-prod -destination 'generic/platform=iOS' -archivePath build/iosApp-prod.xcarchive archive` → `xcodebuild -exportArchive -archivePath build/iosApp-prod.xcarchive -exportOptionsPlist ExportOptions.plist -exportPath build/ipa` | `.ipa` |
| iOS staging | scheme `iosApp-staging`, configuration `Release-staging` | |

- **한 빌드에 한 환경**: KMP `AppConfig` 는 빌드 환경 하나만 가진다. Android 는 `-Papp.env` 와 flavor 가 다르면 빌드가 실패하고, iOS 는 scheme 의 Build Configuration(`*-<env>`)이 정한다
- R8(난독화·축소)·Log.v/d/i 제거는 **prodRelease 에만** 켜진다. 규칙은 `androidApp/proguard-rules.pro`
- Xcode 에서 Archive 는 Product › Scheme 을 `iosApp-prod` 로 두고 Product › Archive (기본 Archive 구성 Release-prod)
- `ExportOptions.plist` 는 배포 방식(app-store / ad-hoc)마다 다르므로 저장소에 두지 않는다 — Xcode Organizer 로 한 번 export 하면 만들어진다

## 4. 로그 · 크래시 · 이벤트

| env | log.level | log.console | 결과 |
|---|---|---|---|
| dev | debug | true | Logcat/Xcode 에 debug 이상, 경고 이상은 크래시 리포터 기록에도 |
| staging | info | true | info 이상 |
| prod | warn | false | 콘솔 없음 — warn 이상은 크래시 리포터(Crashlytics)로만, error+예외는 non-fatal 보고 |

- 코드: `private val log = Logger.withTag("Login")` → `log.d { "…" }`, `log.e(throwable) { "…" }` (Kermit, 모든 모듈)
- 네트워크 로그는 env `network.log` 가 true 일 때만(tag `Http`, debug). Authorization 은 `***`
- 크래시·이벤트는 Firebase 설정 파일이 있을 때만 켜진다(없으면 NoOp, 빌드·실행 같음). 이벤트: ViewModel 에서 `AnalyticsTracker.logEvent("snake_case", mapOf(…))`
- **Crashlytics 심볼**: Android 는 설정 파일이 있으면 crashlytics Gradle 플러그인이 release 매핑을 올린다. iOS 는 dSYM 업로드 Run Script 를 추가한다(Xcode › Build Phases › `"${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run"`, Input Files 에 dSYM 경로 — Firebase 문서). Kotlin 예외는 iOS 에서 클래스 이름·메시지만 보인다(스택은 Kotlin 쪽 로그로)
- 로그인 사용자 id 는 자동으로 크래시·이벤트에 붙고 로그아웃하면 지워진다(core:data)

## 5. 강제·권장 업데이트·운영 공지 (서버 계약은 가정 — DEC-055·132)

`GET {update.checkPath}?platform=android|ios` → `{minimumVersion?, latestVersion?, storeUrl?, notice?: {id, message, title?, blocking}, features?: {키: Boolean}}`

버전은 `major.minor.patch`(env `app.versionName`). 버전 정책(DEC-132):
- 현재 < latestVersion 이고 **major·minor 가 다르면 → 강제**: 닫을 수 없는 안내, "업데이트"만 (예: 1.2.5 → 1.3.0)
- 현재 < latestVersion 이고 **patch 만 다르면 → 권장**: "업데이트"/"나중에" — 나중에 한 그 버전은 다시 묻지 않는다 (예: 1.2.0 → 1.2.3)
- 현재 < minimumVersion → patch 차이여도 **강제** (긴급 수정을 꼭 받게 할 때)
- 그래서 새 기능 출시는 minor 를 올리고, 버그 수정만이면 patch 를 올린다
- **공지(`notice`)**: 업데이트 안내가 없을 때 보인다. `blocking: true` 면 점검 — 닫을 수 없다(서버가 점검을 끝내면 다음 실행부터 사라진다). 아니면 "확인"으로 닫고 같은 `id` 는 다시 보이지 않는다. 문구는 서버가 사용자 언어로 준다(제목이 없으면 앱 기본 제목)
- 확인 실패(네트워크·404)는 조용히 넘어간다. `update.checkPath` 를 비우면 확인하지 않는다
- "업데이트": Android 는 Play In-App Update(강제 IMMEDIATE, 선택 FLEXIBLE → 다운로드 후 바로 설치), Play 가 모르는 빌드면 스토어 페이지. iOS 는 `storeUrl` → 없으면 `https://apps.apple.com/app/id{store.appStoreId}`
- **기능 켜고 끄기(`features`, 28-2·DEC-135)**: 앱 업데이트 없이 기능을 끈다. 키는 `core/data/.../flags/FeatureFlags.kt` 의 `FeatureKey`(지금은 `chat_attachments` — 대화방 첨부 버튼). 마지막에 받은 값을 저장해 다음 실행·오프라인에서도 쓰고, 값이 없으면 키의 기본값. 새 키: `FeatureKey` 에 한 줄 → ViewModel 이 `featureFlags.observe(key)` 를 상태에 → 화면이 숨긴다
- 서버 없이 안내 화면 보기: `core/data/.../di/DataModule.kt` 에서 `single<AppVersionApi> { SampleAppVersionApi() }` (최신 99.0.0 → 강제 안내). 권장·공지는 `SampleAppVersionApi(AppVersionResponse(latestVersion = "<patch+1>", notice = AppNotice("n1", "…")))`
- 코드: `core/data/.../update/AppUpdate.kt`, Android `update/AppUpdateEffect.kt`, iOS `update/app.update.swift`

## 6. 인앱 리뷰

설정 › 앱 평가하기 → KMP `InAppReview.requestReview()` → Android Play In-App Review / iOS `AppStore.requestReview`.
- 표시 여부·횟수는 OS·스토어가 정한다. Android 는 Play 에서 설치한 앱(내부 테스트 트랙 포함)에서만, iOS 는 개발 빌드는 항상 뜨고 TestFlight 에서는 안 뜬다
- 다른 곳(예: 주문 완료 후)에서 부르려면 ViewModel 이 `InAppReview` 를 Koin 으로 받아 부른다. 자주 부르지 않는다

## 7. 릴리스 전 확인

- [ ] `env/common.properties` 버전 올림, `generateEnvConfig` 결과 커밋
- [ ] `scripts/orca.sh check --env prod` (mac 이면 iOS 포함), Android `bundleProdRelease`, iOS `Release-prod` Archive
- [ ] prod 설정 파일: `androidApp/src/prod/google-services.json`, `iosApp/Configuration/Firebase/prod/GoogleService-Info.plist` (커밋 안 함)
- [ ] 서명: Android 키(파일 또는 CI 비밀), iOS TEAM_ID·프로비저닝
- [ ] 서버: 최소·최신 버전 API 가 새 버전을 알고 있는지(강제 업데이트로 막히지 않는지)
- [ ] 태그 `vX.Y.Z` push
