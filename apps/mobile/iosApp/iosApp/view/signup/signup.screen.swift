import SwiftUI
import Core

struct SignupScreen: View {

    let onSignupSucceeded: () -> Void

    @StateObject private var model: SharedViewModel<SignupViewModel, SignupUiState>

    /// - Parameter marketingAgreed: 약관 화면의 선택 동의 값(가입 요청에 실린다)
    init(marketingAgreed: Bool, onSignupSucceeded: @escaping () -> Void) {
        self.onSignupSucceeded = onSignupSucceeded
        _model = StateObject(
            wrappedValue: SharedViewModel(
                IOSAppGraph.shared.signupViewModel(marketingAgreed: marketingAgreed),
                state: \.uiState
            )
        )
    }

    private var viewModel: SignupViewModel { model.viewModel }
    private var state: SignupUiState { model.state }

    var body: some View {
        PapyFormScrollView {
            PapyScreenHeader(title: String(localized: "signup_headline"))

            PapyTextField(
                String(localized: "field_username"),
                text: binding(\.username, viewModel.usernameChanged),
                placeholder: String(localized: "field_username_placeholder"),
                textContentType: .username
            )

            PapyTextField(
                String(localized: "field_password"),
                text: binding(\.password, viewModel.passwordChanged),
                placeholder: String(localized: "field_new_password_placeholder"),
                isSecure: true,
                textContentType: .newPassword
            )

            PapyTextField(
                String(localized: "field_password_confirm"),
                text: binding(\.passwordConfirmation, viewModel.passwordConfirmationChanged),
                placeholder: String(localized: "field_password_confirm_placeholder"),
                isSecure: true,
                textContentType: .newPassword,
                submitAction: .done,
                onSubmit: { if state.isSignupEnabled { viewModel.signup() } }
            )

            if let error = state.error {
                PapyInlineMessage(message: error.localizedMessage, type: .error)
            }
        } bottomBar: {
            PapyButton(
                String(localized: "signup_button"),
                enabled: state.isSignupEnabled || state.isLoading,
                isLoading: state.isLoading
            ) {
                viewModel.signup()
            }
        }
        .papyScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await observeEvents()
        }
    }
}


// MARK: - Binding

private extension SignupScreen {

    /// UiState 값을 읽고, 변경은 ViewModel 메서드로 전달하는 Binding.
    func binding<T>(_ keyPath: KeyPath<SignupUiState, T>, _ onChange: @escaping (T) -> Void) -> Binding<T> {
        Binding(
            get: { state[keyPath: keyPath] },
            set: { onChange($0) }
        )
    }
}


// MARK: - Event Observation

private extension SignupScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .signupSucceeded:
                onSignupSucceeded()
            }
        }
    }
}
