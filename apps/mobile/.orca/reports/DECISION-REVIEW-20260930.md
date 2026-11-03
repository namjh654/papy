# 결정 검토 묶음 — kmp (검토대기 36건, 2026-09-30)

## 처리 방법

```
scripts/orca.sh approve DEC-0xx DEC-0yy …     # 확정
scripts/orca.sh rework  DEC-0xx "이유"          # 재작업
```

내용 전문은 [.orca/DECISIONS.md](../DECISIONS.md). 아래 "권장"은 Claude 의 의견이고 확정은 사용자가 한다.

## A. 사용자가 이미 지시한 것 — 확정만 하면 된다 (5)

| ID | 내용 |
|---|---|
| DEC-064 | deny 규칙에 `git reset --hard`·`git branch -D` 추가 (DEC-024 재작업 반영) |
| DEC-065 | 라이브러리 승인: Turbine·Kover·Kermit·Firebase Crashlytics/Analytics |
| DEC-066 | iOS framework 이름 Core 확정 |
| DEC-095 | push 는 Phase 끝·머신 이동 전에 모아서 |
| DEC-096 | CI 는 예시·문서만 |

## B. 작업 방식·문서·검증 기록 — 되돌리기 쉬움, 권장: 확정 (12)

DEC-024(프로젝트 권한), 062(원격 이름 정리), 063(iOS check 를 scheme 빌드로), 076(merge 실패 안내), 083(release.md), 084·085(Phase 11 정리 목록·결과),
087(템플릿 전 과정 검증 기록), 090(임시 워크트리 규칙), 092(known-limitations 한 곳), 093(v0.2 버전 조합), 097(Windows 에서 iOS Kotlin 컴파일)

## C. 구현 패턴 — 양 플랫폼 실행 확인됨, 권장: 확정 (15)

| ID | 내용 | 바꾸려면 |
|---|---|---|
| DEC-069·070 | 페이징(`Page`·`ListState`·`Paginator`)과 목록 공통 패턴 | 보통 — 공개 API |
| DEC-071 | 하단 탭(Android 단일 NavHost, iOS TabView) | 보통 |
| DEC-072 | 설정 화면(테마·언어) | 쉬움 |
| DEC-073 | 네트워크 상태·웹뷰 직접 구현(라이브러리 없음) | 쉬움 |
| DEC-074 | Turbine·Kover 적용 방식 | 쉬움 |
| DEC-075 | debug 빌드만 로컬 평문 허용 | 쉬움 |
| DEC-077 | 버전을 `env/common.properties` 한 곳에서 | 쉬움 |
| DEC-078·079 | Kermit 로깅, 크래시·이벤트 인터페이스(NoOp 기본) | 보통 |
| DEC-081·082 | 강제·선택 업데이트, 인앱 리뷰 | 보통 |
| DEC-086 | iOS Team ID 를 xcconfig 한 곳으로 | 쉬움 |
| DEC-088·089 | 다크 모드 판정 한 곳, Android 백업 제외 | 쉬움 |

## D. 서버·외부 서비스가 정해지면 다시 볼 것 — 지금은 가정, 권장: 확정하되 표시 유지 (3)

| ID | 내용 | 다시 볼 때 |
|---|---|---|
| DEC-045 | 401 → 세션 만료 → 로그인 (2026-09-30 Android 에뮬레이터에서 확인) | 서버의 401 규약 확정 |
| DEC-068 | [가정] 토큰 재발급·목록 API 모양 | 서버 계약 확정 |
| DEC-094 | Firebase deprecated 토큰 API 정리 보류 | 실제 Firebase 프로젝트 연결 |

## E. 사용자가 직접 정해야 하는 것 (1 + 보류 3)

| 항목 | 물음 | 권장 |
|---|---|---|
| DEC-091 | `phase-2`·`phase-3` 브랜치에 ocra 로 병합되지 않은 커밋 2개(작업내역 정리, phase1 complete)가 남아 있다. 지울지 둘지 | 내용이 ocra 에 이미 반영된 옛 기록이면 삭제. 확인 후 `git branch -D` 는 사용자가 직접 |
| Koin 업그레이드 (PROGRESS Phase 1 보류) | 기준선 라이브러리 | 새 앱 시작 직전에 한 번에 |
| R8 의 Kotlin 2.4 메타데이터 경고 (Phase 8 보류) | R8 버전을 올릴지 | AGP 다음 안정판 때 같이. 2026-09-30 prodRelease(R8) 실행은 문제 없음 |
| UI 자동 테스트 (Phase 6 옵션) | Compose UI 테스트·XCUITest 타깃을 넣을지 | 템플릿에는 넣지 않고 앱에서 필요할 때 |
