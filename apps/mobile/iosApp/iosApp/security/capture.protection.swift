import SwiftUI
import Core

/// 화면 캡처 막기(30-2, DEC-137). iOS 는 스크린샷 자체를 막을 수 없어 할 수 있는 것만 한다. (Android `CaptureProtection` — FLAG_SECURE)
/// - env `security.blockCapture=true`: 화면 녹화·미러링 중에는 내용을 가리고, 스크린샷을 찍으면 조심하라고 안내한다
/// - 그 값이거나 앱 잠금을 켰으면: 앱 전환 화면(멀티태스킹 미리 보기)에서 내용을 가린다
struct CaptureProtection: ViewModifier {

    private let blockCapture = AppConfig.shared.SECURITY_BLOCK_CAPTURE
    @StateObject private var lockEnabled = FlowState(IOSAppGraph.shared.appLock().isEnabled)
    @Environment(\.scenePhase) private var scenePhase
    @State private var isCaptured = UIScreen.main.isCaptured
    @State private var toast: ElsaToast?

    func body(content: Content) -> some View {
        content
            .overlay {
                if blockCapture && isCaptured {
                    PrivacyCover(message: String(localized: "capture_recording_hidden"))
                } else if scenePhase != .active && (blockCapture || lockEnabled.value.boolValue) {
                    PrivacyCover(message: nil)
                }
            }
            .elsaToast($toast)
            .onReceive(NotificationCenter.default.publisher(for: UIScreen.capturedDidChangeNotification)) { _ in
                isCaptured = UIScreen.main.isCaptured
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.userDidTakeScreenshotNotification)) { _ in
                if blockCapture {
                    toast = ElsaToast(message: String(localized: "capture_screenshot_notice"), type: .info)
                }
            }
    }
}

/// 내용을 가리는 화면(앱 전환 미리 보기·녹화 중)
private struct PrivacyCover: View {
    let message: String?

    var body: some View {
        VStack(spacing: ElsaSpacing.md) {
            ElsaIcon("elsa_ic_lock")
                .frame(width: 32, height: 32)
                .foregroundStyle(ElsaColor.onSurfaceVariant)
            if let message {
                Text(message)
                    .elsaFont(ElsaFont.body)
                    .foregroundStyle(ElsaColor.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(ElsaSize.screenPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ElsaColor.background.ignoresSafeArea())
    }
}

extension View {
    func captureProtection() -> some View {
        modifier(CaptureProtection())
    }
}
