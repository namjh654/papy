# 라이브러리·컴포넌트 적용 점검 — kmp (2026-09-30, windows)

소스 기준 점검이다. 실행 확인은 mac 에서 [.orca/notes/mac-next.md](../notes/mac-next.md) 순서로 한다.

## 방법

1. 버전 카탈로그(`gradle/libs.versions.toml`)의 모든 항목이 빌드 스크립트·convention plugin 에서 참조되는지
2. 선언한 의존성마다 소스에 실제 import 가 있는지 (모듈 범위별로 파일 수를 셈)
3. 디자인 시스템 컴포넌트마다 화면에서 쓰이는지, 화면이 원시 위젯(Button·TextField·AlertDialog …)을 직접 쓰지 않는지
4. iOS: Xcode 프로젝트의 SPM 패키지·제품과 Swift import 가 맞는지
5. iOS 쪽 Kotlin(iosMain)을 Windows 에서 klib 로 컴파일 (`compileKotlinIosSimulatorArm64`·`IosArm64`, 모든 모듈)

## iOS (SwiftUI)

| 항목 | 결과 |
|---|---|
| SPM 패키지 | `firebase-ios-sdk` 12.19.2+ 하나. 제품 FirebaseMessaging·FirebaseCrashlytics·FirebaseAnalytics 3개 모두 `#if canImport(...)` 안에서 import 됨 (`push/push.setup.swift`, `monitoring/firebase.monitoring.swift`) |
| Firebase 설정 파일 | `GoogleService-Info.plist` 없음(.gitignore) → NoOp. 넣는 위치·절차 `iosApp/Configuration/Firebase/README.md`, `docs/push.md` |
| 시스템 프레임워크 | SwiftUI·UIKit·PhotosUI(이미지 선택)·WebKit(웹뷰)·StoreKit(리뷰)·UserNotifications(알림) — 모두 쓰는 파일이 있음 |
| Kotlin framework `Core`(SKIE) | Swift 파일 21개가 import. iosMain Kotlin 은 Windows 교차 컴파일 통과 |
| 컴포넌트 45개 | 미사용 없음. 갤러리에서만 쓰는 것: DatePickerField·TimePickerField·ImagePicker·RadioGroup·Toggle·Toast (템플릿 제공용 — 정상) |
| 화면의 원시 위젯 | `app.navigation.swift` NavigationStack·TabView(내비게이션 뼈대), `app.update.swift` 시스템 alert 의 Button, `example.screen.swift` 행 탭용 Button + `ElsaPressableButtonStyle` — 모두 의도된 사용 |

## Android (Compose)

| 항목 | 결과 |
|---|---|
| 카탈로그 | 라이브러리 36·플러그인 9 모두 참조됨 |
| import 없는 선언 | `androidx-lifecycle-viewmodelCompose` — 직접 import 는 없고 `koinViewModel()` 이 내부에서 쓴다. lifecycle 버전을 2.11.0 으로 맞추는 역할이라 **그대로 둠** |
| 한 곳에서만 쓰는 것 | Coil(`ElsaImagePicker` 미리보기), ui-tooling-preview(1 파일), Play review·app-update(각 1 파일, 인터페이스 뒤) — 정상 |
| Firebase | messaging 3·crashlytics 2·analytics 1 파일. `google-services.json` 없으면 플러그인 미적용 + NoOp |
| 컴포넌트 30개 | 미사용 없음. 갤러리에서만: DatePickerField·TimePickerField·ImagePicker·LoadingOverlay·Switch |
| 화면의 원시 위젯 | `AndroidNavHost.kt` 의 Scaffold(하단 탭 뼈대) 하나 — 의도된 사용 |
| 기기 확인 | 에뮬레이터(API 33): 토큰 암호문 저장, 401 → 로그인 전환, 갤러리 전 구간 (PROGRESS Phase 3·4) |

## 바꾼 것

- 없음(코드). `orca.sh check` 가 mac 이 아닌 머신에서도 iOS Kotlin 을 컴파일하도록 함 (DEC-097)

## mac 에서 확인할 것

[.orca/notes/mac-next.md](../notes/mac-next.md)
