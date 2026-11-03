package io.github.namjh654.papy.navigation

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.namjh654.papy.R
import io.github.namjh654.papy.core.data.connectivity.NetworkMonitor
import io.github.namjh654.papy.core.data.push.PushManager
import io.github.namjh654.papy.core.data.session.AuthStatus
import io.github.namjh654.papy.core.data.session.AuthStore
import io.github.namjh654.papy.designsystem.component.PapyNavigationBar
import io.github.namjh654.papy.designsystem.component.PapyOfflineBanner
import io.github.namjh654.papy.designsystem.component.PapyTabItem
import io.github.namjh654.papy.designsystem.component.PapyToastHost
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.push.PushEffects
import io.github.namjh654.papy.update.AppUpdateEffect
import io.github.namjh654.papy.screen.example.ExampleScreen
import io.github.namjh654.papy.screen.gallery.ComponentGalleryScreen
import io.github.namjh654.papy.screen.home.HomeScreen
import io.github.namjh654.papy.screen.login.LoginScreen
import io.github.namjh654.papy.screen.onboarding.OnboardingScreen
import io.github.namjh654.papy.screen.profile.ProfileScreen
import io.github.namjh654.papy.screen.reset.PasswordResetScreen
import io.github.namjh654.papy.screen.settings.SettingsScreen
import io.github.namjh654.papy.screen.signup.SignupScreen
import io.github.namjh654.papy.screen.splash.SplashScreen
import io.github.namjh654.papy.screen.terms.SocialConsentScreen
import io.github.namjh654.papy.screen.terms.TermsScreen
import io.github.namjh654.papy.screen.web.WebScreen
import io.github.namjh654.papy.screen.withdraw.WithdrawScreen
import io.github.namjh654.papy.screen.user.UserProfileScreen
import io.github.namjh654.papy.screen.inquiry.InquiryScreen
import io.github.namjh654.papy.screen.downloads.DownloadsScreen
import io.github.namjh654.papy.screen.notification.NotificationSettingsScreen
import io.github.namjh654.papy.screen.example.ExampleDetailScreen
import io.github.namjh654.papy.screen.notice.NoticeDetailScreen
import io.github.namjh654.papy.screen.notice.NoticeScreen
import io.github.namjh654.papy.screen.permission.PermissionScreen
import io.github.namjh654.papy.screen.inbox.InboxScreen
import io.github.namjh654.papy.screen.search.SearchScreen
import io.github.namjh654.papy.feature.chat.ChatRepository
import io.github.namjh654.papy.screen.chat.ChatRoomScreen
import io.github.namjh654.papy.screen.chat.ChatScreen
import io.github.namjh654.papy.screen.blocked.BlockedUsersScreen
import io.github.namjh654.papy.screen.community.CommunityScreen
import io.github.namjh654.papy.screen.community.PostDetailScreen
import io.github.namjh654.papy.screen.community.PostEditorScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** 종료 안내 뒤 이 시간 안에 다시 눌러야 종료된다(토스트가 보이는 시간과 비슷하게) */
private const val EXIT_WINDOW_MILLIS = 2_000L

@Composable
fun AndroidNavHost() {

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination.currentTab()

    // 세션 만료(401 → 재발급 실패 → SessionRepository.expire) 등으로 인증이 풀리면 메인 화면에서 로그인으로 보낸다.
    // 로그아웃은 SettingsEvent.LoggedOut 으로도 이동하지만 navigateToLogin 이 singleTop 이라 중복돼도 안전하다.
    val authStatus by koinInject<AuthStore>().status.collectAsStateWithLifecycle()
    LaunchedEffect(authStatus) {
        if (authStatus == AuthStatus.Unauthenticated && navController.isInMainGraph()) {
            navController.navigateToLogin()
        }
    }

    val isOnline by koinInject<NetworkMonitor>().isOnline.collectAsStateWithLifecycle()
    val pushManager = koinInject<PushManager>()

    // 탭 루트에서의 뒤로가기(아래 BackHandler)가 쓰는 것들
    val activity = LocalContext.current as? Activity
    val toast = rememberPapyToastState()
    val scope = rememberCoroutineScope()
    val exitHint = stringResource(R.string.main_exit_hint)
    var exitArmedAt by remember { mutableLongStateOf(0L) }

    // 알림 권한 요청(로그인 후), 알림 탭 DeepLink 열기
    PushEffects(navController, authStatus)

    // 메시지 탭 배지: 안 읽은 수(21-4)
    val chatRepository = koinInject<ChatRepository>()
    val chatUnread by chatRepository.unreadTotal.collectAsStateWithLifecycle()
    LaunchedEffect(authStatus) { if (authStatus == AuthStatus.Authenticated) chatRepository.refreshUnread() }

    // 강제·선택 업데이트 안내 (시작 때 한 번 확인)
    AppUpdateEffect()

    Scaffold(
        snackbarHost = { PapyToastHost(toast) },
        // 탭 루트 화면에서만 하단 탭 바
        bottomBar = {
            if (currentTab != null) {
                PapyNavigationBar(
                    items = MainTab.entries.map { tab ->
                        PapyTabItem(
                            label = stringResource(tab.label),
                            icon = tab.icon,
                            selected = tab == currentTab,
                            onClick = { navController.navigateToTab(tab) },
                            badge = if (tab == MainTab.Chat) chatUnread else 0,
                        )
                    },
                )
            }
        },
        containerColor = PapyTheme.colors.background,
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PapyOfflineBanner(visible = !isOnline)

            NavHost(
                navController = navController,
                startDestination = AndroidRoute.Splash,
                // 쌓이는 화면은 Material 공유 축(가로: 살짝 밀며 페이드), 루트끼리(스플래시·온보딩·로그인·탭)는 페이드 — isRoot().
                // iOS 를 흉내 내지 않고 Android 네비게이션 특성대로(DEC-127). 같아야 하는 것은 화면 결과뿐
                enterTransition = {
                    if (targetState.destination.isRoot()) fadeIn() else sharedAxisIn(forward = true)
                },
                exitTransition = {
                    if (targetState.destination.isRoot()) fadeOut() else sharedAxisOut(forward = true)
                },
                popEnterTransition = {
                    if (initialState.destination.isRoot()) fadeIn() else sharedAxisIn(forward = false)
                },
                popExitTransition = {
                    if (initialState.destination.isRoot()) fadeOut() else sharedAxisOut(forward = false)
                },
            ) {

                composable<AndroidRoute.Splash> {
                    SplashScreen(
                        onSessionRestored = navController::navigateToHome,
                        onSessionNotFound = navController::navigateToLogin,
                        onFirstLaunch = navController::navigateToOnboarding,
                    )
                }

                composable<AndroidRoute.Onboarding> {
                    OnboardingScreen(onDone = navController::navigateToLogin)
                }

                composable<AndroidRoute.Login> {
                    LoginScreen(
                        onLoginSucceeded = navController::navigateToHome,
                        onSocialSignedUp = navController::navigateToSocialConsent,
                        onSignupClick = navController::navigateToTerms,
                        onPasswordResetClick = navController::navigateToPasswordReset,
                    )
                }

                composable<AndroidRoute.Terms> { entry ->
                    if (entry.toRoute<AndroidRoute.Terms>().socialConsent) {
                        SocialConsentScreen(
                            onCompleted = navController::navigateToHome,
                            onCancelled = navController::navigateToLogin,
                            onOpenWeb = navController::navigateToWeb,
                        )
                    } else {
                        TermsScreen(
                            onAgreed = navController::navigateToSignup,
                            onOpenWeb = navController::navigateToWeb,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                composable<AndroidRoute.Signup> { entry ->
                    SignupScreen(
                        marketingAgreed = entry.toRoute<AndroidRoute.Signup>().marketingAgreed,
                        // 가입 완료 안내는 로그인 화면이 한다(LoginNoticeStore)
                        onSignupSucceeded = { navController.popBackStack() },
                        onBackClick = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.PasswordReset> {
                    PasswordResetScreen(
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }

                // --- 탭 루트 (MainTab) ---

                composable<AndroidRoute.Home> {
                    HomeScreen(
                        onComponentGalleryClick = navController::navigateToComponentGallery,
                        onExampleClick = navController::navigateToExample,
                        onNoticeClick = navController::navigateToNotice,
                        onInboxClick = navController::navigateToInbox,
                        onSearchClick = navController::navigateToSearch,
                        onPermissionClick = navController::navigateToPermission,
                    )
                }

                composable<AndroidRoute.Example> {
                    ExampleScreen(
                        onItemSelected = navController::navigateToExampleDetail,
                    )
                }

                composable<AndroidRoute.Settings> {
                    SettingsScreen(
                        onLoggedOut = navController::navigateToLogin,
                        onOpenWeb = navController::navigateToWeb,
                        onProfileClick = navController::navigateToProfile,
                        onWithdrawClick = navController::navigateToWithdraw,
                        onBlockedUsersClick = navController::navigateToBlockedUsers,
                        onInquiryClick = navController::navigateToInquiry,
                        onNotificationSettingsClick = navController::navigateToNotificationSettings,
                        onDownloadsClick = navController::navigateToDownloads,
                    )
                }

                // --- 탭 위에 쌓이는 화면 ---

                composable<AndroidRoute.Profile> {
                    ProfileScreen(onBack = { navController.popBackStack() })
                }

                composable<AndroidRoute.Withdraw> {
                    WithdrawScreen(
                        onWithdrawn = navController::navigateToLogin,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.ComponentGallery> {
                    ComponentGalleryScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.Web> { entry ->
                    val route = entry.toRoute<AndroidRoute.Web>()
                    WebScreen(
                        url = route.url,
                        title = route.title,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.Notice> {
                    NoticeScreen(
                        onItemSelected = navController::navigateToNoticeDetail,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.NoticeDetail> { entry ->
                    NoticeDetailScreen(
                        id = entry.toRoute<AndroidRoute.NoticeDetail>().id,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.ExampleDetail> { entry ->
                    ExampleDetailScreen(
                        id = entry.toRoute<AndroidRoute.ExampleDetail>().id,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.Permission> {
                    PermissionScreen(onBack = { navController.popBackStack() })
                }

                composable<AndroidRoute.Inbox> {
                    InboxScreen(
                        // 알림에 연결된 화면: 알림 탭·외부 링크와 같은 길(PushManager.pendingDeepLink)로 연다
                        onOpenLink = { link -> pushManager.openLink(link) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.Search> {
                    SearchScreen(
                        // 예시 데이터의 검색 결과는 예시 상세로 간다
                        onItemSelected = navController::navigateToExampleDetail,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.Chat> {
                    ChatScreen(
                        onItemSelected = { navController.navigateToChatRoom(it.id, it.title) },
                    )
                }

                composable<AndroidRoute.ChatRoom> { entry ->
                    val route = entry.toRoute<AndroidRoute.ChatRoom>()
                    ChatRoomScreen(
                        conversationId = route.conversationId,
                        title = route.title,
                        peerUserId = route.peerUserId.ifEmpty { null },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.Community> {
                    CommunityScreen(
                        onItemSelected = { navController.navigateToPostDetail(it.id) },
                        onCompose = { navController.navigateToPostEditor(null) },
                        onAuthorSelected = navController::navigateToUserProfile,
                    )
                }

                composable<AndroidRoute.PostDetail> { entry ->
                    PostDetailScreen(
                        postId = entry.toRoute<AndroidRoute.PostDetail>().postId,
                        onEdit = { navController.navigateToPostEditor(it) },
                        onClosed = { navController.popBackStack() },
                        onAuthorSelected = navController::navigateToUserProfile,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.PostEditor> { entry ->
                    PostEditorScreen(
                        postId = entry.toRoute<AndroidRoute.PostEditor>().postId.ifEmpty { null },
                        onSaved = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.BlockedUsers> {
                    BlockedUsersScreen(onBack = { navController.popBackStack() })
                }

                composable<AndroidRoute.UserProfile> { entry ->
                    UserProfileScreen(
                        userId = entry.toRoute<AndroidRoute.UserProfile>().userId,
                        onPostSelected = { navController.navigateToPostDetail(it.id) },
                        onMessage = navController::navigateToDirectChat,
                        onClosed = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<AndroidRoute.Downloads> {
                    DownloadsScreen(onBack = { navController.popBackStack() })
                }

                composable<AndroidRoute.NotificationSettings> {
                    NotificationSettingsScreen(onBack = { navController.popBackStack() })
                }

                composable<AndroidRoute.Inquiry> {
                    InquiryScreen(onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() })
                }

                // scaffold:destinations — newFeature 가 이 줄 위에 화면을 추가한다
            }

            // 탭 루트에서의 시스템 뒤로가기: 다른 탭이면 홈 탭으로, 홈 탭이면 안내 후 EXIT_WINDOW 안에 한 번 더 눌러야 종료.
            // NavHost 뒤에 둬야 NavHost 의 뒤로가기 처리보다 먼저 불린다(나중에 등록한 것이 우선).
            BackHandler(enabled = currentTab != null) {
                val now = SystemClock.elapsedRealtime()
                when {
                    currentTab != MainTab.Home -> navController.navigateToTab(MainTab.Home)
                    now - exitArmedAt < EXIT_WINDOW_MILLIS -> activity?.finish()
                    else -> {
                        exitArmedAt = now
                        scope.launch { toast.show(exitHint) }
                    }
                }
            }
        }
    }
}
