import SwiftUI
import Core

/// 설정 → 알림 설정(26-2): 기기 알림이 꺼져 있으면 위에 안내(설정 열기), 아래 종류별 스위치(활동: 채팅·댓글 / 소식: 공지·광고성).
/// 광고성 알림을 바꾸면 처리 날짜를 안내한다(법정 고지). 기기 설정에서 돌아오면 다시 확인한다. (Android `NotificationSettingsScreen`)
struct NotificationSettingsScreen: View {

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.notificationSettingsViewModel(),
        state: \.uiState
    )
    @State private var toast: ElsaToast?
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.locale) private var locale

    private var viewModel: NotificationSettingsViewModel { model.viewModel }
    private var state: NotificationSettingsUiState { model.state }

    var body: some View {
        content
            .elsaScreenBackground(grouped: true)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .tabBar)
            .elsaToast($toast)
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { viewModel.refreshSystem() }
            }
            .task {
                for await event in viewModel.events {
                    switch onEnum(of: event) {
                    case .marketingChanged(let changed):
                        let date = ElsaFormat.date(epochMillis: changed.atMillis, locale: locale)
                        let key: String.LocalizationValue = changed.agreed ? "notification_marketing_agreed" : "notification_marketing_withdrawn"
                        toast = ElsaToast(message: String(format: String(localized: key), date), type: .success)
                    case .updateFailed(let failed):
                        toast = ElsaToast(message: failed.error.localizedMessage, type: .error)
                    }
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if let preferences = state.preferences {
            ScrollView {
                VStack(alignment: .leading, spacing: ElsaSpacing.xl) {
                    ElsaScreenHeader(
                        title: String(localized: "notification_settings_title"),
                        description: String(localized: "notification_settings_description")
                    )
                    if !state.systemEnabled {
                        VStack(alignment: .leading, spacing: ElsaSpacing.sm) {
                            ElsaInlineMessage(message: String(localized: "notification_system_off"), type: .warning)
                            ElsaButton(String(localized: "notification_open_settings"), style: .secondary, size: .small, fullWidth: false) {
                                viewModel.openSystemSettings()
                            }
                        }
                    }
                    ElsaSettingsSection(title: String(localized: "notification_section_activity")) {
                        row(.chat, "notification_chat", "notification_chat_description", preferences.chat)
                        ElsaSettingsDivider()
                        row(.comment, "notification_comment", "notification_comment_description", preferences.comment)
                    }
                    ElsaSettingsSection(title: String(localized: "notification_section_news")) {
                        row(.notice, "notification_notice", "notification_notice_description", preferences.notice)
                        ElsaSettingsDivider()
                        row(.marketing, "notification_marketing", "notification_marketing_description", preferences.marketing)
                    }
                }
                .padding(ElsaSize.screenPadding)
            }
        } else if let error = state.error {
            ElsaErrorState(message: error.localizedMessage, onRetry: { viewModel.load() })
                .frame(maxHeight: .infinity)
        } else {
            ElsaLoadingIndicator().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func row(_ category: NotificationCategory, _ title: String.LocalizationValue, _ description: String.LocalizationValue, _ value: Bool) -> some View {
        ElsaSettingsToggleRow(
            title: String(localized: title),
            description: String(localized: description),
            isOn: Binding(get: { value }, set: { viewModel.toggle(category: category, enabled: $0) })
        )
    }
}
