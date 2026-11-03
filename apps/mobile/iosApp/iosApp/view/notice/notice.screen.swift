import SwiftUI
import Core

/// 공지사항 목록: 큰 제목 + 회색 그룹 배경 위 무테 카드, 당겨서 새로고침, 끝에서 다음 페이지.
struct NoticeScreen: View {

    /// 상세 화면으로(id)
    let onItemSelected: (String) -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.noticeViewModel(),
        state: \.uiState
    )

    @Environment(\.locale) private var locale

    var body: some View {
        PapyPagedList(
            state: model.state.list,
            id: \.id,
            emptyTitle: String(localized: "notice_empty"),
            onRefresh: {
                model.viewModel.refresh()
                await model.waitUntil { !$0.list.isRefreshing }
            },
            onLoadMore: { model.viewModel.loadMore() },
            onRetry: { model.viewModel.load() },
            onRetryLoadMore: { model.viewModel.retryLoadMore() }
        ) {
            PapyScreenHeader(
                title: String(localized: "notice_title"),
                description: String(localized: "notice_description")
            )
        } row: { item in
            Button {
                model.viewModel.itemClicked(item: item)
            } label: {
                PapyCard(spacing: PapySpacing.xxs) {
                    HStack(spacing: PapySpacing.xs) {
                        Text(item.title)
                            .papyFont(PapyFont.subtitle)
                        if item.isNew {
                            PapyBadge(text: String(localized: "notice_new"))
                        }
                    }
                    Text(PapyFormat.relative(epochMillis: item.publishedAt, locale: locale))
                        .papyFont(PapyFont.bodySmall)
                        .foregroundStyle(PapyColor.onSurfaceVariant)
                }
            }
            .buttonStyle(PapyPressableButtonStyle())
        }
        .papyScreenBackground(grouped: true)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await observeEvents()
        }
    }
}


// MARK: - Event Observation

private extension NoticeScreen {

    func observeEvents() async {
        for await event in model.viewModel.events {
            switch onEnum(of: event) {
            case .itemSelected(let selected):
                onItemSelected(selected.id)
            }
        }
    }
}
