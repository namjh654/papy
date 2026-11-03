# Changelog

Keep a Changelog 형식을 가볍게 따른다. 세부 진행은 [PROGRESS.md](PROGRESS.md), 결정은 [.orca/DECISIONS.md](.orca/DECISIONS.md),
알려진 제한은 [docs/known-limitations.md](docs/known-limitations.md).

## [Unreleased]

### Fixed
- `scripts/orca.sh uitest`·`uitest android`: `env/dev.local.properties` 에 `api.baseUrl` 한 줄만 있거나 부팅된 시뮬레이터가 없으면 메시지 없이 끝나던 문제(`set -e` + `grep` 종료코드)
- `:androidUiTest`: 시스템 권한 창의 거부 버튼과 사진 선택기의 사진을 문구 대신 id 로 찾는다 — 기기 언어가 한국어여도 통과

## [v0.4.12] — 2026-10-27

Phase 30. 확인: `orca.sh check`, 전체 KMP 테스트, `uitest all`(실패한 4개는 고친 뒤 그 테스트만 다시 — Android test12d·13·13b·13c, iOS test12d·13 통과).

### Added
- 파일 받기·보관(채팅 파일 누르면 받고 열기, 설정 › 저장한 파일: 열기·공유·지우기, 로그아웃 때 정리) — v0.4.12
- 화면 캡처 막기(env `security.blockCapture`, Android FLAG_SECURE·iOS 녹화 중 가림, 앱 잠금이면 앱 전환 화면 가림) — v0.4.12

### Fixed
- 대화방을 다시 열 때 저장된 메시지를 받은 목록으로 바꾸면 iOS 스크롤이 중간에 남던 문제

### Removed
- 앱 내 결제(IAP·구독)는 템플릿 범위에서 뺀다(DEC-138)

## [v0.4.11] — 2026-10-26

Phase 18~29(23 은 보류). 패치마다 태그 v0.4.1~v0.4.11. 확인: `orca.sh check`, 전체 KMP 테스트(Android host·iOS), `uitest all` Android 20개·iOS 19개(18 통과·1 건너뜀).

### Added
- 소셜 로그인(카카오·구글·애플·네이버, 키 없으면 버튼 없음) — v0.4.1
- 메시지(텔레그램식 대화 목록·대화방, 사진·카메라·파일 첨부, 답장·입력 중·검색, WebSocket/폴링) — v0.4.2·v0.4.5
- 커뮤니티(글·댓글·좋아요·신고·차단), 공통 UI 2차(칩·세그먼트·스켈레톤·검색창), 딥링크·탭 배지 — v0.4.3·v0.4.4
- 로컬 DB(SQLDelight, 기능마다 DB·로그아웃 때 정리), 채팅·커뮤니티 첫 화면 저장·오프라인, 못 보낸 메시지 대기열, iOS 이미지 캐시(Nuke) — v0.4.6
- 버전 정책(major·minor 다르면 강제, patch 만 다르면 권장)·운영 공지(점검·공지) — v0.4.7
- 사용자 프로필·1:1 대화 시작·문의하기 — v0.4.8
- 앱 잠금(생체 인증·기기 암호)·알림 종류별 설정(광고성 동의 날짜 안내) — v0.4.9
- `newFeature --db`(로컬 DB 기능 한 번에), 기능 켜고 끄기(FeatureFlags) — v0.4.10
- 접근성 1차(제목 알림·버튼 역할·작은 버튼 누르는 영역) — v0.4.11

### Changed
- iOS 글자가 시스템 글자 크기(Dynamic Type)를 따른다(최대 accessibility1, DEC-128)
- 화면 전환은 각 플랫폼 방식(Android Material 공유 축, iOS NavigationStack, DEC-127)

## [v0.4] — 2026-10-04

### Added
- Android UI 흐름 테스트 `:androidUiTest`(UiAutomator, 설치된 dev 앱을 밖에서 조작) + `scripts/orca.sh uitest android [--port] [--only]`: 온보딩·가입·재설정·로그인·공지·예시·알림함·검색·권한·프로필·링크·테마·세션 만료·뒤로가기 종료·탈퇴 15개

## [v0.3] — 2026-10-01

Phase 13~16. iOS 는 mac 시뮬레이터에서 확인(`scripts/orca.sh uitest` 14개 통과).

### Changed
- CI 를 끄고 예시·문서만 남김: `.github/workflows/ci.yml` → `docs/ci/github-actions.yml`, 켜는 방법 `docs/ci.md` (DEC-096)
- `scripts/orca.sh check`: mac 이 아닌 머신에서도 iOS 쪽 Kotlin 을 컴파일해 확인 (DEC-097)

### Added
- 로컬 목업 서버 `mockServer`(build-logic tooling, JDK 만) — 서버 계약대로 응답, 세션 만료 제어 경로
- iOS UI 흐름 테스트 `iosApp/UITests` + `scripts/orca.sh uitest`(mac): 온보딩부터 탈퇴까지 14개
- core: 파일 업로드 `BaseApi.upload`, 권한 `PermissionController`, 상대 시간 `RelativeTime`(kotlinx-datetime), `Paginator.update/restart/clear`
- 상세 화면 패턴(`feature/example/detail`), 공지사항 `:feature:notice`(목록·상세), `ElsaContentState`·`ElsaBadge`, 외부 링크 `elsa://<path>`
- 약관 동의, 비밀번호 재설정(2단계), 회원 탈퇴, 프로필 편집(사진 업로드·저장 전 나가기 확인), `ElsaRemoteImage`·`ElsaAvatar`, 비밀번호 규칙(8자)
- 권한 요청 패턴(요청·재확인·설정 이동), 온보딩(첫 실행 한 번), 알림함, 검색(자동 검색·최근 검색어), Android 종료 재확인

### Fixed
- 로그아웃해도 자동 로그인 설정이 남던 문제
- Android: 알림·링크의 딥링크를 열 때 앱이 종료되던 문제(`NavController.openDeepLink`)
- iOS: Kotlin 인터페이스 companion 호출(`RelativeTimeCompanion.shared`) — mac 첫 컴파일 오류

## [v0.2] — 2026-09-30

버전 업그레이드(Phase 12). 기능·공개 API 변경 없음. 근거와 보류 항목은 [docs/known-limitations.md §4](docs/known-limitations.md).

### Changed
- Kotlin 2.4.10 → 2.4.20, SKIE 0.10.14 → 0.10.15
- AGP 9.0.1 → 9.3.1, Gradle 9.1.0 → 9.7.1, compileSdk 36 → 37 (targetSdk 36 유지), lifecycle 2.10.0 → 2.11.0
- Compose BOM 2026.01.00 → 2026.09.00

### 보류
- AGP 9.4.1·Gradle 9.8.0 — Kotlin 2.4.20 호환표 범위 밖 (DEC-093)
- Firebase deprecated 토큰 API → FID 등록 전환 — 서버 계약·실제 Firebase 확인 필요 (DEC-094)

## [v0.1] — 2026-09-30

복사해서 바로 쓰는 KMP 템플릿의 첫 기준판. `:core:common`·`:core:data`·`:feature:*`·`:shared`(iOS framework `Core`) + Android Compose + iOS SwiftUI.

### Added
- 모듈 구조: core:common(에러·ViewModel 베이스·페이징·DeepLink), core:data(네트워크·저장소·세션·푸시·설정·모니터링·업데이트), feature:auth·home·example·settings, :shared umbrella, build-logic convention plugin
- 앱 기본 패턴: 하단 탭(Android `MainTab`·iOS `TabView`), 목록 페이징(`Paginator`·`ElsaPagedList`, 당겨서 새로고침·다음 페이지), 설정(테마·언어·버전·환경·약관·로그아웃·앱 평가), 웹뷰, 오프라인 안내(`NetworkMonitor`)
- 인증: 로그인·회원가입·자동 로그인, 보안 저장소(Keystore/Keychain, 평문에서 이전), 401 → 토큰 재발급 → 1회 재시도(동시 요청 1회)
- 디자인 시스템: `design/tokens.json` → Kotlin/Swift/XML 생성, 양 플랫폼 `Elsa*` 컴포넌트(대응표 design/README.md), 다크 모드·다국어(ko/en)
- 환경: `env/` dev·staging·prod 단일 원본 → KMP `AppConfig`·Android flavor·iOS xcconfig, 세 앱 동시 설치
- 운영: 버전 한 곳(env), 강제·선택 업데이트 안내, Kermit 로깅(prod 는 크래시 리포터로만), Firebase 푸시·Crashlytics·Analytics(설정 파일 있을 때만, 없으면 NoOp), Play/StoreKit 인앱 리뷰, Play 인앱 업데이트
- 템플릿 도구(JDK 만 필요, build-logic `:tooling`): `renameApp`, `newFeature [--tab]`, `templateCheck`(아키텍처·파일 배치·문자열·디자인 토큰·env 일치), `generateDesignTokens`
- 테스트·CI: 모든 ViewModel·Repository 단위 테스트(Turbine), Kover 커버리지, GitHub Actions(템플릿 점검·Android 3환경·iOS)
- 문서: template-setup·conventions·environments·push·release·known-limitations
- Orca 오케스트레이션(`scripts/orca.sh`): 역할 워크트리, 결정 기록, 체크포인트, 임시 워크트리 자동 정리, 머신 이동 전 점검(`preflight`)

### Changed
- 공통 Navigator 제거 → 플랫폼 Native Navigation
- 사용자 문구는 KMP 가 아니라 플랫폼 리소스(`ErrorCode` → 문자열)
- 템플릿 스크립트를 Python 에서 build-logic Kotlin 으로 옮김(Phase 7) — Python 불필요
- 테마: 다크 여부를 KMP `ThemeMode.isDark` 한 곳에서 정하고 시작 화면(윈도우)·본문·시트가 같은 기준을 쓴다

### Fixed
- Android: 앱 테마가 시스템과 다를 때 콜드 스타트 시작 화면이 반대 색으로 보이던 문제
- Android: 백업 복원으로 재설치한 앱이 옛 테마(예: 다크)로 뜨던 문제 — 저장소를 백업·기기 이전에서 제외

### Removed
- `structure.txt`, 구버전·빈 문서(docs/architecture·api·migration·deployment), 빈 `core/ui`·`core/util`, `scripts/*.py`
