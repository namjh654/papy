package cloud.elsa.app.di

import cloud.elsa.app.core.data.connectivity.NetworkMonitor
import cloud.elsa.app.core.data.push.PushManager
import cloud.elsa.app.core.data.session.AuthStore
import cloud.elsa.app.core.data.settings.AppSettingsRepository
import cloud.elsa.app.core.data.update.AppUpdateManager
import cloud.elsa.app.feature.auth.login.LoginViewModel
import cloud.elsa.app.feature.auth.onboarding.OnboardingViewModel
import cloud.elsa.app.feature.auth.reset.PasswordResetViewModel
import cloud.elsa.app.feature.auth.signup.SignupViewModel
import cloud.elsa.app.feature.auth.social.SocialConsentViewModel
import cloud.elsa.app.feature.auth.splash.SplashViewModel
import cloud.elsa.app.feature.auth.terms.TermsViewModel
import cloud.elsa.app.feature.home.HomeViewModel
import cloud.elsa.app.feature.example.ExampleViewModel
import cloud.elsa.app.feature.settings.SettingsViewModel
import cloud.elsa.app.feature.settings.profile.ProfileViewModel
import cloud.elsa.app.feature.settings.withdraw.WithdrawViewModel
import cloud.elsa.app.feature.settings.inquiry.InquiryViewModel
import cloud.elsa.app.feature.settings.downloads.DownloadsViewModel
import cloud.elsa.app.feature.settings.notification.NotificationSettingsViewModel
import cloud.elsa.app.core.data.security.AppLock
import cloud.elsa.app.feature.example.detail.ExampleDetailViewModel
import cloud.elsa.app.feature.notice.NoticeViewModel
import cloud.elsa.app.feature.notice.detail.NoticeDetailViewModel
import cloud.elsa.app.feature.permission.PermissionViewModel
import cloud.elsa.app.feature.inbox.InboxViewModel
import cloud.elsa.app.feature.search.SearchViewModel
import cloud.elsa.app.feature.chat.ChatRepository
import cloud.elsa.app.feature.chat.ChatViewModel
import cloud.elsa.app.feature.chat.room.ChatRoomViewModel
import cloud.elsa.app.feature.community.CommunityViewModel
import cloud.elsa.app.feature.community.blocked.BlockedUsersViewModel
import cloud.elsa.app.feature.community.detail.PostDetailViewModel
import cloud.elsa.app.feature.community.editor.PostEditorViewModel
import cloud.elsa.app.feature.community.user.UserProfileViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Swift에서 Koin 그래프에 접근하는 창구입니다.
 *
 * ViewModel은 factory이므로 호출할 때마다 새 인스턴스를 반환합니다.
 * 화면 수명과 맞추려면 Swift의 `SharedViewModel` 래퍼로 보관하세요.
 */
object IOSAppGraph : KoinComponent {

    /** 앱 전역 인증 상태. 루트 화면이 `status` 를 관찰해 Unauthenticated(401 만료 등)면 로그인으로 전환한다. */
    fun authStore(): AuthStore = get()

    /** 푸시 토큰·알림 탭·권한을 KMP 에 알리고, 루트가 pendingDeepLink 를 관찰한다 */
    fun pushManager(): PushManager = get()

    /** 네트워크 연결 상태. 루트가 `isOnline` 을 관찰해 오프라인 안내를 보여준다 */
    fun networkMonitor(): NetworkMonitor = get()

    /** 테마·언어. 루트가 `theme` 을 관찰해 `.preferredColorScheme` 을 적용한다 */
    fun appSettings(): AppSettingsRepository = get()

    /** 강제·선택 업데이트. 루트가 시작 때 `check()` 하고 `status` 를 보고 안내한다 */
    fun appUpdateManager(): AppUpdateManager = get()

    /** Swift `Data` → Kotlin `ByteArray`(업로드할 파일). 원소를 하나씩 옮기지 않고 한 번에 복사한다 */
    @OptIn(ExperimentalForeignApi::class)
    fun bytes(data: NSData): ByteArray {
        val size = data.length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }

    fun splashViewModel(): SplashViewModel = get()

    fun loginViewModel(): LoginViewModel = get()

    /** 약관 화면의 선택 동의 값을 넘긴다 */
    fun signupViewModel(marketingAgreed: Boolean): SignupViewModel = get { parametersOf(marketingAgreed) }

    fun termsViewModel(): TermsViewModel = get()

    /** 처음 가입한 소셜 사용자의 약관 동의(18-4) */
    fun socialConsentViewModel(): SocialConsentViewModel = get()

    fun onboardingViewModel(): OnboardingViewModel = get()

    fun passwordResetViewModel(): PasswordResetViewModel = get()

    fun profileViewModel(): ProfileViewModel = get()

    fun withdrawViewModel(): WithdrawViewModel = get()

    /** 설정 → 문의하기(25-3) */
    fun inquiryViewModel(): InquiryViewModel = get()

    /** 설정 → 알림 설정(26-2) */
    fun notificationSettingsViewModel(): NotificationSettingsViewModel = get()

    /** 앱 잠금(26-1): 루트가 `isLocked` 를 보고 잠금 화면을 덮고, scenePhase 로 onBackground·onForeground */
    fun appLock(): AppLock = get()

    /** 설정 → 저장한 파일(30-1) */
    fun downloadsViewModel(): DownloadsViewModel = get()

    fun homeViewModel(): HomeViewModel = get()

    fun exampleViewModel(): ExampleViewModel = get()

    fun settingsViewModel(): SettingsViewModel = get()

    fun noticeViewModel(): NoticeViewModel = get()

    fun permissionViewModel(): PermissionViewModel = get()

    fun inboxViewModel(): InboxViewModel = get()

    fun searchViewModel(): SearchViewModel = get()

    fun chatViewModel(): ChatViewModel = get()

    /** 메시지 탭 배지(안 읽은 수). Swift: `for await count in IOSAppGraph.shared.chatUnreadTotal()` */
    fun chatUnreadTotal(): StateFlow<Int> = get<ChatRepository>().unreadTotal

    /** 메인에 들어올 때 한 번 — Swift: `try? await IOSAppGraph.shared.refreshChatUnread()` */
    suspend fun refreshChatUnread() = get<ChatRepository>().refreshUnread()

    fun communityViewModel(): CommunityViewModel = get()

    fun postDetailViewModel(postId: String): PostDetailViewModel = get { parametersOf(postId) }

    /** 새 글은 postId = nil */
    fun postEditorViewModel(postId: String?): PostEditorViewModel = get { parametersOf(postId.orEmpty()) }

    fun blockedUsersViewModel(): BlockedUsersViewModel = get()

    /** 글쓴이를 눌렀을 때 그 사람의 프로필(25-1) */
    fun userProfileViewModel(userId: String): UserProfileViewModel = get { parametersOf(userId) }

    /** 대화방 — 목록에서 고른 대화의 id·이름을 넘긴다 */
    fun chatRoomViewModel(conversationId: String, title: String): ChatRoomViewModel =
        get { parametersOf(conversationId, title) }

    /** 프로필의 "메시지 보내기"(25-2): 그 사람과의 1:1 대화를 찾거나 만들어 연다 */
    fun directChatRoomViewModel(userId: String, name: String): ChatRoomViewModel =
        get { parametersOf("", name, userId) }

    /** 인자가 있는 화면: Swift 에서 `IOSAppGraph.shared.noticeDetailViewModel(id: id)` */
    fun noticeDetailViewModel(id: String): NoticeDetailViewModel = get { parametersOf(id) }

    fun exampleDetailViewModel(id: String): ExampleDetailViewModel = get { parametersOf(id) }
}
