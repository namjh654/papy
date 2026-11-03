package cloud.elsa.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * 화면 전환 규칙(back stack 정리 포함)을 한 곳에 모은 NavController 확장입니다.
 * 화면은 ViewModel 이벤트를 받아 아래 함수만 호출합니다.
 */

/** 세션이 없을 때: 현재 스택을 모두 비우고 로그인으로 이동합니다. */
fun NavController.navigateToLogin() {
    navigate(AndroidRoute.Login) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** 처음 실행: 스플래시를 비우고 온보딩으로 */
fun NavController.navigateToOnboarding() {
    navigate(AndroidRoute.Onboarding) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** 회원가입은 약관 동의부터 */
fun NavController.navigateToTerms() {
    navigate(AndroidRoute.Terms()) {
        launchSingleTop = true
    }
}

/** 처음 가입한 소셜 사용자: 로그인 화면을 비우고 약관 동의로(뒤로 = 로그아웃 후 로그인, 18-4) */
fun NavController.navigateToSocialConsent() {
    navigate(AndroidRoute.Terms(socialConsent = true)) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** 약관 화면은 스택에서 빼고 회원가입을 올린다(가입 화면에서 뒤로 → 로그인) */
fun NavController.navigateToSignup(marketingAgreed: Boolean) {
    navigate(AndroidRoute.Signup(marketingAgreed)) {
        popUpTo<AndroidRoute.Terms> { inclusive = true }
        launchSingleTop = true
    }
}

fun NavController.navigateToPasswordReset() {
    navigate(AndroidRoute.PasswordReset) {
        launchSingleTop = true
    }
}

fun NavController.navigateToProfile() {
    navigate(AndroidRoute.Profile) {
        launchSingleTop = true
    }
}

fun NavController.navigateToWithdraw() {
    navigate(AndroidRoute.Withdraw) {
        launchSingleTop = true
    }
}

/** 로그인/세션 복원 성공: 인증 화면을 스택에서 제거하고 홈 탭으로 이동합니다. */
fun NavController.navigateToHome() {
    navigate(AndroidRoute.Home) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** 탭 전환: 탭마다 스택 상태를 저장·복원하고, 같은 탭을 다시 눌러도 쌓이지 않는다 */
fun NavController.navigateToTab(tab: MainTab) {
    navigate(tab.route) {
        popUpTo<AndroidRoute.Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 지금 화면이 탭 루트면 그 탭(하단 탭 바를 보여준다), 아니면 null */
fun NavDestination?.currentTab(): MainTab? =
    this?.let { destination -> MainTab.entries.firstOrNull { destination.hasRoute(it.route::class) } }

internal fun sharedAxisIn(forward: Boolean): EnterTransition =
    slideInHorizontally(tween(SHARED_AXIS_MILLIS)) { width -> if (forward) width / SHARED_AXIS_OFFSET else -width / SHARED_AXIS_OFFSET } +
        fadeIn(tween(SHARED_AXIS_MILLIS))

internal fun sharedAxisOut(forward: Boolean): ExitTransition =
    slideOutHorizontally(tween(SHARED_AXIS_MILLIS)) { width -> if (forward) -width / SHARED_AXIS_OFFSET else width / SHARED_AXIS_OFFSET } +
        fadeOut(tween(SHARED_AXIS_MILLIS))

/** Material 공유 축: 화면 너비의 1/10 만큼 밀며 300ms 동안 바뀐다 */
private const val SHARED_AXIS_MILLIS = 300
private const val SHARED_AXIS_OFFSET = 10

/**
 * 화면 전환 애니메이션(DEC-127): 루트(스플래시·온보딩·로그인·탭 루트)로 가고 오는 전환은 페이드, 그 위에 쌓이는 화면은 Material 공유 축 [sharedAxisIn]/[sharedAxisOut].
 * 탭을 추가하면 [MainTab] 만 고치면 된다.
 */
fun NavDestination.isRoot(): Boolean =
    hasRoute<AndroidRoute.Splash>() ||
        hasRoute<AndroidRoute.Onboarding>() ||
        hasRoute<AndroidRoute.Login>() ||
        MainTab.entries.any { tab -> hasRoute(tab.route::class) }

/**
 * 로그인이 필요한 화면(아래 "로그인 전 화면" 외 전부)에 있는지. 새 화면은 자동으로 포함된다.
 * 로그인 전에 열리는 화면을 추가하면 여기에 한 줄 넣는다.
 */
fun NavController.isInMainGraph(): Boolean {
    val destination = currentDestination ?: return false
    val beforeLogin = destination.hasRoute<AndroidRoute.Splash>() ||
        destination.hasRoute<AndroidRoute.Onboarding>() ||
        destination.hasRoute<AndroidRoute.Login>() ||
        destination.hasRoute<AndroidRoute.Terms>() ||
        destination.hasRoute<AndroidRoute.Signup>() ||
        destination.hasRoute<AndroidRoute.PasswordReset>()
    // 약관 전문(웹뷰)은 로그인 전(약관 화면)에도 열린다
    val webBeforeLogin = destination.hasRoute<AndroidRoute.Web>() &&
        previousBackStackEntry?.destination?.hasRoute<AndroidRoute.Terms>() == true
    return !beforeLogin && !webBeforeLogin
}

fun NavController.navigateToComponentGallery() {
    navigate(AndroidRoute.ComponentGallery) {
        launchSingleTop = true
    }
}

fun NavController.navigateToWeb(url: String, title: String) {
    navigate(AndroidRoute.Web(url = url, title = title))
}

fun NavController.navigateToExample() {
    navigateToTab(MainTab.Example)
}

fun NavController.navigateToNoticeDetail(id: String) {
    navigate(AndroidRoute.NoticeDetail(id)) {
        launchSingleTop = true
    }
}

fun NavController.navigateToExampleDetail(id: String) {
    navigate(AndroidRoute.ExampleDetail(id)) {
        launchSingleTop = true
    }
}

fun NavController.navigateToNotice() {
    navigate(AndroidRoute.Notice) {
        launchSingleTop = true
    }
}

fun NavController.navigateToPermission() {
    navigate(AndroidRoute.Permission) {
        launchSingleTop = true
    }
}

fun NavController.navigateToInbox() {
    navigate(AndroidRoute.Inbox) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSearch() {
    navigate(AndroidRoute.Search) {
        launchSingleTop = true
    }
}

fun NavController.navigateToChat() {
    navigateToTab(MainTab.Chat)
}

/** 대화방: 같은 방을 두 번 쌓지 않는다 */
fun NavController.navigateToChatRoom(conversationId: String, title: String) {
    navigate(AndroidRoute.ChatRoom(conversationId, title)) {
        launchSingleTop = true
    }
}

fun NavController.navigateToCommunity() {
    navigateToTab(MainTab.Community)
}

fun NavController.navigateToPostDetail(postId: String) {
    navigate(AndroidRoute.PostDetail(postId)) { launchSingleTop = true }
}

/** @param postId 고칠 글, 새 글은 null */
fun NavController.navigateToPostEditor(postId: String?) {
    navigate(AndroidRoute.PostEditor(postId.orEmpty())) { launchSingleTop = true }
}

fun NavController.navigateToBlockedUsers() {
    navigate(AndroidRoute.BlockedUsers) { launchSingleTop = true }
}

fun NavController.navigateToUserProfile(userId: String) {
    navigate(AndroidRoute.UserProfile(userId)) { launchSingleTop = true }
}

/** 프로필의 "메시지 보내기": 그 사람과의 1:1 대화방(대화 id 는 KMP 가 찾는다) */
fun NavController.navigateToDirectChat(userId: String, name: String) {
    navigate(AndroidRoute.ChatRoom(conversationId = "", title = name, peerUserId = userId)) { launchSingleTop = true }
}

fun NavController.navigateToInquiry() {
    navigate(AndroidRoute.Inquiry) { launchSingleTop = true }
}

fun NavController.navigateToNotificationSettings() {
    navigate(AndroidRoute.NotificationSettings) { launchSingleTop = true }
}

fun NavController.navigateToDownloads() {
    navigate(AndroidRoute.Downloads) { launchSingleTop = true }
}
