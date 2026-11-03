# 환경 (dev / staging / prod)

환경별 값의 원본은 `env/` 폴더 하나다. 빌드가 여기서 **KMP 공통 `AppConfig`**, **Android flavor**, **iOS Build Configuration** 값을 만든다.
core 코드는 `AppConfig` 의 값(URL·플래그)만 쓰고 환경 이름으로 분기하지 않는다. 결정: DEC-058~061.

## 파일

| 파일 | 커밋 | 내용 |
|---|---|---|
| `env/common.properties` | O | 모든 환경 공통 공개 값 (`app.name`, 기본 플래그) |
| `env/<env>.properties` | O | 환경별 공개 값 — 공통 값을 덮어쓴다 (`api.baseUrl`, `network.log`, `app.nameSuffix`, `app.idSuffix`) |
| `env/<env>.local.properties.example` | O | 비밀 키 **목록**(값 비움) |
| `env/<env>.local.properties` | X (.gitignore) | 비밀 값 — `.example` 을 복사해 채운다 |
| `env/release-signing.local.properties` | X | Android 릴리스 서명 (`.example` 참고, 또는 환경변수) |

합치는 순서: `common` → `<env>` → `.example` 의 키(값 없으면 빈 문자열) → `<env>.local`.
**세 환경의 키 집합은 같아야 한다** — 다르면 `./gradlew templateCheck`(checkEnvConfig)가 실패한다(한 환경에만 있는 키는 다른 환경 빌드를 깬다).

필수 키: `app.name`, `app.nameSuffix`, `app.idSuffix`, `api.baseUrl`, `network.log`.

## 만들어지는 것

| 대상 | 결과 | 생성 |
|---|---|---|
| KMP | `core:data` 의 `AppConfig` 상수 (`API_BASE_URL`, `NETWORK_LOG`, `FEATURE_EXAMPLE`, `SECRET_…`, `ENVIRONMENT`) | 빌드할 때 자동 (`papy.appconfig`, build/ 아래 — 커밋 안 함) |
| Android | flavor `dev`·`staging`·`prod`: applicationId 접미사 `app.idSuffix`, 앱 이름 `app.name` + `app.nameSuffix` | 빌드할 때 자동 (`papy.android.environments`) |
| iOS | `iosApp/Configuration/Env.generated.xcconfig` (APP_ENV·APP_DISPLAY_NAME·BUNDLE_ID_SUFFIX 를 `[config=*-<env>]` 로) | `./gradlew generateEnvConfig` → **커밋** (최신 여부는 templateCheck 가 검사) |

키 이름 → 상수: `api.baseUrl` → `API_BASE_URL`, `feature.newHome` → `FEATURE_NEW_HOME`. 값 타입: `true/false` → Boolean, 정수 → Int, 그 밖 → String.

## 어떤 환경으로 빌드되나

KMP 공통 코드는 한 빌드에 한 환경만 가진다. 환경은 이 순서로 정해진다.

1. `-Papp.env=<env>` — `scripts/orca.sh check --env staging`, CI
2. 환경변수 `APP_ENV` — Xcode 빌드 단계(`Env.generated.xcconfig` 의 APP_ENV 가 스크립트에 전달됨)
3. 요청한 Android 태스크의 flavor — `assembleStagingDebug`, Android Studio 에서 `stagingDebug` 실행
4. 그 밖에는 `dev`

안전장치: 이번 빌드 환경과 다른 flavor 를 빌드하면(`./gradlew assemble` 처럼 전 flavor 를 한 번에, 또는 `-Papp.env=dev assembleStagingDebug`) 명확한 메시지로 실패한다. 환경별로 따로 실행한다.

```bash
scripts/orca.sh check                      # dev
scripts/orca.sh check --env staging        # staging (ORCA_ENV=staging 도 같음)
./gradlew :androidApp:assembleProdRelease  # prod 릴리스 (R8)
```

## 플랫폼별

| | Android | iOS |
|---|---|---|
| 이름 | flavor `dev` / `staging` / `prod` | Build Configuration `Debug-dev` … `Release-prod` (6개), scheme `iosApp-dev` / `iosApp-staging` / `iosApp-prod` |
| 동시 설치 | applicationId `…​.dev`, `…​.staging`, (prod 접미사 없음) | 번들 ID `…$(BUNDLE_ID_SUFFIX)` |
| 앱 이름 | `resValue app_name` = `Papy Dev` / `Papy Stg` / `Papy` | `CFBundleDisplayName = $(APP_DISPLAY_NAME)` |
| Firebase | `androidApp/src/<env>/google-services.json` (없는 flavor 는 경고 후 푸시 NoOp) | `iosApp/Configuration/Firebase/<env>/GoogleService-Info.plist` (빌드 단계가 복사, 없으면 NoOp) |
| 릴리스 | `prodRelease` 에만 R8·리소스 축소·`Log.v/d/i` 제거(`proguard-rules.pro`) | `Release-*` 는 Kotlin framework release 빌드 |
| 서명 | `env/release-signing.local.properties` 또는 `ANDROID_KEYSTORE_FILE`·`ANDROID_KEYSTORE_PASSWORD`·`ANDROID_KEY_ALIAS`·`ANDROID_KEY_PASSWORD` (없으면 unsigned) | Xcode Signing(TEAM_ID) |

## 자주 하는 일

**값 바꾸기** — `env/<env>.properties` 수정 → Android·KMP 는 다음 빌드에 반영. 앱 이름·ID 접미사를 바꿨으면 `./gradlew generateEnvConfig` 후 xcconfig 커밋.

**키(기능 플래그 등) 추가** — 세 환경 모두에 넣는다(공통이면 `common.properties` 한 곳). 코드에서 `AppConfig.FEATURE_X`. `./gradlew templateCheck`.

**비밀 값 넣기** — `env/<env>.local.properties.example` 에 키만 추가(커밋) → 각자 `env/<env>.local.properties` 에 값. CI 는 저장소 비밀 변수로 파일을 만들어 넣는다. 앱에 들어간 값은 추출될 수 있으니 서버 비밀(서명 키, 관리자 토큰)은 두지 않는다.

**환경 추가(예: qa)** — `EnvConfig.ENVIRONMENTS`(build-logic/tooling) 에 추가 → `env/qa.properties`·`.example` → `./gradlew generateEnvConfig` → iOS 에 `Debug-qa`/`Release-qa` Build Configuration 과 scheme 추가(Xcode) → CI 매트릭스 추가.

**로컬 목업 서버로 돌려 보기** — `env/dev.local.properties`(커밋 안 함)에 `api.baseUrl=http://127.0.0.1:8765` 를 넣으면 dev 빌드만 그 주소를 쓴다(local 이 마지막에 덮어씀).
목업 서버는 템플릿 도구로 띄운다: `./gradlew -p build-logic :tooling:run --args="mockServer [--port 8765]"` — 서버 계약(template-setup §2)대로 응답하고, 테스트용 제어 경로(`POST /__mock/expire`·`/__mock/reset`)가 있다. Android 에뮬레이터는 `http://10.0.2.2:8765`.
- iOS 시뮬레이터: 그대로 붙는다(ATS 는 IP 주소·localhost 를 막지 않는다)
- Android 에뮬레이터: `adb reverse tcp:8765 tcp:8765` 후 같은 주소. 평문 HTTP 는 **debug 빌드에서만** localhost·127.0.0.1·10.0.2.2 로 허용된다(`androidApp/src/debug/res/xml/network_security_config.xml`). release 는 HTTPS 만
- 끝나면 그 줄을 지운다(다시 env 의 주소)

## 앱 동작 키 (Phase 9·10)

| 키 | 기본(common) | 뜻 |
|---|---|---|
| `auth.refreshPath` | `/api/auth/refresh` | 401 때 토큰 재발급 경로. 비우면 재발급 없이 바로 로그아웃(세션 만료) — DEC-045 |
| `app.showEnvironment` | `false` (dev·staging `true`) | 설정 화면에 현재 환경 표시. 코드는 환경 이름으로 분기하지 않고 이 값만 본다 |
| `url.terms` · `url.privacy` | example.com | 설정 화면 약관·개인정보 처리방침(웹뷰). 비우면 행을 숨긴다 |
| `app.versionName` · `app.versionCode` | `1.0.0` · `1` | 앱 버전 — 여기 한 곳에서 올린다 ([release.md](release.md) §1) |
| `log.level` · `log.console` | `info` · `true` (dev `debug`, prod `warn`·`false`) | Kermit 로그 수준·콘솔 출력. prod 는 크래시 리포터로만 ([release.md](release.md) §4) |
| `update.checkPath` | `/api/app/version` | 강제·선택 업데이트 확인 API. 비우면 확인 안 함 ([release.md](release.md) §5) |
| `store.appStoreId` | (비움) | iOS 업데이트 안내의 App Store 이동(서버가 storeUrl 을 안 줄 때) |
| `chat.websocketUrl` | (비움) | 메시지 실시간 WebSocket 주소(`wss://…`). 비우면 3초 폴링 (DEC-126) |
| `security.blockCapture` | `false` | 화면 캡처 막기(DEC-137). Android 는 스크린샷·녹화 차단(FLAG_SECURE), iOS 는 녹화·미러링 중 가림 + 스크린샷 안내. 앱 잠금을 켜면 최근 앱 화면은 이 값과 상관없이 가린다 |
