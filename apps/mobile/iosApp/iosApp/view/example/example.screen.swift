import SwiftUI
import Core

/// 목록 화면 기준 예시: 큰 제목 + 회색 그룹 배경 위 무테 카드, 당겨서 새로고침, 끝에서 다음 페이지.
struct ExampleScreen: View {

    /// 상세 화면으로(id)
    let onItemSelected: (String) -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.exampleViewModel(),
        state: \.uiState
    )

    var body: some View {
        ElsaPagedList(
            state: model.state.list,
            id: \.id,
            emptyTitle: String(localized: "example_empty"),
            onRefresh: {
                model.viewModel.refresh()
                await model.waitUntil { !$0.list.isRefreshing }
            },
            onLoadMore: { model.viewModel.loadMore() },
            onRetry: { model.viewModel.load() },
            onRetryLoadMore: { model.viewModel.retryLoadMore() }
        ) {
            ElsaScreenHeader(
                title: String(localized: "example_title"),
                description: String(localized: "example_description")
            )
        } row: { item in
            Button {
                model.viewModel.itemClicked(item: item)
            } label: {
                ElsaCard(spacing: ElsaSpacing.xxs) {
                    HStack(spacing: ElsaSpacing.xs) {
                        Text(item.title)
                            .elsaFont(ElsaFont.subtitle)
                        if item.favorite {
                            ElsaBadge(text: String(localized: "example_favorite"))
                        }
                    }
                    if let subtitle = item.subtitle {
                        Text(subtitle)
                            .elsaFont(ElsaFont.bodySmall)
                            .foregroundStyle(ElsaColor.onSurfaceVariant)
                    }
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

private extension ExampleScreen {

    func observeEvents() async {
        for await event in model.viewModel.events {
            switch onEnum(of: event) {
            case .itemSelected(let selected):
                onItemSelected(selected.id)
            }
        }
    }
}
