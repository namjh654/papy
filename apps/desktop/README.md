# apps/desktop — Tauri 데스크톱 앱 (Windows/macOS)

Tauri 2.x stable + React + TypeScript. `src-tauri` 는 `papy-core` 를 부르는 얇은 command 만 가진다.
**현재 기능: 창을 띄우고 엔진 버전을 표시하는 것뿐. 파일 열기는 아직 없다.**

## 선행 조건

[Tauri 공식 선행 조건](https://v2.tauri.app/start/prerequisites/): macOS 는 Xcode(또는 Command Line Tools), Windows 는 Microsoft C++ Build Tools 와 WebView2.

## 실행

```bash
pnpm install                                   # 저장소 루트에서
pnpm desktop:dev                               # 개발 실행
pnpm --filter @papy/desktop tauri build        # 패키징 (macOS: .app/.dmg, Windows: .msi/.exe)
```

## 설정 메모

- 번들 식별자 `io.github.namjh654.papy` 는 **임시값**이다. 배포 전 확정한다(모바일 applicationId·Bundle ID 와 맞춤).
- 아이콘(`src-tauri/icons`)은 `app-icon.png`(임시 단색 문서 모양)에서 `pnpm tauri icon` 으로 생성한 자리표시자다.
- 권한(capabilities)은 `core:default` 만. 파일 대화상자 등은 기능을 승인받을 때 플러그인과 함께 추가한다.
- CSP 는 `self` 만 허용한다(원격 리소스 로드 없음).

## 릴리스

`v*` 태그를 push 하면 `.github/workflows/release.yml` 이 아래 파일을 빌드해 GitHub Release(미리보기)에 올린다. 코드 서명은 아직 없다.

| OS | 러너 | 산출물 |
|---|---|---|
| macOS (Apple Silicon / Intel) | macos-latest | `.dmg`, `.app.tar.gz` |
| Windows x64 | windows-latest | NSIS `-setup.exe`(한국어/영어), `.msi`(ko-KR, en-US) |
| Linux x64 | ubuntu-22.04 | `.AppImage`, `.deb`, `.rpm` |

Linux 빌드에는 `libwebkit2gtk-4.1-dev` 등 [Tauri Linux 선행 조건](https://v2.tauri.app/start/prerequisites/#linux)이 필요하다.

## 검증 결과 (2026-11-03)

| 항목 | macOS 26.6.2 arm64 (로컬) | Windows (CI) | Linux (CI) |
|---|---|---|---|
| `typecheck`, `cargo fmt --check`, `clippy -D warnings` | 통과 | 통과 | PR CI 에서 확인 |
| 패키징 | `.app`, `.dmg`(2.7 MiB) 통과 | NSIS 통과 | `deb`, `AppImage` PR CI 에서 확인 |
| 앱 실행·엔진 버전 표시 | 수동 확인 안 함(이 환경에 화면 캡처 권한 없음) | 미확인 | 미확인 |
