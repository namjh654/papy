import Foundation
import Core
#if canImport(FirebaseCrashlytics)
import FirebaseCrashlytics
#endif
#if canImport(FirebaseAnalytics)
import FirebaseAnalytics
#endif

/// Firebase Crashlytics·Analytics 를 KMP `CrashReporter`·`AnalyticsTracker` 로. SDK·설정이 없으면 NoOp. (Android `FirebaseMonitoring.kt`)
enum FirebaseMonitoring {

    static func crashReporter(enabled: Bool) -> CrashReporter {
        #if canImport(FirebaseCrashlytics)
        if enabled { return FirebaseCrashReporter() }
        #endif
        return NoOpCrashReporter.shared
    }

    static func analytics(enabled: Bool) -> AnalyticsTracker {
        #if canImport(FirebaseAnalytics)
        if enabled { return FirebaseAnalyticsTracker() }
        #endif
        return NoOpAnalyticsTracker.shared
    }
}

#if canImport(FirebaseCrashlytics)
/// Kermit warn 이상 → KMP CrashReporterLogWriter → 여기. Kotlin 예외는 NSError(클래스 이름·메시지)로 보낸다.
final class FirebaseCrashReporter: NSObject, CrashReporter {
    var isEnabled: Bool { true }

    func log(message: String) {
        Crashlytics.crashlytics().log(message)
    }

    func recordException(throwable: KotlinThrowable, message: String?) {
        if let message { Crashlytics.crashlytics().log(message) }
        let error = NSError(
            domain: String(describing: type(of: throwable)),
            code: 0,
            userInfo: [NSLocalizedDescriptionKey: throwable.message ?? ""]
        )
        Crashlytics.crashlytics().record(error: error)
    }

    func setUserId(id: String?) {
        Crashlytics.crashlytics().setUserID(id ?? "")
    }
}
#endif

#if canImport(FirebaseAnalytics)
final class FirebaseAnalyticsTracker: NSObject, AnalyticsTracker {
    var isEnabled: Bool { true }

    func logEvent(name: String, params: [String: String]) {
        Analytics.logEvent(name, parameters: params)
    }

    func setUserId(id: String?) {
        Analytics.setUserID(id)
    }
}
#endif
