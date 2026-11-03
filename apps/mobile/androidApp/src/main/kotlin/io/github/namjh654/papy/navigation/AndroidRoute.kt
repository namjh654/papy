package io.github.namjh654.papy.navigation

import kotlinx.serialization.Serializable

/**
 * Android 화면 경로입니다. (Navigation Compose type-safe route)
 *
 * Route는 Android에만 존재하며 KMP ViewModel은 이 타입을 알지 못합니다.
 * 탭 루트 화면(Home·Example·Settings)은 [MainTab] 에도 등록한다.
 */
sealed interface AndroidRoute {

    @Serializable
    data object Splash : AndroidRoute

    /** 처음 실행 때 한 번 */
    @Serializable
    data object Onboarding : AndroidRoute

    @Serializable
    data object Login : AndroidRoute

    /** 약관 동의(회원가입 전). [socialConsent]: 처음 가입한 소셜 사용자의 동의(세션은 이미 시작됨, 18-4) */
    @Serializable
    data class Terms(val socialConsent: Boolean = false) : AndroidRoute

    @Serializable
    data class Signup(val marketingAgreed: Boolean = false) : AndroidRoute

    @Serializable
    data object PasswordReset : AndroidRoute

    /** 프로필 편집(설정 맨 위) */
    @Serializable
    data object Profile : AndroidRoute

    /** 회원 탈퇴(설정 → 계정) */
    @Serializable
    data object Withdraw : AndroidRoute

    @Serializable
    data object Home : AndroidRoute

    @Serializable
    data object Settings : AndroidRoute

    /** 디자인 시스템 컴포넌트 갤러리 (템플릿 확인용) */
    @Serializable
    data object ComponentGallery : AndroidRoute

    /** 웹 페이지(약관·공지). 신뢰하는 자체 페이지만 연다 */
    @Serializable
    data class Web(val url: String, val title: String) : AndroidRoute

    @Serializable
    data object Example : AndroidRoute

    @Serializable
    data object Notice : AndroidRoute

    /** 공지 상세 — 인자는 route 의 프로퍼티로 넘긴다 */
    @Serializable
    data class NoticeDetail(val id: String) : AndroidRoute

    /** 예시 상세(수정 → 목록 반영) */
    @Serializable
    data class ExampleDetail(val id: String) : AndroidRoute

    @Serializable
    data object Permission : AndroidRoute

    @Serializable
    data object Inbox : AndroidRoute

    @Serializable
    data object Search : AndroidRoute

    @Serializable
    data object Chat : AndroidRoute

    /** 대화방 — 목록에서 고른 대화의 id·이름. 프로필에서 열면 id 는 "" 이고 [peerUserId](25-2) */
    @Serializable
    data class ChatRoom(val conversationId: String, val title: String, val peerUserId: String = "") : AndroidRoute

    @Serializable
    data object Community : AndroidRoute

    /** 글 상세 */
    @Serializable
    data class PostDetail(val postId: String) : AndroidRoute

    /** 글쓰기·고치기 — 새 글은 "" */
    @Serializable
    data class PostEditor(val postId: String = "") : AndroidRoute

    /** 설정 → 차단한 사용자 */
    @Serializable
    data object BlockedUsers : AndroidRoute

    /** 사용자 프로필(글쓴이를 눌렀을 때, 25-1) */
    @Serializable
    data class UserProfile(val userId: String) : AndroidRoute

    /** 설정 → 문의하기(25-3) */
    @Serializable
    data object Inquiry : AndroidRoute

    /** 설정 → 알림 설정(26-2) */
    @Serializable
    data object NotificationSettings : AndroidRoute

    /** 설정 → 저장한 파일(30-1) */
    @Serializable
    data object Downloads : AndroidRoute

    // scaffold:routes — newFeature 가 이 줄 위에 경로를 추가한다
}
