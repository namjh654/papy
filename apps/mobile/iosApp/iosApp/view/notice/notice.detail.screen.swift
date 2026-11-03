import SwiftUI
import Core

/// 공지 상세: 큰 제목 + 날짜 + 본문(스크롤). (Android `NoticeDetailScreen`)
struct NoticeDetailScreen: View {

    @StateObject private var model: SharedViewModel<NoticeDetailViewModel, NoticeDetailUiState>

    @Environment(\.locale) private var locale

    init(id: String) {
        _model = StateObject(
            wrappedValue: SharedViewModel(IOSAppGraph.shared.noticeDetailViewModel(id: id), state: \.uiState)
        )
    }

    private var state: NoticeDetailUiState { model.state }

    var body: some View {
        PapyContentState(
            value: state.item,
            isLoading: state.isLoading,
            error: state.error,
            onRetry: { model.viewModel.load() }
        ) { item in
            ScrollView {
                VStack(alignment: .leading, spacing: PapySpacing.lg) {
                    PapyScreenHeader(
                        title: item.title,
                        description: PapyFormat.date(epochMillis: item.publishedAt, locale: locale)
                    )
                    if let body = item.body {
                        Text(body)
                            .papyFont(PapyFont.body)
                            .foregroundStyle(PapyColor.onSurface)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(PapySize.screenPadding)
            }
        }
        .papyScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
    }
}
