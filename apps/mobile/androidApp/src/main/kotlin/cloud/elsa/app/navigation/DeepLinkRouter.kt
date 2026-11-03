package cloud.elsa.app.navigation

import androidx.navigation.NavController
import cloud.elsa.app.core.common.navigation.DeepLink

/**
 * DeepLink(path) → Android 화면. 푸시 탭·외부 링크가 여기로 들어온다.
 * 처리했으면 true. 모르는 path 는 false(현재 화면 유지).
 * iOS `DeepLinkRouter` 와 같은 path 를 쓴다.
 *
 * 이름을 `navigate` 로 짓지 않는다 — NavController 의 멤버 `navigate(route: T)` 가 확장 함수보다 먼저 선택돼
 * DeepLink 를 화면 경로로 직렬화하려다 앱이 종료된다.
 */
fun NavController.openDeepLink(deepLink: DeepLink): Boolean {
    // 인자가 있는 화면: <path>/<id>
    val id = deepLink.path.substringAfter('/', "")
    when (deepLink.path.substringBefore('/').takeIf { id.isNotEmpty() }) {
        "notice" -> return navigateToNoticeDetail(id).let { true }
        "example" -> return navigateToExampleDetail(id).let { true }
        // 이름은 대화방이 불러온다(21-3)
        "chat" -> return navigateToChatRoom(id, title = "").let { true }
        "community" -> return navigateToPostDetail(id).let { true }
        // scaffold:deeplink-details — 상세 화면 path 는 이 줄 위에
    }
    when (deepLink.path) {
        "home" -> navigateToTab(MainTab.Home)
        "settings" -> navigateToTab(MainTab.Settings)
        "gallery" -> navigateToComponentGallery()
        "example" -> navigateToExample()
        "notice" -> navigateToNotice()
        "permission" -> navigateToPermission()
        "inbox" -> navigateToInbox()
        "search" -> navigateToSearch()
        "chat" -> navigateToChat()
        "community" -> navigateToCommunity()
        // scaffold:deeplinks — newFeature 가 이 줄 위에 path 를 추가한다
        else -> return false
    }
    return true
}
