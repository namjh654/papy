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
        ElsaPagedList(
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
            ElsaScreenHeader(title: String(localized: "inbox_title"))
        } row: { item in
            Button {
                model.viewModel.itemClicked(item: item)
            } label: {
                ElsaCard(spacing: ElsaSpacing.xxs) {
                    HStack(spacing: ElsaSpacing.sm) {
                        if !item.read {
                            Circle()
                                .fill(ElsaColor.primary)
                                .frame(width: 8, height: 8)
                        }
                        Text(item.title)
                            .elsaFont(item.read ? ElsaFont.body : ElsaFont.bodyStrong)
                            .foregroundStyle(item.read ? ElsaColor.onSurfaceVariant : ElsaColor.onSurface)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(ElsaFormat.relative(epochMillis: item.createdAt, locale: locale))
                            .elsaFont(ElsaFont.caption)
                            .foregroundStyle(ElsaColor.onSurfaceTertiary)
                    }
                    if let body = item.body {
                        Text(body)
                            .elsaFont(ElsaFont.bodySmall)
                            .foregroundStyle(ElsaColor.onSurfaceVariant)
                    }
                }
            }
            .buttonStyle(ElsaPressableButtonStyle())
        }
        .elsaScreenBackground(grouped: true)
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
