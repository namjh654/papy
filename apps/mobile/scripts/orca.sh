#!/usr/bin/env bash
# Orca — ocra 브랜치 오케스트레이션 도구
#
#   ocra 브랜치(메인 워크트리) : 공통 비즈니스 로직(core), 소스 병합, 점검, 결정 기록
#   ocra-android 워크트리      : androidApp UI + 공통 로직 연결
#   ocra-ios 워크트리          : iosApp UI + 공통 로직 연결
#
# 자동/수동 모드는 "승인 게이트(gate)"만 다르다. 워크트리와 역할은 동일하다.
# macOS 기본 bash 3.2 와 Windows Git Bash 에서 동작하도록 작성한다
# (연관 배열, ${var,,}, sed -i 사용 금지).
#
# 사용법: scripts/orca.sh help

set -euo pipefail

ORCA_BRANCH="ocra"
ROLES="android ios"

die() { echo "orca: $*" >&2; exit 1; }
info() { echo "orca: $*"; }

git rev-parse --show-toplevel >/dev/null 2>&1 || die "git 저장소 안에서 실행하세요."
# 프로젝트 루트 = 이 스크립트의 상위 폴더. 모노레포(papy/apps/mobile)처럼 git 최상위가 아니어도 된다.
ROOT=$(cd "$(dirname "$0")/.." && pwd -P)
PREFIX=$(git -C "$ROOT" rev-parse --show-prefix)
# 첫 번째 worktree 가 메인(ocra) 워크트리. 모드/결정/동기화 기록은 항상 메인 쪽에 둔다.
MAIN=$(git worktree list --porcelain | awk '/^worktree /{print substr($0,10); exit}')
MAIN="${MAIN%/}${PREFIX:+/${PREFIX%/}}"
ORCA="$MAIN/.orca"
CONFIG="$ORCA/config"
DECISIONS="$ORCA/DECISIONS.md"
SYNC="$ORCA/SYNC.md"
CACHE="$ORCA/.cache"
REPORTS="$ORCA/reports"

today() { date +%F; }
now() { date '+%F %H:%M'; }

# ---------------------------------------------------------------- 환경 판별

machine() {
  if [ -n "${ORCA_MACHINE:-}" ]; then echo "$ORCA_MACHINE"; return; fi
  case "$(uname -s)" in
    Darwin) echo mac ;;
    MINGW*|MSYS*|CYGWIN*) echo windows ;;
    *) echo linux ;;
  esac
}

current_branch() { git rev-parse --abbrev-ref HEAD; }

role_of_branch() {
  case "$1" in
    "$ORCA_BRANCH") echo orca ;;
    "$ORCA_BRANCH"-android) echo android ;;
    "$ORCA_BRANCH"-ios) echo ios ;;
    *) echo other ;;
  esac
}

current_role() { role_of_branch "$(current_branch)"; }

role_branch() { echo "$ORCA_BRANCH-$1"; }

role_dir() {
  local parent name
  parent=$(dirname "$MAIN"); name=$(basename "$MAIN")
  echo "$parent/$name-$1"
}

# 임시 워크트리: <메인 폴더>-tmp-<이름> / 브랜치 ocra-tmp-<이름>. merge tmp 가 성공하면 같은 단계에서 정리한다.
tmp_branch() { echo "$ORCA_BRANCH-tmp-$1"; }
tmp_dir() { role_dir "tmp-$1"; }

# 메인·역할(android/ios) 워크트리가 아닌 워크트리 목록("경로 브랜치") — 남은 임시 워크트리 경고용
stray_worktrees() {
  local path="" branch="" line
  { git worktree list --porcelain; echo; } | while IFS= read -r line; do
    case "$line" in
      "worktree "*) path="${line#worktree }" ;;
      "branch "*) branch="${line#branch refs/heads/}" ;;
      detached) branch="(detached)" ;;
      "")
        if [ -n "$path" ] && [ "$path" != "$MAIN" ] && [ "$path" != "$(role_dir android)" ] && [ "$path" != "$(role_dir ios)" ]; then
          echo "$path $branch"
        fi
        path=""; branch="" ;;
    esac
  done
}

warn_stray_worktrees() {
  local stray; stray=$(stray_worktrees)
  [ -z "$stray" ] && return 0
  echo "orca: [경고] 남은 임시 워크트리가 있습니다 (역할 워크트리 android/ios 는 제외):" >&2
  echo "$stray" | sed 's/^/  /' >&2
  echo "orca: 병합했으면 'orca.sh merge tmp <이름>' 이 정리한다. 직접 할 때: git worktree remove <경로> → git branch -d <브랜치> → git worktree prune (강제 옵션 금지)" >&2
  return 1
}

# 새 워크트리에 커밋되지 않는 로컬 파일(local.properties = Android SDK 위치)을 복사한다. 없으면 그 워크트리에서 Android 빌드가 안 된다
copy_local_files() {
  local dir="$1"
  if [ -f "$MAIN/local.properties" ] && [ ! -e "$dir/local.properties" ]; then
    cp "$MAIN/local.properties" "$dir/local.properties"
  fi
}

# 정리: worktree remove → branch -d → prune. 실패해도 강제하지 않고 보고만 한다(종료코드 0)
cleanup_tmp() {
  local dir="$1" br="$2" ok=1
  if [ -d "$dir" ]; then
    # core.longpaths: Windows 에서 build/ 안의 긴 경로 때문에 폴더가 반쯤 지워진 채 남는 것을 막는다
    git -c core.longpaths=true worktree remove "$dir" || { echo "orca: [정리 실패] $dir — 커밋 안 된 변경이 있거나 잠겨 있다. 강제로 지우지 않았다." >&2; ok=0; }
  fi
  if [ $ok = 1 ] && git show-ref --verify --quiet "refs/heads/$br"; then
    git branch -d "$br" || { echo "orca: [정리 실패] $br — 병합되지 않은 커밋이 있다. 강제로 지우지 않았다." >&2; ok=0; }
  fi
  git worktree prune
  if [ $ok = 1 ]; then info "임시 워크트리 정리: $dir, $br"; fi
  return 0
}

# 역할별 수정 허용 경로(접두사). 이 밖의 변경은 check/merge 에서 거부된다.
allowed_paths() {
  case "$1" in
    android) echo "androidApp/ .orca/notes/android.md" ;;
    ios) echo "iosApp/ .orca/notes/ios.md" ;;
    *) echo "" ;;
  esac
}

# ---------------------------------------------------------------- config

config_get() {
  [ -f "$CONFIG" ] || { echo "$2"; return; }
  local v
  v=$(awk -F= -v k="$1" '$1==k {print $2}' "$CONFIG" | tail -n 1)
  [ -n "$v" ] && echo "$v" || echo "$2"
}

config_set() {
  local tmp="$CONFIG.tmp"
  mkdir -p "$ORCA"
  touch "$CONFIG"
  awk -F= -v k="$1" -v v="$2" 'BEGIN{done=0} $1==k {print k"="v; done=1; next} {print} END{if(!done) print k"="v}' "$CONFIG" > "$tmp"
  mv "$tmp" "$CONFIG"
}

mode() {
  local m="${ORCA_MODE:-$(config_get mode manual)}"
  case "$m" in auto|manual) echo "$m" ;; *) die "알 수 없는 모드: $m (auto|manual)" ;; esac
}

# ---------------------------------------------------------------- 승인 게이트
# 자동 모드: 점검이 통과했다면 바로 진행 (결정은 '검토대기'로 남아 최종 보고에서 검토).
# 수동 모드: ORCA_APPROVE=1 이거나 터미널에서 y 입력 시에만 진행. 아니면 종료코드 3.
#            Claude 는 종료코드 3을 받으면 사용자에게 승인을 요청하고 멈춘다.

gate() {
  local what="$1"
  if [ "$(mode)" = auto ]; then
    info "[auto] 통과: $what"
    return 0
  fi
  if [ "${ORCA_APPROVE:-0}" = 1 ]; then
    info "[manual] 승인됨(ORCA_APPROVE=1): $what"
    return 0
  fi
  if [ -t 0 ] && [ -t 1 ]; then
    printf 'orca: [manual] 승인 필요 — %s\n진행할까요? [y/N] ' "$what"
    local ans; read -r ans
    case "$ans" in y|Y|yes) return 0 ;; esac
  fi
  echo "orca: [manual] 승인 필요 — $what" >&2
  echo "orca: 사용자 승인 후 ORCA_APPROVE=1 로 다시 실행하세요." >&2
  exit 3
}

# ---------------------------------------------------------------- 명령: status / mode

cmd_status() {
  local role; role=$(current_role)
  echo "모드        : $(mode)$([ -n "${ORCA_MODE:-}" ] && echo ' (ORCA_MODE 환경변수)')"
  echo "머신        : $(machine)"
  echo "현재 위치   : $ROOT"
  echo "브랜치/역할 : $(current_branch) / $role"
  echo "메인(ocra)  : $MAIN"
  echo
  echo "워크트리:"
  git worktree list | sed 's/^/  /'
  warn_stray_worktrees || true
  echo
  local last; last=$(last_checkpoint "$(machine)" "")
  echo "이 머신 마지막 체크포인트: ${last:-없음}"
  if [ -f "$DECISIONS" ]; then
    echo "결정 상태: 승인대기 $(count_status 승인대기) · 검토대기 $(count_status 검토대기) · 재작업 $(count_status 재작업)"
  fi
}

cmd_mode() {
  if [ $# -eq 0 ]; then mode; return; fi
  case "$1" in auto|manual) ;; *) die "사용법: orca.sh mode [auto|manual]" ;; esac
  config_set mode "$1"
  info "모드 = $1  (워크트리/역할은 그대로, 승인 게이트만 변경)"
  if [ "$(git -C "$MAIN" rev-parse --abbrev-ref HEAD)" = "$ORCA_BRANCH" ] && ! git -C "$MAIN" diff --quiet -- .orca/config; then
    git -C "$MAIN" commit -q -m "orca: mode $1" -- .orca/config && info "ocra 에 커밋됨 (push 하면 다른 머신에도 적용)"
  fi
}

# ---------------------------------------------------------------- 명령: setup

cmd_setup() {
  [ "$ROOT" = "$MAIN" ] || die "setup 은 메인(ocra) 워크트리에서 실행하세요: $MAIN"
  local targets="${1:-}"
  if [ -z "$targets" ]; then
    # iOS 빌드는 macOS 에서만 가능하므로 Windows 에서는 android 만 기본 생성.
    if [ "$(machine)" = mac ]; then targets="android ios"; else targets="android"; fi
  elif [ "$targets" = all ]; then
    targets="$ROLES"
  fi
  git show-ref --verify --quiet "refs/heads/$ORCA_BRANCH" || die "'$ORCA_BRANCH' 브랜치가 없습니다."
  local r br dir
  for r in $targets; do
    br=$(role_branch "$r"); dir=$(role_dir "$r")
    if ! git show-ref --verify --quiet "refs/heads/$br"; then
      if git show-ref --verify --quiet "refs/remotes/origin/$br"; then
        git branch "$br" "origin/$br"
      else
        git branch "$br" "$ORCA_BRANCH"
      fi
      info "브랜치 생성: $br"
    fi
    if [ -d "$dir" ]; then
      info "이미 있음: $dir"
    else
      git worktree add "$dir" "$br"
      copy_local_files "$dir"
      info "워크트리 생성: $dir ($br)"
    fi
  done
}

# ---------------------------------------------------------------- 명령: check

# $1=role, $2=range (예: ocra...ocra-android). 허용 경로 밖 변경 파일을 출력.
scope_violations() {
  local allowed; allowed=$(allowed_paths "$1")
  [ -n "$allowed" ] || return 0
  git diff --name-only "$2" | while IFS= read -r f; do
    [ -n "$f" ] || continue
    local ok=0 p
    for p in $allowed; do
      case "$f" in "$p"*) ok=1 ;; esac
    done
    [ $ok = 1 ] || echo "$f"
  done
}

run() { echo "+ $*"; "$@"; }

# iOS framework(Core) 를 만드는 umbrella 모듈. KMP 모듈 테스트는 testAndroidHostTest 로 전부 돈다.
UMBRELLA=":shared"

# 점검 환경 (check --env 로 바꾼다. merge 뒤 점검은 기본 dev)
APP_ENV="${ORCA_ENV:-dev}"

# Windows 전용 환경 보정 (DEC-016). 환경이 정상이면 아무것도 하지 않는다.
#  - JAVA_HOME 이 비었거나 잘못됐을 때만(따옴표·$ 포함, java.exe 없음) ORCA_JAVA_HOME → Android Studio jbr 로 대체
#  - PATH 에 따옴표·$ 가 든 항목이 있을 때만 그 항목을 제거 (host test 의 -Djava.library.path 인자를 깨뜨림)
# mac/linux 에서는 호출 즉시 반환한다.
windows_java_env() {
  [ "$(machine)" = windows ] || return 0
  local jh="${JAVA_HOME:-}" cand ok=1
  case "$jh" in ''|*'"'*|*'$'*) ok=0 ;; *) [ -x "$(cygpath -u "$jh" 2>/dev/null)/bin/java.exe" ] || ok=0 ;; esac
  if [ $ok = 0 ]; then
    jh=""
    for cand in "${ORCA_JAVA_HOME:-}" "/c/Program Files/Android/Android Studio/jbr"; do
      [ -n "$cand" ] && [ -x "$(cygpath -u "$cand")/bin/java.exe" ] && { jh=$(cygpath -w "$cand"); break; }
    done
    if [ -n "$jh" ]; then
      info "JAVA_HOME 이 유효하지 않아 $jh 사용 (ORCA_JAVA_HOME 으로 지정 가능)"
      export JAVA_HOME="$jh"
    fi
  fi
  if printf '%s' "$PATH" | tr ':' '\n' | grep -q '["$]'; then
    info "PATH 의 따옴표/\$ 포함 항목 제거"
    PATH=$(printf '%s' "$PATH" | tr ':' '\n' | grep -v '["$]' | paste -sd: -)
    export PATH
  fi
}

gradle() {
  windows_java_env
  if [ "$(machine)" = windows ] && [ -f "$MAIN/gradlew.bat" ] && ! [ -x "$ROOT/gradlew" ]; then
    run cmd //c gradlew.bat "$@"
  else
    run ./gradlew "$@"
  fi
}

# iOS 앱 빌드. 환경별 공유 scheme(iosApp-<env>)으로 빌드한다 (서명 없이 시뮬레이터용).
# -target 모드는 SPM 패키지를 패키지 폴더의 build/ 에 만들어 nanopb 의 BUILD 파일과 충돌한다(대소문자 무시 파일시스템).
# ORCA_IOS_BUILD 로 명령을 바꾸거나 ORCA_IOS_BUILD=skip 으로 생략할 수 있다.
ios_app_build() {
  case "${ORCA_IOS_BUILD:-}" in
    skip) info "xcodebuild 생략 (ORCA_IOS_BUILD=skip)" ;;
    "") run xcodebuild -quiet -project iosApp/iosApp.xcodeproj -scheme "iosApp-$APP_ENV" -configuration "Debug-$APP_ENV" \
          -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build ;;
    *) run sh -c "$ORCA_IOS_BUILD" ;;
  esac
}


# 역할별 점검
#   android : core 테스트 + Android 빌드
#   ios     : core iOS 컴파일 + xcodebuild (mac 전용)
#   ocra    : 토큰 + android 전체 + (mac 이면) ios 전체, (mac 이 아니면) iOS Kotlin 컴파일만
build_checks() {
  local role="$1"
  # 환경(dev/staging/prod): check --env <env> 또는 ORCA_ENV. KMP AppConfig·Android flavor·iOS 구성이 모두 같은 환경 (docs/environments.md)
  local env_cap; env_cap="$(printf '%s' "$APP_ENV" | cut -c1 | tr '[:lower:]' '[:upper:]')$(printf '%s' "$APP_ENV" | cut -c2-)"
  info "환경: $APP_ENV"
  ( cd "$ROOT"
    case "$role" in
      android) gradle -Papp.env="$APP_ENV" testAndroidHostTest ":androidApp:assemble${env_cap}Debug" ;;
      ios)
        [ "$(machine)" = mac ] || die "iOS 점검은 macOS 에서만 가능합니다."
        gradle -Papp.env="$APP_ENV" "$UMBRELLA:compileKotlinIosSimulatorArm64"
        ios_app_build ;;
      *)
        # templateCheck = 디자인 토큰 최신 + 문자열 키 + 아키텍처 규칙 + 환경 설정 일치 (build-logic, JDK 만 필요)
        gradle -Papp.env="$APP_ENV" templateCheck testAndroidHostTest ":androidApp:assemble${env_cap}Debug"
        if [ "$(machine)" = mac ]; then
          gradle -Papp.env="$APP_ENV" "$UMBRELLA:compileKotlinIosSimulatorArm64"
          ios_app_build
        else
          # mac 이 아니어도 iOS 쪽 Kotlin(iosMain)은 klib 로 컴파일된다(Kotlin 교차 컴파일). 링크·Swift·실행은 mac 에서만
          gradle -Papp.env="$APP_ENV" "$UMBRELLA:compileKotlinIosSimulatorArm64"
          info "iOS Kotlin 컴파일까지 확인. framework 링크·xcodebuild·실행은 mac 에서 수행 (이 머신: $(machine))"
        fi ;;
    esac )
}

cmd_check() {
  local role="" build=1 a expect_env=0
  APP_ENV="${ORCA_ENV:-dev}"
  for a in "$@"; do
    if [ $expect_env = 1 ]; then APP_ENV="$a"; expect_env=0; continue; fi
    case "$a" in
      --no-build) build=0 ;;
      --env) expect_env=1 ;;
      --env=*) APP_ENV="${a#--env=}" ;;
      *) role="$a" ;;
    esac
  done
  case "$APP_ENV" in dev|staging|prod) ;; *) die "알 수 없는 환경: $APP_ENV (dev|staging|prod)" ;; esac
  [ -n "$role" ] || role=$(current_role)
  if [ "$role" = android ] || [ "$role" = ios ]; then
    local br; br=$(role_branch "$role")
    local bad; bad=$(scope_violations "$role" "$ORCA_BRANCH...$br")
    # 현재 워크트리의 커밋 안 된 변경도 검사
    if [ "$(current_role)" = "$role" ]; then
      bad="$bad
$(scope_violations "$role" HEAD)"
    fi
    bad=$(echo "$bad" | awk 'NF' | sort -u)
    if [ -n "$bad" ]; then
      echo "orca: [$role] 역할 범위 밖 변경:" >&2
      echo "$bad" | sed 's/^/  /' >&2
      echo "orca: 공통 로직 변경은 .orca/notes/$role.md 에 요청으로 남기고 ocra 에서 처리하세요." >&2
      exit 2
    fi
    info "[$role] 범위 점검 통과 ($(allowed_paths "$role"))"
  fi
  [ $build = 1 ] && build_checks "$role"
  info "check 완료 ($role)"
}

# ---------------------------------------------------------------- 명령: refresh / merge

cmd_refresh() {
  local role; role=$(current_role)
  [ "$role" = android ] || [ "$role" = ios ] || die "refresh 는 android/ios 워크트리에서 실행하세요."
  git diff --quiet && git diff --cached --quiet || die "커밋 안 된 변경이 있습니다."
  run git merge --no-edit "$ORCA_BRANCH"
}

cmd_merge() {
  local role="${1:-}"; shift || true
  if [ "$role" = tmp ]; then cmd_merge_tmp "$@"; return; fi
  case "$role" in android|ios) ;; *) die "사용법: orca.sh merge <android|ios> | merge tmp <이름> [--no-build]" ;; esac
  [ "$ROOT" = "$MAIN" ] && [ "$(current_branch)" = "$ORCA_BRANCH" ] || die "merge 는 메인 워크트리의 $ORCA_BRANCH 브랜치에서 실행하세요."
  git diff --quiet && git diff --cached --quiet || die "ocra 에 커밋 안 된 변경이 있습니다."
  local br; br=$(role_branch "$role")
  git show-ref --verify --quiet "refs/heads/$br" || die "$br 브랜치가 없습니다. (orca.sh setup)"
  if [ -z "$(git log --oneline "$ORCA_BRANCH..$br")" ]; then info "$br 에 병합할 커밋이 없습니다."; return 0; fi

  local bad; bad=$(scope_violations "$role" "$ORCA_BRANCH...$br")
  if [ -n "$bad" ]; then
    echo "orca: $br 에 역할 범위 밖 변경이 있어 병합하지 않습니다:" >&2
    echo "$bad" | sed 's/^/  /' >&2
    exit 2
  fi
  echo "병합 대상 ($br → $ORCA_BRANCH):"
  git log --oneline "$ORCA_BRANCH..$br" | sed 's/^/  /'
  git diff --stat "$ORCA_BRANCH...$br" | tail -n 1

  gate "$br → $ORCA_BRANCH 병합"
  run git merge --no-ff -m "orca: merge $br into $ORCA_BRANCH" "$br"
  if [ "${1:-}" != --no-build ]; then
    if ! build_checks orca; then
      echo "orca: 병합 후 점검 실패. 'git revert -m 1 HEAD' 로 되돌리거나(기록 유지) 워크트리에서 고친 뒤 다시 merge 하세요." >&2
      exit 1
    fi
  fi
  append_sync "merge $br"
}

# ---------------------------------------------------------------- 명령: uitest (iOS UI 흐름 테스트, mac 전용)
# 로컬 목업 서버를 띄우고, dev 앱을 그 서버로 빌드해 시뮬레이터에 새로 설치한 뒤 iosApp/UITests 를 돌린다.
# env/dev.local.properties 는 잠시 바꿨다가 원래대로 돌려놓는다. 결과 번들(화면 캡처 포함): build/uitest/result.xcresult

uitest_cleanup() {
  [ -n "${UITEST_SERVER:-}" ] && kill "$UITEST_SERVER" 2>/dev/null
  # gradlew 를 끊어도 :tooling:run 이 남는 경우가 있어 포트를 쓰는 프로세스도 정리한다
  [ -n "${UITEST_PORT:-}" ] && lsof -ti "tcp:$UITEST_PORT" -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null
  if [ -f "${UITEST_BACKUP:-/nonexistent}" ]; then mv "$UITEST_BACKUP" "$UITEST_ENV"; elif [ -n "${UITEST_ENV:-}" ]; then rm -f "$UITEST_ENV"; fi
}

cmd_uitest() {
  [ "$(machine)" = mac ] || die "uitest 는 macOS 에서만 가능합니다."
  local sim="" port=8765 only="" capture="" a expect=""
  for a in "$@"; do
    if [ -n "$expect" ]; then eval "$expect=\$a"; expect=""; continue; fi
    case "$a" in
      --sim) expect=sim ;;
      --port) expect=port ;;
      --only) expect=only ;;
      --capture) capture=1 ;;
      *) die "사용법: orca.sh uitest [--sim '<시뮬레이터 이름 또는 UDID>'] [--port 8765] [--only test05_notice] [--capture]" ;;
    esac
  done
  cd "$ROOT"
  UITEST_PORT="$port"
  lsof -ti "tcp:$port" -sTCP:LISTEN >/dev/null 2>&1 && die "포트 $port 를 이미 쓰고 있습니다 (--port 로 바꾸거나 종료)"
  local out="$ROOT/build/uitest"; mkdir -p "$out"
  local udid
  if [ -n "$sim" ]; then
    udid=$(xcrun simctl list devices available | grep -F "$sim" | grep -oE '[0-9A-F-]{36}' | head -n 1 || true)
  else
    udid=$(xcrun simctl list devices booted | grep -oE '[0-9A-F-]{36}' | head -n 1 || true)
    [ -n "$udid" ] || udid=$(xcrun simctl list devices available | grep -E 'iPhone' | grep -oE '[0-9A-F-]{36}' | head -n 1 || true)
  fi
  [ -n "$udid" ] || die "시뮬레이터를 찾지 못했습니다 (--sim 으로 지정)"
  xcrun simctl boot "$udid" 2>/dev/null || true
  info "시뮬레이터: $udid"

  # 1) 목업 서버
  # trap 은 함수가 끝난 뒤 돌기 때문에 지역 변수가 아닌 UITEST_* 를 쓴다
  UITEST_ENV="$ROOT/env/dev.local.properties"; UITEST_BACKUP="$out/dev.local.properties.backup"; UITEST_SERVER=""
  local local_env="$UITEST_ENV" backup="$UITEST_BACKUP"
  # uitest all 이 env 를 한 번만 바꾸고 되돌린다 → 여기서는 건드리지 않는다
  if [ -n "${ORCA_UITEST_ENV_MANAGED:-}" ]; then UITEST_ENV=""; UITEST_BACKUP=""; fi
  [ -n "$UITEST_ENV" ] && [ -f "$local_env" ] && cp "$local_env" "$backup"
  trap uitest_cleanup EXIT
  ./gradlew -q -p build-logic :tooling:run --args="mockServer --port $port" > "$out/mock-server.log" 2>&1 &
  UITEST_SERVER=$!
  local i; for i in $(seq 1 120); do curl -sf "http://127.0.0.1:$port/__mock/health" >/dev/null && break; sleep 1; done
  curl -sf "http://127.0.0.1:$port/__mock/health" >/dev/null || die "목업 서버가 뜨지 않았습니다 ($out/mock-server.log)"
  [ -z "$UITEST_ENV" ] || { [ -f "$backup" ] && { grep -v '^api.baseUrl=' "$backup" || true; }; echo "api.baseUrl=http://127.0.0.1:$port"; } > "$local_env"

  # 2) dev 앱 빌드(시뮬레이터용 ad-hoc 서명 — Keychain 이 동작해야 자동 로그인을 볼 수 있다) → 새로 설치
  run xcodebuild -quiet -project iosApp/iosApp.xcodeproj -scheme iosApp-dev -configuration Debug-dev \
    -destination "id=$udid" -derivedDataPath "$out/DerivedData" CODE_SIGN_IDENTITY=- build
  local app; app=$(find "$out/DerivedData/Build/Products/Debug-dev-iphonesimulator" -maxdepth 1 -name '*.app' | head -n 1)
  local bundle; bundle=$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$app/Info.plist")
  xcrun simctl uninstall "$udid" "$bundle" 2>/dev/null || true
  run xcrun simctl install "$udid" "$app"

  # 3) UI 테스트 (TEST_RUNNER_ 접두어 환경 변수는 테스트 프로세스에 그대로 전달된다)
  rm -rf "$out/result.xcresult"
  run env TEST_RUNNER_APP_BUNDLE_ID="$bundle" TEST_RUNNER_MOCK_URL="http://127.0.0.1:$port" TEST_RUNNER_CAPTURE="${capture:-0}" \
    xcodebuild -quiet test -project iosApp/UITests/ElsaUITests.xcodeproj -scheme ElsaUITests \
      -destination "id=$udid" -derivedDataPath "$out/DerivedData-tests" -resultBundlePath "$out/result.xcresult" \
      ${only:+-only-testing:ElsaUITests/AppFlowTests/$only} CODE_SIGN_IDENTITY=-
  info "uitest 통과 — 결과: build/uitest/result.xcresult (open 으로 열면 화면 캡처)"
}

# ---------------------------------------------------------------- 명령: uitest android (Android UI 흐름 테스트, 모든 머신)
# 로컬 목업 서버를 띄우고, 연결된 에뮬레이터·기기에 dev 앱과 :androidUiTest 를 설치해 돌린다(UiAutomator 로 설치된 앱을 밖에서 조작).
# env/dev.local.properties 는 잠시 바꿨다가 원래대로 돌려놓는다. 결과: androidUiTest/build/reports/androidTests/connected/debug/index.html

uitest_android_cleanup() {
  # set -e 아래에서 도는 trap 이라 실패해도 다음 줄로 가게 || true 를 붙인다(목업 서버가 먼저 끝났을 수 있다)
  [ -z "${UTA_SERVER:-}" ] || kill "$UTA_SERVER" 2>/dev/null || true
  # gradlew 를 끊어도 :tooling:run 이 띄운 JVM 이 남는다 → 포트를 쓰는 프로세스를 정리한다
  if [ -n "${UTA_PORT:-}" ]; then
    if [ "$(machine)" = windows ]; then
      netstat -ano 2>/dev/null | grep -E "127.0.0.1:${UTA_PORT}[[:space:]].*LISTENING" | awk '{print $NF}' | sort -u \
        | while read -r pid; do taskkill //F //PID "$pid" >/dev/null 2>&1 || true; done || true
    else
      lsof -ti "tcp:$UTA_PORT" -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null || true
    fi
  fi
  if [ -f "${UTA_BACKUP:-/nonexistent}" ]; then mv "$UTA_BACKUP" "$UTA_ENV"; elif [ -n "${UTA_ENV:-}" ]; then rm -f "$UTA_ENV"; fi
}

# adb: ANDROID_HOME → ANDROID_SDK_ROOT → local.properties 의 sdk.dir → PATH
android_adb() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$sdk" ] && [ -f "$ROOT/local.properties" ]; then
    sdk=$(grep '^sdk.dir=' "$ROOT/local.properties" | cut -d= -f2- | sed 's/\\\\/\\/g; s/\\:/:/g')
  fi
  local candidate
  for candidate in "$sdk/platform-tools/adb" "$sdk/platform-tools/adb.exe"; do
    [ -n "$sdk" ] && [ -x "$candidate" ] && { echo "$candidate"; return; }
  done
  command -v adb || die "adb 를 찾지 못했습니다 (ANDROID_HOME 또는 local.properties 의 sdk.dir)"
}

# 연결된 기기가 없으면 AVD(ORCA_AVD, 기본 Pixel_9)를 켜고 부팅이 끝날 때까지 기다린다(DEC-121)
ensure_android_device() {
  local adb="$1"
  [ "$("$adb" get-state 2>/dev/null)" = device ] && return 0
  local avd="${ORCA_AVD:-Pixel_9}" emulator
  emulator="$(dirname "$(dirname "$adb")")/emulator/emulator"
  [ -x "$emulator" ] || [ -x "$emulator.exe" ] || die "에뮬레이터·기기가 연결되지 않았고 emulator 를 찾지 못했습니다 (adb devices)"
  info "에뮬레이터 켜는 중: $avd (ORCA_AVD 로 바꿀 수 있음)"
  nohup "$emulator" -avd "$avd" -no-snapshot-save -no-boot-anim >/dev/null 2>&1 &
  local i
  for i in $(seq 1 180); do
    [ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && { info "에뮬레이터 준비됨"; return 0; }
    sleep 2
  done
  die "에뮬레이터가 6분 안에 켜지지 않았습니다 ($avd)"
}

cmd_uitest_android() {
  local port=8765 dport="" only="" capture="" a expect=""
  for a in "$@"; do
    if [ -n "$expect" ]; then eval "$expect=\$a"; expect=""; continue; fi
    case "$a" in
      --port) expect=port ;;
      --device-port) expect=dport ;;
      --only) expect=only ;;
      --capture) capture=1 ;;
      *) die "사용법: orca.sh uitest android [--port 8765] [--device-port 8765] [--only test05_notice] [--capture]" ;;
    esac
  done
  cd "$ROOT"
  windows_java_env
  local adb; adb=$(android_adb)
  ensure_android_device "$adb"
  curl -sf "http://127.0.0.1:$port/__mock/health" >/dev/null 2>&1 && die "포트 $port 를 이미 쓰고 있습니다 (--port 로 바꾸거나 종료)"
  local out="$ROOT/build/uitest-android"; mkdir -p "$out"

  # 1) 목업 서버 (trap 은 함수가 끝난 뒤 돌기 때문에 전역 UTA_* 를 쓴다)
  # 기기 쪽 포트(앱이 부르는 주소)는 따로 둘 수 있다 — uitest all 은 앱 주소를 iOS 와 같게(8765) 두고 목업 서버만 나눈다
  dport="${dport:-$port}"
  UTA_PORT="$port"; UTA_ENV="$ROOT/env/dev.local.properties"; UTA_BACKUP="$out/dev.local.properties.backup"; UTA_SERVER=""
  if [ -n "${ORCA_UITEST_ENV_MANAGED:-}" ]; then UTA_ENV=""; UTA_BACKUP=""; fi
  [ -n "$UTA_ENV" ] && [ -f "$UTA_ENV" ] && cp "$UTA_ENV" "$UTA_BACKUP"
  trap uitest_android_cleanup EXIT
  ./gradlew -q -p build-logic :tooling:run --args="mockServer --port $port" > "$out/mock-server.log" 2>&1 &
  UTA_SERVER=$!
  local i; for i in $(seq 1 120); do curl -sf "http://127.0.0.1:$port/__mock/health" >/dev/null 2>&1 && break; sleep 1; done
  curl -sf "http://127.0.0.1:$port/__mock/health" >/dev/null 2>&1 || die "목업 서버가 뜨지 않았습니다 ($out/mock-server.log)"

  # 2) 기기의 127.0.0.1:<port> → 이 컴퓨터의 목업 서버 (앱과 테스트가 같은 주소를 쓴다)
  run "$adb" reverse "tcp:$dport" "tcp:$port"
  [ -z "$UTA_ENV" ] || { [ -f "$UTA_BACKUP" ] && { grep -v '^api.baseUrl=' "$UTA_BACKUP" || true; }; echo "api.baseUrl=http://127.0.0.1:$dport"; } > "$UTA_ENV"

  # 3) dev 앱 + 테스트 설치·실행 (끝나면 Gradle 이 둘 다 지운다)
  run ./gradlew :androidUiTest:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.mockUrl=http://127.0.0.1:$dport" \
    ${only:+"-Pandroid.testInstrumentationRunnerArguments.class=cloud.elsa.app.uitest.AppFlowTest#$only"} \
    ${capture:+"-Pandroid.testInstrumentationRunnerArguments.capture=true"}
  info "uitest android 통과 — 결과: androidUiTest/build/reports/androidTests/connected/debug/index.html (실패 화면 글자: adb logcat -s AppFlowTest)"
}

# ---------------------------------------------------------------- 명령: uitest all (두 플랫폼 동시, DEC-121)
# Android(포트 8765)·iOS(포트 8766)를 각자 목업 서버와 함께 동시에 돌린다. 로그: build/uitest-all/{android,ios}.log

cmd_uitest_all() {
  [ "$(machine)" = mac ] || die "uitest all 은 macOS 에서만 가능합니다(iOS 포함). Android 만: orca.sh uitest android"
  cd "$ROOT"
  local out="$ROOT/build/uitest-all" sim="" a expect=""
  for a in "$@"; do
    if [ -n "$expect" ]; then eval "$expect=\$a"; expect=""; continue; fi
    case "$a" in
      --sim) expect=sim ;;
      *) die "사용법: orca.sh uitest all [--sim '<시뮬레이터 이름 또는 UDID>']" ;;
    esac
  done
  mkdir -p "$out"
  # 앱 주소는 두 플랫폼 모두 127.0.0.1:8765 — iOS 목업 서버는 8765, Android 는 adb reverse 로 기기 8765 → 맥 8767(다른 목업 서버).
  # env/dev.local.properties 는 여기서 한 번만 바꾸고 끝나면 되돌린다(각 실행기는 건드리지 않음)
  UALL_ENV="$ROOT/env/dev.local.properties"; UALL_BACKUP="$out/dev.local.properties.backup"
  rm -f "$UALL_BACKUP"; [ -f "$UALL_ENV" ] && cp "$UALL_ENV" "$UALL_BACKUP"
  trap 'if [ -f "$UALL_BACKUP" ]; then mv "$UALL_BACKUP" "$UALL_ENV"; else rm -f "$UALL_ENV"; fi' EXIT
  { [ -f "$UALL_BACKUP" ] && { grep -v '^api.baseUrl=' "$UALL_BACKUP" || true; }; echo "api.baseUrl=http://127.0.0.1:8765"; } > "$UALL_ENV"
  export ORCA_UITEST_ENV_MANAGED=1
  ( set -e; cmd_uitest_android --port 8767 --device-port 8765 ) > "$out/android.log" 2>&1 &
  local android=$!
  ( set -e; cmd_uitest --port 8765 ${sim:+--sim "$sim"} ) > "$out/ios.log" 2>&1 &
  local ios=$!
  local ra=0 ri=0
  wait "$android" || ra=$?
  wait "$ios" || ri=$?
  info "Android: $([ $ra = 0 ] && echo 통과 || echo "실패($ra)") — $out/android.log"
  info "iOS: $([ $ri = 0 ] && echo 통과 || echo "실패($ri)") — $out/ios.log"
  [ $ra = 0 ] && [ $ri = 0 ] || die "uitest all 실패"
}

# ---------------------------------------------------------------- 명령: capture (디자인 비교 캡처, DEC-112)
# 두 플랫폼의 testCapture_auth(같은 순서·같은 이름, 한국어 — 인증 7개 + 메시지·커뮤니티 4개)를 돌려 화면을 모으고 나란히 보는 페이지를 만든다.
# 결과: build/capture/index.html (+ android/·ios/ PNG). 한쪽 기기가 없으면 그쪽만 비워 둔다.

cmd_capture() {
  cd "$ROOT"
  local out="$ROOT/build/capture" test=testCapture_auth adb=""
  rm -rf "$out"; mkdir -p "$out/android" "$out/ios"
  adb=$(android_adb 2>/dev/null || true)
  if [ -n "$adb" ] && [ "$("$adb" get-state 2>/dev/null)" = device ]; then
    # 글자 크기·화면 크기·굵은 글씨를 기본값으로 맞춰 찍고 되돌린다(접근성 설정이 바뀐 기기면 비교가 안 된다)
    local scale density bold
    scale=$("$adb" shell settings get system font_scale | tr -d '\r')
    density=$("$adb" shell wm density | sed -n 's/^Override density: //p' | tr -d '\r')
    bold=$("$adb" shell settings get secure font_weight_adjustment | tr -d '\r')
    "$adb" shell settings put system font_scale 1.0
    "$adb" shell wm density reset
    "$adb" shell settings put secure font_weight_adjustment 0
    # `( … ) || …` 로 부르면 bash 가 안쪽의 set -e 를 무시해 실패가 통과로 보인다 → 따로 받는다
    local status
    set +e
    ( set -e; cmd_uitest_android --only "$test" --capture )
    status=$?
    set -e
    "$adb" shell settings put system font_scale "${scale:-1.0}"
    [ -n "$density" ] && "$adb" shell wm density "$density"
    [ "$bold" = null ] || "$adb" shell settings put secure font_weight_adjustment "${bold:-0}"
    [ $status = 0 ] || die "Android 캡처 실패"
    run "$adb" pull /data/local/tmp/elsa-capture/. "$out/android"
  else
    info "주의: Android 기기·에뮬레이터가 없어 Android 캡처를 건너뜁니다"
  fi
  if [ "$(machine)" = mac ]; then
    # 기본 글자 크기·라이트 모드로 찍고 되돌린다. Android 목업 서버가 포트를 늦게 놓는 경우가 있어 iOS 는 다른 포트를 쓴다
    local size look status
    size=$(xcrun simctl ui booted content_size 2>/dev/null || true)
    look=$(xcrun simctl ui booted appearance 2>/dev/null || true)
    xcrun simctl ui booted content_size large 2>/dev/null || true
    xcrun simctl ui booted appearance light 2>/dev/null || true
    set +e
    ( set -e; cmd_uitest --only "$test" --capture --port 8766 )
    status=$?
    set -e
    [ -n "$size" ] && xcrun simctl ui booted content_size "$size" 2>/dev/null || true
    [ -n "$look" ] && xcrun simctl ui booted appearance "$look" 2>/dev/null || true
    [ $status = 0 ] || die "iOS 캡처 실패"
    local raw="$out/ios-raw"; mkdir -p "$raw"
    run xcrun xcresulttool export attachments --path "$ROOT/build/uitest/result.xcresult" --output-path "$raw"
    # manifest.json: exportedFileName ↔ suggestedHumanReadableName("<NN-이름>_0_<UUID>.png") → <NN-이름>.png
    # 화면 녹화·UI 스냅샷 같은 다른 첨부도 섞여 있으므로 캡처 이름(두 자리 번호-이름)만 꺼낸다
    grep -oE '"exportedFileName" *: *"[^"]+"|"suggestedHumanReadableName" *: *"[^"]+"' "$raw/manifest.json" \
      | sed -E 's/^"[^"]+" *: *"([^"]+)"$/\1/' | paste - - | while IFS=$'\t' read -r file name; do
        case "$name" in [0-9][0-9]-*_*.png) cp "$raw/$file" "$out/ios/${name%%_*}.png" ;; esac
      done
  else
    info "주의: macOS 가 아니라 iOS 캡처를 건너뜁니다"
  fi
  capture_page "$out"
  info "capture 완료 — $out/index.html"
}

capture_page() {  # $1=출력 폴더 → index.html (화면 이름마다 Android | iOS)
  local out="$1" name
  {
    echo '<!doctype html><meta charset="utf-8"><title>디자인 비교</title>'
    echo '<style>body{font:14px -apple-system,sans-serif;margin:24px;background:#f4f4f5;color:#18181b}h1{font-size:20px}'
    echo 'section{background:#fff;border-radius:12px;padding:16px;margin:0 0 20px}h2{font-size:15px;margin:0 0 12px}'
    echo '.row{display:flex;gap:24px;align-items:flex-start}.col{flex:0 0 360px}.col p{margin:0 0 6px;color:#71717a}'
    echo 'img{width:360px;border:1px solid #e4e4e7;border-radius:8px}.none{width:360px;height:120px;display:grid;place-items:center;border:1px dashed #d4d4d8;border-radius:8px;color:#a1a1aa}</style>'
    echo "<h1>디자인 비교 — $(date '+%Y-%m-%d %H:%M') · $(git rev-parse --short HEAD)</h1>"
    for name in $( (ls "$out/android" "$out/ios" 2>/dev/null) | grep '\.png$' | sed 's/\.png$//' | sort -u); do
      echo "<section><h2>$name</h2><div class=row>"
      for p in android ios; do
        if [ -f "$out/$p/$name.png" ]; then echo "<div class=col><p>$p</p><img src=\"$p/$name.png\"></div>"
        else echo "<div class=col><p>$p</p><div class=none>캡처 없음</div></div>"; fi
      done
      echo "</div></section>"
    done
  } > "$out/index.html"
}

# ---------------------------------------------------------------- 동기화 기록 (머신 전환)
# SYNC.md 표: | 시각 | 머신 | 브랜치 | 커밋 | 메모 |
# 머신이 바뀌면 이 머신의 마지막 체크포인트(없으면 전체의 마지막) 이후 diff 만 본다.

last_checkpoint() {  # $1=machine(빈값=모두) $2=branch(빈값=모두) → commit
  [ -f "$SYNC" ] || return 0
  awk -F'|' -v m="$1" -v b="$2" '
    /^\| 20[0-9][0-9]-/ {
      gsub(/ /, "", $3); gsub(/ /, "", $4); gsub(/[ `]/, "", $5)
      if ((m=="" || $3==m) && (b=="" || $4==b)) c=$5
    }
    END { if (c!="") print c }' "$SYNC"
}

append_sync() {
  local note="$1" head
  head=$(git rev-parse --short HEAD)
  [ -f "$SYNC" ] || die "$SYNC 가 없습니다."
  printf '| %s | %s | %s | `%s` | %s |\n' "$(now)" "$(machine)" "$(current_branch)" "$head" "$note" >> "$SYNC"
  # 기록 커밋은 메인(ocra) 워크트리에서만, SYNC.md 한 파일만 커밋한다.
  if [ "$(git -C "$MAIN" rev-parse --abbrev-ref HEAD)" = "$ORCA_BRANCH" ]; then
    git -C "$MAIN" commit -q -m "orca: checkpoint $(machine) $(current_branch) $head" -- .orca/SYNC.md \
      && info "체크포인트 기록: $(machine) $(current_branch) $head"
  else
    info "체크포인트 기록(미커밋): 메인 워크트리가 $ORCA_BRANCH 가 아님"
  fi
}

# 임시 워크트리 병합: 병합(+점검)이 성공하면 같은 단계에서 워크트리·브랜치를 정리한다. 역할 워크트리는 여기서 다루지 않는다.
cmd_merge_tmp() {
  local name="${1:-}"; shift || true
  [ -n "$name" ] || die "사용법: orca.sh merge tmp <이름> [--no-build]"
  [ "$ROOT" = "$MAIN" ] && [ "$(current_branch)" = "$ORCA_BRANCH" ] || die "merge 는 메인 워크트리의 $ORCA_BRANCH 브랜치에서 실행하세요."
  git diff --quiet && git diff --cached --quiet || die "ocra 에 커밋 안 된 변경이 있습니다."
  local br dir; br=$(tmp_branch "$name"); dir=$(tmp_dir "$name")
  git show-ref --verify --quiet "refs/heads/$br" || die "$br 브랜치가 없습니다. (orca.sh tmp $name)"
  if [ -d "$dir" ] && [ -n "$(git -C "$dir" status --porcelain)" ]; then
    die "$dir 에 커밋 안 된 변경이 있습니다. 커밋할지(병합) 버릴지 먼저 정하세요."
  fi
  if [ -n "$(git log --oneline "$ORCA_BRANCH..$br")" ]; then
    echo "병합 대상 ($br → $ORCA_BRANCH):"
    git log --oneline "$ORCA_BRANCH..$br" | sed 's/^/  /'
    gate "$br → $ORCA_BRANCH 병합"
    run git merge --no-ff -m "orca: merge $br into $ORCA_BRANCH" "$br"
    if [ "${1:-}" != --no-build ]; then
      if ! build_checks orca; then
        echo "orca: 병합 후 점검 실패 — 임시 워크트리는 남겨 둔다. 'git revert -m 1 HEAD' 로 되돌리거나 고친 뒤 다시 merge 하세요." >&2
        exit 1
      fi
    fi
    append_sync "merge $br"
  else
    info "$br 에 병합할 커밋이 없습니다(검증 전용) — 정리만 한다."
  fi
  cleanup_tmp "$dir" "$br"
}

# 임시 워크트리 만들기: <메인>-tmp-<이름> (브랜치 ocra-tmp-<이름>, ocra 에서 분기)
cmd_tmp() {
  local name="${1:-}"
  [ -n "$name" ] || die "사용법: orca.sh tmp <이름>"
  local br dir; br=$(tmp_branch "$name"); dir=$(tmp_dir "$name")
  [ -e "$dir" ] && die "$dir 가 이미 있습니다."
  run git worktree add "$dir" -b "$br" "$ORCA_BRANCH"
  copy_local_files "$dir"
  info "끝나면 커밋 후 ocra 에서: orca.sh merge tmp $name (병합 성공 시 같은 단계에서 정리)"
}

# 머신 이동 전 점검: 워크트리 미커밋 · 역할/임시 브랜치 미병합 · 남은 임시 워크트리 · origin push(브랜치·태그)
cmd_preflight() {
  local problems=0 path br n
  git fetch -q origin 2>/dev/null || info "fetch 실패 — push 상태는 마지막으로 알던 origin 기준"
  echo "워크트리 미커밋:"
  for path in $(git worktree list --porcelain | awk '/^worktree /{print $2}'); do
    n=$(git -C "$path" status --porcelain 2>/dev/null | wc -l | tr -d ' ')
    echo "  $path: $n"
    [ "$n" = 0 ] || problems=$((problems + 1))
  done
  echo "ocra 로 미병합 커밋:"
  for br in "$(role_branch android)" "$(role_branch ios)" $(git for-each-ref --format='%(refname:short)' "refs/heads/$ORCA_BRANCH-tmp-*"); do
    git show-ref --verify --quiet "refs/heads/$br" || continue
    n=$(git rev-list --count "$ORCA_BRANCH..$br")
    echo "  $br: $n"
    [ "$n" = 0 ] || problems=$((problems + 1))
  done
  warn_stray_worktrees || problems=$((problems + 1))
  echo "origin push:"
  for br in "$ORCA_BRANCH" main; do
    if ! git show-ref --verify --quiet "refs/remotes/origin/$br"; then echo "  $br: origin 에 없음"; problems=$((problems + 1)); continue; fi
    n=$(git rev-list --count "origin/$br..$br")
    echo "  $br: push 안 된 커밋 $n"
    [ "$n" = 0 ] || problems=$((problems + 1))
  done
  local remote_tags missing
  remote_tags=$(git ls-remote --tags origin 2>/dev/null | awk '{print $2}' | sed 's#refs/tags/##; s#\^{}##' | sort -u)
  missing=$(git tag -l | sort | comm -23 - <(echo "$remote_tags"))
  echo "  origin 에 없는 태그: $(echo ${missing:-없음})"
  [ -z "$missing" ] || problems=$((problems + 1))
  if [ $problems = 0 ]; then info "머신 이동 전 점검 통과"; else echo "orca: 머신 이동 전 점검 — 확인할 항목 $problems 개" >&2; return 1; fi
}

cmd_checkpoint() { append_sync "${*:-checkpoint}"; warn_stray_worktrees || true; }

area_of() {
  case "$1" in
    core/*) echo "공통(core)" ;;
    androidApp/*) echo "Android" ;;
    iosApp/*) echo "iOS" ;;
    .orca/*|*.md|docs/*) echo "기록/문서" ;;
    *) echo "빌드/기타" ;;
  esac
}

cmd_sync() {
  local fetch=1 a
  for a in "$@"; do [ "$a" = --offline ] && fetch=0; done
  if [ $fetch = 1 ] && git remote | grep -q .; then
    git fetch -q origin 2>/dev/null || info "fetch 실패 — 로컬 기준으로 진행"
  fi
  local m base src
  m=$(machine)
  base=$(last_checkpoint "$m" "$(current_branch)")
  src="이 머신($m)·이 브랜치 마지막 체크포인트"
  if [ -z "$base" ] || ! git cat-file -e "$base^{commit}" 2>/dev/null; then
    base=$(last_checkpoint "$m" ""); src="이 머신($m) 마지막 체크포인트"
  fi
  if [ -z "$base" ] || ! git cat-file -e "$base^{commit}" 2>/dev/null; then
    base=$(last_checkpoint "" ""); src="전체 마지막 체크포인트"
  fi

  mkdir -p "$CACHE"
  local out="$CACHE/sync-digest.md"
  {
    echo "# Sync digest — $(now) · $m · $(current_branch) · 모드 $(mode)"
    echo
    if [ -z "$base" ] || ! git cat-file -e "$base^{commit}" 2>/dev/null; then
      echo "체크포인트 없음 → 첫 동기화. ARCHITECTURE.md §1, PROGRESS.md §1·§3, .orca/DECISIONS.md 만 읽고 시작."
    else
      echo "기준: \`$base\` ($src) → \`$(git rev-parse --short HEAD)\`"
      echo
      echo "## 커밋"
      git log --oneline "$base..HEAD" | sed 's/^/- /'
      [ -n "$(git log --oneline "$base..HEAD")" ] || echo "- (없음)"
      echo
      echo "## 변경 파일 (영역별)"
      git diff --name-status "$base..HEAD" | while IFS="$(printf '\t')" read -r st f rest; do
        [ -n "$f" ] || continue
        [ -n "${rest:-}" ] && f="$rest"
        echo "$(area_of "$f")	$st $f"
      done | sort | awk -F'\t' '{ if ($1!=prev) { print "### " $1; prev=$1 } print "- " $2 }'
      echo
      echo "## 결정 변경 (DECISIONS.md)"
      git diff "$base..HEAD" -- .orca/DECISIONS.md | grep '^+| DEC-' | sed 's/^+/  /' || echo "  (없음)"
      echo
      echo "## 워크트리 요청 (notes)"
      git diff "$base..HEAD" -- .orca/notes | grep -E '^\+- \[' | sed 's/^+/  /' || echo "  (없음)"
    fi
    echo
    echo "## 병합 대기 (역할 브랜치 → $ORCA_BRANCH)"
    local r br
    for r in $ROLES; do
      br=$(role_branch "$r")
      if git show-ref --verify --quiet "refs/heads/$br"; then
        echo "- $br: $(git rev-list --count "$ORCA_BRANCH..$br") 커밋 앞섬, $(git rev-list --count "$br..$ORCA_BRANCH") 커밋 뒤처짐"
      fi
    done
    echo
    echo "## 미결 결정"
    if [ -f "$DECISIONS" ]; then
      [ -n "$(decisions_by_status '승인대기|검토대기|재작업')" ] && decisions_by_status '승인대기|검토대기|재작업' || echo "(없음)"
    fi
    echo
    echo "## 작업트리 미커밋 변경"
    git status --short | sed 's/^/    /'
  } > "$out"
  cat "$out"
  echo
  info "요약 저장: ${out#$MAIN/}  (Claude 는 이 파일과 여기 나온 파일만 읽는다)"
}

# ---------------------------------------------------------------- 결정 기록
# DECISIONS.md 표: | ID | 날짜 | 모드 | 영역 | 결정 | 근거 | 상태 |
# 상태: 승인대기(manual) → 확정 / 검토대기(auto, 최종 보고에서 검토) → 확정 | 재작업 → 검토대기

# 상태 컬럼(마지막 칸)이 정규식 $1 과 일치하는 결정 행 출력
decisions_by_status() {
  [ -f "$DECISIONS" ] || return 0
  awk -F'|' -v re="$1" '/^\| DEC-/ { st=$8; gsub(/^ +| +$/, "", st); if (st ~ "^(" re ")$") print }' "$DECISIONS"
}

count_status() { decisions_by_status "$1" | awk 'END{print NR}'; }

next_id() {
  local n
  n=$(grep -oE '^\| DEC-[0-9]+' "$DECISIONS" | sed 's/.*DEC-//' | sort -n | tail -n 1)
  printf 'DEC-%03d' "$(( 10#${n:-0} + 1 ))"
}

cmd_decide() {
  local area="공통" why="-" title=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --area) area="$2"; shift 2 ;;
      --why) why="$2"; shift 2 ;;
      *) title="${title:+$title }$1"; shift ;;
    esac
  done
  [ -n "$title" ] || die '사용법: orca.sh decide "결정 내용" [--area 영역] [--why "근거"]'
  local id st m
  id=$(next_id); m=$(mode)
  [ "$m" = auto ] && st="검토대기" || st="승인대기"
  title=$(echo "$title" | tr '|' '/'); why=$(echo "$why" | tr '|' '/')
  printf '| %s | %s | %s | %s | %s | %s | %s |\n' "$id" "$(today)" "$m" "$area" "$title" "$why" "$st" >> "$DECISIONS"
  info "$id 기록 ($st)"
  [ "$m" = manual ] && info "수동 모드: 사용자 승인(orca.sh approve $id) 전에는 이 결정에 의존하는 작업을 진행하지 않습니다."
  return 0
}

set_status() {  # $1=id $2=status $3=memo(optional)
  grep -qE "^\| $1 \|" "$DECISIONS" || die "$1 없음"
  local tmp="$DECISIONS.tmp"
  awk -F'|' -v OFS='|' -v id="$1" -v st="$2" -v memo="${3:-}" '
    { split($2, a, " ") }
    a[1]==id && $0 ~ /^\|/ {
      $8=" " st " "
      if (memo!="") $7=$7 "(" memo ") "
    }
    { print }' "$DECISIONS" > "$tmp"
  mv "$tmp" "$DECISIONS"
  info "$1 → $2"
}

cmd_approve() { [ $# -gt 0 ] || die "사용법: orca.sh approve DEC-### ..."; local i; for i in "$@"; do set_status "$i" 확정; done; }
cmd_rework() { [ $# -ge 1 ] || die '사용법: orca.sh rework DEC-### "사유"'; set_status "$1" 재작업 "재작업: ${2:-사유 미기재}"; }
cmd_reopen() { [ $# -ge 1 ] || die "사용법: orca.sh reopen DEC-###"; set_status "$1" 검토대기 "재작업 완료 $(today)"; }

# ---------------------------------------------------------------- 최종 보고

# notes 의 열린 항목(- [ ])을 소속 소제목(### …)과 함께 출력
notes_open() {
  awk '/^## /{h=""; shown=0; next}
       /^### /{h=substr($0,5); shown=0; next}
       /^- \[ \]/{ if (h!="" && !shown) { print "- **" h "**"; shown=1 }
                    print (h!="" ? "  " : "") $0 }' "$1"
}

# notes/ios.md 의 "### Phase …" 절마다 mac 확인 항목이 남아 있으면 "mac 검증 전 미확정"
ios_phase_status() {
  local f="$ORCA/notes/ios.md"
  [ -f "$f" ] || { echo "(notes/ios.md 없음)"; return 0; }
  awk '/^## /{h=""; next}
       /^### Phase/{h=substr($0,5); order[++k]=h; n[h]=0; next}
       /^- \[ \]/ && h!="" {n[h]++}
       END{ if (k==0) print "(기록 없음)"
            for (i=1;i<=k;i++) printf "- %s — iOS: %s\n", order[i],
              (n[order[i]]>0 ? "**mac 검증 전 미확정** (확인 " n[order[i]] "건 남음)" : "mac 검증 완료") }' "$f"
}

cmd_report() {
  mkdir -p "$REPORTS"
  local last base out
  last=$(ls "$REPORTS" 2>/dev/null | grep -E '^REPORT-.*\.md$' | sort | tail -n 1 || true)
  base=""
  [ -n "$last" ] && base=$(awk '/^기준 커밋:/ {gsub(/`/,""); print $NF; exit}' "$REPORTS/$last")
  out="$REPORTS/REPORT-$(date +%Y%m%d-%H%M).md"
  {
    echo "# Orca 종합 보고 — $(now)"
    echo
    echo "- 모드: $(mode) · 머신: $(machine) · 브랜치: $(current_branch)"
    echo "- 이전 보고: ${last:-없음}"
    echo "- 범위: \`${base:-처음}\` → \`$(git rev-parse --short HEAD)\`"
    echo "기준 커밋: \`$(git rev-parse --short HEAD)\`"
    echo
    echo "## 1. 검토할 결정 (검토대기 · 승인대기 · 재작업)"
    echo
    echo "| ID | 날짜 | 모드 | 영역 | 결정 | 근거 | 상태 |"
    echo "|---|---|---|---|---|---|---|"
    [ -n "$(decisions_by_status '승인대기|검토대기|재작업')" ] && decisions_by_status '승인대기|검토대기|재작업' || echo "| - | | | | 없음 | | |"
    echo
    echo "> 확정: \`scripts/orca.sh approve DEC-###\` · 재작업: \`scripts/orca.sh rework DEC-### \"사유\"\`"
    echo
    echo "## 2. 변경 요약"
    echo
    if [ -n "$base" ] && git cat-file -e "$base^{commit}" 2>/dev/null; then
      echo '```text'
      git log --oneline "$base..HEAD"
      echo
      git diff --stat "$base..HEAD" | tail -n 20
      echo '```'
    else
      echo '```text'
      git log --oneline -20
      echo '```'
    fi
    echo
    echo "## 3. 역할 브랜치 상태"
    echo
    local r br
    for r in $ROLES; do
      br=$(role_branch "$r")
      git show-ref --verify --quiet "refs/heads/$br" || { echo "- $br: 없음"; continue; }
      echo "- $br: 미병합 $(git rev-list --count "$ORCA_BRANCH..$br") 커밋, 범위 위반 $(scope_violations "$r" "$ORCA_BRANCH...$br" | awk 'NF' | wc -l | tr -d ' ')건"
    done
    echo
    echo "## 4. 워크트리 요청 (notes)"
    echo
    for r in $ROLES; do
      [ -f "$ORCA/notes/$r.md" ] || continue
      echo "### $r"
      notes_open "$ORCA/notes/$r.md" | grep . || echo "(열린 요청 없음)"
      echo
    done
    echo "## 5. iOS 검증 상태 (Phase 별, DEC-027)"
    echo
    ios_phase_status
    echo
    echo "## 6. 분기점 (Phase 태그, DEC-041)"
    echo
    if [ -n "$(git tag -l 'phase-*')" ]; then
      echo "| 태그 | 커밋 | 날짜 | 내용 |"
      echo "|---|---|---|---|"
      git for-each-ref --sort=creatordate refs/tags/phase-* \
        --format='| `%(refname:short)` | `%(*objectname:short)%(if)%(*objectname)%(then)%(else)%(objectname:short)%(end)` | %(creatordate:short) | %(contents:subject) |'
      echo
      echo '되돌아가기 (작업 브랜치를 새로 만들어 확인 — 기존 브랜치는 그대로):'
      echo '```bash'
      echo 'git switch -c restore-phase-N phase-N     # 그 지점에서 새 브랜치'
      echo 'git diff phase-N..ocra --stat             # 그 뒤 바뀐 것'
      echo 'git revert --no-edit phase-N..ocra        # ocra 에서 그 이후 커밋을 되돌리는 커밋 생성(이력 보존)'
      echo '```'
    else
      echo "(태그 없음)"
    fi
    echo
    echo "## 7. 재작업 계획"
    echo
    [ -n "$(decisions_by_status 재작업)" ] && decisions_by_status 재작업 | awk -F'|' '{print "- [ ]" $2 ":" $6 "→" $7}' || echo "(재작업 없음)"
  } > "$out"
  cat "$out"
  echo
  info "보고서: ${out#$MAIN/}"
}

# ---------------------------------------------------------------- help

cmd_help() {
  cat <<'EOF'
Orca — ocra 오케스트레이션 (자동/수동 모드)

  status                      모드·머신·역할·워크트리·미결 결정 요약
  mode [auto|manual]          모드 확인/변경 (워크트리·역할은 유지, 승인 게이트만 다름)
  setup [android|ios|all]     역할 브랜치(ocra-android/ocra-ios)와 워크트리 생성
  sync [--offline]            머신 전환 시: 마지막 체크포인트 이후 diff 요약 → .orca/.cache/sync-digest.md
  checkpoint "메모"           현재 HEAD를 SYNC.md 에 기록 (세션 종료 시)
  check [role] [--no-build] [--env dev|staging|prod]   역할 범위 점검 + 빌드/테스트 (기본 dev, ORCA_ENV 로도 지정)
  uitest [--sim 이름] [--port 8765] [--only 테스트]   (mac) 목업 서버 + 시뮬레이터에서 iOS UI 흐름 테스트 (iosApp/UITests)
  uitest android [--port 8765] [--only 테스트]       목업 서버 + 에뮬레이터·기기에서 Android UI 흐름 테스트 (androidUiTest)
  uitest all [--sim 이름]                            (mac) Android·iOS UI 흐름 테스트를 동시에 (DEC-121)
  capture                     두 플랫폼 인증 화면 캡처(한국어, 같은 이름) → build/capture/index.html 나란히 비교 (DEC-112)
  refresh                     (android/ios 워크트리) ocra 최신 내용을 병합
  merge <android|ios>         (ocra) 범위 점검 → 승인 게이트 → 병합 → 점검
  tmp <이름>                  임시 워크트리 <메인>-tmp-<이름>(브랜치 ocra-tmp-<이름>) 만들기
  merge tmp <이름>            (ocra) 임시 브랜치 병합 → 점검 → 성공하면 같은 단계에서 정리
                              (worktree remove → branch -d → worktree prune, 실패하면 강제 없이 보고)
  preflight                   머신 이동 전 점검: 워크트리 미커밋·미병합·남은 임시 워크트리·origin push·태그
  gate "설명"                 승인 게이트 (auto: 통과, manual: 승인 없으면 종료코드 3)
  decide "내용" [--area A] [--why W]   결정 기록 (auto: 검토대기, manual: 승인대기)
  approve DEC-### ...         결정 확정
  rework DEC-### "사유"       재작업 지정
  reopen DEC-###              재작업 완료 → 다시 검토대기
  report                      종합 보고서 생성 (.orca/reports/)

환경변수: ORCA_MODE(일시 모드), ORCA_APPROVE=1(수동 승인), ORCA_MACHINE, ORCA_IOS_BUILD(명령 또는 skip)
종료코드: 2 = 역할 범위 위반, 3 = 수동 모드 승인 필요
EOF
}

cmd="${1:-help}"; shift || true
case "$cmd" in
  status) cmd_status ;;
  mode) cmd_mode "$@" ;;
  setup) cmd_setup "$@" ;;
  sync) cmd_sync "$@" ;;
  checkpoint) cmd_checkpoint "$@" ;;
  check) cmd_check "$@" ;;
  uitest)
    case "${1:-}" in
      android) shift; cmd_uitest_android "$@" ;;
      all) shift; cmd_uitest_all "$@" ;;
      *) cmd_uitest "$@" ;;
    esac ;;
  capture) cmd_capture ;;
  refresh) cmd_refresh ;;
  merge) cmd_merge "$@" ;;
  tmp) cmd_tmp "$@" ;;
  preflight) cmd_preflight "$@" ;;
  gate) gate "${*:-작업 진행}" ;;
  decide) cmd_decide "$@" ;;
  approve) cmd_approve "$@" ;;
  rework) cmd_rework "$@" ;;
  reopen) cmd_reopen "$@" ;;
  report) cmd_report ;;
  help|-h|--help) cmd_help ;;
  *) die "알 수 없는 명령: $cmd (orca.sh help)" ;;
esac
