import SwiftUI
import Core

/// 회원 탈퇴: 안내 → 본인 확인 → 체크 → 확인 알림 → 요청. (Android `WithdrawScreen`)
struct WithdrawScreen: View {

    /// 탈퇴 완료 → 로그인 화면으로
    let onWithdrawn: () -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.withdrawViewModel(),
        state: \.uiState
    )

    @State private var alert: ElsaAlert?

    private var viewModel: WithdrawViewModel { model.viewModel }
    private var state: WithdrawUiState { model.state }

    var body: some View {
        ElsaFormScrollView {
            ElsaScreenHeader(
                title: String(localized: "withdraw_title"),
                description: String(localized: "withdraw_description")
            )

            ElsaInlineMessage(message: String(localized: "withdraw_notice"), type: .warning)

            // 소셜로 가입한 사용자는 비밀번호가 없다 — 체크만으로 본인 의사를 확인한다(18-4)
            if state.requiresPassword {
                ElsaTextField(
                String(localized: "field_password"),
                text: Binding(get: { state.password }, set: { viewModel.passwordChanged(value: $0) }),
                placeholder: String(localized: "field_password_placeholder"),
                helperText: String(localized: "withdraw_password_helper"),
                isSecure: true,
                textContentType: .password,
                submitAction: .done
                )
            }

            ElsaCheckbox(
                label: String(localized: "withdraw_agree"),
                isOn: Binding(get: { state.confirmed }, set: { viewModel.confirmedChanged(value: $0) })
            )

            if let error = state.error {
                ElsaInlineMessage(message: error.localizedMessage, type: .error)
            }
        } bottomBar: {
            ElsaButton(
                String(localized: "withdraw_button"),
                style: .destructive,
                enabled: state.canSubmit || state.isLoading,
                isLoading: state.isLoading
            ) {
                alert = ElsaAlert(
                    title: String(localized: "withdraw_confirm_title"),
                    message: String(localized: "withdraw_confirm_message"),
                    confirmText: String(localized: "withdraw_button"),
                    dismissText: String(localized: "common_cancel"),
                    isDestructive: true,
                    onConfirm: { viewModel.withdraw() }
                )
            }
        }
        .elsaScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .elsaAlert($alert)
        .task {
            await observeEvents()
        }
    }
}


// MARK: - Event Observation

private extension WithdrawScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .withdrawn:
                onWithdrawn()
            }
        }
    }
}
