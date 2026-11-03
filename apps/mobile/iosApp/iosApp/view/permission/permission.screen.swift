import SwiftUI
import Core

/// 권한 요청 기준 화면: 권한마다 "왜 필요한지" + 현재 상태 + 상태에 맞는 버튼.
/// 화면이 다시 보일 때마다(설정 앱에서 돌아올 때) 상태를 다시 읽는다. (Android `PermissionScreen`)
struct PermissionScreen: View {

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.permissionViewModel(),
        state: \.uiState
    )

    @Environment(\.scenePhase) private var scenePhase

    @State private var alert: ElsaAlert?

    private var viewModel: PermissionViewModel { model.viewModel }
    private var state: PermissionUiState { model.state }

    private let permissions: [AppPermission] = [.notifications, .camera, .location]

    var body: some View {
        ElsaScrollScreen {
            ElsaScreenHeader(
                title: String(localized: "permission_title"),
                description: String(localized: "permission_description")
            )

            ForEach(permissions, id: \.self) { permission in
                let status = state.status(permission: permission)
                ElsaCard {
                    HStack(spacing: ElsaSpacing.sm) {
                        Text(title(permission))
                            .elsaFont(ElsaFont.subtitle)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        if let status {
                            ElsaBadge(text: label(status), type: badgeType(status))
                        }
                    }
                    Text(reason(permission))
                        .elsaFont(ElsaFont.bodySmall)
                        .foregroundStyle(ElsaColor.onSurfaceVariant)
                    if let status, !status.isGranted {
                        ElsaButton(
                            String(localized: status == .permanentlyDenied ? "permission_open_settings" : "permission_allow"),
                            style: .secondary,
                            size: .small,
                            isLoading: state.requesting == permission
                        ) {
                            viewModel.request(permission: permission)
                        }
                    }
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .elsaAlert($alert)
        // 재확인: 처음 보일 때 + 다른 앱(설정)에서 돌아올 때마다
        .onAppear { viewModel.refresh() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { viewModel.refresh() }
        }
        .task {
            await observeEvents()
        }
    }

    private func title(_ permission: AppPermission) -> String {
        switch permission {
        case .notifications: String(localized: "permission_notifications")
        case .camera: String(localized: "permission_camera")
        case .location: String(localized: "permission_location")
        }
    }

    /// 왜 필요한지 — 시스템 창을 띄우기 전에 먼저 보여준다
    private func reason(_ permission: AppPermission) -> String {
        switch permission {
        case .notifications: String(localized: "permission_notifications_reason")
        case .camera: String(localized: "permission_camera_reason")
        case .location: String(localized: "permission_location_reason")
        }
    }

    private func label(_ status: PermissionStatus) -> String {
        switch status {
        case .granted: String(localized: "permission_status_granted")
        case .notRequested: String(localized: "permission_status_not_requested")
        case .denied, .permanentlyDenied: String(localized: "permission_status_denied")
        }
    }

    private func badgeType(_ status: PermissionStatus) -> ElsaMessageType {
        switch status {
        case .granted: .success
        case .notRequested: .info
        case .denied, .permanentlyDenied: .warning
        }
    }
}


// MARK: - Event Observation

private extension PermissionScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .settingsRequired:
                alert = ElsaAlert(
                    title: String(localized: "permission_settings_title"),
                    message: String(localized: "permission_settings_message"),
                    confirmText: String(localized: "permission_open_settings"),
                    dismissText: String(localized: "common_cancel"),
                    onConfirm: { viewModel.openSettings() }
                )
            }
        }
    }
}
