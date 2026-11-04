> **Papy 모노레포 안내 (2026-11-03)**
>
> 이 폴더는 KMP 템플릿 [namjh654/kmp](https://github.com/namjh654/kmp) 커밋 `c5a0d93`(v0.4.12)을 복사 편입한 것이다. 아래 본문은 템플릿 원문이다.
> **Papy 1단계(2026-11-04)**: 시작 화면이 문서 앱(최근 문서 → 문서 보기, `feature:documents`)이다. Rust 엔진은 UniFFI 로 연결했다([ADR 0006](../../docs/decisions/0006-mobile-bridge.md)).
> 빌드 전에 저장소 루트에서 `node scripts/fetch-rhwp.mjs` → `node scripts/build-mobile-engine.mjs all` (Android NDK·rustup 모바일 타깃 필요).
> 템플릿의 로그인·탭·채팅 등은 화면에서만 뺐고 코드는 남아 있다. 템플릿의 ELSA 이름은 Papy 로 바꿨다(프로젝트 `papykmp`, 패키지·앱 ID `io.github.namjh654.papy`, 딥링크 `papy://`).
> 출처·편입 시 변경·바꿀 식별자 후보는 [docs/architecture/kmp-template-origin.md](../../docs/architecture/kmp-template-origin.md).
>
> 실행: 이 폴더에서 `scripts/orca.sh check` (템플릿 점검 + 단위 테스트 + Android dev 빌드 + iOS Kotlin 컴파일 + mac 이면 xcodebuild).
> 검증 결과(macOS 26.6.2 arm64, Xcode 27.2): `scripts/orca.sh check` 통과 — `./gradlew :androidApp:assembleDevDebug` 성공, iOS `xcodebuild`(시뮬레이터, 서명 없음) 성공.
> 시뮬레이터·기기 실행과 UI 흐름 테스트(`orca.sh uitest`)는 수행하지 않았다.

# KMP Native UI 템플릿

새 앱을 시작할 때 **복사해서 바로 쓰는** Kotlin Multiplatform 템플릿이다.
비즈니스 로직(네트워크·저장소·세션·ViewModel)은 KMP 로 한 번만 만들고, 화면과 내비게이션은 플랫폼마다 Native 로 만든다.

- **Android**: Jetpack Compose + Navigation Compose
- **iOS**: SwiftUI + NavigationStack (Kotlin API 는 SKIE 로 Swift 친화 타입)
- **공유**: `core:*`·`feature:*` 모듈의 UiState · Event · ViewModel · Repository · Api

> 화면까지 공유하는 판(Decompose + Compose Multiplatform)은 별도 저장소 [`kmp-decompose`](https://github.com/namjh654/kmp-decompose) 이다.
> 어느 쪽을 쓸지는 아래 [두 템플릿 비교](#두-템플릿-비교) 를 본다.

현재 버전: **v0.4** (2026-10-04, [릴리스](https://github.com/namjh654/kmp/releases)) · 다음 할 일 [PROGRESS.md §1](PROGRESS.md) · 상세 진행 [PROGRESS.md](PROGRESS.md) · 변경 이력 [CHANGELOG.md](CHANGELOG.md) · 알려진 제한 [docs/known-limitations.md](docs/known-limitations.md)

---

## 들어 있는 것

| 영역 | 내용 |
|---|---|
| 앱 흐름 | 온보딩(첫 실행 한 번) → 스플래시(세션 복원) → 로그인 · 회원가입(약관 동의) · 비밀번호 재설정 → 하단 탭(홈 · 예시 · 설정) |
| 화면 패턴 | 목록(페이징·당겨서 새로고침·다음 페이지), 상세(id 로 불러오기·수정 → 목록 반영), 공지사항, 알림함, 검색(입력 중 자동 검색·최근 검색어), 권한 요청, 프로필 편집(사진 업로드), 회원 탈퇴, 웹뷰, 컴포넌트 갤러리 |
| 인증·세션 | 자동 로그인, 보안 저장소(Android Keystore / iOS Keychain), 401 → 토큰 재발급 → 1회 재시도 → 실패 시 로그인으로 |
| 네트워크 | Ktor(OkHttp / Darwin), 공통 `BaseApi`(get/post/put/delete/**upload**), 오류 정규화 `AppError` → `ErrorCode` |
| 플랫폼 기능 | 푸시(FCM, 설정 파일 없으면 NoOp), 권한(알림·카메라·위치: 요청 → 재확인 → 설정 이동), 외부 링크 `papy://<path>`, 강제·선택 업데이트, 인앱 리뷰, 크래시·이벤트(Firebase, 설정 시) |
| 화면 공통 | 다크 모드, 다국어(ko·en), 오프라인 안내, Android 뒤로가기 규칙(다른 탭 → 홈 → "한 번 더 누르면 종료") |
| 디자인 시스템 | `design/tokens.json` → Kotlin/Swift 토큰 생성, 양 플랫폼 같은 이름의 `Papy*` 컴포넌트 ([design/README.md](design/README.md) 대응표) |
| 환경 | dev · staging · prod — `env/*.properties` 한 곳 → KMP `AppConfig`, Android flavor, iOS xcconfig. 세 앱 동시 설치 |
| 도구 | `newFeature`(새 화면·탭 뼈대를 KMP·Android·iOS 에 한 번에), `renameApp`, `templateCheck`(구조·문자열·토큰·env 검사), `mockServer`(로컬 목업 서버) |
| 테스트 | KMP 단위 테스트 약 160개(ViewModel·Repository·core, Turbine), UI 흐름 테스트 iOS 14개(`scripts/orca.sh uitest`)·Android 15개(`scripts/orca.sh uitest android`) |

## 빠른 시작

필요한 것: JDK 21(Gradle 데몬), Android Studio, (iOS) macOS + Xcode.

```bash
scripts/orca.sh check                    # 템플릿 점검 + 전 모듈 테스트 + Android 빌드 + iOS Kotlin 컴파일 (+ mac 이면 xcodebuild)
./gradlew :androidApp:assembleDevDebug   # Android dev 빌드
open iosApp/iosApp.xcodeproj             # (mac) scheme iosApp-dev
```

서버 없이 돌려 보기 — 로컬 목업 서버:

```bash
./gradlew -p build-logic :tooling:run --args="mockServer --port 8765"
# env/dev.local.properties (커밋 안 함): api.baseUrl=http://127.0.0.1:8765   (에뮬레이터는 adb reverse tcp:8765 tcp:8765)
```

새 앱으로 바꾸기:

```bash
./gradlew -p build-logic :tooling:run --args="renameApp --package com.acme.shop --app-name 'Acme Shop' --bundle-id com.acme.shop --team-id ABCDE12345"
```

이어서 [docs/template-setup.md](docs/template-setup.md) 순서(API 주소 → Firebase → 디자인 → 예시 정리)를 따른다.

## 새 기능 추가

```bash
./gradlew -p build-logic :tooling:run --args="newFeature notice --title-ko 공지사항 --title-en Notices"   # 탭 위에 쌓이는 화면
./gradlew -p build-logic :tooling:run --args="newFeature board --title-ko 게시판 --title-en Board --tab"  # 하단 탭
```

KMP 모듈(UiState·Event·ViewModel·Repository·Api·Koin·테스트) + Android 화면 + iOS 화면 + 양쪽 내비게이션·딥링크·문자열을 만들고,
만든 직후 `scripts/orca.sh check` 를 통과한다. 이후 기준 코드를 보고 화면을 바꾼다 — 규칙은 [docs/conventions.md](docs/conventions.md).

| 만들 화면 | 기준 코드 |
|---|---|
| 목록 | `feature/example`, `feature/notice` |
| 상세(인자 있는 화면)·수정 → 목록 반영 | `feature/example/detail`, `feature/notice/detail` |
| 여러 단계 화면 | `feature/auth/reset` |
| 수정 폼·파일 업로드·저장 전 나가기 확인 | `feature/settings/profile` |
| 되돌릴 수 없는 작업 | `feature/settings/withdraw` |
| 권한이 필요한 화면 | `feature/permission` |
| 검색·낙관적 갱신 | `feature/search`, `feature/inbox` |

## 구조

```text
feature:*      UiState · Event · ViewModel · Repository · Api            (KMP)
   │           auth · home · example · settings · notice · inbox · search · permission
core:data      network(Ktor·BaseApi) · storage · session · push · permission · update · monitoring   (KMP)
   │
core:common    AppError·ErrorCode · StatefulViewModel · Paginator·ListState · DeepLink · RelativeTime (KMP)

shared         Koin 진입점 initKoin, iOS framework "Core"(위 모듈 전부 export), IOSAppGraph
 ├─ androidApp  Compose · Navigation Compose · 디자인 시스템 · FCM·Play·권한 창 연결
 └─ iosApp      SwiftUI · NavigationStack · 디자인 시스템 · FCM·StoreKit 연결 · UITests
```

원칙

1. 비즈니스 로직은 KMP(`core:*`, `feature:*`)에서 공유한다. KMP 는 UI 프레임워크·Navigation API 에 의존하지 않는다.
2. ViewModel 은 "무슨 일이 일어났는지"만 Event 로 알리고, 어디로 갈지는 플랫폼 Navigation 이 정한다.
3. 사용자 문구는 KMP 가 만들지 않는다. `ErrorCode` 만 넘기고 문구는 플랫폼 리소스(strings.xml / Localizable.xcstrings)가 정한다.
4. feature 끼리 의존하지 않는다. 공유할 것은 `core:data`·`core:common` 으로 올린다.
5. 환경 값은 `env/*.properties` 가 원본이고 코드는 `AppConfig` 만 읽는다(환경 이름으로 분기하지 않는다).

```text
LoginViewModel.login() ─ 성공 ─▶ LoginEvent.LoginSucceeded
                                   ├─ Android: EventEffect → navController.navigateToHome()
                                   └─ iOS:     for await + onEnum(of:) → phase = .main
```

자세한 설계와 이유: [ARCHITECTURE.md](ARCHITECTURE.md)

## 기술 스택

| 분류 | 버전 |
|---|---|
| Kotlin · Coroutines · Serialization | 2.4.20 · 1.11.0 · 1.10.0 |
| Android Gradle Plugin · compileSdk / targetSdk / minSdk | 9.3.1 · 37 / 36 / 24 |
| Compose BOM · Navigation Compose · Lifecycle | 2026.09.00 · 2.9.4 · 2.11.0 |
| SKIE · iOS 최소 | 0.10.15 · iOS 18.2 |
| Koin · Ktor · DataStore · Okio | 4.2.2 · 3.5.0 · 1.2.1 · 3.16.0 |
| kotlinx-datetime · Coil(Android) · Kermit | 0.8.0 · 3.3.0 · 2.2.0 |
| Firebase BoM · Play Review / App Update | 34.19.0 · 2.0.2 / 2.1.0 |
| Turbine · Kover | 1.2.1 · 0.9.11 |

## 문서

| 문서 | 내용 |
|---|---|
| [docs/template-setup.md](docs/template-setup.md) | 새 앱으로 바꾸는 순서, 서버 계약(가정) 표 |
| [docs/conventions.md](docs/conventions.md) | 이름·패키지·파일 배치, 화면 패턴별 규칙, 뒤로가기·모달 규칙 |
| [docs/environments.md](docs/environments.md) | dev·staging·prod, 비밀 값, 목업 서버 |
| [docs/push.md](docs/push.md) | 푸시(FCM) 켜는 방법·딥링크 |
| [docs/release.md](docs/release.md) | 버전·서명·환경별 릴리스·업데이트 안내·로그 |
| [docs/ci.md](docs/ci.md) | CI(꺼져 있음) 켜는 방법 |
| [docs/known-limitations.md](docs/known-limitations.md) | 확인하지 못한 것·서버 가정·플랫폼 한계·보류한 버전 |
| [design/README.md](design/README.md) | 디자인 토큰, Android ↔ iOS 컴포넌트 대응표 |

## 작업 내용 (v0.1 → v0.4)

| 버전 | Phase | 내용 |
|---|---|---|
| v0.1 (2026-09-30) | 0~11 | 분석·문서화 → 기반 정리 → 모듈 분리(core:common·core:data·feature:*) → 네트워크·인증·보안 저장소 → 디자인 시스템·다국어 → 푸시(FCM, NoOp 대체) → 테스트·템플릿화(`newFeature`·`renameApp`) → 템플릿 도구를 Python 에서 Kotlin 으로 → 환경 분리(dev·staging·prod) → 앱 기본 패턴(탭·목록·설정·웹뷰·오프라인) → 운영(버전·업데이트·로그·크래시·리뷰) → 리팩토링 |
| v0.2 (2026-09-30) | 12 | 버전 업그레이드(Kotlin 2.4.20, AGP 9.3.1, Gradle 9.7.1, Compose BOM 2026.09.00, SKIE 0.10.15) |
| v0.3 (2026-10-01) | 13~16 | core 보강(업로드·권한·상대 시간·페이징), 상세 패턴·공지사항·외부 링크, 약관·비밀번호 재설정·탈퇴·프로필, 권한·온보딩·알림함·검색·Android 종료 재확인, 로컬 목업 서버, iOS UI 흐름 테스트 14개, 결정 전부 확정 |
| v0.4 (2026-10-04) | 17 | Android UI 흐름 테스트 15개(`:androidUiTest`, UiAutomator) + `scripts/orca.sh uitest android` |

v0.3 에서 고친 버그: 로그아웃해도 자동 로그인 설정이 남던 문제, Android 에서 알림·링크로 화면을 열 때 앱이 종료되던 문제, iOS 의 Kotlin companion 호출.

## 확인 상태 (v0.4)

| 항목 | 방법 | 결과 |
|---|---|---|
| 템플릿 점검 + KMP 테스트 + 빌드 | `scripts/orca.sh check --env dev\|staging\|prod` | 3환경 통과 (mac·Windows) |
| iOS 화면 흐름 | `scripts/orca.sh uitest` (목업 서버 + XCUITest) | 14개 통과 |
| Android 화면 흐름 | `scripts/orca.sh uitest android` (에뮬레이터 API 33 + 목업 서버 + UiAutomator) | 15개 통과 |
| Android 릴리스(R8) | `prodRelease` 디버그 키 서명, 로그인 이후 흐름 | 통과 |

확인하지 못한 것(실서버, Firebase 실제 수신, 스토어 배포, 실기기)과 서버 계약 가정은 [docs/known-limitations.md](docs/known-limitations.md).

## 두 템플릿 비교

| | kmp (이 저장소) | kmp-decompose |
|---|---|---|
| 화면 | Android Compose · iOS SwiftUI 를 각각 만든다 | Compose Multiplatform 한 벌 |
| 새 화면 하나 | KMP 모듈 + Android 화면 + iOS 화면(`newFeature` 가 셋 다 만든다) | feature 모듈 하나 + 루트 등록 |
| 내비게이션 | 플랫폼별(Navigation Compose / NavigationStack) | Decompose 한 곳(스택·탭·딥링크·복원) |
| iOS 느낌 | 시스템 그대로 | Compose 가 그린 화면 |
| Swift 연동 | SKIE(Kotlin 버전이 SKIE 지원 범위에 묶임) | 없음 |
| 맞는 팀 | Android·iOS 개발자가 각각 있을 때, iOS 다운 화면이 중요할 때 | Kotlin 개발자 위주, 화면을 빨리 많이 만들 때 |
| 두 플랫폼 디자인 | 같은 시안·토큰으로 맞추고 화면 캡처를 나란히 비교(DEC-112), 렌더링 차이는 허용 | 같은 코드가 그려 자동으로 같다 |

**고르는 기준 (2026-10-22)** — 새 앱을 시작할 때 두 질문으로 정한다. 로직(저장소·세션·네트워크)은 두 저장소가 같은 구조다.
1. iOS 네이티브 느낌(스크롤·시트·입력·접근성·제스처)이 중요한가?
2. iOS 개발자가 SwiftUI 로 일하는가?

하나라도 예 → **kmp**(네이티브 UI). 둘 다 아니고 두 플랫폼이 똑같아야 하는 것이 최우선이거나 인원이 적다 → **kmp-decompose**(동일 UI 우선). 기본 서비스 템플릿은 kmp, kmp-decompose 는 동일 UI 우선 판으로 함께 유지한다.

## 개발 방식

Orca: `ocra` 브랜치에서 계획·공통 로직·병합·점검·결정 기록을 하고, `ocra-android`·`ocra-ios` 워크트리에서 플랫폼 화면을 만든다.
결정은 [.orca/DECISIONS.md](.orca/DECISIONS.md), 절차는 [.orca/README.md](.orca/README.md), 진행은 [PROGRESS.md](PROGRESS.md).
