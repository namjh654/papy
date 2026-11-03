# mac 검증 순서 — kmp (2026-10-01 windows 에서 Phase 13·14·15 작성) — **2026-10-01 mac 에서 완료** (결과: ios.md)

Windows 에서 한 것: core 보강, 새 feature 5개(notice·permission·inbox·search + auth/settings 안의 화면들), Android 화면 전부 에뮬레이터 확인,
iOS 쪽 **Kotlin 은 컴파일 확인**, **Swift 는 작성만** 했다(컴파일해 본 적 없음). 그래서 mac 에서는 Swift 컴파일 오류부터 잡는다.

## 1. 받기·빌드

```
git pull && scripts/orca.sh sync
scripts/orca.sh check            # dev: 테스트 + Android + iOS framework + xcodebuild
```

xcodebuild 가 실패하면 아래 "Swift 에서 틀렸을 수 있는 곳"부터 본다. 고친 뒤 `check --env staging`, `--env prod`.

## 2. Swift 에서 틀렸을 수 있는 곳 (우선순위)

| 파일 | 의심 지점 |
|---|---|
| `view/*/…detail.screen.swift`, `signup.screen.swift` | `init` 에서 `_model = StateObject(wrappedValue: SharedViewModel(…, state: \.uiState))` 의 제네릭 추론 |
| `designsystem/format/elsa.format.swift` | `onEnum(of: RelativeTime.companion.since(epochMillis:))`, `value.count`(Int32) |
| `view/terms/terms.screen.swift` | `Agreement` 의 case 이름, `agreement.isRequired`, `state.isChecked(agreement:)` |
| `view/permission/permission.screen.swift` | `AppPermission`·`PermissionStatus` case 이름(`.notRequested`, `.permanentlyDenied`), `status.isGranted` |
| `view/profile/profile.screen.swift` | `photosPicker(isPresented:selection:matching:)`, `IOSAppGraph.shared.bytes(data:)` 의 반환 타입(`KotlinByteArray`) |
| `view/login/login.screen.swift` | `.notice(let event)` 안의 `switch event.notice` |
| `view/onboarding/onboarding.screen.swift` | `TabView(selection:)` 의 `Int` 태그와 `Int32` 상태 |
| `navigation/app.navigation.swift` | `AppRoute` 새 case 의 `navigationDestination`, `authPath = [.signup(marketingAgreed:)]` |
| `component/ui.error.text.swift` | `ErrorCode` 새 case 4개 이름 |

## 3. 실행 확인 (시뮬레이터, 로컬 목업 서버가 있으면 env/dev.local.properties 로)

| # | 흐름 | 볼 것 |
|---|---|---|
| 1 | 첫 실행 | 온보딩(밀기·다음·건너뛰기) → 로그인. 다시 실행하면 바로 로그인 |
| 2 | 회원가입 | 약관(전체 동의·전문 보기 웹뷰) → 가입(8자 미만 오류) → 로그인 화면 토스트 |
| 3 | 비밀번호 재설정 | 코드 요청 → 둘째 단계에서 뒤로 → 첫 단계, 완료 → 로그인 토스트 |
| 4 | 홈 → 공지사항 | 목록(상대 시간·새 글 배지) → 상세 → 뒤로 |
| 5 | 예시 탭 | 항목 → 상세 → 즐겨찾기 → 목록 배지 |
| 6 | 알림함 | 눌러서 공지 상세 이동, 읽음 표시, 모두 읽음 |
| 7 | 검색 | 입력 멈추면 결과, 검색 버튼 → 최근 검색어, 결과 없음 |
| 8 | 권한 | 알림·카메라·위치 창, 거부 → "설정 열기" → 돌아오면 상태 갱신 |
| 9 | 설정 | 프로필 행 → 사진 선택·저장(토스트)·변경 후 뒤로 → 확인, 회원 탈퇴 |
| 10 | 링크 | `xcrun simctl openurl booted elsa://notice/5`, `elsa://settings` |
| 11 | (이전부터 남은 것) | iOS 갤러리 수동 확인, 401 → 로그인 전환 |

## 4. 끝나면

- `.orca/notes/ios.md` 의 Phase 13·14·15 체크, `PROGRESS.md` 의 mac 줄 체크
- 다음 차례: `../kmp-decompose/app/.orca/notes/mac-next.md`
- 결정 검토: DEC-095~100 (`.orca/reports/DECISION-REVIEW-20260930.md` 이후 추가분)
