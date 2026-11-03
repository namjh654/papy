import SwiftUI
import Core

/// 검색: 위에 입력칸, 아래는 검색 전이면 최근 검색어, 검색 뒤면 결과 목록(빈 결과·오류 포함). (Android `SearchScreen`)
struct SearchScreen: View {

    /// 결과를 눌렀을 때(id)
    let onItemSelected: (String) -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.searchViewModel(),
        state: \.uiState
    )

    private var viewModel: SearchViewModel { model.viewModel }
    private var state: SearchUiState { model.state }

    var body: some View {
        VStack(spacing: 0) {
            ElsaSearchBar(
                text: Binding(get: { state.query }, set: { viewModel.queryChanged(value: $0) }),
                onSearch: { viewModel.submit() }
            )
            .padding(.horizontal, ElsaSize.screenPadding)
            .padding(.vertical, ElsaSpacing.sm)

            if state.showRecent {
                recentSearches
            } else {
                ElsaPagedList(
                    state: state.results,
                    id: \.id,
                    emptyTitle: String(format: String(localized: "search_no_results"), state.searched ?? ""),
                    onRefresh: {
                        viewModel.retry()
                        await model.waitUntil { !$0.results.isLoading }
                    },
                    onLoadMore: { viewModel.loadMore() },
                    onRetry: { viewModel.retry() },
                    onRetryLoadMore: { viewModel.retryLoadMore() }
                ) {
                    EmptyView()
                } row: { item in
                    Button {
                        viewModel.itemClicked(item: item)
                    } label: {
                        ElsaCard(spacing: ElsaSpacing.xxs) {
                            Text(item.title)
                                .elsaFont(ElsaFont.subtitle)
                            if let subtitle = item.subtitle {
                                Text(subtitle)
                                    .elsaFont(ElsaFont.bodySmall)
                                    .foregroundStyle(ElsaColor.onSurfaceVariant)
                            }
                        }
                    }
                    .buttonStyle(ElsaPressableButtonStyle())
                }
            }
        }
        .elsaScreenBackground()
        .navigationTitle(String(localized: "search_title"))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await observeEvents()
        }
    }

    @ViewBuilder
    private var recentSearches: some View {
        if state.recent.isEmpty {
            ElsaEmptyState(title: String(localized: "search_recent_empty"))
            Spacer()
        } else {
            ScrollView {
                VStack(spacing: 0) {
                    HStack {
                        Text("search_recent")
                            .elsaFont(ElsaFont.bodyStrong)
                            .foregroundStyle(ElsaColor.onSurfaceVariant)
                        Spacer()
                        ElsaButton(String(localized: "search_recent_clear"), style: .text, size: .small, fullWidth: false) {
                            viewModel.clearRecent()
                        }
                    }
                    // 최근 검색어는 칩(누르면 다시 검색, x 로 지우기) — 21-2
                    ElsaChipGroup {
                        ForEach(state.recent, id: \.self) { query in
                            ElsaChip(label: query, action: { viewModel.recentClicked(query: query) }, onRemove: { viewModel.removeRecent(query: query) })
                        }
                    }
                    .padding(.vertical, ElsaSpacing.sm)
                }
                .padding(.horizontal, ElsaSize.screenPadding)
            }
        }
    }
}


// MARK: - Event Observation

private extension SearchScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .itemSelected(let selected):
                onItemSelected(selected.id)
            }
        }
    }
}
