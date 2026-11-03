# CI (문서만 — 지금은 켜져 있지 않다)

이 템플릿은 **CI 를 돌리지 않는다**. GitHub Actions 설정(러너·비밀 값·요금)을 해 두지 않았기 때문에
워크플로 파일을 `.github/workflows/` 에 두지 않고, 예시를 [ci/github-actions.yml](ci/github-actions.yml) 로만 남겼다.
점검은 로컬에서 `scripts/orca.sh check [--env dev|staging|prod]` 로 한다 (mac 에서는 iOS 빌드까지).

## 켜는 방법 (새 앱에서 CI 가 필요할 때)

1. 예시를 복사한다: `mkdir -p .github/workflows && cp docs/ci/github-actions.yml .github/workflows/ci.yml`
2. 브랜치 이름(`on.push.branches`)을 저장소에 맞게 고친다.
3. push 하면 잡 3개가 돈다.

| 잡 | 러너 | 하는 일 |
|---|---|---|
| 템플릿 점검 + KMP 테스트 | ubuntu | `./gradlew templateCheck testAndroidHostTest` + Kover 리포트(아티팩트 `kover-report`) |
| Android dev · staging · prod | ubuntu | `:androidApp:assemble<Env>Debug`, prod 는 `assembleProdRelease`(R8 축소 경로) |
| iOS | macos | 공유 scheme `iosApp-dev` 시뮬레이터 빌드(서명 없음) |

## 알아 둘 것

- 2026-09-30 꺼 두기 전 마지막 실행(`ocra`·`ocra-android`·`ocra-ios` push): **5개 잡 모두 통과** — 템플릿 점검 + KMP 테스트 1m, Android dev·staging·prod 각 1m10s, iOS xcodebuild 2m30s.
- **이 예시는 실제로 돌려 본 적이 없다.** 로컬 `orca.sh check` 와 같은 명령을 옮겨 적은 것이다. 처음 켤 때 러너의 Xcode·JDK 버전을 확인한다.
- macOS 러너는 분당 요금이 ubuntu 의 약 10배다. 비공개 저장소라면 iOS 잡을 `pull_request` 에서만 돌리는 것을 권한다.
- 비밀 값(`env/*.local.properties`, Firebase 설정 파일, 릴리스 서명)은 저장소에 없다. CI 에서 릴리스 서명·Firebase 가 필요하면
  GitHub Secrets 에서 파일로 풀어 쓰는 단계를 추가한다(없으면 unsigned·NoOp 으로 빌드된다).
- 다른 CI(GitLab CI 등)를 쓰면 표의 명령만 옮기면 된다.
