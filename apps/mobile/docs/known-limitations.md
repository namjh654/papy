# 알려진 제한 (템플릿 v0.3)

v0.1(2026-09-30)부터 v0.3(2026-10-01)까지 **확인하지 못했거나 가정으로 둔 것**을 한 곳에 모은다. 새 앱을 시작할 때 이 목록부터 본다.
확인 결과(테마·언어·백업·자동 로그인·오프라인·업데이트 매트릭스)는 `.orca/reports/` 의 v0.1 보고서.

## 1. 서버 계약 가정 (5가지)

실제 서버가 정해지면 KMP 안에서만 바꾼다 — 위치·순서는 [template-setup.md §2 "서버 계약"](template-setup.md#2-api-url--환경-dev--staging--prod).

| # | 가정 | 바꿀 곳 |
|---|---|---|
| 1 | 로그인 `POST /api/auth/login {id, password}` → `accessToken`/`token`, `refreshToken`, `user` (필드가 `id` 인지 `username` 인지 미확정) | `feature/auth/.../login/LoginApi.kt`·DTO |
| 2 | 토큰 재발급 `POST /api/auth/refresh {refreshToken}` → `{accessToken, refreshToken?}` — 없으면 env `auth.refreshPath` 를 비운다(401 이면 바로 로그아웃) | `core/data/.../session/TokenRefresher.kt` |
| 3 | 목록 페이징 `GET …?page=N` → `{items, nextPage}` | feature 의 `<Name>Api.kt` (`Remote<Name>Api`) |
| 4 | 버전 확인 `GET /api/app/version?platform=android\|ios` → `{minimumVersion, latestVersion, storeUrl, notice?}`(정책·공지: release.md §5) — 없으면 env `update.checkPath` 를 비운다 | `core/data/.../update/AppUpdate.kt` |
| 5 | 소셜 로그인 `POST /api/auth/social {provider, token, authorizationCode?, nonce?}` → `{accessToken, refreshToken, user{id, displayName, email}, isNewUser}` — 서버가 각 사 토큰을 검증하고 계정을 찾거나 만든다. `isNewUser` 면 앱이 약관 동의를 받는다(#6) | `feature/auth/.../social/SocialLoginApi.kt` |
| 6 | 소셜 신규 가입 동의 `POST /api/auth/social/agreements {marketingAgreed}` (로그인된 상태, 필수 약관은 화면이 막는다). 동의 화면에서 뒤로 가면 로그아웃만 한다 — 서버에 반쯤 만들어진 계정은 서버가 정리한다고 가정 | `feature/auth/.../social/SocialConsentApi.kt` |
| 7 | 소셜 사용자 탈퇴 `POST /api/account/withdraw {}`(비밀번호 없음, `user.provider` 로 판단). 애플은 서버가 가입 때 받은 `authorizationCode` 로 토큰을 폐기한다(App Store 가이드라인 5.1.1). 앱은 탈퇴 뒤 카카오 연결 끊기·네이버 연동 해제·구글 자격 증명 삭제 | `feature/settings/.../withdraw/WithdrawRepository.kt` |
| 8 | 메시지(DEC-125) `GET /api/chats?page=` → `{items, nextPage}`, `GET /api/chats/{id}/messages?before=|after=` → `{items(오래된→새), hasOlder, peerReadAt}`, `POST …/messages {clientId, text?, attachment?}`(같은 clientId 는 한 번), `POST …/attachments`(multipart `file`, `kind`) → `{kind, name, url, size, mimeType}`, `POST …/read {messageId}`, 답장 `replyToId`(응답 `replyTo {messageId, senderId, text?, attachmentKind?}`), 입력 중 `POST …/typing`(받기: 프레임 `{"type":"typing"}`·폴링 `peerTyping`), 검색 `GET …/messages/search?q=` → `{items}`(22). 실시간은 WebSocket `{chat.websocketUrl}?conversationId=` 글자 프레임 `{"type":"message"|"read", …}`, 없으면 폴링(`after=`). 앱 기본은 예시 API(`SampleChatApi`) | `feature/chat/.../ChatApi.kt`·`realtime/ChatRealtime.kt`·`di/ChatModule.kt` |
| 9 | 커뮤니티 `GET /api/posts?page=` → `{items, nextPage}`(최신순, 차단한 사람 제외), `GET /api/posts/{id}`, `POST·PUT /api/posts {content, imageUrls}`, `DELETE /api/posts/{id}`, `POST·DELETE /api/posts/{id}/like` → `{likeCount, likedByMe}`, `GET·POST /api/posts/{id}/comments`, `DELETE /api/comments/{id}`, `POST /api/uploads`(multipart `file`) → `{url}`, `POST /api/reports {targetType: post\|comment, targetId, reason: spam\|abuse\|inappropriate\|other}`, `GET·POST /api/blocks`, `DELETE /api/blocks/{userId}`. 글·댓글의 `isMine` 은 서버가 정한다. App Store 1.2: 신고·차단은 앱에 있고, 부적절한 내용 거르기·신고에 제때 대응(관행상 24시간 안)·연락처 표시는 서버·운영 몫 | `feature/community/.../CommunityApi.kt`·`di/CommunityModule.kt` |
| 10 | 사람·문의(25) 프로필 `GET /api/users/{id}` → `{id, name, avatarUrl?, bio?, postCount, isMe}`, 그 사람의 글 `GET /api/users/{id}/posts?page=` → `{items, nextPage}`, 1:1 대화 `POST /api/chats/direct {userId}` → `Conversation`(있으면 그것, 없으면 새로), 문의 `POST /api/inquiries {category: account\|bug\|suggestion\|other, content, email?, appVersion, platform}` → 204. 답장 방법(이메일·앱 알림)은 서버가 정한다 | `feature/community/.../CommunityApi.kt`·`feature/chat/.../ChatApi.kt`·`feature/settings/.../inquiry/InquiryApi.kt` |
| 11 | 알림 설정(26) `GET /api/push/preferences` → `{chat, comment, notice, marketing, marketingUpdatedAt?}`, `PUT` 같은 경로 `{chat, comment, notice, marketing}` → 바뀐 값. 서버가 보낼 때 종류별 값을 보고 거른다(앱은 받은 알림을 다시 거르지 않는다). `marketingUpdatedAt` 은 광고성 동의·철회 시각(앱이 날짜를 안내) | `feature/settings/.../notification/NotificationSettingsApi.kt` |

그 밖에 가정: 푸시 토큰 `POST/DELETE /api/push/tokens`, 로그아웃은 서버 호출 없음, 예시 상세 `GET /api/example/{id}`·`PUT /api/example/{id}/favorite`, 공지 `GET /api/notices?page=`·`GET /api/notices/{id}`, 비밀번호 재설정 `POST /api/auth/password/reset`·`/confirm`, 탈퇴 `POST /api/account/withdraw {password}`(아이디 사용자), 프로필 `POST /api/me/avatar`(multipart `file`)·`PUT /api/me`, 회원가입 요청의 `marketingAgreed`, 알림함 `GET /api/notifications`·`POST …/{id}/read`·`POST …/read-all`, 검색 `GET /api/search?q=&page=`.

## 2. 확인하지 못한 것 (실기기·외부 서비스 필요)

| 항목 | 이유 | 확인 방법 |
|---|---|---|
| 푸시 수신·알림 탭 이동 | Firebase 프로젝트 설정 파일·실기기(APNs) 필요 | [push.md](push.md) "확인" |
| Crashlytics·Analytics 수신 | Firebase 프로젝트 필요 | [release.md](release.md) §4 |
| 인앱 리뷰·인앱 업데이트 실제 표시 | 스토어(Play 내부 테스트 트랙 / App Store) 배포 필요. 개발 빌드는 Play 가 표시하지 않음 | [release.md](release.md) §5·§6 |
| iOS 권한 "설정 열기" | 시뮬레이터에서는 앱 설정이 아니라 설정 첫 화면이 열렸다(표준 `UIApplicationOpenSettingsURLString`). 카메라·위치 권한을 설정에서 바꾸면 iOS 가 앱을 종료한다(OS 동작) | 실기기 |
| iOS 오프라인 안내 | 시뮬레이터는 네트워크를 끌 수 없음. `NWPathMonitor` 첫 값이 오기 전(수 ms)에는 온라인으로 가정 | 실기기 비행기 모드 |
| 실서버 연동 | §1 가정 | 서버 확정 후 |
| 메시지 실시간 WebSocket·카메라 촬영 | WebSocket 은 서버가 없어 연결해 보지 못했다(폴링·예시 API 로 흐름 확인). iOS 시뮬레이터에는 카메라가 없어 "카메라를 쓸 수 없어요" 안내까지만, Android 에뮬레이터 카메라·파일 열기는 손으로 확인 필요 | 서버 연결 뒤 `chat.websocketUrl`, 실기기 |
| 실제 소셜 SDK 로그인·연결 끊기(카카오·구글·네이버·애플) | 각 사 키·콘솔 등록(패키지명·키 해시·SHA-1·번들 ID)이 없다. 자동 테스트는 dev `social.mock` 으로 흐름만 확인 | [template-setup.md](template-setup.md) §3-1 대로 키를 넣고 dev `social.mock=false` |
| 앱 잠금 실제 생체 인증(26) | 시뮬레이터·에뮬레이터에 생체·기기 암호를 등록하지 않아 잠금 화면·본인 확인은 손으로 확인하지 못했다(잠금 수단이 없으면 설정 행이 숨는다). 잠그는 시점은 KMP 테스트로 확인 | 실기기에서 설정 → 앱 잠금 켜기 → 30초 뒤 돌아오기 |
| CI | GitHub Actions 설정을 하지 않아 꺼 두었다. 예시(`docs/ci/github-actions.yml`)는 돌려 본 적 없음 | [ci.md](ci.md) 대로 켠 뒤 첫 실행 확인 |

## 3. 플랫폼 한계 (설계상 그대로 둔 것)

- **iOS 런치 스크린은 시스템 모드를 따른다** — 앱 설정이 다크이고 시스템이 라이트면 콜드 스타트 첫 순간 밝은 런치 스크린 뒤 다크 화면(반대도 같음). 첫 SwiftUI 화면부터는 앱 설정을 따른다(`AppTheme.loadSaved`).
- **iOS 언어는 다음 실행부터** — iOS 는 실행 중 앱 언어를 바꿀 수 없어 설정 탭에서 바꾸면 재시작 안내만 한다(`AppleLanguages`). Android 는 바로 바뀐다.
- **Android 11 이하 시작 화면** — 앱별 야간 모드 API 가 없어 시스템이 그리는 시작 화면은 시스템 모드를 따른다(12 이상은 앱 테마와 같다).
- **Android 백업** — 앱 저장소·보안 저장소는 클라우드 백업·기기 이전에서 뺐다. 재설치·새 기기는 설정·로그인 없이 시작한다. 옮길 값이 생기면 별도 DataStore 파일로 두고 `data_extraction_rules.xml` 에 include.
- **iOS 에서 Kotlin 예외** — Crashlytics 에 클래스 이름·메시지만 간다(Kotlin 스택 없음).
- **UI 자동 테스트는 문구로 화면을 찾는다** — iOS `scripts/orca.sh uitest`(XCUITest 14개, 한국어), Android `scripts/orca.sh uitest android`(UiAutomator 15개, 영어 — 앱 언어를 en-US 로 고정). 문구를 바꾸면 `iosApp/UITests`·`androidUiTest` 도 고친다. 시스템 권한 창 버튼은 id(`permission_deny…_button`)로 찾아 기기 언어와 무관하다.
- 메시지 첨부(사진·파일)는 원본을 로컬 DB 에 두지 않는다 — 앱을 끄면 실패한 첨부는 사라진다(글 메시지는 대기열에 남아 다시 보낸다, docs/local-db.md §4). 원본까지 남기려면 파일로 복사해 경로를 대기열에 둔다
- iOS 는 스크린샷을 막을 수 없다(OS 제한) — `security.blockCapture` 를 켜도 녹화·미러링 중 가림과 스크린샷 뒤 안내까지만(Android 는 FLAG_SECURE 로 막는다)
- 받은 파일은 앱 전용 폴더에만 있다(사용자 파일 앱·다운로드 폴더에 보이지 않음) — 내보내기는 공유 시트("파일에 저장"·드라이브)
- 오프라인에서 보이는 것은 저장한 첫 페이지·최근 메시지뿐이다(다음 페이지·검색·상세는 서버 필요)

## 4. 버전 — v0.2 에서 올린 것과 남긴 것 (Phase 12, 2026-09-30)

근거는 각 프로젝트의 릴리스 노트·호환표. 표는 `.orca/reports/` 의 Phase 12 보고서에도 있다 (DEC-093·094).

| 항목 | v0.1 → v0.2 | 남은 것 / 이유 |
|---|---|---|
| Kotlin + SKIE | 2.4.10 → 2.4.20, 0.10.14 → 0.10.15 | 최신. SKIE 가 다음 Kotlin 을 지원할 때 함께 올린다 |
| AGP | 9.0.1 → 9.3.1 | 9.4.1 은 **보류** — Kotlin 2.4.20 호환표의 AGP 상한이 9.3.1 |
| Gradle | 9.1.0 → 9.7.1 | 9.8.0 은 **보류** — Kotlin 2.4.20 호환표의 Gradle 상한이 9.7.0 (AGP 9.3 최소 9.5.0) |
| compileSdk | 36 → 37 | targetSdk 는 36 그대로(동작 변경 검토 후 따로) |
| lifecycle (androidx·JetBrains) | 2.10.0 → 2.11.0 | — |
| Compose BOM | 2026.01.00 → 2026.09.00 | navigation-compose 2.9.4 는 그대로 |
| Firebase | BoM 34.19.0 (이미 최신) | deprecated `getToken`·`deleteToken`·`onNewToken`(Android 25.1.0), `deleteToken`(iOS 12.18.0) 경고가 남는다. 대체는 설치 ID(FID) 기반 등록이라 서버에 보내는 값이 바뀐다 — 서버 계약 확정 + 실제 Firebase 프로젝트로 수신 확인이 가능할 때 옮긴다 |
| Ktor · serialization · Coil · navigation-compose | 그대로 | v0.2 범위 밖. 3.5.0 → 3.6.0, 1.10.0 → 1.11.0, 3.3.0 → 3.6.3, 2.9.4 → 2.10.2 (2026-09-30 조회, 호환 미확인) |

다음에 올릴 때도 같은 순서: Kotlin+SKIE → AGP·Gradle·compileSdk·lifecycle → Compose BOM, 단계마다 `scripts/orca.sh check --env dev|staging|prod` + 실행 확인.

