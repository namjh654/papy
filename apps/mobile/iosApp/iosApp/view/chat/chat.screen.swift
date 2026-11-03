import SwiftUI
import Core

/// 대화 목록(메시지 탭): 큰 제목, 대화 한 줄 = 아바타·이름·마지막 메시지·시간·안 읽은 수(텔레그램 목록과 같은 구성). (Android `ChatScreen`)
struct ChatScreen: View {

    let onItemSelected: (Conversation) -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.chatViewModel(),
        state: \.uiState
    )

    var body: some View {
        PapyPagedList(
            state: model.state.list,
            id: \.id,
            emptyTitle: String(localized: "chat_empty"),
            onRefresh: {
                model.viewModel.refresh()
                await model.waitUntil { !$0.list.isRefreshing }
            },
            onLoadMore: { model.viewModel.loadMore() },
            onRetry: { model.viewModel.load() },
            onRetryLoadMore: { model.viewModel.retryLoadMore() }
        ) {
            PapyScreenHeader(title: String(localized: "chat_title"))
        } row: { conversation in
            ConversationRow(conversation: conversation) {
                model.viewModel.conversationClicked(conversation: conversation)
            }
        }
        .papyListSkeleton {
            VStack(spacing: 0) { ForEach(0..<6, id: \.self) { _ in PapySkeletonRow(avatar: 52) } }
                .padding(.horizontal, -PapySpacing.xl)
        }
        .papyScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await observeEvents()
        }
    }
}

/// 대화 한 줄(미리보기 대상, DEC-122). (Android `ConversationRow`)
struct ConversationRow: View {
    let conversation: Conversation
    let action: () -> Void

    @Environment(\.locale) private var locale

    var body: some View {
        PapyListItem(
            title: conversation.title,
            subtitle: conversation.lastMessage,
            action: action,
            leading: { PapyAvatar(name: conversation.title, url: conversation.avatarUrl, size: 52) },
            trailing: {
                VStack(alignment: .trailing, spacing: PapySpacing.xs) {
                    if let at = conversation.lastMessageAt?.int64Value {
                        Text(time(at))
                            .papyFont(PapyFont.caption)
                            .foregroundStyle(PapyColor.onSurfaceTertiary)
                    }
                    if conversation.unreadCount > 0 {
                        Text(conversation.unreadCount > 99 ? "99+" : "\(conversation.unreadCount)")
                            .papyFont(PapyFont.caption)
                            .foregroundStyle(PapyColor.onPrimary)
                            .padding(.horizontal, PapySpacing.xs)
                            .padding(.vertical, PapySpacing.xxs)
                            .frame(minWidth: 20)
                            .background(PapyColor.primary, in: Capsule())
                    }
                }
            }
        )
        // 목록이 화면 좌우 여백 안에 있으므로 줄 자체의 좌우 여백은 뺀다
        .padding(.horizontal, -PapySpacing.xl)
    }

    /// 오늘이면 시각, 아니면 날짜
    private func time(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        return Calendar.current.isDateInToday(date) ? PapyFormat.time(date, locale: locale) : PapyFormat.date(date, locale: locale)
    }
}


// MARK: - Event Observation

private extension ChatScreen {

    func observeEvents() async {
        for await event in model.viewModel.events {
            switch onEnum(of: event) {
            case .conversationSelected(let selected):
                onItemSelected(selected.conversation)
            }
        }
    }
}

#Preview("대화 목록") {
    VStack(spacing: 0) {
        ConversationRow(conversation: Conversation(id: "1", title: "엘사", avatarUrl: nil, lastMessage: "내일 봬요!", lastMessageAt: KotlinLong(value: Int64(Date().timeIntervalSince1970 * 1000)), unreadCount: 2)) {}
        ConversationRow(conversation: Conversation(id: "2", title: "디자인팀", avatarUrl: nil, lastMessage: "새 아이콘을 올렸어요.", lastMessageAt: KotlinLong(value: 0), unreadCount: 0)) {}
    }
    .padding(.horizontal, PapySpacing.xl)
    .papyScreenBackground()
}
