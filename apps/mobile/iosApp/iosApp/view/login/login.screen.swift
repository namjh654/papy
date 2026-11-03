import SwiftUI
import Core

struct LoginScreen: View {

    let onLoginSucceeded: () -> Void
    /// 처음 가입한 소셜 사용자 → 약관 동의(18-4)
    let onSocialSignedUp: () -> Void
    let onSignupTap: () -> Void
    let onPasswordResetTap: () -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.loginViewModel(),
        state: \.uiState
    )

    private var viewModel: LoginViewModel { model.viewModel }
    private var state: LoginUiState { model.state }

    @State private var toast: PapyToast?

    private var canLogin: Bool { state.isLoginEnabled && !state.isLoading }

    var body: some View {
        PapyFormScrollView {
            Spacer(minLength: PapySpacing.xxl)

            PapyScreenHeader(
                title: String(localized: "login_title"),
                description: String(localized: "login_description")
            )

            PapyTextField(
                String(localized: "field_username"),
                text: binding(\.username, viewModel.usernameChanged),
                placeholder: String(localized: "field_username_placeholder"),
                textContentType: .username
            )

            PapyTextField(
                String(localized: "field_password"),
                text: binding(\.password, viewModel.passwordChanged),
                placeholder: String(localized: "field_password_placeholder"),
                isSecure: true,
                textContentType: .password,
                submitAction: .done,
                onSubmit: { if canLogin { viewModel.login() } }
            )

            // 한 줄에 안 들어가면(영어·큰 글자) 다음 줄로 (Android FlowRow)
            PapyFlowLayout(spacing: PapySpacing.lg) {
                PapyCheckbox(
                    label: String(localized: "login_remember_id"),
                    isOn: binding(\.rememberId, viewModel.rememberIdChanged)
                )
                PapyCheckbox(
                    label: String(localized: "login_auto_login"),
                    isOn: binding(\.autoLogin, viewModel.autoLoginChanged)
                )
            }

            if let error = state.error {
                PapyInlineMessage(message: error.localizedMessage, type: .error)
            }

            HStack {
                Spacer()
                PapyButton(String(localized: "login_forgot_password"), style: .text, size: .small, fullWidth: false) {
                    onPasswordResetTap()
                }
            }

            PapySocialLoginSection(providers: state.socialProviders, enabled: !state.isLoading) { provider in
                viewModel.socialLoginTapped(provider: provider)
            }
        } bottomBar: {
            PapyButton(
                String(localized: "login_button"),
                enabled: state.isLoginEnabled,
                isLoading: state.isLoading
            ) {
                viewModel.login()
            }

            HStack(spacing: 0) {
                Text("login_no_account")
                    .papyFont(PapyFont.bodySmall)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
                PapyButton(String(localized: "login_signup"), style: .text, size: .small, fullWidth: false) {
                    onSignupTap()
                }
            }
        }
        .papyScreenBackground()
        .papyToast($toast)
        .task {
            await observeEvents()
        }
    }
}


// MARK: - Binding

private extension LoginScreen {

    /// UiState 값을 읽고, 변경은 ViewModel 메서드로 전달하는 Binding.
    func binding<T>(_ keyPath: KeyPath<LoginUiState, T>, _ onChange: @escaping (T) -> Void) -> Binding<T> {
        Binding(
            get: { state[keyPath: keyPath] },
            set: { onChange($0) }
        )
    }
}


// MARK: - Event Observation

private extension LoginScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .loginSucceeded:
                onLoginSucceeded()
            case .startSocialSignIn(let event):
                // 소셜(DEC-105~109): 플랫폼 SDK 로 인증 → 결과를 KMP 로(dev mock 은 이 이벤트 없이 바로 로그인)
                switch await IOSSocialSdk.signIn(event.provider) {
                case .success(let credential): viewModel.socialCredentialReceived(credential: credential)
                case .cancelled: viewModel.socialSignInCancelled()
                case .failed: viewModel.socialSignInFailed()
                }
            case .socialSignedUp:
                // 처음 가입한 소셜 사용자: 약관 동의를 받고 메인으로(18-4)
                onSocialSignedUp()
            case .notice(let event):
                // 회원가입·비밀번호 재설정을 마치고 돌아왔을 때
                switch event.notice {
                case .signedUp:
                    toast = PapyToast(message: String(localized: "login_notice_signed_up"), type: .success)
                case .passwordChanged:
                    toast = PapyToast(message: String(localized: "login_notice_password_changed"), type: .success)
                }
            }
        }
    }
}
