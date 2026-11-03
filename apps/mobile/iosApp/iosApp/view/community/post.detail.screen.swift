import SwiftUI
import Core

/// 글 상세(20-4): 글쓴이·본문 전체·사진·좋아요/댓글 수, 댓글 목록, 아래 댓글 입력(키보드 위).
/// 위 더보기: 내 글이면 수정·삭제, 남의 글이면 신고·차단. 댓글 더보기도 같다(내 댓글은 삭제). (Android `PostDetailScreen`)
struct PostDetailScreen: View {

    let onEdit: (String) -> Void
    let onClosed: () -> Void
    let onAuthorSelected: (String) -> Void

    @StateObject private var model: SharedViewModel<PostDetailViewModel, PostDetailUiState>

    init(postId: String, onEdit: @escaping (String) -> Void, onClosed: @escaping () -> Void, onAuthorSelected: @escaping (String) -> Void) {
        self.onEdit = onEdit
        self.onClosed = onClosed
        self.onAuthorSelected = onAuthorSelected
        _model = StateObject(wrappedValue: SharedViewModel(IOSAppGraph.shared.postDetailViewModel(postId: postId), state: \.uiState))
    }

    private enum Menu: Equatable {
        case post
        case comment(Comment)
        case report(Comment?)
    }

    @State private var toast: PapyToast?
    @State private var alert: PapyAlert?
    @State private var menu: Menu?

    private var viewModel: PostDetailViewModel { model.viewModel }
    private var state: PostDetailUiState { model.state }

    var body: some View {
        content
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if state.post != nil {
                    PapyChatInputBar(
                        text: Binding(get: { state.commentInput }, set: { viewModel.commentInputChanged(value: $0) }),
                        canSend: state.canSendComment,
                        onSend: { viewModel.sendComment() },
                        onAttach: nil,
                        placeholder: String(localized: "community_comment_placeholder")
                    )
                }
            }
            .papyScreenBackground()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .tabBar)
            .toolbar {
                if state.post != nil {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { menu = .post } label: { PapyIcon("papy_ic_more") }
                            .accessibilityLabel(String(localized: "community_more"))
                    }
                }
            }
            .papyToast($toast)
            .papyAlert($alert)
            .papyActionSheet(
                isPresented: Binding(get: { menu != nil }, set: { if !$0 { menu = nil } }),
                title: menuTitle,
                actions: menuActions
            )
            .task {
                await observeEvents()
            }
    }

    @ViewBuilder
    private var content: some View {
        if let post = state.post {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    postBody(post)
                    PapySettingsDivider().padding(.horizontal, -PapySpacing.xl)
                    Text("community_comments")
                        .papyFont(PapyFont.bodyStrong)
                        .foregroundStyle(PapyColor.onSurface)
                        .padding(.top, PapySpacing.lg)
                        .padding(.bottom, PapySpacing.xs)
                    if state.comments.isEmpty {
                        Text("community_comment_empty")
                            .papyFont(PapyFont.bodySmall)
                            .foregroundStyle(PapyColor.onSurfaceTertiary)
                            .padding(.vertical, PapySpacing.md)
                    }
                    ForEach(state.comments, id: \.id) { comment in
                        PapyCommentRow(
                            authorName: comment.author.name,
                            avatarUrl: comment.author.avatarUrl,
                            time: PapyFormat.relative(epochMillis: comment.createdAt),
                            content: comment.content,
                            onMore: { menu = .comment(comment) },
                            onAuthorTap: { onAuthorSelected(comment.author.id) }
                        )
                    }
                }
                .padding(.horizontal, PapySize.screenPadding)
                .padding(.vertical, PapySpacing.md)
            }
            .scrollDismissesKeyboard(.interactively)
        } else if let error = state.error {
            PapyErrorState(message: error.localizedMessage, onRetry: { viewModel.load() })
                .frame(maxHeight: .infinity)
        } else {
            PapyLoadingIndicator().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func postBody(_ post: Post) -> some View {
        VStack(alignment: .leading, spacing: PapySpacing.md) {
            PapyAuthorRow(
                name: post.author.name,
                avatarUrl: post.author.avatarUrl,
                time: PapyFormat.relative(epochMillis: post.createdAt),
                onAuthorTap: { onAuthorSelected(post.author.id) }
            )
            Text(post.content).papyFont(PapyFont.body).foregroundStyle(PapyColor.onSurface)
            PapyImageGrid(count: post.imageUrls.count) { index in PapyPostImage(url: post.imageUrls[index]) }
            HStack(spacing: PapySpacing.xs) {
                PapyLikeButton(liked: post.likedByMe, count: post.likeCount) { viewModel.likeClicked() }
                PapyReactionButton(icon: "papy_ic_comment", count: post.commentCount, label: String(localized: "community_comments")) {}
            }
            .offset(x: -PapySpacing.sm)
        }
        .padding(.bottom, PapySpacing.sm)
    }

    // MARK: - Menus

    private var menuTitle: String? {
        if case .report = menu { return String(localized: "community_report_title") }
        return nil
    }

    private var menuActions: [PapyAction] {
        guard let menu, let post = state.post else { return [] }
        switch menu {
        case .post:
            if post.isMine {
                return [
                    PapyAction(label: String(localized: "community_edit"), icon: "papy_ic_edit") { viewModel.editClicked() },
                    PapyAction(label: String(localized: "community_delete"), icon: "papy_ic_delete", isDestructive: true) {
                        confirmDelete(title: String(localized: "community_delete_title")) { viewModel.deletePost() }
                    },
                ]
            }
            return othersActions(report: { self.menu = .report(nil) }, block: { confirmBlock(post.author.id) })
        case .comment(let comment):
            if comment.isMine {
                return [
                    PapyAction(label: String(localized: "community_delete"), icon: "papy_ic_delete", isDestructive: true) {
                        confirmDelete(title: String(localized: "community_delete_comment_title")) { viewModel.deleteComment(comment: comment) }
                    },
                ]
            }
            return othersActions(report: { self.menu = .report(comment) }, block: { confirmBlock(comment.author.id) })
        case .report(let comment):
            return ReportReasonMenu.actions { reason in
                if let comment { viewModel.reportComment(comment: comment, reason: reason) } else { viewModel.reportPost(reason: reason) }
            }
        }
    }

    private func othersActions(report: @escaping () -> Void, block: @escaping () -> Void) -> [PapyAction] {
        [
            PapyAction(label: String(localized: "community_report"), icon: "papy_ic_report", action: report),
            PapyAction(label: String(localized: "community_block"), icon: "papy_ic_block", isDestructive: true, action: block),
        ]
    }

    private func confirmDelete(title: String, onConfirm: @escaping () -> Void) {
        alert = PapyAlert(
            title: title,
            message: String(localized: "community_delete_message"),
            confirmText: String(localized: "community_delete"),
            dismissText: String(localized: "common_cancel"),
            isDestructive: true,
            onConfirm: onConfirm
        )
    }

    private func confirmBlock(_ userId: String) {
        alert = PapyAlert(
            title: String(localized: "community_block_title"),
            message: String(localized: "community_block_message"),
            confirmText: String(localized: "community_block"),
            dismissText: String(localized: "common_cancel"),
            isDestructive: true,
            onConfirm: { viewModel.block(userId: userId) }
        )
    }
}

/// 신고 사유 메뉴(App Store 1.2). (Android `reportReasonActions`)
enum ReportReasonMenu {
    static func actions(onPick: @escaping (ReportReason) -> Void) -> [PapyAction] {
        [
            PapyAction(label: String(localized: "community_report_spam")) { onPick(.spam) },
            PapyAction(label: String(localized: "community_report_abuse")) { onPick(.abuse) },
            PapyAction(label: String(localized: "community_report_inappropriate")) { onPick(.inappropriate) },
            PapyAction(label: String(localized: "community_report_other")) { onPick(.other) },
        ]
    }
}


// MARK: - Event Observation

private extension PostDetailScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .editRequested(let request):
                onEdit(request.post.id)
            case .closed:
                onClosed()
            case .reported:
                toast = PapyToast(message: String(localized: "community_reported"), type: .success)
            case .actionFailed(let failed):
                toast = PapyToast(message: failed.error.localizedMessage, type: .error)
            }
        }
    }
}
