import SwiftUI
import Core

/// 약관 동의(회원가입 전): 전체 동의 + 항목별 체크(필수·선택 표시, 전문 보기), 하단 고정 버튼. (Android `TermsScreen`)
struct TermsScreen: View {

    /// 선택 동의(마케팅) 값을 들고 회원가입으로
    let onAgreed: (Bool) -> Void
    let onOpenWeb: (URL, String) -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.termsViewModel(),
        state: \.uiState
    )

    private var viewModel: TermsViewModel { model.viewModel }
    private var state: TermsUiState { model.state }

    var body: some View {
        TermsContent(
            state: state,
            onToggleAll: { viewModel.toggleAll() },
            onToggle: { viewModel.toggle(agreement: $0) },
            onContinue: { viewModel.continueClicked() },
            onOpenWeb: onOpenWeb
        )
        .task {
            await observeEvents()
        }
    }
}

/// 처음 가입한 소셜 사용자(18-4): 같은 약관 화면에서 동의 → 서버에 기록 → 메인. (Android `SocialConsentScreen`)
/// 뒤로는 가입을 마치지 않은 것이므로 로그아웃하고 로그인으로 돌아간다 — 기본 뒤로·밀어서 뒤로는 막는다.
struct SocialConsentScreen: View {

    let onCompleted: () -> Void
    let onCancelled: () -> Void
    let onOpenWeb: (URL, String) -> Void

    @StateObject private var terms = SharedViewModel(
        IOSAppGraph.shared.termsViewModel(),
        state: \.uiState
    )
    @StateObject private var consent = SharedViewModel(
        IOSAppGraph.shared.socialConsentViewModel(),
        state: \.uiState
    )

    var body: some View {
        TermsContent(
            state: terms.state,
            onToggleAll: { terms.viewModel.toggleAll() },
            onToggle: { terms.viewModel.toggle(agreement: $0) },
            onContinue: { terms.viewModel.continueClicked() },
            onOpenWeb: onOpenWeb,
            isLoading: consent.state.isLoading,
            error: consent.state.error?.localizedMessage
        )
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button { consent.viewModel.cancel() } label: {
                    Image(systemName: "chevron.left")
                }
                .accessibilityLabel(String(localized: "common_back"))
            }
        }
        .task {
            for await event in terms.viewModel.events {
                switch onEnum(of: event) {
                case .agreed(let agreed): consent.viewModel.agree(marketing: agreed.marketing)
                }
            }
        }
        .task {
            for await event in consent.viewModel.events {
                switch onEnum(of: event) {
                case .completed: onCompleted()
                case .cancelled: onCancelled()
                }
            }
        }
    }
}

/// 상태·콜백만 받는 본문(미리보기 대상, DEC-122)
struct TermsContent: View {

    let state: TermsUiState
    let onToggleAll: () -> Void
    let onToggle: (Agreement) -> Void
    let onContinue: () -> Void
    let onOpenWeb: (URL, String) -> Void
    var isLoading: Bool = false
    var error: String? = nil

    private let agreements: [Agreement] = [.service, .privacy, .marketing]

    var body: some View {
        PapyFormScrollView {
            PapyScreenHeader(title: String(localized: "terms_title"))

            // 화면 배경 위에 바로 둔다 — 같은 색 카드는 보이지 않고 들여쓰기만 생긴다(제목과 왼쪽 정렬)
            VStack(alignment: .leading, spacing: PapySpacing.md) {
                PapyCheckbox(
                    label: String(localized: "terms_all"),
                    isOn: Binding(get: { state.allChecked }, set: { _ in onToggleAll() })
                )
                PapySettingsDivider()
                ForEach(agreements, id: \.self) { agreement in
                    HStack {
                        PapyCheckbox(
                            label: title(agreement),
                            isOn: Binding(
                                get: { state.isChecked(agreement: agreement) },
                                set: { _ in onToggle(agreement) }
                            ),
                            description: String(localized: agreement.isRequired ? "terms_required" : "terms_optional")
                        )
                        Spacer(minLength: 0)
                        if let url = state.url(agreement: agreement).flatMap(URL.init(string:)) {
                            PapyButton(String(localized: "terms_view"), style: .text, size: .small, fullWidth: false) {
                                onOpenWeb(url, title(agreement))
                            }
                        }
                    }
                }
            }

            if let error {
                PapyInlineMessage(message: error, type: .error)
            }
        } bottomBar: {
            PapyButton(String(localized: "terms_continue"), enabled: state.canContinue || isLoading, isLoading: isLoading) {
                onContinue()
            }
        }
        .papyScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
    }

    private func title(_ agreement: Agreement) -> String {
        switch agreement {
        case .service: String(localized: "settings_terms")
        case .privacy: String(localized: "settings_privacy")
        case .marketing: String(localized: "terms_marketing")
        }
    }
}


// MARK: - Event Observation

private extension TermsScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .agreed(let agreed):
                onAgreed(agreed.marketing)
            }
        }
    }
}


#Preview("약관") {
    NavigationStack {
        TermsContent(
            state: TermsUiState(checked: [], serviceUrl: "https://example.com", privacyUrl: nil),
            onToggleAll: {}, onToggle: { _ in }, onContinue: {}, onOpenWeb: { _, _ in }
        )
    }
}
