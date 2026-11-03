import Foundation
import Core
#if canImport(FirebaseCore)
import FirebaseCore
#endif

/// KMP 에 넘길 플랫폼 SDK 구현 묶음(`PlatformServices`). Firebase SDK(SPM)가 링크돼 있고
/// `GoogleService-Info.plist` 가 번들에 있으면 푸시·크래시·이벤트가 켜지고, 아니면 각각 NoOp — 빌드·실행은 같다.
/// (Android `PapyApplication`)
enum PlatformSetup {

    static func makeServices() -> PlatformServices {
        let firebase = configureFirebase()
        return PlatformServices(
            pushProvider: PushSetup.makeProvider(firebaseConfigured: firebase),
            crashReporter: FirebaseMonitoring.crashReporter(enabled: firebase),
            analytics: FirebaseMonitoring.analytics(enabled: firebase),
            inAppReview: StoreKitInAppReview(),
            // 소셜 SDK 세션 정리(DEC-107) — 로그아웃 직전 KMP 가 부른다
            socialAuth: IOSSocialAuthProvider()
        )
    }

    /// plist 가 있을 때만 FirebaseApp.configure(). 설정됐으면 true
    private static func configureFirebase() -> Bool {
        #if canImport(FirebaseCore)
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            return true
        }
        #endif
        return false
    }
}
