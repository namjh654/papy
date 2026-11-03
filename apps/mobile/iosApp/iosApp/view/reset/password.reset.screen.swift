import SwiftUI
import Core

/// 비밀번호 재설정: 단계에 따라 입력칸과 하단 버튼이 바뀐다. 둘째 단계에서는 뒤로 버튼이 첫 단계로 간다.
/// (Android `PasswordResetScreen`)
struct PasswordResetScreen: View {

    /// 완료 → 로그인으로 돌아간다(안내는 로그인 화면이 한다)
    let onDone: () -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.passwordResetViewModel(),
        state: \.uiState
    )

    @State private var toast: PapyToast?

    private var viewModel: PasswordResetViewModel { model.viewModel }
    private var state: PasswordResetUiState { model.state }

    var body: some View {
        PapyFormScrollView {
            if state.step == .request {
                PapyScreenHeader(
                    title: String(localized: "reset_title"),
                    description: String(localized: "reset_request_description")
                )
                PapyTextField(
                    String(localized: "field_username"),
                    text: binding(\.username, viewModel.usernameChanged),
                    placeholder: String(localized: "field_username_placeholder"),
                    textContentType: .username,
                    submitAction: .done,
                    onSubmit: { if state.canRequest { viewModel.requestCode() } }
                )
            } else {
                PapyScreenHeader(
                    title: String(localized: "reset_title"),
                    description: String(format: String(localized: "reset_confirm_description"), state.username)
                )
                PapyTextField(
                    String(localized: "field_code"),
                    text: binding(\.code, viewModel.codeChanged),
                    placeholder: String(localized: "field_code_placeholder"),
                    keyboardType: .numberPad,
                    textContentType: .oneTimeCode
                )
                PapyTextField(
                    String(localized: "field_new_password"),
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
                    onSubmit: { if state.canConfirm { viewModel.confirm() } }
                )
                PapyButton(
                    String(localized: "reset_resend"),
                    style: .text,
                    size: .small,
                    enabled: !state.isLoading,
                    fullWidth: false
                ) {
                    viewModel.resendCode()
                }
            }

            if let error = state.error {
                PapyInlineMessage(message: error.localizedMessage, type: .error)
            }
        } bottomBar: {
            if state.step == .request {
                PapyButton(
                    String(localized: "reset_request_button"),
                    enabled: state.canRequest || state.isLoading,
                    isLoading: state.isLoading
                ) {
                    viewModel.requestCode()
                }
            } else {
                PapyButton(
                    String(localized: "reset_confirm_button"),
                    enabled: state.canConfirm || state.isLoading,
                    isLoading: state.isLoading
                ) {
                    viewModel.confirm()
                }
            }
        }
        .papyScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        // 둘째 단계에서는 기본 뒤로(화면 닫기) 대신 "첫 단계로" 버튼을 단다(밀어서 닫기도 막힌다)
        .navigationBarBackButtonHidden(state.canGoToPreviousStep)
        .toolbar {
            if state.canGoToPreviousStep {
                ToolbarItem(placement: .topBarLeading) {
                    PapyBackButton { viewModel.previousStep() }
                }
            }
        }
        .papyToast($toast)
        .task {
            await observeEvents()
        }
    }
}


// MARK: - Binding

private extension PasswordResetScreen {

    func binding<T>(_ keyPath: KeyPath<PasswordResetUiState, T>, _ onChange: @escaping (T) -> Void) -> Binding<T> {
        Binding(
            get: { state[keyPath: keyPath] },
            set: { onChange($0) }
        )
    }
}


// MARK: - Event Observation

private extension PasswordResetScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .codeResent:
                toast = PapyToast(message: String(localized: "reset_resent"), type: .success)
            case .done:
                onDone()
            }
        }
    }
}
