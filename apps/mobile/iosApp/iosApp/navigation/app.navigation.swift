import SwiftUI
import Core

/// SwiftUI Native Navigation 루트입니다.
///
/// 각 화면은 KMP ViewModel 이벤트를 받아 콜백만 호출하고,
/// 어떤 화면으로 이동할지는 여기서만 결정합니다.
struct AppNavigation: View {

    @State private var phase: AppPhase = .splash

    @State private var authPath: [AppRoute] = []

    /// 메인: 하단 탭 + 탭마다 따로 쌓이는 스택
    @State private var selectedTab: MainTab = .home

    @State private var paths: [MainTab: [MainRoute]] = [:]

    /// 세션 만료(401 → 재발급 실패 → SessionRepository.expire) 등으로 인증이 풀리면 메인에서 로그인으로 보낸다.
    @StateObject private var auth = FlowState(IOSAppGraph.shared.authStore().status)

    /// 알림 권한 요청(메인 진입 시), 알림 탭 DeepLink 열기
    @StateObject private var push = PushObserver()

    /// 오프라인 안내
    @StateObject private var online = FlowState(IOSAppGraph.shared.networkMonitor().isOnline)

    /// 설정 탭의 테마(시스템·라이트·다크) → AppTheme 가 창에 적용
    @StateObject private var theme = FlowState(IOSAppGraph.shared.appSettings().theme)

    /// 메시지 탭 배지: 안 읽은 수(21-4)
    @StateObject private var chatUnread = FlowState(IOSAppGraph.shared.chatUnreadTotal())

    var body: some View {
        ZStack {
            switch phase {

            case .splash:
                SplashScreen(
                    onSessionRestored: { show(.main) },
                    onSessionNotFound: { show(.auth) },
                    onFirstLaunch: { show(.onboarding) }
                )

            case .onboarding:
                OnboardingScreen(onDone: { show(.auth) })
                    .transition(.opacity)

            case .auth:
                NavigationStack(path: $authPath) {
                    LoginScreen(
                        onLoginSucceeded: { show(.main) },
                        // 로그인 화면 대신 약관 동의만 남긴다(뒤로 = 로그아웃 후 로그인)
                        onSocialSignedUp: { authPath = [.socialConsent] },
                        // 회원가입은 약관 동의부터
                        onSignupTap: { authPath.append(.terms) },
                        onPasswordResetTap: { authPath.append(.passwordReset) }
                    )
                    .navigationDestination(for: AppRoute.self) { route in
                        switch route {
                        case .terms:
                            TermsScreen(
                                // 약관 화면은 스택에서 빼고 회원가입을 올린다(가입 화면에서 뒤로 → 로그인)
                                onAgreed: { marketing in authPath = [.signup(marketingAgreed: marketing)] },
                                onOpenWeb: { url, title in authPath.append(.web(url: url, title: title)) }
                            )
                        case .socialConsent:
                            SocialConsentScreen(
                                onCompleted: { authPath = []; show(.main) },
                                onCancelled: { authPath = [] },
                                onOpenWeb: { url, title in authPath.append(.web(url: url, title: title)) }
                            )
                        case .signup(let marketingAgreed):
                            SignupScreen(
                                marketingAgreed: marketingAgreed,
                                // 가입 완료 안내는 로그인 화면이 한다(LoginNoticeStore)
                                onSignupSucceeded: { authPath = [] }
                            )
                        case .passwordReset:
                            PasswordResetScreen(onDone: { authPath = [] })
                        case .web(let url, let title):
                            WebScreen(url: url, title: title)
                        }
                    }
                }
                .transition(.opacity)

            case .main:
                TabView(selection: $selectedTab) {
                    ForEach(MainTab.allCases, id: \.self) { tab in
                        Tab(String(localized: tab.title), image: tab.icon, value: tab) {
                            NavigationStack(path: path(for: tab)) {
                                root(for: tab)
                                    .navigationDestination(for: MainRoute.self) { route in
                                        destination(for: route)
                                    }
                            }
                        }
                        .badge(badge(for: tab))
                    }
                }
                .tint(ElsaColor.onSurface)
                .transition(.opacity)
            }
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            if !online.value.boolValue {
                ElsaOfflineBanner()
            }
        }
        .animation(.easeInOut(duration: 0.26), value: phase)
        .animation(.easeInOut(duration: 0.2), value: online.value.boolValue)
        .onAppear { AppTheme.apply(theme.value) }
        .onChange(of: theme.value) { _, mode in AppTheme.apply(mode) }
        .appUpdateAlert()
        // 앱 잠금(26-1): 잠겨 있으면 화면 전체를 덮는다
        .appLockGate()
        // 화면 캡처 막기(30-2): 녹화 중 가림·스크린샷 안내(env security.blockCapture), 앱 전환 화면 가림
        .captureProtection()
        // 외부 링크 elsa://<path> → 알림 탭과 같은 길(pendingDeepLink)로 연다
        .onOpenURL { url in
            // 소셜 SDK 로 돌아온 URL 이 먼저(카카오·구글·네이버), 아니면 앱 링크
            if IOSSocialSdk.handle(url: url) { return }
            _ = push.manager.openLink(uri: url.absoluteString)
        }
        .onChange(of: auth.value) { _, status in
            if status == .unauthenticated && phase == .main {
                show(.auth)
            }
        }
        .onChange(of: phase) { _, next in
            if next == .main {
                refreshBadges()
                push.requestPermissionIfNeeded()
                openPendingDeepLink()
            }
        }
        .onChange(of: push.pendingDeepLink) { _, _ in
            openPendingDeepLink()
        }
    }

    // MARK: - Tabs

    /// 탭 루트 화면
    @ViewBuilder
    private func root(for tab: MainTab) -> some View {
        switch tab {
        case .home:
            HomeScreen(
                onComponentGalleryTap: { open(.componentGallery) },
                onExampleTap: { selectedTab = .example },
                onNoticeTap: { open(.notice) },
                onInboxTap: { open(.inbox) },
                onSearchTap: { open(.search) },
                onPermissionTap: { open(.permission) }
            )

        case .example:
            ExampleScreen(onItemSelected: { id in open(.exampleDetail(id: id)) })

        case .chat:
            ChatScreen(onItemSelected: { conversation in open(.chatRoom(id: conversation.id, title: conversation.title)) })

        case .community:
            CommunityScreen(
                onItemSelected: { post in open(.postDetail(id: post.id)) },
                onCompose: { open(.postEditor(id: nil)) },
                onAuthorSelected: { open(.userProfile(id: $0)) }
            )

        // scaffold:tab-roots — newFeature --tab 이 이 줄 위에 탭 루트를 추가한다

        case .settings:
            SettingsScreen(
                onLoggedOut: { show(.auth) },
                onOpenWeb: { url, title in open(.web(url: url, title: title)) },
                onProfileTap: { open(.profile) },
                onWithdrawTap: { open(.withdraw) },
                onInquiryTap: { open(.inquiry) },
                onNotificationSettingsTap: { open(.notificationSettings) },
                onDownloadsTap: { open(.downloads) },
                onBlockedUsersTap: { open(.blockedUsers) }
            )
        }
    }

    /// 탭 위에 쌓이는 화면 (어느 탭에서든 같다)
    @ViewBuilder
    private func destination(for route: MainRoute) -> some View {
        switch route {
        case .componentGallery:
            ComponentGalleryScreen()

        case .web(let url, let title):
            WebScreen(url: url, title: title)

        case .notice:
            NoticeScreen(onItemSelected: { id in open(.noticeDetail(id: id)) })

        case .noticeDetail(let id):
            NoticeDetailScreen(id: id)

        case .exampleDetail(let id):
            ExampleDetailScreen(id: id)

        case .postDetail(let id):
            PostDetailScreen(postId: id, onEdit: { open(.postEditor(id: $0)) }, onClosed: { back() }, onAuthorSelected: { open(.userProfile(id: $0)) })

        case .postEditor(let id):
            PostEditorScreen(postId: id, onSaved: { back() })

        case .blockedUsers:
            BlockedUsersScreen()

        case .chatRoom(let id, let title):
            ChatRoomScreen(conversationId: id, title: title)

        case .directChat(let userId, let name):
            ChatRoomScreen(conversationId: "", title: name, peerUserId: userId)

        case .userProfile(let id):
            UserProfileScreen(
                userId: id,
                onPostSelected: { open(.postDetail(id: $0.id)) },
                onMessage: { userId, name in open(.directChat(userId: userId, name: name)) },
                onClosed: { back() }
            )

        case .inquiry:
            InquiryScreen(onDone: { back() })

        case .notificationSettings:
            NotificationSettingsScreen()

        case .downloads:
            DownloadsScreen()

        case .profile:
            ProfileScreen()

        case .withdraw:
            WithdrawScreen(onWithdrawn: { show(.auth) })

        case .permission:
            PermissionScreen()

        case .inbox:
            // 알림에 연결된 화면: 알림 탭·외부 링크와 같은 길(pendingDeepLink)로 연다
            InboxScreen(onOpenLink: { link in _ = push.manager.openLink(uri: link) })

        case .search:
            // 예시 데이터의 검색 결과는 예시 상세로 간다
            SearchScreen(onItemSelected: { id in open(.exampleDetail(id: id)) })

        // scaffold:main-destinations — newFeature 가 이 줄 위에 화면을 추가한다
        }
    }

    private func path(for tab: MainTab) -> Binding<[MainRoute]> {
        Binding(
            get: { paths[tab] ?? [] },
            set: { paths[tab] = $0 }
        )
    }

    /// 메인에 들어올 때 한 번: 메시지 탭을 열기 전에도 배지가 보이게
    private func refreshBadges() {
        Task { try? await IOSAppGraph.shared.refreshChatUnread() }
    }

    /// 탭 배지 수(메시지 탭만, 21-4)
    private func badge(for tab: MainTab) -> Int {
        tab == .chat ? Int(chatUnread.value.int32Value) : 0
    }

    /// 지금 탭 위에 화면을 연다
    private func open(_ route: MainRoute) {
        paths[selectedTab, default: []].append(route)
    }

    // MARK: - Deep link

    /// 메인에 있을 때만 연다. 로그인 전이면 로그인·세션 복원 후 메인에 들어올 때 연다.
    /// 지금 탭에서 한 화면 뒤로(글을 지웠거나 저장했을 때)
    private func back() {
        guard paths[selectedTab]?.isEmpty == false else { return }
        paths[selectedTab]?.removeLast()
    }

    private func openPendingDeepLink() {
        guard phase == .main, let deepLink = push.pendingDeepLink else { return }
        switch DeepLinkRouter.destination(for: deepLink) {
        case .tab(let tab):
            paths[tab] = []
            selectedTab = tab
        case .route(let route):
            open(route)
        case nil:
            break
        }
        push.manager.consumeDeepLink()
    }

    private func show(_ next: AppPhase) {
        authPath = []
        paths = [:]
        selectedTab = .home
        phase = next
    }
}
