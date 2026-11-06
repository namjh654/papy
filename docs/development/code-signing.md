# 코드 서명 (데스크톱·모바일)

지금 데스크톱 설치 파일은 서명이 없어 첫 실행 때 OS 보안 경고가 나온다(website/install 안내). 서명을 켜려면 아래가 필요하다.
비용이 드는 것은 사람(저장소 주인)이 신청·결제해야 하고, 받은 값은 GitHub 저장소 Secrets 에 넣는다.

## macOS — Developer ID 서명 + 공증(notarization)

| 필요한 것 | 비용 | 비고 |
|---|---|---|
| Apple Developer Program 가입(개인 가능) | 연 99 USD | 같은 계정으로 iOS 앱 배포도 한다 |
| "Developer ID Application" 인증서(.p12 + 암호) | 포함 | Xcode 또는 developer.apple.com 에서 만든다 |
| 공증용 App Store Connect API 키(.p8, Key ID, Issuer ID) | 포함 | Apple ID + 앱 전용 암호로도 된다 |

GitHub Secrets 이름(Tauri 가 읽는 환경 변수): `APPLE_CERTIFICATE`(p12 를 base64), `APPLE_CERTIFICATE_PASSWORD`,
`APPLE_SIGNING_IDENTITY`(예: `Developer ID Application: 이름 (TEAMID)`), `APPLE_API_KEY`(Key ID), `APPLE_API_ISSUER`,
`APPLE_API_KEY_PATH`(CI 에서 .p8 을 파일로 쓴 경로). 넣으면 release.yml 의 tauri-action 이 서명·공증한다.

## Windows — Authenticode 서명

| 방법 | 비용(대략) | 개인(한국) 가능? | 비고 |
|---|---|---|---|
| Azure Artifact Signing(구 Trusted Signing) | 월 10 USD 안팎 | **아니오** — 개인은 미국·캐나다만, 법인은 미국·캐나다·EU·영국 | 가장 싸고 SmartScreen 평판이 빨리 쌓인다. 해외 법인이 있으면 검토 |
| OV 코드 서명 인증서(Sectigo·DigiCert 등) | 연 수십만 원~ | 예(개인·사업자) | 2023년부터 하드웨어 토큰(USB) 또는 클라우드 서명이 필수 — CI 에서는 클라우드 서명 상품을 골라야 한다 |
| EV 코드 서명 인증서 | 연 수십만~100만 원대 | 사업자 | 2024년부터 SmartScreen 즉시 통과 혜택은 OV 와 같아졌다 |

설정: `apps/desktop/src-tauri/tauri.conf.json` 의 `bundle.windows.signCommand`(클라우드 서명 도구 명령, `%1` 이 파일 경로) 또는
`certificateThumbprint`, 그리고 CI Secrets(서명 서비스 키). 서명 전에도 SmartScreen 평판은 다운로드 수로 쌓인다.

## Linux

필수 아님. AppImage·deb 는 서명 없이 배포가 일반적이다(원하면 GPG 서명 파일을 함께 올린다).

## 모바일 (스토어 배포 때)

- Android: Google Play 개발자 등록 1회 25 USD, 업로드 키(keystore) — Play 앱 서명 사용.
- iOS: Apple Developer Program(위와 같은 계정), 배포 인증서·프로비저닝은 Xcode 자동 서명.

## 권장 순서

1. macOS 먼저: 개인 가입으로 바로 되고, 공증이 없으면 macOS 는 "손상된 앱" 경고가 커서 효과가 크다.
2. Windows: 개인이면 클라우드 서명이 되는 OV 인증서, 법인(미국·캐나다·EU·영국)이 있으면 Azure Artifact Signing.
3. 값이 준비되면 release.yml 에 Secrets 를 연결한다(서명 Secrets 가 없으면 지금처럼 서명 없이 만든다).
