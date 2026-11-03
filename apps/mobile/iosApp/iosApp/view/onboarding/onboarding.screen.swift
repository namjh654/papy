import SwiftUI
import Core

/// 온보딩(처음 실행 때 한 번): 밀어서 넘기는 쪽 + 쪽 표시 점 + 하단 고정 버튼, 오른쪽 위 건너뛰기. (Android `OnboardingScreen`)
struct OnboardingScreen: View {

    /// 끝까지 봤거나 건너뛰었다 → 로그인 화면으로
    let onDone: () -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.onboardingViewModel(),
        state: \.uiState
    )

    private var viewModel: OnboardingViewModel { model.viewModel }
    private var state: OnboardingUiState { model.state }

    private struct Page {
        let icon: String
        let title: String.LocalizationValue
        let description: String.LocalizationValue
    }

    /// 쪽 내용. 쪽 수는 KMP `OnboardingUiState.PAGE_COUNT` 와 같아야 한다
    private let pages: [Page] = [
        Page(icon: "elsa_ic_tab_home", title: "onboarding_1_title", description: "onboarding_1_description"),
        Page(icon: "elsa_ic_tab_list", title: "onboarding_2_title", description: "onboarding_2_description"),
        Page(icon: "elsa_ic_tab_settings", title: "onboarding_3_title", description: "onboarding_3_description"),
    ]

    /// 밀어서 바뀐 쪽 → ViewModel, 버튼으로 바뀐 쪽 → TabView
    private var page: Binding<Int> {
        Binding(
            get: { Int(state.page) },
            set: { viewModel.pageChanged(page: Int32($0)) }
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                ElsaButton(String(localized: "onboarding_skip"), style: .text, size: .small, fullWidth: false) {
                    viewModel.skip()
                }
            }

            TabView(selection: page) {
                ForEach(pages.indices, id: \.self) { index in
                    VStack(spacing: ElsaSpacing.xl) {
                        ElsaIcon(pages[index].icon, size: 56)
                            .foregroundStyle(ElsaColor.onPrimaryContainer)
                            .frame(width: 120, height: 120)
                            .background(ElsaColor.primaryContainer, in: Circle())
                        Text(String(localized: pages[index].title))
                            .elsaFont(ElsaFont.headline)
                            .foregroundStyle(ElsaColor.onBackground)
                        Text(String(localized: pages[index].description))
                            .elsaFont(ElsaFont.body)
                            .foregroundStyle(ElsaColor.onSurfaceVariant)
                    }
                    .multilineTextAlignment(.center)
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: state.page)

            ElsaPageIndicator(pageCount: Int(state.pageCount), currentPage: Int(state.page))
                .padding(.vertical, ElsaSpacing.xl)

            ElsaButton(String(localized: state.isLastPage ? "onboarding_start" : "onboarding_next")) {
                viewModel.next()
            }
        }
        .padding(ElsaSize.screenPadding)
        .elsaScreenBackground()
        .task {
            await observeEvents()
        }
    }
}


// MARK: - Event Observation

private extension OnboardingScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .done:
                onDone()
            }
        }
    }
}
