# Orca — ocra 오케스트레이션

KMP 템플릿 개발을 **ocra(공통 로직·병합·점검)** + **Android/iOS 워크트리(Native UI·연결)** 로 나누어 진행하는 방법.
명령은 모두 `scripts/orca.sh` (Windows 는 Git Bash 또는 `scripts\orca.cmd`).

## 1. 역할

| 위치 | 브랜치 | 담당 | 수정 가능 경로 |
|---|---|---|---|
| 메인 워크트리 `kmp/` | `ocra` | 공통 비즈니스 로직, 소스 병합, 점검, 결정 기록, 종합 보고 | 전체 (UI 는 가급적 워크트리에 위임) |
| `../kmp-android/` | `ocra-android` | Compose UI, ViewModel 연결, Android 플랫폼 코드 | `androidApp/**`, `.orca/notes/android.md` |
| `../kmp-ios/` | `ocra-ios` | SwiftUI, SKIE/ViewModel 연결, iOS 플랫폼 코드 (mac 전용) | `iosApp/**`, `.orca/notes/ios.md` |

- 워크트리에서 core 변경이 필요하면 `notes/<role>.md` 에 요청 → ocra 가 구현·커밋 → 워크트리에서 `orca.sh refresh`.
- 범위 밖 변경은 `check`/`merge` 가 종료코드 2로 거부한다.

## 2. 자동 모드 / 수동 모드

워크트리와 역할은 **같다**. 다른 점은 승인 게이트뿐이다.

| 게이트 | 자동(auto) | 수동(manual) |
|---|---|---|
| 워크트리 → ocra 병합 (`merge`) | 범위·빌드 점검 통과 시 진행 | 사용자 승인 후 진행 |
| Phase 시작 / 작업 단위 시작 (`gate`) | 진행 | 사용자 승인 후 진행 |
| 결정 기록 (`decide`) | `검토대기` 로 기록, 작업 계속 | `승인대기` — 승인 전 의존 작업 중단 |
| push | 점검 통과 시 진행 | 사용자 승인 후 진행 |
| 최종 보고 (`report`) | 누적 결정 종합 검토 → 재작업 | 동일 |

```bash
scripts/orca.sh mode            # 현재 모드
scripts/orca.sh mode manual     # 수동 전환 (config 를 ocra 에 커밋하면 다른 머신에도 적용)
ORCA_MODE=manual scripts/orca.sh merge android   # 이번 한 번만 수동
```

수동 모드에서 게이트는 터미널이면 `y/N` 을 묻고, 비대화(Claude 실행)면 **종료코드 3** 으로 멈춘다.
Claude 는 사용자에게 승인을 받은 뒤 `ORCA_APPROVE=1` 로 같은 명령을 다시 실행한다.

## 3. 머신 전환 (mac ↔ windows) — 토큰 절약

```bash
scripts/orca.sh sync                   # 시작: 이 머신 마지막 체크포인트 이후 diff 요약
                                       #  → .orca/.cache/sync-digest.md (gitignore)
scripts/orca.sh checkpoint "한 일 요약"  # 종료: 현재 HEAD 를 SYNC.md 에 기록 후 ocra 에 커밋
git push                               # 다른 머신에서 이어받도록
```

- Claude 는 세션 시작 시 전체 코드를 훑지 않고 digest 와 거기 나온 파일만 읽는다.
- iOS 빌드/점검은 mac 에서만. Windows 에서는 `setup` 이 android 워크트리만 만든다.
- 체크포인트는 머신·브랜치별로 찾는다(같은 머신·브랜치 → 같은 머신 → 전체 순).

## 4. 한 사이클

```text
[ocra]     sync → (gate) core 작업 → check → commit
[android]  refresh → UI/연결 작업 → check android → commit      (병렬)
[ios]      refresh → UI/연결 작업 → check ios → commit          (병렬, mac)
[ocra]     merge android → merge ios → check → decide(필요 시) → checkpoint
[ocra]     report → 사용자 검토 → approve / rework → 재작업 사이클
```

## 5. 파일

| 파일 | 내용 | 누가 쓰나 |
|---|---|---|
| `config` | 모드 | `orca.sh mode` |
| `DECISIONS.md` | 결정 기록 (DEC-###, 상태) | ocra (`decide/approve/rework/reopen`) |
| `SYNC.md` | 머신/세션 체크포인트 | `orca.sh checkpoint`, `merge` |
| `notes/<role>.md` | 워크트리 → ocra 요청, 결정 제안, 작업 로그 | 각 워크트리 |
| `reports/REPORT-*.md` | 종합 보고서 | `orca.sh report` |
| `.cache/` | sync digest (커밋 안 함) | `orca.sh sync` |
