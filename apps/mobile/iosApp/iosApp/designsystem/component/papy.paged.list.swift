import SwiftUI
import Core

/// 목록 화면 공통 틀 (KMP `ListState` + `Paginator`). (Android `PapyPagedList`)
/// 당겨서 새로고침, 끝에서 `prefetch` 개 전에 다음 페이지, 첫 로딩·전체 오류·빈 화면·다음 페이지 실패를 한 곳에서 처리한다.
///
/// ```swift
/// PapyPagedList(
///     state: state.list, id: \.id, emptyTitle: String(localized: "notice_empty"),
///     onRefresh: { model.viewModel.refresh(); await model.waitUntil { !$0.list.isRefreshing } },
///     onLoadMore: { model.viewModel.loadMore() },
///     onRetry: { model.viewModel.load() }, onRetryLoadMore: { model.viewModel.retryLoadMore() }
/// ) { PapyScreenHeader(…) } row: { item in PapyCard { … } }
/// ```
struct PapyPagedList<Item: AnyObject, ID: Hashable, Header: View, Row: View>: View {
    let state: ListState<Item>
    let id: KeyPath<Item, ID>
    let emptyTitle: String
    let onRefresh: () async -> Void
    let onLoadMore: () -> Void
    let onRetry: () -> Void
    let onRetryLoadMore: () -> Void
    @ViewBuilder let header: () -> Header
    @ViewBuilder let row: (Item) -> Row

    @Environment(\.papyListSkeleton) private var skeleton

    /// 끝에서 몇 개 전에 다음 페이지를 요청할지
    private let prefetch = 3

    /// Kotlin 제네릭 클래스의 `List<T>` 는 Swift 에서 `[Any]` 로 보인다 → 여기서 한 번만 `[Item]` 으로
    private var items: [Item] { state.items.compactMap { $0 as? Item } }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: PapySpacing.md) {
                header()

                // 목록이 있는데 새로고침이 실패했으면 목록 위에 짧게 알린다
                if let error = state.error, !state.items.isEmpty {
                    PapyInlineMessage(message: error.localizedMessage, type: .error)
                }

                if state.isLoading && state.items.isEmpty {
                    if let skeleton { skeleton } else { loading }
                } else if state.showFullError, let error = state.error {
                    PapyErrorState(message: error.localizedMessage, onRetry: onRetry)
                } else if state.isEmpty {
                    PapyEmptyState(title: emptyTitle)
                } else {
                    ForEach(items, id: id) { item in
                        row(item)
                            .onAppear { loadMoreIfNeeded(after: item) }
                    }
                }

                if state.isLoadingMore {
                    loading
                }
                if let error = state.loadMoreError {
                    PapyInlineMessage(message: error.localizedMessage, type: .error)
                    PapyButton(String(localized: "common_retry"), style: .secondary, action: onRetryLoadMore)
                }
            }
            .padding(PapySize.screenPadding)
        }
        .refreshable { await onRefresh() }
    }

    private var loading: some View {
        PapyLoadingIndicator()
            .frame(maxWidth: .infinity)
            .padding(PapySpacing.xl)
    }

    private func loadMoreIfNeeded(after item: Item) {
        let items = items
        guard state.canLoadMore,
              let index = items.firstIndex(where: { $0 === item }),
              index >= items.count - 1 - prefetch else { return }
        onLoadMore()
    }
}
