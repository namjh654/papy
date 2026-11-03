# 템플릿 완성도·라이브러리 점검 (2026-09-29, 구현 없음)

라이브러리 추가·교체는 승인 전까지 하지 않는다. 버전은 2026-09-29 Maven 조회 기준 최신 안정판.
"Phase" 는 제안이다(9 = 앱 기본 패턴, 10 = 운영·릴리스).

## (a) 기능 패턴

| 항목 | 현황 | 추천 | 이유 | 라이브러리 | iOS(SKIE) 영향 | Phase |
|---|---|---|---|---|---|---|
| 목록 공통 패턴 (로딩·빈 화면·에러 재시도·당겨서 새로고침·페이징) | **일부** — `newFeature` 생성물에 로딩·빈 화면·에러+재시도. 당겨서 새로고침·페이징 없음 | 넣는다 | 거의 모든 앱의 첫 화면. 생성기가 한 가지 방식으로 만들어야 함 | 없음 — Android `PullToRefreshBox`(material3), iOS `.refreshable`, 페이징은 core:common 의 작은 `Paginator`(page/nextKey 상태) | `ListUiState` 류 data class 추가 — Swift 에 그대로 노출, 문제 없음 | 9 |
| 메인 내비게이션 (하단 탭) | **없음** — 홈 하나 + 스택 | 넣는다 | 템플릿 앱 대부분이 탭 구조. 나중에 넣으면 NavHost·AppNavigation 구조를 다시 짜야 함 | 없음 — Android `NavigationBar` + 탭별 중첩 그래프, iOS `TabView` + 탭별 `NavigationStack` | 없음 (플랫폼 소유) | 9 |
| 설정 화면 (로그아웃·언어·테마·앱 버전·환경) | **없음** — 로그아웃은 홈 버튼, `SettingStorage`(theme·language) 저장소만 있음 | 넣는다 | 로그아웃 위치·테마 저장·환경 확인(스테이징 QA)이 모든 앱에 필요 | 없음 — 언어는 Android 앱별 언어(AppCompat 없이 `LocaleManager`)/iOS 설정 앱 이동, 테마는 `SettingStorage` + ElsaTheme | `SettingsUiState`, `AppConfig.ENVIRONMENT`(표시 전용) 노출 | 9 |
| 릴리스 준비 (서명·R8·버전 코드) | **일부** — prodRelease R8·로그 제거, 서명 자리(Phase 8). 버전 코드는 Android `versionCode = 1` 고정, iOS `CURRENT_PROJECT_VERSION = 1` 고정 | 넣는다 | 버전이 두 곳에 흩어져 있고 자동 증가가 없음 | 없음 — 버전 단일 원본(예: `env/common.properties` 또는 `gradle.properties` 의 versionName + CI 빌드 번호) → Android versionCode/Name + iOS xcconfig 생성, 릴리스 체크리스트 문서 | 없음 | 10 |
| 강제 업데이트 | **없음** | 자리만 넣는다 | 운영 앱에 거의 필수이나 서버 계약(최소 버전 API) 필요 | Android Play In-App Update `app-update-ktx` 2.1.0(선택), iOS 는 스토어 링크 | core `AppVersionPolicy`(최소·권장 버전 판정) + 이벤트 — 문제 없음 | 10 (서버 계약 후) |
| 크래시·이벤트 로그 연결 자리 | **없음** | 자리(인터페이스 + NoOp)는 넣는다 | PushProvider 와 같은 패턴으로 제공자 교체 가능하게 | 구현은 아래 (b) 크래시·분석 | `AnalyticsTracker`·`CrashReporter` 인터페이스 — Swift 구현 시 프로토콜 | 10 |

## (b) 공통·앱 기본 라이브러리

| 항목 | 현황 | 추천 | 이유 | 라이브러리·버전 | iOS(SKIE) 영향 | Phase |
|---|---|---|---|---|---|---|
| 로깅 | **없음** — Ktor `Logger.SIMPLE`(println) | 넣는다 | 로그 수준·태그·iOS OSLog 출력, 크래시 리포트 연동 | **Kermit 2.2.0** (Touchlab, SKIE 와 같은 곳). Napier 2.7.1 보다 유지보수·CrashKiOS 연동 우위 | 공통 내부에서만 쓰면 노출 없음 | 10 |
| 날짜 (kotlinx-datetime) | **보류 결정(DEC-010)** — UI 는 플랫폼 Date/epoch, 공통 날짜 로직 없음 | 지금은 넣지 않음 | 공통 날짜 계산이 생길 때(서버 날짜 필드 정렬·마감 계산) 도입 | kotlinx-datetime 0.8.x | `Instant`/`LocalDate` 는 Swift 에서 불편 → DTO 는 문자열/epoch, ViewModel 에서 변환 | 필요 시 |
| 이미지 로딩 (공통 Coil 3 전환) | Android Coil 3.3.0(compose), iOS 없음(PhotosPicker 만) | 공통 전환 **안 함**, Android Coil 유지 | 공통 Coil 은 Compose Multiplatform UI 전제 — Native UI 원칙(ARCHITECTURE 2.1)과 맞지 않음. iOS 원격 이미지는 `AsyncImage` 로 시작 | Coil 3.6.3 은 Compose BOM 을 앞질러 보류(DEC-008) — BOM 올릴 때 함께 | 없음 | – |
| 페이징 | **없음** | 라이브러리 없이 | androidx paging 3.5.1(KMP)은 `PagingData` 를 Swift 에서 소비하기 어려움(SKIE 로도 LazyPagingItems 대응 없음). 템플릿에는 작은 `Paginator` 가 충분 | (쓰려면) androidx paging-common 3.5.1 / cash multiplatform-paging 3.1.1-0.3.1 | 라이브러리 사용 시 Swift 쪽 어댑터 필요 | 9 |
| 권한 처리 | 푸시 권한만(플랫폼) | 라이브러리 없이 | moko-permissions 0.20.1 은 Activity/Compose 바인딩 중심. Native UI 원칙상 플랫폼 요청 + KMP 는 상태 enum(`PushPermission` 패턴) | – | 없음 | 필요 시 |
| 네트워크 연결 상태 | **없음** | 넣는다(직접 구현) | 오프라인 배너·재시도에 흔히 필요, 구현이 작음 | 직접: Android `ConnectivityManager` / iOS `NWPathMonitor` + KMP `NetworkMonitor: StateFlow<Boolean>` (대안: dev.jordond connectivity 2.5.2) | StateFlow → SKIE `SkieSwiftStateFlow<Bool>` | 9 |
| 크래시 리포팅·분석 | **없음** | 넣는다(승인 필요) | FCM 과 같은 Firebase 프로젝트를 재사용, 설정 파일 없으면 NoOp 규칙 그대로 | Firebase Crashlytics·Analytics(BoM 34.19.0 / firebase-ios-sdk 12.19.x), Kotlin 크래시 심볼은 **CrashKiOS 0.10.0**(+ Kermit) | iOS SPM 제품 추가(Crashlytics·Analytics) — mac 검증 | 10 |
| 인앱 리뷰·업데이트 | **없음** | 자리만 | 강제 업데이트와 같이 설계 | Android `review-ktx` 2.0.2 · `app-update-ktx` 2.1.0, iOS `SKStoreReviewController`/`requestReview` | 없음(플랫폼) | 10 |
| 웹뷰 | **없음** | 넣는다(라이브러리 없이) | 약관·공지 화면에 흔함 | Android `WebView`, iOS `WKWebView` 를 `ElsaWebView` 공통 컴포넌트로 | 없음 | 9 |
| 테스트 도구 | coroutines-test 만(DEC-050) | **Turbine 넣기 추천**, Kover 선택 | Flow 이벤트 테스트가 늘면 `first()` 만으로 부족. 커버리지 수치가 필요하면 Kover | Turbine 1.2.1, Kover 0.9.9 | 테스트 전용 — 없음 | 9 (Turbine) |

## 지금 쓰는 라이브러리 — 교체·정리 후보

| 대상 | 현재 | 제안 | 이유 | 위험 |
|---|---|---|---|---|
| AGP | 9.0.1 | 최신 9.x 로 | 이 R8 이 Kotlin 2.4 메타데이터 경고(Phase 8 prodRelease). compileSdk 37·lifecycle 2.11 도 AGP 버전에 묶임 | 기준선 변경 — 승인 필요 |
| lifecycle | 2.10.0 | AGP 올린 뒤 2.11.x | 2.11 은 compileSdk 37 요구(DEC-043) | AGP 와 함께 |
| SKIE | 0.10.14 | 0.10.15 | Kotlin 2.4.x 지원 패치 — 릴리스 노트 확인 후 | iOS 빌드 영향 — mac 검증 필요 |
| Ktor | 3.5.0 | 3.6.0 | 마이너 업데이트 | 낮음 |
| Navigation Compose | 2.9.4 | 2.10.x | 최신 안정 — compileSdk 요구사항 확인 필요 | 중간 |
| Coil (Android) | 3.3.0 | Compose BOM 올릴 때 3.6.x | DEC-008 | 낮음 |
| Ktor 로그 출력 | `Logger.SIMPLE` | Kermit 도입 시 Kermit 로 연결 | println 은 iOS Console 에서 구분 어려움 | 낮음 |
| Firebase iOS SPM (DEC-053) | 항상 링크 | 유지 또는 `#if canImport` 만 남기기 | 결정 검토표 "확인 필요" 참고 | – |
