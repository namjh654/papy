import UIKit
import UserNotifications
import Core

/// 알림 표시·탭 처리. `iOSApp` 에 `@UIApplicationDelegateAdaptor` 로 연결한다.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// 앱이 떠 있을 때도 배너로 보여 준다
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    /// 알림 탭 → KMP PushManager.pendingDeepLink → AppNavigation 이 메인에서 연다(콜드 스타트 포함)
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let data = PushSetup.stringData(response.notification.request.content.userInfo)
        IOSAppGraph.shared.pushManager().onNotificationOpened(data: data)
    }
}
