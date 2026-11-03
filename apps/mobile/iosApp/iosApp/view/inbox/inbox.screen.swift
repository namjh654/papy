import SwiftUI
import Core

/// 알림함: 안 읽은 알림은 점 + 진한 글씨, 오른쪽 위 "모두 읽음". (Android `InboxScreen`)
struct InboxScreen: View {

    /// 알림에 연결된 화면 열기(딥링크와 같은 형식)
    let onOpenLink: (String) -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.inboxViewModel(),
        state: \.uiState
    )

    @Environment(\.locale) private var locale

    var body: some View {
        PapyPagedList(
            state: model.state.list,
            id: \.id,
            emptyTitle: String(localized: "inbox_empty"),
            onRefresh: {
                model.viewModel.refresh()
                await model.waitUntil { !$0.list.isRefreshing }
            },
            onLoadMore: { model.viewModel.loadMore() },
            onRetry: { model.viewModel.load() },
            onRetryLoadMore: { model.viewModel.retryLoadMore() }
        ) {
            PapyScreenHeader(title: String(localized: "inbox_title"))
        } row: { item in
            Button {
                model.viewModel.itemClicked(item: item)
            } label: {
                PapyCard(spacing: PapySpacing.xxs) {
                    HStack(spacing: PapySpacing.sm) {
                        if !item.read {
                            Circle()
                                .fill(PapyColor.primary)
                                .frame(width: 8, height: 8)
                        }
                        Text(item.title)
                            .papyFont(item.read ? PapyFont.body : PapyFont.bodyStrong)
                            .foregroundStyle(item.read ? PapyColor.onSurfaceVariant : PapyColor.onSurface)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(PapyFormat.relative(epochMillis: item.createdAt, locale: locale))
                            .papyFont(PapyFont.caption)
                            .foregroundStyle(PapyColor.onSurfaceTertiary)
                    }
                    if let body = item.body {
                        Text(body)
                            .papyFont(PapyFont.bodySmall)
                            .foregroundStyle(PapyColor.onSurfaceVariant)
                    }
                }
            }
            .buttonStyle(PapyPressableButtonStyle())
        }
        .papyScreenBackground(grouped: true)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if model.state.hasUnread {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(String(localized: "inbox_mark_all_read")) {
                        model.viewModel.markAllRead()
                    }
                }
            }
        }
        .task {
            await observeEvents()
        }
    }
}


// MARK: - Event Observation

private extension InboxScreen {

    func observeEvents() async {
        for await event in model.viewModel.events {
            switch onEnum(of: event) {
            case .open(let open):
                onOpenLink(open.link)
            }
        }
    }
}
