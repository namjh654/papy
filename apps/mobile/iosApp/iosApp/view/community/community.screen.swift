import SwiftUI
import Core

/// 커뮤니티 목록(탭): 큰 제목, 글 카드(글쓴이·시간·본문 3줄·사진·좋아요·댓글), 당겨서 새로고침·끝에서 다음, 오른쪽 아래 글쓰기. (Android `CommunityScreen`)
struct CommunityScreen: View {

    let onItemSelected: (Post) -> Void
    let onCompose: () -> Void
    let onAuthorSelected: (String) -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.communityViewModel(),
        state: \.uiState
    )
    @State private var toast: PapyToast?
    /// 새 글을 올린 뒤 맨 위로
    @State private var scrollToTop = 0

    var body: some View {
        ScrollViewReader { proxy in
            PapyPagedList(
                state: model.state.list,
                id: \.id,
                emptyTitle: String(localized: "community_empty"),
                onRefresh: {
                    model.viewModel.refresh()
                    await model.waitUntil { !$0.list.isRefreshing }
                },
                onLoadMore: { model.viewModel.loadMore() },
                onRetry: { model.viewModel.load() },
                onRetryLoadMore: { model.viewModel.retryLoadMore() }
            ) {
                VStack(alignment: .leading, spacing: PapySpacing.md) {
                    PapyScreenHeader(title: String(localized: "community_title")).id("top")
                    PapySegmentedTabs(
                        options: [String(localized: "community_filter_all"), String(localized: "community_filter_mine")],
                        selectedIndex: model.state.filter == .mine ? 1 : 0,
                        onSelect: { model.viewModel.filterChanged(filter: $0 == 1 ? .mine : .all) }
                    )
                    .padding(.horizontal, PapySpacing.lg - PapySize.screenPadding)
                }
            } row: { post in
                CommunityPostCard(
                    post: post,
                    onTap: { model.viewModel.postClicked(post: post) },
                    onLike: { model.viewModel.likeClicked(post: post) },
                    onAuthorTap: { onAuthorSelected(post.author.id) }
                )
            }
            .onChange(of: scrollToTop) { withAnimation { proxy.scrollTo("top", anchor: .top) } }
            .papyListSkeleton {
                VStack(spacing: PapySpacing.md) { ForEach(0..<3, id: \.self) { _ in PapySkeletonCard() } }
                    .padding(.horizontal, PapySpacing.lg - PapySize.screenPadding)
            }
        }
        .safeAreaPadding(.bottom, PapySize.controlHeight)
        .overlay(alignment: .bottomTrailing) {
            PapyFloatingButton(icon: "papy_ic_edit", label: String(localized: "community_write")) {
                model.viewModel.composeClicked()
            }
            .padding(PapySize.screenPadding)
        }
        .papyScreenBackground(grouped: true)
        .navigationBarTitleDisplayMode(.inline)
        .papyToast($toast)
        .task {
            await observeEvents()
        }
    }
}

/// 목록의 글 카드(커뮤니티 목록·사용자 프로필이 같이 쓴다). 메뉴는 상세에서. `onAuthorTap` 이 nil 이면 글쓴이를 누를 수 없다(이미 그 사람의 프로필)
struct CommunityPostCard: View {
    let post: Post
    let onTap: () -> Void
    let onLike: () -> Void
    let onAuthorTap: (() -> Void)?

    var body: some View {
        PapyPostCard(
            authorName: post.author.name,
            avatarUrl: post.author.avatarUrl,
            time: PapyFormat.relative(epochMillis: post.createdAt),
            content: post.content,
            imageCount: post.imageUrls.count,
            liked: post.likedByMe,
            likeCount: post.likeCount,
            commentCount: post.commentCount,
            onTap: onTap,
            onLike: onLike,
            onAuthorTap: onAuthorTap
        ) { index in
            PapyPostImage(url: post.imageUrls[index])
        }
        // 목록 좌우 여백(24)보다 카드는 조금 넓게 — Android 와 같은 16
        .padding(.horizontal, PapySpacing.lg - PapySize.screenPadding)
    }
}

// MARK: - Event Observation

private extension CommunityScreen {

    func observeEvents() async {
        for await event in model.viewModel.events {
            switch onEnum(of: event) {
            case .postSelected(let selected):
                onItemSelected(selected.post)
            case .composeRequested:
                onCompose()
            case .likeFailed:
                toast = PapyToast(message: String(localized: "community_like_failed"), type: .error)
            case .scrollToTop:
                scrollToTop += 1
            }
        }
    }
}
