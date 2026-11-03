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
        ElsaPagedList(
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
            ElsaScreenHeader(
                title: String(localized: "notice_title"),
                description: String(localized: "notice_description")
            )
        } row: { item in
            Button {
                model.viewModel.itemClicked(item: item)
            } label: {
                ElsaCard(spacing: ElsaSpacing.xxs) {
                    HStack(spacing: ElsaSpacing.xs) {
                        Text(item.title)
                            .elsaFont(ElsaFont.subtitle)
                        if item.isNew {
                            ElsaBadge(text: String(localized: "notice_new"))
                        }
                    }
                    Text(ElsaFormat.relative(epochMillis: item.publishedAt, locale: locale))
                        .elsaFont(ElsaFont.bodySmall)
                        .foregroundStyle(ElsaColor.onSurfaceVariant)
                }
            }
            .buttonStyle(ElsaPressableButtonStyle())
        }
        .elsaScreenBackground(grouped: true)
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
