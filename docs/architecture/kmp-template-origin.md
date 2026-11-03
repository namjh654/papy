# KMP 템플릿 출처와 편입 기록

## 원본

| 항목 | 값 |
|---|---|
| 원본 저장소 | https://github.com/namjh654/kmp (비공개) |
| 원본 커밋 | `c5a0d93576b723d48cb1fbd92d0510feeeb5d584` (2026-10-30, 템플릿 v0.4.12) |
| 편입일 | 2026-11-03 |
| 선택 근거 | 후보 `kmp`(Native UI: Android Compose + iOS SwiftUI)와 `kmp-decompose`(Compose Multiplatform 화면 공유) 중, 기획서의 "Android Compose / iOS SwiftUI" 와 일치하는 `kmp` 를 사용자가 선택 |
| 원본 라이선스 | 원본 저장소에 LICENSE 파일 없음(저작자: 저장소 소유자 본인) |

## 편입 방식

- 서브모듈이 아닌 **복사 편입**. 원본 Git 이력은 가져오지 않았다(`git archive HEAD` 로 추적 파일만 복사).
- 제외: `.kotlin/` (원본에 실수로 커밋된 Kotlin 빌드 캐시 3개 파일).
- 템플릿의 설정·문서(`CLAUDE.md`, `.orca/`, `.claude/`, `docs/`, `CHANGELOG.md` 등)는 그대로 보존했다.

## 편입 후 변경

| 파일 | 변경 | 이유 |
|---|---|---|
| `scripts/orca.sh` | 프로젝트 루트를 `git rev-parse --show-toplevel` 대신 스크립트 위치 기준으로 계산, 메인 워크트리 경로에 하위 폴더(`apps/mobile`) 접두어를 붙임 | 모노레포 하위 폴더에서 `check` 가 `./gradlew` 를 찾지 못해 실패 |

## 공개 전 점검 결과 (2026-11-03)

- 추적 파일 중 키스토어(`*.jks`/`*.keystore`), `*.p12`, provisioning profile, `google-services.json`, `GoogleService-Info.plist`, `.env`, `env/*.local.properties` 없음.
- API 키 형태 문자열(Google `AIza…`, GitHub 토큰, PEM 개인키) 검색 결과 없음. 이메일은 `example.com` 등 예시 값뿐.
- 개인 절대 경로(`/Users/…`) 없음. iOS `DEVELOPMENT_TEAM` 은 `$(TEAM_ID)` 변수.
- 참고: `env/dev.properties` 에 템플릿 개발용 Postman Mock Server 주소가 공개 값으로 들어 있다. 비밀값은 아니지만 Papy 에서는 쓰지 않으므로 이름 변경 시 함께 정리를 권장한다.

## 식별자 변경 (2026-11-03 완료)

템플릿의 `renameApp` 도구(`./gradlew -p build-logic :tooling:run --args="renameApp …"`)로 패키지·앱 이름·번들 ID 를 바꾸고,
도구가 다루지 않는 접두어·리소스·딥링크는 같은 규칙으로 일괄 치환했다. `.orca/` 기록과 `CHANGELOG.md` 의 과거 항목, renameApp 의 옛 이름 검출식은 원문 그대로 둔다.

| 항목 | 원본 값 | 바꾼 값 |
|---|---|---|
| Gradle 루트 프로젝트명 | `elsaapp` | `papykmp` |
| 앱 표시명 | `ELSA` (+ `Dev`/`Stg`) | `Papy` (+ `Dev`/`Stg`) |
| Android namespace / applicationId · Kotlin 패키지 | `cloud.elsa.app` | `io.github.namjh654.papy` (데스크톱 앱 식별자와 같음) |
| iOS Bundle ID | `cloud.elsa.app.elsaapp$(BUNDLE_ID_SUFFIX)` | `io.github.namjh654.papy$(BUNDLE_ID_SUFFIX)` |
| iOS Team ID | 템플릿 작성자 값 | 비움 — 배포 시 본인 Team ID |
| 딥링크 스킴 | `elsa://` | `papy://` |
| UI 컴포넌트·토큰·리소스 접두어 | `Elsa*`, `elsa_*`, `.elsaFont` 등 | `Papy*`, `papy_*`, `.papyFont` |
| 저장소 이름(DataStore·Keychain 등) | `elsa.*` | `papy.*` |

또한 템플릿 기능(로그인·회원가입·푸시·소셜 로그인·채팅 등) 중 상당수는 Papy 원칙(무계정·오프라인)과 맞지 않는다 —
2026-11-03 결정: 이번에는 이름만 바꾸고 기능은 유지한다. 어떤 기능을 걷어낼지는 모바일 개발을 시작할 때 정한다.
