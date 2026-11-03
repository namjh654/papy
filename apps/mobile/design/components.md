# 컴포넌트 명세 (DEC-112)

두 플랫폼 구현이 따르는 수치. 값은 토큰 이름으로 적는다(실제 값은 `tokens.json`). 리뷰·비교(`scripts/orca.sh capture` → `build/capture/index.html`)의 기준이다.
보이는 값(색·글자·간격·모양)은 같게, 동작 관례(뒤로가기 모양·날짜 선택기·시스템 시트·상단 바)는 플랫폼대로.
상태 규칙은 Material 3 상태 기준(눌림·비활성·오류·포커스, DEC-114)을 따른다.

## 공통

| 항목 | 값 |
|---|---|
| 글꼴 | Pretendard (Regular·Medium·SemiBold·Bold), 굵기는 타이포그래피 토큰이 정한다 |
| 한국어 줄바꿈 | 어절 단위 (Android `LineBreak.WordBreak.Phrase`, iOS 기본) |
| 아이콘 | `design/icons` 선 아이콘, 기본 크기 `size.icon`. 색은 쓰는 곳의 글자색 |
| 화면 좌우 여백 | `size.screenPadding` |
| 폼 항목 간격 | `spacing.lg` |

## 큰 제목 `ElsaScreenHeader`

| 항목 | 값 |
|---|---|
| 제목 | `typography.headline`, `onBackground` |
| 설명 | `typography.body`, `onSurfaceVariant` |
| 제목–설명 | `spacing.sm` |
| 아래 여백 | `spacing.md` |

## 입력칸 `ElsaTextField` / `ElsaSelectField`

| 항목 | 값 |
|---|---|
| 라벨 | `typography.label`, `onSurfaceVariant` (비활성 `onDisabled`), 입력칸까지 `spacing.xs` |
| 칸 | 최소 높이 `size.controlHeight`, radius `radius.md`, 채움 `fill`, 테두리 없음 |
| 글자 | `typography.body`, 안내 글자 `onSurfaceTertiary` |
| 포커스 | 테두리 `primary` |
| 오류 | 테두리 `error` + 아래 문구 `typography.caption` `error` |
| 아이콘(앞·비밀번호 보기) | `size.icon`, `onSurfaceVariant` |

## 버튼 `ElsaButton`

| 크기 | 높이 | radius | 좌우 여백 | 글자 |
|---|---|---|---|---|
| Large(기본, 하단 CTA) | `size.controlHeight` | `radius.lg` | `spacing.xl` | `typography.button` |
| Small | `size.controlHeightSmall` | `radius.md` | `spacing.lg` | `typography.label` |

| 스타일 | 배경 / 글자 | 비활성 |
|---|---|---|
| Primary | `primary` / `onPrimary` | `disabled` / `onDisabled` |
| Secondary | `fill` / `onSurface` | `disabled` / `onDisabled` |
| Outline | 없음, 테두리 `size.border` `outlineStrong` / `primary` | 테두리 `outline`, 글자 `onDisabled` |
| Text | 없음 / `primary` | 글자 `onDisabled` |
| Destructive | `error` / `onError` | `disabled` / `onDisabled` |

앞 아이콘 `size.icon`, 아이콘–글자 `spacing.sm`. 로딩 중에는 글자 대신 표시기, 누를 수 없다.

## 체크박스 `ElsaCheckbox`

| 항목 | 값 |
|---|---|
| 상자 | `size.checkbox`, 선택 시 `primary` |
| 상자–라벨 | `spacing.sm` |
| 라벨 / 설명 | `typography.body` / `typography.caption` `onSurfaceVariant` |

## 인라인 알림 `ElsaInlineMessage`

| 항목 | 값 |
|---|---|
| 모양 | radius `radius.md`, 종류별 `<type>Container` 배경 |
| 안쪽 여백 | 좌우 `spacing.lg`, 위아래 `spacing.md` |
| 아이콘 | 종류별 `elsa_ic_info·success·warning·error`, `size.icon`, 종류별 강조색 |
| 글자 | `typography.bodySmall`, 아이콘–글자 `spacing.sm` |

## 하단 고정 영역 (`ElsaFormColumn` bottomBar / `ElsaFormScrollView` bottomBar)

| 항목 | 값 |
|---|---|
| 좌우 | `size.screenPadding` |
| 위 / 아래 | `spacing.sm` / `spacing.lg` |
| 항목 간격 | `spacing.xs` |
| 키보드 | 키보드 바로 위에 붙는다 |

## 목록 행 `ElsaListItem` (19-0)

| 항목 | 값 |
|---|---|
| 구성 | 앞(아이콘·아바타) – 제목·부제 – 뒤(시간·배지·스위치), 항목 간격 `spacing.md` |
| 높이 | 최소 `size.controlHeight`, 안쪽 여백 좌우 `spacing.xl`·위아래 `spacing.md` |
| 글자 | 제목 `typography.bodyStrong` 한 줄, 부제 `typography.bodySmall`·`onSurfaceVariant` 두 줄, 제목–부제 `spacing.xxs` |
| 눌림 | 누를 수 있을 때만(Android ripple, iOS `ElsaPressableButtonStyle`) |
| 설정 화면 | 값·화살표가 정해진 `ElsaSettingsRow` 를 쓴다 |

## 바텀시트 `ElsaBottomSheet` / 액션 메뉴 `ElsaActionSheet` (19-0)

| 항목 | 값 |
|---|---|
| 높이 | 내용 높이만큼(반쯤 열린 단계 없음), 끌어내리기·바깥 누르기로 닫힘, 손잡이 표시 |
| 배경 | `surface` |
| 제목 | 바텀시트 `typography.title`, 액션 메뉴 `typography.bodySmall`·`onSurfaceVariant`, 좌우 `spacing.xl` |
| 액션 항목 | 높이 `size.controlHeight`, 아이콘–글자 `spacing.lg`, 위험 항목은 `error` |
| 동작 | 액션은 시트가 닫힌 뒤 실행(카메라·파일 선택 화면과 겹치지 않게) |
| 하나 고르기 | `ElsaOptionSheet`/`elsaOptionSheet` 는 바텀시트 위에 만든다 |

## 화면 전환 (19-0, DEC-127)

전환은 각 플랫폼 네비게이션 특성대로 한다(같아야 하는 것은 화면 결과). 소스는 각자 — Compose Navigation / SwiftUI NavigationStack.

| 전환 | Android | iOS |
|---|---|---|
| 위에 쌓이는 화면(상세·설정 하위·약관 등) | Material 공유 축(가로): 화면 너비 1/10 밀며 페이드, 300ms | `NavigationStack` 기본 push |
| 루트끼리(스플래시·온보딩·로그인·탭 전환) | 페이드 | 단계 교체 페이드(`AppPhase`), 탭은 전환 없음 |
| 기준 | `NavDestination.isRoot()`(AndroidNavigator) — 탭을 늘리면 `MainTab` 만 고친다 | — |

## 메시지 (19-2, `ElsaChat.kt` / `elsa.chat.swift`)

화면 구성·동작은 텔레그램처럼, 코드·이름·로고는 쓰지 않는다(DEC-111·125). 묶기·날짜 구분 규칙은 KMP `buildRows`(같은 사람·같은 날·5분).

| 항목 | 값 |
|---|---|
| 말풍선 | 최대 너비 300, 너비는 내용에 맞춤, 안쪽 좌우 `spacing.md`·위아래 `spacing.sm`, 글자 `typography.body` |
| 색 | 내 것 `primary`/`onPrimary`(오른쪽), 상대 `surface`/`onSurface`(왼쪽), 배경 `backgroundGrouped` |
| 모서리 | 18, 묶음의 이어지는 쪽 6, 꼬리 쪽(아래 바깥) 마지막은 3 |
| 간격 | 묶음 안 `spacing.xxs`, 묶음 사이 `spacing.sm` |
| 시간·상태 | 묶음의 마지막에만, 오른쪽 아래 `typography.caption` 70% — 보내는 중 시계, 보냄 ✓, 읽음 ✓✓(14), 실패는 말풍선 밖 `error` 아이콘(누르면 다시 보내기) |
| 사진 | 300×225 채움(여백 없음), 보내는 중에는 기기 미리보기 |
| 파일 | 원 40(글자색 15%) 안 `elsa_ic_file` + 이름(`bodyStrong` 한 줄)·크기(`caption`) |
| 날짜 구분선 | 가운데 알약 `surface` 90%, `typography.caption`·`onSurfaceVariant` |
| 입력창 | `surface` 위, 첨부(`elsa_ic_attach`) – 채움 입력칸(`fill`, radius `radius.lg`, 최대 5줄) – 보내기(`elsa_ic_send`, 글자가 있을 때 `primary`) |
| 맨 아래로 | 원 `controlHeightSmall`, `surface`, 그림자, 바닥에서 240 이상 올라가면 |
| 첨부 메뉴·길게 눌러 메뉴 | `ElsaActionSheet` — 사진·카메라·파일 / 복사·다시 보내기·지우기 |

## 커뮤니티 (20-2, `ElsaPost.kt` / `elsa.post.swift`)

| 항목 | 값 |
|---|---|
| 글 카드 | `surface`, radius `radius.xl`, 안쪽 좌 `spacing.lg`·위 `spacing.md`, 항목 간격 `spacing.sm`, 목록 배경 `backgroundGrouped`, 카드 사이 `spacing.md` |
| 글쓴이 줄 | 아바타 40, 이름 `bodyStrong`, 시간 `caption`·`onSurfaceTertiary`(방금 전·n분 전, KMP `RelativeTime`), 더보기 `elsa_ic_more` |
| 본문 | `typography.body`, 목록은 3줄(말줄임), 상세는 전체 |
| 사진 묶음 | 1장 16:9 · 2장 2:1 · 3장 3:2(왼쪽 2/3) · 4장 1:1(2×2), 간격 `spacing.xxs`, radius `radius.md`, 주소 없으면 `fill` + `elsa_ic_image` |
| 좋아요·댓글 | 아이콘 `size.icon` + 숫자 `typography.label`, 누른 좋아요는 `elsa_ic_heart_filled`·`error`, 최소 높이 `controlHeightSmall` |
| 댓글 행 | 아바타 32, 이름 `label`·시간 `caption` 한 줄, 내용 `body`, 위아래 `spacing.sm` |
| 글쓰기 버튼 | 오른쪽 아래 원 `controlHeight`, `primary`, `elsa_ic_edit`, 화면 가장자리에서 `size.screenPadding` |
| 글쓰기 화면 | 입력칸 `fill`·radius `radius.lg`·최소 200, 글자 수 `n/2000`(caption, 넘으면 `error`), 사진 줄 `imageThumbnail`·빼기 버튼 24(`scrim`), 하단 고정 올리기/저장 |
| 메뉴 | 내 글: 수정·삭제(확인), 남의 글·댓글: 신고(사유 4개)·차단(확인) — `ElsaActionSheet` |
| 글쓴이 누르기(25) | 글 카드·상세의 아바타+이름, 댓글의 아바타 → 사용자 프로필. 누르는 영역 radius `radius.md` |
| 사용자 프로필(25) | 위 카드 `surface`·radius `radius.xl`·안쪽 `spacing.lg`, 가운데 아바타 88 → 이름 `title` → 소개 `body`·`onSurfaceVariant` → 글 수 `caption` → "메시지 보내기" Primary(앞 아이콘 `elsa_ic_chat`, 내 프로필이면 없음). 아래 그 사람의 글 카드(글쓴이 누르기 없음), 오른쪽 위 더보기 → 차단 |
| 문의하기(25) | 큰 제목 → 종류 칩(`ElsaChip` 4개, 줄바꿈) → 내용(여러 줄 최소 6줄, `n/2000`) → 이메일(계정 이메일로 채움) → 하단 고정 "보내기" → 확인 알림 후 닫기 |

## 채팅 고도화 (22-2, `ElsaChatExtras.kt` / `elsa.chat.extras.swift`)

| 항목 | 값 |
|---|---|
| 인용 블록(말풍선 안) | 왼쪽 막대 3(글자색 80%), 바탕 글자색 12%, radius `radius.xs`, 이름 `label`·원문 한 줄 `bodySmall` 80%, 누르면 원문으로 옮겨 1.5초 강조(`primary` 15%) |
| 답장 막대(입력창 위) | `surface`, `elsa_ic_reply`·막대 3(`primary`), "답장 · 이름" `label`·`primary`, 원문 한 줄, 오른쪽 취소. 이름은 내 것이면 "나", 아니면 대화 상대 |
| 밀어서 답장 | 보낸 말풍선을 오른쪽으로 64 넘게 밀었다 놓으면(최대 84까지 따라옴), 길게 눌러 메뉴의 "답장"도 같다 |
| 입력 중 | 상대 말풍선 안 점 3개(8, 간격 `spacing.xs`), 0.2초씩 늦게 0.3 ↔ 1 깜박임, 5초 뒤 사라짐 |
| 사진 전체 화면 | 검은 바탕, 1~4배 확대·이동, 두 번 누르면 원래대로, 오른쪽 위 닫기 |
| 대화 검색 | 상단 돋보기 → 검색창(상단) + 결과 목록(`ElsaListItem`: 메시지·날짜), 누르면 그 메시지로(위로 최대 10묶음 더 불러옴), 없으면 안내 |

## 칩·세그먼트·스켈레톤·검색창 (21-1, `ElsaChips.kt` / `elsa.chips.swift`)

| 항목 | 값 |
|---|---|
| 칩 | 알약(`radius.full`), 최소 높이 36, 좌우 `spacing.md`, 글자 `typography.label`, 선택 `primary`/`onPrimary`, 아니면 `fill`/`onSurface`, 지우기 28(아이콘 `iconSmall`), 묶음 간격 `spacing.sm`(넘치면 다음 줄) |
| 세그먼트 탭 | `fill` 바탕·radius `radius.md`·안쪽 `spacing.xxs`, 고른 칸 `surface`+`divider` 테두리·radius `radius.sm`, 칸 너비 같음, 높이 `controlHeightSmall`, 고른 글자 `bodyStrong`·나머지 `body`/`onSurfaceVariant` |
| 스켈레톤 | `fill` 상자, 1초 주기로 불투명도 1 ↔ 0.45, 행(아바타+두 줄 40%·80%)·카드(아바타 40+제목+두 줄) — 목록 첫 로딩 자리 |
| 검색창 | 알약 `fill`, 최소 높이 44, 앞 `elsa_ic_search`·뒤 지우기(글자 있을 때), 키보드 검색 키 |
| 탭 배지 | 메시지 탭 아이콘 위 `error` 숫자(99+), 0 이면 없음 |

## 설정 스위치·잠금 화면 (26)

| 항목 | 값 |
|---|---|
| 설정 스위치 행 | `ElsaSettingsSwitchRow`(Android) / `ElsaSettingsToggleRow`(iOS): 제목 `body` + 설명 `bodySmall`, 오른쪽 스위치(`primary`), 좌우 `spacing.xl`·위아래 `spacing.sm`, 줄 전체를 눌러 바꾼다 |
| 알림 설정 화면 | 큰 제목 → (기기 알림이 꺼져 있으면) 경고 안내 + "설정 열기" Secondary Small → 섹션 "활동"(채팅·댓글)·"소식"(공지·광고성), 배경 `backgroundGrouped` |
| 잠금 화면 | 배경 `background`, 가운데 원 72(`primaryContainer`) 안 자물쇠 32(`onPrimaryContainer`) → 제목 `title` → 설명 `body`·`onSurfaceVariant`, 아래 "잠금 해제" Primary. 나타나면 바로 본인 확인 |

## 화면: 로그인

위에서부터: 여백 `spacing.xxl` → 큰 제목(로그인 + 설명) → 아이디 → 비밀번호(보기 아이콘) → 체크박스 2개(간격 `spacing.lg`)
→ (실패 시) 오류 인라인 알림 → "비밀번호를 잊으셨나요?"(Text·Small, 오른쪽 정렬).
하단 고정: 로그인 버튼(Large, 아이디·비밀번호가 모두 있을 때 활성) → "아직 계정이 없나요?"(`bodySmall`) + 회원가입(Text·Small).
실패하면 아이디는 두고 비밀번호를 비운다(DEC-116). 틀린 비밀번호는 "아이디 또는 비밀번호가 맞지 않아요."(DEC-115).

약관·가입·비밀번호 재설정도 같은 틀(큰 제목 → 입력·목록 → 하단 고정 버튼)이다. 약관 목록은 카드 없이 화면 배경에 두어 제목과 왼쪽을 맞춘다.
