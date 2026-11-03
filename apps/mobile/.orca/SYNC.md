# SYNC — 머신/세션 체크포인트

> `scripts/orca.sh checkpoint "메모"` 가 한 줄씩 추가한다. 직접 편집하지 않는다.
> 머신이 바뀌면 `scripts/orca.sh sync` 가 **이 머신의 마지막 체크포인트 이후 diff** 만 요약한다
> (없으면 전체 마지막 체크포인트 기준). 매번 전체 코드를 다시 읽지 않기 위한 기준점이다.

| 시각 | 머신 | 브랜치 | 커밋 | 메모 |
|---|---|---|---|---|
| 2026-09-29 13:04 | mac | ocra | `26b76a0` | Phase 0 분석 완료 (ARCHITECTURE/PROGRESS 작성) |
| 2026-09-29 09:06 | linux | ocra | `134b6e7` | merge ocra-android |
| 2026-09-29 09:06 | linux | ocra | `4ada199` | merge ocra-ios |
| 2026-09-29 09:07 | linux | ocra | `c8ec0b4` | 디자인 시스템 1차(토큰+양 플랫폼 컴포넌트+키보드+갤러리) 병합, mac 빌드 확인 대기 |
| 2026-09-29 19:18 | windows | ocra | `7ba5821` | merge ocra-android |
| 2026-09-29 19:21 | windows | ocra | `75a25ef` | merge ocra-ios |
| 2026-09-29 19:22 | windows | ocra | `f8a012e` | Phase 4 마무리: Windows 빌드 환경 보정, 다크 모드 시작 배경, 스타일 방향(토큰·컴포넌트·4화면), 다국어 기반(ko/en, ErrorCode, 로케일 포맷, 키 검사). iOS mac 빌드 대기 |
| 2026-09-29 19:42 | windows | ocra | `a0770fd` | merge ocra-ios |
| 2026-09-29 19:43 | windows | ocra | `cc34dc1` | Phase 1 기반 정리 완료: 문서 정리, Compose catalog, core 빌드 설정, 빈 디렉터리 삭제, GitHub Actions CI, 공유 Xcode scheme(mac 검증 대기). JDK 25 확인, orca.sh 환경 보정 정리, 권한 설정 |
| 2026-09-29 20:21 | windows | ocra | `daa7ffe` | merge ocra-ios |
| 2026-09-29 20:24 | windows | ocra | `1bd1d95` | Phase 2 모듈 분리 완료: build-logic, core:common/core:data, feature:auth/home, shared(Core export), StatefulViewModel, AppJson, lifecycle 2.10.0·Koin 4.2.2, 규칙 문서. iOS mac 검증 대기 |
| 2026-09-29 20:33 | windows | ocra | `fac8b10` | merge ocra-android |
| 2026-09-29 20:33 | windows | ocra | `bed33ff` | merge ocra-ios |
| 2026-09-29 20:34 | windows | ocra | `8f08320` | Phase 3 완료: 네트워크 강화, 401→expire, SecureStorage(Keystore/Keychain)+마이그레이션, useMock→DI, ApiConfig 단일 경로, 루트 AuthStatus 관찰, 테스트 22건. iOS mac 검증 대기 |
| 2026-09-29 20:43 | windows | ocra | `a294f74` | merge ocra-android |
| 2026-09-29 20:43 | windows | ocra | `08a1d63` | merge ocra-ios |
| 2026-09-29 20:47 | windows | ocra | `9fd153a` | merge ocra-android |
| 2026-09-29 20:47 | windows | ocra | `3d261e3` | merge ocra-ios |
| 2026-09-29 20:53 | windows | ocra | `3329cf0` | Phase 6 완료: ViewModel 테스트, arch_check, new_feature.py + :feature:example, rename_app.py + template-setup 가이드. iOS mac 검증 대기 |
| 2026-09-29 21:05 | windows | ocra | `a373cd9` | merge ocra-ios |
| 2026-09-29 21:05 | windows | ocra | `53942f6` | merge ocra-android |
| 2026-09-29 21:07 | windows | ocra | `03643ca` | Phase 5 완료: 푸시 공통(DeepLink·PushManager·PushRepository 세션 연동), Android FCM(설정 없으면 NoOp), iOS Firebase SPM·FcmPushProvider(mac 대기), docs/push.md |
| 2026-09-29 21:29 | windows | ocra | `eae9a74` | Phase 7 완료: Python 제거, build-logic Kotlin tooling + templateCheck, newFeature/renameApp 은 -p build-logic 로 단독 실행 |
| 2026-09-29 23:40 | windows | ocra | `5ee6a39` | merge ocra-ios |
| 2026-09-29 23:44 | windows | ocra | `d40e2a6` | Phase 8 완료: env/ 단일 원본(공개·비밀 분리), KMP AppConfig 생성, Android flavor dev/staging/prod·prodRelease R8, iOS 6 구성·3 scheme(mac 대기), orca check --env, CI 3환경 |
| 2026-09-29 23:57 | windows | ocra | `7196ff8` | 머신 이동 전 점검: 워크트리 미커밋 없음, ocra-android/ios 전부 병합됨, ocra = origin/ocra. 다음: 결정 approve/rework, mac 검증(ios.md 37건), Phase 9·10 범위 결정 |
| 2026-09-30 08:46 | mac | ocra | `97c9bab` | iOS mac 검증 Phase 1~8 완료 (3환경 xcodebuild·실행·Keychain·마이그레이션·Archive), orca check scheme 빌드, 원격 origin=GitHub |
| 2026-09-30 09:17 | mac | ocra | `618913c` | merge ocra-android |
| 2026-09-30 09:17 | mac | ocra | `5d49721` | merge ocra-ios |
| 2026-09-30 09:29 | mac | ocra | `f5f75e7` | Phase 9 완료: 목록 페이징·하단 탭·설정·네트워크 상태·웹뷰·토큰 재발급·Turbine/Kover, newFeature --tab, 3환경 check 통과 |
| 2026-09-30 09:41 | mac | ocra | `f36fb06` | merge ocra-android |
| 2026-09-30 09:41 | mac | ocra | `c9d411c` | merge ocra-ios |
| 2026-09-30 09:47 | mac | ocra | `fa0cd46` | Phase 10 완료: 버전 단일 관리·강제/선택 업데이트·Kermit·Crashlytics/Analytics·인앱 리뷰/업데이트·release.md, 3환경 check·Archive 통과 |
| 2026-09-30 09:50 | mac | ocra | `59bd37e` | merge ocra-android |
| 2026-09-30 09:50 | mac | ocra | `8f03022` | merge ocra-ios |
| 2026-09-30 10:07 | mac | ocra | `bd3c306` | Phase 11 완료: 반복 UI 통합·배치 규칙 검사·문서 정리·템플릿 전 과정 검증(renameApp→예시 제거→newFeature→3환경 check), Phase 12 제안 |
| 2026-09-30 11:28 | mac | ocra | `fc8249e` | merge ocra-android |
| 2026-09-30 11:28 | mac | ocra | `eee16b0` | merge ocra-ios |
| 2026-09-30 12:03 | mac | ocra | `b17d88e` | 임시 워크트리 경고 시험 |
| 2026-09-30 12:18 | mac | ocra | `02231d4` | merge ocra-android |
| 2026-09-30 13:14 | mac | ocra | `3876d8c` | v0.1 마무리: 테마 일원화·시작 화면 일치·백업 제외, 확인 매트릭스 7종, orca 임시 워크트리 규칙, known-limitations, 3환경 check 통과 |
| 2026-09-30 16:07 | mac | ocra | `e3d0c10` | Phase 12: Kotlin 2.4.20+SKIE 0.10.15, AGP 9.3.1+Gradle 9.7.1+compileSdk 37+lifecycle 2.11.0, Compose BOM 2026.09.00 — 3환경 check·실행 확인. AGP 9.4·Gradle 9.8·Firebase FID 는 제외 |
| 2026-09-30 17:33 | mac | ocra | `d1d0260` | v0.2 완료 후 kmp-decompose v0.1 작성, 비교 요약 보고 |
| 2026-09-30 18:25 | windows | ocra | `d352218` | windows 복귀: pull(v0.2), check dev·staging·prod 통과(테스트 83건). kmp-decompose 는 C:/worknam/kmp-decompose/app 에 같은 워크트리 구성 |
| 2026-09-30 19:09 | windows | ocra | `8d35ff2` | Windows: Android 기기 확인(토큰 암호화·401 전환·갤러리) 기록, push 규칙(DEC-095). kmp-decompose Phase 5·6 완료 |
| 2026-09-30 19:28 | windows | ocra | `15e88be` | v0.2 이후 정리: CI 문서화, iOS Kotlin 교차 컴파일 점검, 라이브러리·컴포넌트 점검(코드 변경 없음). 다음: mac 에서 .orca/notes/mac-next.md |
| 2026-09-30 22:26 | windows | ocra | `edc3bc3` | Windows 마무리: R8 릴리스 실행 확인, renameApp·newFeature 확인(kmp-decompose), orca 워크트리 보완, 결정 검토 묶음. 다음: mac 에서 .orca/notes/mac-next.md |
| 2026-10-01 00:12 | windows | ocra | `9f6bee1` | Phase 13: core 이식 + 상세 패턴 + 공지 + 버그 2건 (Android 확인, iOS mac 대기) |
| 2026-10-01 00:24 | windows | ocra | `220dddb` | Phase 14: 약관·재설정·탈퇴·프로필 (Android 확인, iOS mac 대기) |
| 2026-10-01 00:36 | windows | ocra | `85d331e` | Phase 15: 권한·온보딩·알림함·검색 (Android 확인, iOS mac 대기). 다음: mac 에서 .orca/notes/mac-next.md |
| 2026-10-01 08:36 | mac | ocra | `4753966` | merge ocra-ios |
| 2026-10-01 09:23 | mac | ocra | `2eb6675` | mac: Phase 13~15 iOS 검증 완료(Swift 오류 1건 수정 49f5634, check 3환경, 시뮬레이터 흐름 전부·401·딥링크) |
| 2026-10-01 10:31 | mac | ocra | `e8845e4` | mac: iOS 갤러리·테마·로그 마스킹 확인 |
| 2026-10-01 11:21 | mac | ocra | `c092511` | merge ocra-ios |
| 2026-10-01 11:25 | mac | ocra | `e788609` | v0.3: Phase 13~16 완료(check 3환경, uitest 14개), 결정 전부 확정 |
| 2026-10-04 17:51 | windows | ocra | `80a50bf` | README 정리(프로젝트 설명·작업 내용·확인 상태) |
| 2026-10-04 18:20 | windows | ocra | `63651b5` | GitHub 설명·토픽·릴리스, 다음 액션 정리 |
| 2026-10-04 19:27 | windows | ocra | `9655b32` | Phase 17 Android UI 흐름 테스트 15개 통과, 문서 v0.4 |
| 2026-10-06 09:02 | mac | ocra | `969fa90` | mac 확인(v0.4): check 3환경·uitest 통과, uitest·뒤로가기 수정 |
| 2026-10-22 21:34 | mac | ocra | `d0b4c70` | DEC-104 확정, Phase 18 소셜 로그인 계획 작성(착수 전 D13~D17 확정 필요), main=origin/main 맞춤 |
| 2026-10-22 21:43 | mac | ocra | `33105d0` | DEC-105~112 확정, Phase 18(18-0 디자인 동등성 포함)·Phase 19 메시지 UI 계획, 미결 D18~D20 |
| 2026-10-22 22:00 | mac | ocra | `107bc3e` | merge ocra-ios |
| 2026-10-22 22:14 | mac | ocra | `4c258d1` | merge ocra-android |
| 2026-10-22 22:14 | mac | ocra | `197d918` | merge ocra-ios |
| 2026-10-22 22:56 | mac | ocra | `a7b6137` | merge ocra-ios |
| 2026-10-22 23:09 | mac | ocra | `42d7161` | merge ocra-android |
| 2026-10-22 23:09 | mac | ocra | `26f7e74` | merge ocra-ios |
| 2026-10-22 23:19 | mac | ocra | `0cf42b0` | Phase 18-0 디자인 동등성 완료(capture·명세·Pretendard·Lucide 아이콘·로그인 버그), 18-1 소셜 로그인 KMP 완료(DEC-105~118). 다음: 18-2 Android SDK·버튼. push 는 Phase 18 끝날 때 |
| 2026-10-23 16:02 | mac | ocra | `09dfbe4` | merge ocra-android |
| 2026-10-23 16:02 | mac | ocra | `5203555` | merge ocra-ios |
| 2026-10-23 16:40 | mac | ocra | `80b1d2f` | merge ocra-ios |
| 2026-10-23 16:47 | mac | ocra | `d8ba4d8` | merge ocra-ios |
| 2026-10-23 16:59 | mac | ocra | `2387fb2` | merge ocra-android |
| 2026-10-23 16:59 | mac | ocra | `a3001d2` | merge ocra-ios |
| 2026-10-26 08:04 | mac | ocra | `3425948` | merge ocra-ios |
| 2026-10-26 08:14 | mac | ocra | `6335cfa` | Phase 18 완료: 소셜 로그인(카카오·구글·애플·네이버) KMP·Android·iOS, 신규 가입 동의·소셜 탈퇴, 미리보기(DEC-122), uitest all 양 플랫폼 통과 |
| 2026-10-26 09:09 | mac | ocra | `a7d4cba` | merge ocra-android |
| 2026-10-26 09:09 | mac | ocra | `b86b688` | merge ocra-ios |
| 2026-10-26 09:47 | mac | ocra | `a1f8382` | merge ocra-android |
| 2026-10-26 09:47 | mac | ocra | `dfd509c` | merge ocra-ios |
| 2026-10-26 09:47 | mac | ocra | `dbdb4e7` | merge ocra-ios |
| 2026-10-26 09:51 | mac | ocra | `a2b0e0d` | merge ocra-ios |
| 2026-10-26 09:52 | mac | ocra | `7a29857` | merge ocra-android |
| 2026-10-26 10:04 | mac | ocra | `5162837` | merge ocra-android |
| 2026-10-26 10:07 | mac | ocra | `9712fb4` | Phase 19 완료: 공통 UI 보강(바텀시트·목록 행·액션 메뉴·화면 전환 DEC-127), 메시지 :feature:chat(KMP 22 테스트)·텔레그램식 화면·네이티브 첨부 3종(사진·카메라·파일), 목업 API, uitest all 통과 |
| 2026-10-26 10:25 | mac | ocra | `64d47c3` | merge ocra-android |
| 2026-10-26 10:38 | mac | ocra | `e653780` | merge ocra-android |
| 2026-10-26 10:38 | mac | ocra | `59ffbaa` | merge ocra-ios |
| 2026-10-26 11:03 | mac | ocra | `6eecacc` | merge ocra-android |
| 2026-10-26 11:03 | mac | ocra | `72912db` | merge ocra-ios |
| 2026-10-26 11:08 | mac | ocra | `2a02c2b` | merge ocra-android |
| 2026-10-26 11:08 | mac | ocra | `928de09` | merge ocra-ios |
| 2026-10-26 11:24 | mac | ocra | `8160f29` | merge ocra-ios |
| 2026-10-26 11:31 | mac | ocra | `c16ee1a` | merge ocra-ios |
| 2026-10-26 11:49 | mac | ocra | `03dbe1b` | merge ocra-ios |
| 2026-10-26 11:56 | mac | ocra | `69741d4` | Phase 20 완료: 커뮤니티 :feature:community(KMP 22 테스트)·글 카드/상세/글쓰기/차단 목록 양 플랫폼, 신고·차단(App Store 1.2), 목업 API, iOS Dynamic Type(DEC-128)·로그인 체크박스 줄바꿈, uitest 양 플랫폼 통과 |
| 2026-10-26 12:08 | mac | ocra | `a609fc3` | merge ocra-android |
| 2026-10-26 12:08 | mac | ocra | `1b6292a` | merge ocra-ios |
| 2026-10-26 12:09 | mac | ocra | `197a999` | merge ocra-ios |
| 2026-10-26 13:08 | mac | ocra | `d94e146` | merge ocra-tmp-p22 |
| 2026-10-26 13:13 | mac | ocra | `924c7ab` | merge ocra-android |
| 2026-10-26 13:13 | mac | ocra | `00bf091` | merge ocra-ios |
| 2026-10-26 13:33 | mac | ocra | `a4c964b` | merge ocra-android |
| 2026-10-26 13:33 | mac | ocra | `3cc1159` | merge ocra-ios |
| 2026-10-26 13:42 | mac | ocra | `cf04564` | Phase 21·22 완료: 칩·세그먼트·스켈레톤·검색창, 딥링크 chat/community, 메시지 배지, 캡처 11화면 / 답장·입력 중·대화 검색·사진 전체 화면, uitest 양 플랫폼 통과 |
| 2026-10-26 15:17 | mac | ocra | `3ab7f63` | merge ocra-android |
| 2026-10-26 15:18 | mac | ocra | `8e0abe1` | merge ocra-ios |
| 2026-10-26 15:29 | mac | ocra | `74b525f` | Phase 24(SQLDelight 로컬 DB·오프라인·iOS Nuke 이미지 캐시)·27(버전 정책·운영 공지) 완료, uitest all 통과 |
| 2026-10-26 15:44 | mac | ocra | `9dfc4b0` | merge ocra-android |
| 2026-10-26 15:44 | mac | ocra | `d1a2633` | merge ocra-ios |
| 2026-10-26 15:45 | mac | ocra | `68a73ac` | merge ocra-ios |
| 2026-10-26 16:05 | mac | ocra | `aeeae31` | merge ocra-android |
| 2026-10-26 16:06 | mac | ocra | `051f279` | merge ocra-ios |
| 2026-10-26 16:14 | mac | ocra | `ae0b3d3` | Phase 25(사용자 프로필·1:1 대화 시작·문의하기) 완료, uitest all 통과 |
| 2026-10-26 16:15 | mac | ocra | `7a8b8f1` | merge ocra-tmp-p26 |
| 2026-10-26 16:20 | mac | ocra | `aa43de1` | merge ocra-android |
| 2026-10-26 16:20 | mac | ocra | `fefbf85` | merge ocra-ios |
| 2026-10-26 16:29 | mac | ocra | `79270ae` | Phase 26(앱 잠금·알림 종류별 설정) 완료, uitest all 통과 |
| 2026-10-26 16:50 | mac | ocra | `59d1568` | merge ocra-android |
| 2026-10-26 16:50 | mac | ocra | `2bb5294` | merge ocra-ios |
| 2026-10-26 16:55 | mac | ocra | `b59417d` | merge ocra-android |
| 2026-10-26 16:55 | mac | ocra | `94e97b1` | merge ocra-ios |
| 2026-10-26 17:08 | mac | ocra | `d146934` | Phase 28(newFeature --db·기능 켜고 끄기)·29(접근성 1차·최종 검수) 완료, uitest all 통과 → v0.4.11 |
| 2026-10-26 21:28 | mac | ocra | `e7d67d8` | merge ocra-ios |
| 2026-10-27 08:26 | mac | ocra | `e9c8796` | merge ocra-ios |
| 2026-10-27 08:30 | mac | ocra | `29eca56` | merge ocra-ios |
| 2026-10-27 08:43 | mac | ocra | `781f79f` | merge ocra-ios |
| 2026-10-27 08:45 | mac | ocra | `4d51c3d` | Phase 30(파일 받기·보관·화면 캡처 막기) 완료, 실패 테스트 원인 수정 후 해당 테스트 통과, 임시 워크트리 2개 정리 |
