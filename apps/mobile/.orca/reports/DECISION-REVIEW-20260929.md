# 결정 검토 — DEC-021 ~ DEC-055 (2026-09-29)

되돌리기: **쉬움** = 파일·설정 몇 곳 되돌리면 끝 / **어려움** = 사용자 데이터·배포물·여러 플랫폼 계약에 흔적이 남음.
확인 필요 = 승인 전에 직접 판단해 주실 것. 나머지는 일괄 approve 대상.

| 번호 | 한 줄 내용 | 되돌리기 | 확인 필요 |
|---|---|---|---|
| DEC-021 | 문자열 키 일치 검사(strings_check.py) | 쉬움 | – (Phase 7 에서 Kotlin 으로 대체 예정) |
| DEC-022 | JDK 25 로 빌드 확인, 툴체인 고정 안 함 | 쉬움 | – **대체됨**(DEC-054) |
| DEC-023 | orca.sh 는 환경이 정상이면 JAVA_HOME/PATH 를 건드리지 않음 | 쉬움 | – |
| DEC-024 | `.claude/settings.json` 허용·차단 명령 목록 | 쉬움 | **필요** |
| DEC-025 | 토스 기반 UI 스타일 확정, 자체 팔레트 | 쉬움 | – |
| DEC-026 | core 공개 계약 변경은 auto 에서도 먼저 질문, SKIE 노출 기획 | 쉬움 | – |
| DEC-027 | iOS 는 같은 Phase 에 작성, mac 검증 대기로 누적 | 쉬움 | – |
| DEC-028 | Phase 자동 진행 + 정지 조건 | 쉬움 | – |
| DEC-029 | CI = GitHub Actions 3잡 | 쉬움 | – |
| DEC-030 | 빈 core/ui·core/util 삭제 | 쉬움 | – |
| DEC-031 | core androidResources 비활성 | 쉬움 | – |
| DEC-033 | 모듈 구성 core:common·core:data·feature:*·shared | 어려움 | – (사용자 결정) |
| DEC-034 | iOS framework 이름 Core 유지, umbrella `:shared` | 어려움 | **필요** |
| DEC-035 | 패키지 이동·initKoin 시그니처·StatefulViewModel (공개 계약) | 어려움 | – (사용자 승인됨) |
| DEC-036 | UseCase 기본 없음, 결과 타입·로거 미도입 | 쉬움 | – |
| DEC-037 | 모듈별 Koin 모듈 + initKoin 단일 진입점 | 쉬움 | – |
| DEC-038 | lifecycle 2.11.0 / Koin 4.2.2 | 쉬움 | – **대체됨**(DEC-043) |
| DEC-039 | build-logic convention plugin | 쉬움 | – |
| DEC-040 | Phase 3·5 진행 방향(사용자 지시 기록) | 쉬움 | – |
| DEC-041 | Phase 태그·단계별 커밋 | 쉬움 | – |
| DEC-042 | core API 이전 시 플랫폼 import 는 ocra 가 기계적으로 수정 | 쉬움 | – |
| DEC-043 | lifecycle 2.10.0(compileSdk 36 기준선 유지), Koin 4.2.2 | 쉬움 | – |
| DEC-044 | 타임아웃·로그(운영 제외)·query 인코딩·ContentNegotiation 제거 | 쉬움 | – |
| DEC-045 | 401 → 세션 만료(재발급 없음) | 쉬움 | **필요** |
| DEC-046 | SecureStorage 직접 구현 + 평문 토큰 마이그레이션 | 어려움 | **필요** |
| DEC-047 | useMock 제거 → Api interface + Koin 교체 | 쉬움 | – |
| DEC-048 | Push = 뼈대 + FCM 기본, 설정 없으면 NoOp | 쉬움 | – |
| DEC-049 | Phase 6 을 5 보다 먼저 | 쉬움 | – |
| DEC-050 | 테스트는 coroutines-test 만, 커버리지 = 규칙 검사 | 쉬움 | – |
| DEC-051 | new_feature.py 생성기 + :feature:example | 쉬움 | – (Phase 7 에서 Kotlin 으로 대체 예정) |
| DEC-052 | rename_app.py + template-setup 가이드 | 쉬움 | – (Phase 7 에서 Kotlin 으로 대체 예정) |
| DEC-053 | 푸시 공통 구현(PushManager·SessionListener 등, 공개 API 추가만) | 쉬움 | **필요**(iOS SPM) |
| DEC-054 | 빌드 데몬은 Corretto 21 고정(기존 설정) 확인 | 쉬움 | – |
| DEC-055 | D6 서버 계약 | – | **가정** (승인 대상 아님) |

## 확인 필요 항목

- **DEC-024 권한 목록** — 자동 진행을 위해 `git *`, `python *` 전체를 허용했다. `git reset --hard`·`git checkout -- .`·`git branch -D` 는 차단하지 않았다(임시 워크트리 정리에 사용). 더 좁힐지(예: `git reset --hard` 차단) 판단 필요.
- **DEC-034 framework 이름 `Core`** — Swift 는 `import Core` 그대로지만, Gradle 경로는 `:shared` 라 이름이 어긋난다. mac 검증 후 `Shared` 로 바꿀지(모든 Swift import 수정) 또는 `Core` 로 확정할지.
- **DEC-045 401 처리** — 지금은 401 이면 바로 로그아웃(세션 만료). 서버가 refresh token 을 주면 재발급 후 재시도가 맞다. 서버 계약(DEC-055)과 함께 결정.
- **DEC-046 보안 저장소** — 한 번 배포하면 토큰이 Keystore/Keychain 으로 옮겨져, 되돌리면 사용자가 재로그인해야 한다. 라이브러리 대신 직접 구현(Keychain cinterop 은 mac 미검증)으로 확정할지.
- **DEC-053 iOS Firebase SPM** — 설정 파일이 없어도 firebase-ios-sdk 를 항상 내려받아 링크한다(빌드 시간·앱 크기 증가). 대안: SPM 을 빼고 `#if canImport` 만 남겨 Firebase 를 쓸 앱에서만 추가.
