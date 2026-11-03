import SwiftUI
import Core

/// 상세 화면 기준 예시: 큰 제목 + 본문, 하단 고정 버튼. 로딩·오류·내용 전환은 `PapyContentState` 가 한다.
/// 인자(id)는 `IOSAppGraph.shared.exampleDetailViewModel(id:)` 로 넘긴다. (Android `ExampleDetailScreen`)
struct ExampleDetailScreen: View {

    @StateObject private var model: SharedViewModel<ExampleDetailViewModel, ExampleDetailUiState>

    @State private var toast: PapyToast?

    init(id: String) {
        _model = StateObject(
            wrappedValue: SharedViewModel(IOSAppGraph.shared.exampleDetailViewModel(id: id), state: \.uiState)
        )
    }

    private var viewModel: ExampleDetailViewModel { model.viewModel }
    private var state: ExampleDetailUiState { model.state }

    var body: some View {
        PapyContentState(
            value: state.item,
            isLoading: state.isLoading,
            error: state.error,
            onRetry: { viewModel.load() }
        ) { item in
            PapyFormScrollView {
                PapyScreenHeader(title: item.title, description: item.subtitle)

                if let body = item.body {
                    Text(body)
                        .papyFont(PapyFont.body)
                        .foregroundStyle(PapyColor.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } bottomBar: {
                PapyButton(
                    String(localized: item.favorite ? "example_detail_unfavorite" : "example_detail_favorite"),
                    style: item.favorite ? .secondary : .primary,
                    isLoading: state.isSaving
                ) {
                    viewModel.toggleFavorite()
                }
            }
        }
        .papyScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .papyToast($toast)
        .task {
            await observeEvents()
        }
    }
}


// MARK: - Event Observation

private extension ExampleDetailScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .saveFailed(let failed):
                toast = PapyToast(message: failed.error.localizedMessage, type: .error)
            }
        }
    }
}
