import SwiftUI
import Core

/// 앱 잠금(26-1): KMP `AppLock` 이 잠그라고 하면 화면 전체를 잠금 화면으로 덮고 바로 본인 확인을 띄운다.
/// scenePhase(background·active)를 AppLock 에 알린다 — 몇 초 안에 돌아오면 잠그지 않는 것은 KMP 가 정한다. (Android `AppLockGate`)
struct AppLockGate: ViewModifier {

    private let appLock = IOSAppGraph.shared.appLock()
    @StateObject private var locked = FlowState(IOSAppGraph.shared.appLock().isLocked)
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .overlay {
                if locked.value.boolValue {
                    AppLockScreen(onUnlock: unlock)
                        .transition(.opacity)
                        // 본인 확인 창(Face ID 등)이 떠 있는 동안은 scenePhase 가 inactive — active 가 된 뒤 띄운다
                        .task { if scenePhase == .active { unlock() } }
                }
            }
            .animation(.easeInOut(duration: 0.2), value: locked.value.boolValue)
            .onChange(of: scenePhase) { _, phase in
                switch phase {
                case .background: appLock.onBackground()
                case .active: appLock.onForeground()
                default: break
                }
            }
    }

    private func unlock() {
        Task {
            if await DeviceAuth.authenticate(reason: String(localized: "app_lock_prompt")) {
                appLock.unlock()
            }
        }
    }
}

/// 잠금 화면: 가운데 자물쇠 → 제목·설명 → 아래 "잠금 해제"
struct AppLockScreen: View {
    let onUnlock: () -> Void

    var body: some View {
        VStack(spacing: PapySpacing.md) {
            Spacer()
            PapyIcon("papy_ic_lock")
                .frame(width: 32, height: 32)
                .foregroundStyle(PapyColor.onPrimaryContainer)
                .frame(width: 72, height: 72)
                .background(PapyColor.primaryContainer, in: Circle())
            Text("app_lock_title")
                .papyFont(PapyFont.title)
                .foregroundStyle(PapyColor.onBackground)
                .multilineTextAlignment(.center)
            Text("app_lock_description")
                .papyFont(PapyFont.body)
                .foregroundStyle(PapyColor.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Spacer()
            PapyButton(String(localized: "app_lock_unlock"), action: onUnlock)
        }
        .padding(PapySize.screenPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PapyColor.background.ignoresSafeArea())
    }
}

extension View {
    func appLockGate() -> some View {
        modifier(AppLockGate())
    }
}

#Preview("잠금 화면") {
    AppLockScreen(onUnlock: {})
}
