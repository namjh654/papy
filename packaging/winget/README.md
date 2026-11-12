# winget 등록

Windows 사용자가 `winget install namjh654.Papy` 로 설치할 수 있게 [microsoft/winget-pkgs](https://github.com/microsoft/winget-pkgs)에 올리는 설치 정보(manifest)다.
winget 으로 설치하면 SmartScreen 경고 창이 뜨지 않는다. 코드 서명은 필요 없다.

- 경로는 winget-pkgs 와 같다: `manifests/n/namjh654/Papy/<버전>/`.
- 설치 파일은 NSIS `Papy_<버전>_x64-setup.exe`(현재 사용자 설치, `/S` 조용히 설치)다.

## 새 버전 올리기

1. 릴리스가 끝나면 `setup.exe` 의 SHA256 을 구한다.
   ```sh
   curl -fsSL -o setup.exe https://github.com/namjh654/papy/releases/download/v<버전>/Papy_<버전>_x64-setup.exe
   shasum -a 256 setup.exe
   ```
2. 이전 버전 폴더를 복사해 `PackageVersion`, `InstallerUrl`, `InstallerSha256`(대문자), `ReleaseDate`, `ReleaseNotesUrl` 을 바꾼다.
3. winget-pkgs 포크(namjh654/winget-pkgs)에 같은 경로로 올리고 PR 을 연다. 제목은 `New version: namjh654.Papy version <버전>`.
   - 첫 PR 에서는 Microsoft CLA 봇이 동의 댓글을 요구한다.
   - 검증 봇이 설치 시험과 Defender 검사를 한 뒤 사람 검토를 거쳐 병합된다(보통 며칠).
