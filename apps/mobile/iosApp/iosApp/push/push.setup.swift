import Foundation
import Core
#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
import FirebaseCore
import FirebaseMessaging
#endif

/// 푸시 제공자 선택. Firebase 가 설정돼 있으면(PlatformSetup) FCM, 아니면 비활성(`NoOpPushProvider`)
/// — 빌드·실행은 같다. (Android `FcmPushProvider.createOrNoOp`)
enum PushSetup {

    static func makeProvider(firebaseConfigured: Bool) -> PushProvider {
        #if canImport(FirebaseCore) && canImport(FirebaseMessaging)
        if firebaseConfigured {
            return FcmPushProvider()
        }
        #endif
        return NoOpPushProvider.shared
    }

    /// 알림 userInfo → KMP 에 넘길 문자열 data (`aps` 등 문자열이 아닌 값은 버린다)
    static func stringData(_ userInfo: [AnyHashable: Any]) -> [String: String] {
        var data: [String: String] = [:]
        for (key, value) in userInfo {
            if let key = key as? String, let value = value as? String {
                data[key] = value
            }
        }
        return data
    }
}

#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
/// FCM 제공자(기본). APNs 를 직접 쓰려면 `PushProvider` 를 구현한 다른 클래스를 `PushSetup.makeProvider()` 에서 돌려준다.
/// APNs 토큰 → FCM 토큰 연결은 Firebase 의 메서드 스위즐링이 처리한다.
final class FcmPushProvider: NSObject, PushProvider, MessagingDelegate {

    var id: String { "fcm" }
    var isEnabled: Bool { true }

    override init() {
        super.init()
        Messaging.messaging().delegate = self
    }

    func deleteToken() {
        Messaging.messaging().deleteToken { _ in }
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        IOSAppGraph.shared.pushManager().onNewToken(token: fcmToken)
    }
}
#endif
