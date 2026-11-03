import SwiftUI
import Core

/// 사용자 프로필(25-1): 글쓴이를 누르면 열린다. 위에 프로필 카드(사진·이름·소개·글 수·메시지 보내기), 아래에 그 사람이 쓴 글.
/// 오른쪽 위 더보기 → 차단(확인 뒤 닫힌다). 내 프로필이면 메시지·차단이 없다. (Android `UserProfileScreen`)
struct UserProfileScreen: View {

    let onPostSelected: (Post) -> Void
    let onMessage: (String, String) -> Void
    let onClosed: () -> Void

    @StateObject private var model: SharedViewModel<UserProfileViewModel, UserProfileUiState>

    init(userId: String, onPostSelected: @escaping (Post) -> Void, onMessage: @escaping (String, String) -> Void, onClosed: @escaping () -> Void) {
        self.onPostSelected = onPostSelected
        self.onMessage = onMessage
        self.onClosed = onClosed
        _model = StateObject(wrappedValue: SharedViewModel(IOSAppGraph.shared.userProfileViewModel(userId: userId), state: \.uiState))
    }

    @State private var toast: PapyToast?
    @State private var alert: PapyAlert?
    @State private var menuOpen = false

    private var viewModel: UserProfileViewModel { model.viewModel }
    private var state: UserProfileUiState { model.state }

    var body: some View {
        content
            .papyScreenBackground(grouped: true)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .tabBar)
            .toolbar {
                if state.canInteract {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { menuOpen = true } label: { PapyIcon("papy_ic_more") }
                            .accessibilityLabel(String(localized: "community_more"))
                    }
                }
            }
            .papyToast($toast)
            .papyAlert($alert)
            .papyActionSheet(isPresented: $menuOpen, actions: [
                PapyAction(label: String(localized: "community_block"), isDestructive: true) {
                    alert = PapyAlert(
                        title: String(localized: "community_block_title"),
                        message: String(localized: "community_block_message"),
                        confirmText: String(localized: "community_block"),
                        dismissText: String(localized: "common_cancel"),
                        isDestructive: true,
                        onConfirm: { viewModel.block() }
                    )
                },
            ])
            .task {
                await observeEvents()
            }
    }

    @ViewBuilder
    private var content: some View {
        if let profile = state.profile {
            PapyPagedList(
                state: state.posts,
                id: \.id,
                emptyTitle: String(localized: "user_profile_no_posts"),
                onRefresh: {
                    viewModel.refresh()
                    await model.waitUntil { !$0.posts.isRefreshing }
                },
                onLoadMore: { viewModel.loadMore() },
                onRetry: { viewModel.refresh() },
                onRetryLoadMore: { viewModel.retryLoadMore() }
            ) {
                UserProfileCard(profile: profile, onMessage: { viewModel.messageClicked() })
                    .padding(.horizontal, PapySpacing.lg - PapySize.screenPadding)
            } row: { post in
                CommunityPostCard(
                    post: post,
                    onTap: { viewModel.postClicked(post: post) },
                    onLike: { viewModel.likeClicked(post: post) },
                    onAuthorTap: nil
                )
            }
        } else if let error = state.error {
            PapyErrorState(message: error.localizedMessage, onRetry: { viewModel.load() })
                .frame(maxHeight: .infinity)
        } else {
            PapyLoadingIndicator().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .postSelected(let selected):
                onPostSelected(selected.post)
            case .messageRequested(let request):
                onMessage(request.userId, request.name)
            case .blocked:
                onClosed()
            case .actionFailed(let failed):
                toast = PapyToast(message: failed.error.localizedMessage, type: .error)
            }
        }
    }
}

/// 프로필 카드: 가운데 사진 88 → 이름(title) → 소개 → 글 수 → 메시지 보내기(내 프로필이면 없음). (Android `UserProfileCard`)
struct UserProfileCard: View {
    let profile: UserProfile
    let onMessage: () -> Void

    var body: some View {
        VStack(spacing: PapySpacing.sm) {
            PapyAvatar(name: profile.name, url: profile.avatarUrl, size: 88)
            Text(profile.name)
                .papyFont(PapyFont.title)
                .foregroundStyle(PapyColor.onSurface)
                .multilineTextAlignment(.center)
            if let bio = profile.bio, !bio.isEmpty {
                Text(bio)
                    .papyFont(PapyFont.body)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
            Text(String(format: String(localized: "user_profile_post_count"), Int64(profile.postCount)))
                .papyFont(PapyFont.caption)
                .foregroundStyle(PapyColor.onSurfaceTertiary)
            if !profile.isMe {
                PapyButton(String(localized: "user_profile_message"), icon: "papy_ic_chat", action: onMessage)
                    .padding(.top, PapySpacing.sm)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(PapySpacing.lg)
        .background(PapyColor.surface, in: RoundedRectangle(cornerRadius: PapyRadius.xl, style: .continuous))
        .padding(.bottom, PapySpacing.md)
    }
}

#Preview("프로필 카드") {
    VStack {
        UserProfileCard(profile: UserProfile(id: "u1", name: "엘사", avatarUrl: nil, bio: "겨울을 좋아해요.", postCount: 12, isMe: false), onMessage: {})
        UserProfileCard(profile: UserProfile(id: "me", name: "나", avatarUrl: nil, bio: nil, postCount: 3, isMe: true), onMessage: {})
    }
    .padding(PapySpacing.lg)
    .background(PapyColor.backgroundGrouped)
}
