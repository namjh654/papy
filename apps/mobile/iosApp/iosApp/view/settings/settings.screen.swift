import SwiftUI
import Core

/// 설정 탭: 화면(테마·언어) · 정보(버전·환경·약관) · 계정(로그아웃). (Android `SettingsScreen`)
/// 새 행은 ElsaSettingsRow 한 줄 + 값은 KMP AppSettingsRepository / SettingsUiState.
struct SettingsScreen: View {

    let onLoggedOut: () -> Void
    let onOpenWeb: (URL, String) -> Void
    let onProfileTap: () -> Void
    let onWithdrawTap: () -> Void
    let onInquiryTap: () -> Void
    let onNotificationSettingsTap: () -> Void
    let onDownloadsTap: () -> Void
    let onBlockedUsersTap: () -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.settingsViewModel(),
        state: \.uiState
    )

    @State private var showThemeSheet = false
    @State private var showLanguageSheet = false
    @State private var alert: ElsaAlert?

    private var state: SettingsUiState { model.state }

    /// 앱 잠금: 켤 때는 먼저 본인 확인 — 성공해야 켠다
    private func setAppLock(_ enabled: Bool) {
        guard enabled else { model.viewModel.appLockChanged(enabled: false); return }
        Task {
            if await DeviceAuth.authenticate(reason: String(localized: "app_lock_prompt")) {
                model.viewModel.appLockChanged(enabled: true)
            }
        }
    }

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-"
    }

    var body: some View {
        ElsaScrollScreen(spacing: ElsaSpacing.xl) {
            ElsaScreenHeader(title: String(localized: "settings_title"))

            if let user = state.user {
                Button(action: onProfileTap) {
                    ElsaCard {
                        HStack(spacing: ElsaSpacing.lg) {
                            ElsaAvatar(name: user.name, url: user.avatarUrl)
                            VStack(alignment: .leading, spacing: ElsaSpacing.xxs) {
                                Text(user.name)
                                    .elsaFont(ElsaFont.subtitle)
                                Text(user.email ?? String(localized: "settings_profile_edit"))
                                    .elsaFont(ElsaFont.bodySmall)
                                    .foregroundStyle(ElsaColor.onSurfaceVariant)
                            }
                            Spacer(minLength: 0)
                        }
                    }
                }
                .buttonStyle(ElsaPressableButtonStyle())
            }

            ElsaSettingsSection(title: String(localized: "settings_section_display")) {
                ElsaSettingsRow(title: String(localized: "settings_theme"), value: label(state.theme)) {
                    showThemeSheet = true
                }
                ElsaSettingsDivider()
                ElsaSettingsRow(title: String(localized: "settings_language"), value: label(state.language)) {
                    showLanguageSheet = true
                }
            }

            // 알림·보안(26): 알림 종류별 설정, 앱 잠금(기기에 잠금 수단이 있을 때만)
            ElsaSettingsSection(title: String(localized: "settings_section_security")) {
                ElsaSettingsRow(title: String(localized: "settings_notifications")) {
                    onNotificationSettingsTap()
                }
                if DeviceAuth.isAvailable {
                    ElsaSettingsDivider()
                    ElsaSettingsToggleRow(
                        title: String(localized: "settings_app_lock"),
                        description: String(localized: "settings_app_lock_description"),
                        isOn: Binding(get: { state.appLockEnabled }, set: { enabled in setAppLock(enabled) })
                    )
                }
            }

            ElsaSettingsSection(title: String(localized: "settings_section_info")) {
                ElsaSettingsRow(title: String(localized: "settings_version"), value: appVersion)
                ElsaSettingsDivider()
                ElsaSettingsRow(title: String(localized: "settings_rate_app")) {
                    model.viewModel.rateApp()
                }
                ElsaSettingsDivider()
                // 앱 안의 연락 수단(App Store 1.2, 25-3)
                ElsaSettingsRow(title: String(localized: "settings_inquiry")) {
                    onInquiryTap()
                }
                if let environment = state.environment {
                    ElsaSettingsDivider()
                    ElsaSettingsRow(title: String(localized: "settings_environment"), value: environment)
                }
                if let url = state.termsUrl.flatMap(URL.init(string:)) {
                    ElsaSettingsDivider()
                    ElsaSettingsRow(title: String(localized: "settings_terms")) {
                        onOpenWeb(url, String(localized: "settings_terms"))
                    }
                }
                if let url = state.privacyUrl.flatMap(URL.init(string:)) {
                    ElsaSettingsDivider()
                    ElsaSettingsRow(title: String(localized: "settings_privacy")) {
                        onOpenWeb(url, String(localized: "settings_privacy"))
                    }
                }
            }

            ElsaSettingsSection(title: String(localized: "settings_section_account")) {
                // 받아 둔 파일(30-1)
                ElsaSettingsRow(title: String(localized: "settings_downloads")) {
                    onDownloadsTap()
                }
                ElsaSettingsDivider()
                // 커뮤니티 차단 해제(App Store 1.2) — 커뮤니티 모듈을 빼면 이 줄도 뺀다
                ElsaSettingsRow(title: String(localized: "settings_blocked_users")) { onBlockedUsersTap() }
                ElsaSettingsDivider()
                ElsaSettingsRow(title: String(localized: "settings_logout"), destructive: true) {
                    guard !state.isLoggingOut else { return }
                    alert = ElsaAlert(
                        title: String(localized: "settings_logout_confirm"),
                        confirmText: String(localized: "settings_logout"),
                        dismissText: String(localized: "common_cancel"),
                        isDestructive: true,
                        onConfirm: { model.viewModel.logout() }
                    )
                }
                ElsaSettingsDivider()
                ElsaSettingsRow(title: String(localized: "settings_withdraw")) {
                    onWithdrawTap()
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .elsaOptionSheet(
            isPresented: $showThemeSheet,
            title: String(localized: "settings_theme"),
            options: [ThemeMode.system, .light, .dark],
            selected: state.theme,
            label: label,
            onSelect: { model.viewModel.selectTheme(mode: $0) }
        )
        .elsaOptionSheet(
            isPresented: $showLanguageSheet,
            title: String(localized: "settings_language"),
            options: [AppLanguage.system, .korean, .english],
            selected: state.language,
            label: label,
            onSelect: { model.viewModel.selectLanguage(language: $0) }
        )
        .elsaAlert($alert)
        .task {
            await observeEvents()
        }
    }

    private func label(_ mode: ThemeMode) -> String {
        switch mode {
        case .light: String(localized: "settings_theme_light")
        case .dark: String(localized: "settings_theme_dark")
        default: String(localized: "settings_theme_system")
        }
    }

    private func label(_ language: AppLanguage) -> String {
        switch language {
        case .korean: String(localized: "settings_language_ko")
        case .english: String(localized: "settings_language_en")
        default: String(localized: "settings_language_system")
        }
    }
}


// MARK: - Event Observation

private extension SettingsScreen {

    func observeEvents() async {
        for await event in model.viewModel.events {
            switch onEnum(of: event) {
            case .loggedOut:
                onLoggedOut()
            case .languageChanged(let changed):
                // iOS 는 실행 중 언어를 바꿀 수 없다 → 다음 실행부터 적용(AppleLanguages), 안내만 한다
                AppLanguageSetting.apply(changed.language)
                alert = ElsaAlert(title: String(localized: "settings_language_restart"))
            }
        }
    }
}

/// 앱 언어(AppleLanguages). 기기 설정이면 키를 지워 시스템 언어를 따른다.
enum AppLanguageSetting {
    static func apply(_ language: AppLanguage) {
        if let tag = language.tag {
            UserDefaults.standard.set([tag], forKey: "AppleLanguages")
        } else {
            UserDefaults.standard.removeObject(forKey: "AppleLanguages")
        }
    }
}
