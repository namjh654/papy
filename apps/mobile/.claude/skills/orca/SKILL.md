---
name: orca
description: ocra 브랜치 오케스트레이션 절차 (자동/수동 모드). ocra 에서 공통 로직·병합·점검·결정 기록을 하고 Android/iOS 워크트리에 UI 작업을 나눌 때, 머신 전환 후 이어서 개발할 때, 종합 보고/재작업할 때 사용.
---

# Orca 절차

모든 상태는 `scripts/orca.sh` 로 다룬다. 사람용 설명은 `.orca/README.md`.

## 0. 시작
1. `scripts/orca.sh status` — 모드(auto/manual), 머신, 역할 확인.
2. `scripts/orca.sh sync` — `.orca/.cache/sync-digest.md` 만 읽는다. 전체 저장소 탐색 금지.
3. 할 일은 PROGRESS.md 의 현재 Phase 미완료 체크박스 + digest 의 "미결 결정" + notes 의 열린 요청에서 고른다.

## 1. ocra (메인 워크트리) 사이클
1. `scripts/orca.sh gate "Phase N: <작업>"` — 종료코드 3 이면 사용자에게 승인 요청 후 멈춤.
2. core 작업 → `scripts/orca.sh check` → 커밋.
3. 플랫폼 작업 분배: 워크트리가 없으면 `scripts/orca.sh setup`.
   - 서브에이전트를 쓸 때는 각각 `../kmp-android`, `../kmp-ios` 디렉터리에서 작업하게 하고
     프롬프트에 "해당 워크트리의 허용 경로만 수정, 시작 시 `scripts/orca.sh refresh`, 끝나면
     `scripts/orca.sh check <role>` 후 커밋, core 변경 필요 시 `.orca/notes/<role>.md` 에 요청" 을 넣는다.
   - iOS 는 mac 에서만 빌드 가능. 다른 머신이면 iOS 작업은 notes 에 남기고 건너뛴다.
4. `scripts/orca.sh merge android`, `scripts/orca.sh merge ios` — 범위 위반(종료코드 2)이면 해당 워크트리에서 수정.
5. notes 의 `[결정제안]` 과 작업 중 내린 결정 → `scripts/orca.sh decide "..." --area X --why "..."`.
   - auto: `검토대기` 로 두고 계속. manual: `승인대기` 면 의존 작업 중단, 사용자에게 질문.
6. PROGRESS.md 체크박스 갱신 → 커밋 → `scripts/orca.sh checkpoint "요약"` → push (manual 이면 push 전 `gate`).

## 2. 최종 보고 / 재작업
1. Phase 완료 조건 충족 시 `scripts/orca.sh report` → 생성된 보고서를 사용자에게 요약 보고
   (검토할 결정, 변경 요약, 미병합 상태, 열린 요청).
2. 사용자 판단에 따라 `approve DEC-###` / `rework DEC-### "사유"`.
3. 재작업 항목을 1 사이클로 처리 → `reopen DEC-###` → 다시 보고.

## 모드 차이 요약
워크트리·역할·기록은 동일. manual 은 gate/merge/decide/push 에서 사용자 승인 후 진행.
승인 받은 명령은 `ORCA_APPROVE=1 scripts/orca.sh ...` 로 재실행.
