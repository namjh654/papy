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
        ElsaContentState(
            value: state.item,
            isLoading: state.isLoading,
            error: state.error,
            onRetry: { model.viewModel.load() }
        ) { item in
            ScrollView {
                VStack(alignment: .leading, spacing: ElsaSpacing.lg) {
                    ElsaScreenHeader(
                        title: item.title,
                        description: ElsaFormat.date(epochMillis: item.publishedAt, locale: locale)
                    )
                    if let body = item.body {
                        Text(body)
                            .elsaFont(ElsaFont.body)
                            .foregroundStyle(ElsaColor.onSurface)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(ElsaSize.screenPadding)
            }
        }
        .elsaScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
    }
}
