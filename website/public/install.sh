#!/bin/sh
# Papy macOS 설치 스크립트 / Papy installer for macOS
#
#   curl -fsSL https://papy-omega.vercel.app/install.sh | sh
#
# GitHub Releases 에서 Mac 종류에 맞는 Papy.app 을 받아 /Applications 에 넣는다.
# curl 로 받은 파일에는 macOS 격리 표시가 붙지 않아, 처음 실행할 때 보안 경고 없이 열린다.
# Downloads the Papy.app build for this Mac from GitHub Releases into /Applications.
# Files fetched with curl are not quarantined, so the first launch opens without the Gatekeeper prompt.
#
# 환경 변수 / environment:
#   PAPY_VERSION=0.4.0        특정 버전 설치(기본: 최신 정식판) / install a specific version (default: latest stable)
#   PAPY_INSTALL_DIR=<dir>    설치 폴더(기본: /Applications) / install folder (default: /Applications)
set -eu

REPO="namjh654/papy"

say() { printf '%s\n' "$*"; }
fail() { printf 'papy-install: %s\n' "$*" >&2; exit 1; }

[ "$(uname -s)" = "Darwin" ] || fail "macOS 전용입니다. Windows·Linux 는 https://papy-omega.vercel.app/download/ 에서 받으세요. (macOS only)"
command -v curl >/dev/null 2>&1 || fail "curl 이 필요합니다. (curl is required)"

case "$(uname -m)" in
  arm64) ARCH="aarch64" ;;
  x86_64)
    # Rosetta 로 돌아가는 터미널이면 실제 칩은 Apple Silicon 이다.
    if [ "$(sysctl -n sysctl.proc_translated 2>/dev/null || echo 0)" = "1" ]; then ARCH="aarch64"; else ARCH="x64"; fi ;;
  *) fail "지원하지 않는 CPU 입니다: $(uname -m) (unsupported CPU)" ;;
esac

if [ -n "${PAPY_VERSION:-}" ]; then
  VERSION="${PAPY_VERSION#v}"
else
  # releases/latest 는 시험판(prerelease)을 건너뛴 최신 정식판을 돌려준다.
  VERSION="$(curl -fsSL -H 'Accept: application/vnd.github+json' "https://api.github.com/repos/$REPO/releases/latest" \
    | sed -n 's/^ *"tag_name": *"v\{0,1\}\([^"]*\)".*/\1/p' | head -n 1)"
  [ -n "$VERSION" ] || fail "최신 버전을 알아내지 못했습니다. 잠시 뒤 다시 시도하거나 PAPY_VERSION 을 지정하세요. (could not resolve latest version)"
fi

ASSET="Papy_${VERSION}_${ARCH}.app.tar.gz"
URL="https://github.com/$REPO/releases/download/v$VERSION/$ASSET"

DEST="${PAPY_INSTALL_DIR:-/Applications}"
if [ ! -d "$DEST" ] || [ ! -w "$DEST" ]; then
  if [ -z "${PAPY_INSTALL_DIR:-}" ]; then
    DEST="$HOME/Applications"
    mkdir -p "$DEST"
  else
    fail "설치 폴더에 쓸 수 없습니다: $DEST (install folder not writable)"
  fi
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/papy-install.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT INT TERM

say "Papy $VERSION ($ARCH) 받는 중… / downloading…"
curl -fL --progress-bar -o "$TMP/$ASSET" "$URL" || fail "받지 못했습니다: $URL (download failed)"

tar -xzf "$TMP/$ASSET" -C "$TMP"
[ -d "$TMP/Papy.app" ] || fail "압축 파일에 Papy.app 이 없습니다. (Papy.app missing in archive)"
# 혹시 붙어 있을 격리 표시를 지운다(브라우저로 받은 파일을 넘긴 경우 대비).
xattr -dr com.apple.quarantine "$TMP/Papy.app" 2>/dev/null || true
codesign --verify --deep --strict "$TMP/Papy.app" 2>/dev/null || fail "앱 서명 확인에 실패했습니다. 설치하지 않았습니다. (signature check failed)"

if pgrep -x papy-desktop >/dev/null 2>&1; then
  say "실행 중인 Papy 를 닫습니다… / quitting running Papy…"
  osascript -e 'tell application id "io.github.namjh654.papy" to quit' >/dev/null 2>&1 || true
  sleep 2
fi

rm -rf "$DEST/Papy.app"
mv "$TMP/Papy.app" "$DEST/Papy.app"

say "설치했습니다: $DEST/Papy.app / installed"
say "실행: open \"$DEST/Papy.app\""
