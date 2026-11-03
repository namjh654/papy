# CLAUDE.md

## 목적

이 저장소의 최종 목표는 **새 앱을 시작할 때 복사해서 바로 쓰는, 정형화된 KMP 템플릿**이다.
모든 판단은 이 기준으로 한다: 새 기능을 추가하는 방법이 한 가지로 정해져 있고, 따라 하기 쉬울 것.
템플릿 목적에 맞는 개선은 묻지 않고 진행하고 결정으로 기록한다(정지 조건은 그대로). 과한 추상화·지금 안 쓰는 라이브러리는 추가하지 않는다.

KMP 템플릿 (`:core` KMP + `androidApp` Compose + `iosApp` SwiftUI). 구조는 [ARCHITECTURE.md](ARCHITECTURE.md), 진행·Phase 는 [PROGRESS.md](PROGRESS.md).
개발은 Orca 방식으로 진행한다 — 상세: [.orca/README.md](.orca/README.md), 절차: `/orca` 스킬.

## 세션 시작 (토큰 절약 — 전체 코드 훑기 금지)

1. `scripts/orca.sh status` → 모드·머신·브랜치/역할 확인
2. `scripts/orca.sh sync` → `.orca/.cache/sync-digest.md` 만 읽는다
3. digest 에 나온 파일과 **현재 작업에 필요한 파일만** 연다. ARCHITECTURE/PROGRESS 는 필요한 절만 읽는다.
   체크포인트가 없을 때만 ARCHITECTURE §1, PROGRESS §1·§3·§5, `.orca/DECISIONS.md` 를 읽는다.

## 역할 (현재 브랜치로 판단)

- `ocra`: 계획, core 공통 비즈니스 로직, 병합(`orca.sh merge`), 점검, 판단·결정 기록, 보고. UI 작업은 워크트리에 위임.
  필요하면 임시 워크트리에서 작업한다: `orca.sh tmp <이름>` → `<메인>-tmp-<이름>`(브랜치 `ocra-tmp-<이름>`).
  **정리 규칙**: `orca.sh merge tmp <이름>` 이 병합(+점검)에 성공하면 같은 단계에서
  `git worktree remove` → `git branch -d` → `git worktree prune` 으로 정리한다. 실패하면 강제(`--force`, `-D`)하지 않고 보고한다.
  병합할 커밋이 없는 검증 전용 워크트리도 같은 명령으로 정리한다(커밋 안 된 변경이 있으면 멈춤 — 버릴지 사용자에게 확인).
  역할 워크트리(`-android`, `-ios`)는 정리 대상이 아니다.
  `checkpoint`·`status`·`preflight` 는 남은 임시 워크트리(`app-phase-*`, `*-tmp-*` 등 역할 외 워크트리)를 경고한다.
- `ocra-android`: 플랫폼 UI 와 로직 연결만. `androidApp/**` 만 수정. core 변경이 필요하면 `.orca/notes/android.md` 에 요청.
- `ocra-ios`: 플랫폼 UI 와 로직 연결만. `iosApp/**` 만 수정 (mac 전용, 다른 머신에서는 코드만 작성하고 빌드는 "mac 대기"). core 변경이 필요하면 `.orca/notes/ios.md` 에 요청.

## 모드

- `auto`: 게이트 자동 통과. 결정은 `orca.sh decide` 로 `검토대기` 기록 후 계속 진행, 최종 `report` 에서 종합 보고.
- `manual`: 역할·워크트리는 같다. `orca.sh gate/merge` 가 종료코드 3 이면 **멈추고 사용자에게 승인 요청**,
  승인 후 `ORCA_APPROVE=1` 로 재실행. `decide` 결과가 `승인대기` 면 그 결정에 의존하는 작업을 하지 않는다.
- 모드 변경은 사용자가 요청할 때만.

## 규칙

- 결정(라이브러리 선택, 구조·계약 변경, 범위 조정 등)은 반드시 `orca.sh decide` 로 남긴다. 워크트리에서는 notes 에 `[결정제안]`.
- 커밋 전 `orca.sh check [role]` (범위 + 빌드). iOS 빌드는 mac 에서만.
- 세션 종료 시 `orca.sh checkpoint "한 일 요약"`. push 는 아래 규칙에 따른다.
- 머신 이동 전: `orca.sh preflight` (워크트리 미커밋·미병합·남은 임시 워크트리·origin push·태그) 가 통과해야 한다.
- 문서 갱신은 변경된 부분만 (PROGRESS 체크박스, DECISIONS 행).
- Phase 가 끝날 때마다 `checkpoint` → `report` 로 종합 보고 후 **멈춘다**. 사용자가 approve / rework 로 판단한다.
- 기준선(SKIE, Koin, 로컬 스토리지 구조, 코루틴 비동기, 로그인·홈 흐름 — ARCHITECTURE §1.5)은 재분석하지 않는다.
  바꿔야 하거나 되돌리기 어려운 결정은 auto 모드라도 진행 전에 사용자에게 묻는다.
- push 는 Phase 가 끝날 때, 또는 머신 이동 전 마무리(`orca.sh preflight`) 때 모아 둔 커밋을 한 번에 한다. 그 사이에는 push 하지 않는다.
- 새 화면은 처음부터 다크 모드·다국어(문자열 리소스) 적용.
- 환경 값은 `env/*.properties` 가 원본(docs/environments.md). 코드는 `AppConfig` 값만 쓰고 환경 이름으로 분기하지 않는다. 점검은 `scripts/orca.sh check [--env staging|prod]`.
- UI 스타일은 토스 기반(큰 제목, 넓은 여백, 채움형 입력칸, 무테 카드, 하단 고정 CTA, 바텀시트). 로고·브랜드 색·일러스트 복제 금지, 자체 팔레트. `design/reference/` 캡처가 생기면 그 기준으로 재조정 (DEC-025).
- core 공개 계약(UiState·Repository·UseCase 시그니처, AppError 구조) 변경은 auto 모드라도 먼저 사용자에게 묻는다.
  core 공개 API 는 기획 단계에서 Swift(SKIE) 노출 형태를 함께 적는다 (DEC-026).
- iOS 화면은 같은 Phase 안에서 ocra-ios 워크트리에 같이 작성한다. Windows 에서 검증 못 한 iOS 작업은 멈추지 않고
  `.orca/notes/ios.md` 에 Phase 별 "mac 검증 대기"(Phase, 커밋, 바뀐 파일, 확인 항목)로 쌓고, 보고서·PROGRESS 에
  "mac 검증 전 미확정"으로 표시한다 (DEC-027).
- Windows 빌드: 시스템 JAVA_HOME = Android Studio jbr(JDK 25, 런처만). 빌드 데몬은 Corretto 21 고정(DEC-054). `orca.sh` 는 JAVA_HOME/PATH 가 잘못됐을 때만
  보정한다(대체 JDK: `ORCA_JAVA_HOME` 또는 jbr, DEC-023).
  테스트 JVM 이 깨지면 `./gradlew --stop` 후 재실행.
