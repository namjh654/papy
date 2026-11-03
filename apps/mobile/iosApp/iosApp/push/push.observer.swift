import SwiftUI
import UserNotifications
import Core

/// KMP `PushManager` 를 SwiftUI 에 연결한다. 앱 루트(AppNavigation)에서 한 번만 쓴다.
@MainActor
final class PushObserver: ObservableObject {

    /// 알림 탭으로 들어온, 아직 열지 않은 DeepLink
    @Published private(set) var pendingDeepLink: DeepLink?

    let manager: PushManager

    private var task: Task<Void, Never>?

    init(manager: PushManager = IOSAppGraph.shared.pushManager()) {
        self.manager = manager
        let flow = manager.pendingDeepLink
        pendingDeepLink = flow.value

        task = Task { [weak self] in
            for await next in flow {
                self?.pendingDeepLink = next
            }
        }
    }

    deinit {
        task?.cancel()
    }

    /// 로그인 후 호출: 권한 요청 → 결과를 KMP 에 알리고, 허용되면 원격 알림 등록(→ FCM 토큰)
    func requestPermissionIfNeeded() {
        guard manager.provider.isEnabled else { return }
        Task {
            let granted = (try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])) ?? false
            manager.onPermissionChanged(permission: granted ? .granted : .denied)
            if granted {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }
}
