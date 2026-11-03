import SwiftUI
import Core

/// 강제·선택 업데이트 안내(루트에 한 번). 시작 때 KMP `AppUpdateManager.check()`, 강제 업데이트는 닫을 수 없다.
/// "업데이트" 는 App Store 로 보낸다(서버 storeUrl → 없으면 env store.appStoreId). (Android `AppUpdateEffect`)
/// 업데이트 안내가 없으면 운영 공지(DEC-132) — 점검(blocking)은 확인해도 닫히지 않는다.
struct AppUpdateAlert: ViewModifier {

    private let manager = IOSAppGraph.shared.appUpdateManager()

    @StateObject private var status = FlowState(IOSAppGraph.shared.appUpdateManager().status)
    @StateObject private var notice = FlowState(IOSAppGraph.shared.appUpdateManager().notice)

    @Environment(\.openURL) private var openURL

    func body(content: Content) -> some View {
        content
            .task { manager.check() }
            .alert(title, isPresented: isPresented) {
                switch onEnum(of: status.value) {
                case .required(let required):
                    Button(String(localized: "update_button")) { openStore(required.storeUrl) }
                case .optional(let optional):
                    Button(String(localized: "update_later"), role: .cancel) { manager.dismissOptional() }
                    Button(String(localized: "update_button")) {
                        manager.dismissOptional()
                        openStore(optional.storeUrl)
                    }
                case .upToDate:
                    EmptyView()
                }
            } message: {
                switch onEnum(of: status.value) {
                case .required: Text("update_required_message")
                case .optional(let optional): Text(String(format: String(localized: "update_optional_message"), optional.latestVersion))
                case .upToDate: EmptyView()
                }
            }
            .alert(noticeTitle, isPresented: isNoticePresented) {
                Button(String(localized: "common_confirm")) { manager.dismissNotice() }
            } message: {
                Text(notice.value?.message ?? "")
            }
    }

    /// 업데이트 안내가 없을 때만. 점검은 확인해도 상태가 그대로라 다시 뜬다
    private var isNoticePresented: Binding<Bool> {
        Binding(
            get: { status.value is AppUpdateStatusUpToDate && notice.value != nil },
            set: { _ in }
        )
    }

    private var noticeTitle: String {
        guard let value = notice.value else { return "" }
        return value.title ?? String(localized: value.blocking ? "app_notice_maintenance_title" : "app_notice_title")
    }

    /// 강제 업데이트는 버튼을 눌러도 상태가 그대로라 다시 뜬다
    private var isPresented: Binding<Bool> {
        Binding(
            get: { !(status.value is AppUpdateStatusUpToDate) },
            set: { _ in }
        )
    }

    private var title: String {
        switch onEnum(of: status.value) {
        case .required: String(localized: "update_required_title")
        case .optional: String(localized: "update_optional_title")
        case .upToDate: ""
        }
    }

    private func openStore(_ storeUrl: String?) {
        let fallback = AppConfig.shared.STORE_APP_STORE_ID.isEmpty ? nil : "https://apps.apple.com/app/id\(AppConfig.shared.STORE_APP_STORE_ID)"
        guard let string = storeUrl ?? fallback, let url = URL(string: string) else { return }
        openURL(url)
    }
}

extension View {
    func appUpdateAlert() -> some View {
        modifier(AppUpdateAlert())
    }
}
