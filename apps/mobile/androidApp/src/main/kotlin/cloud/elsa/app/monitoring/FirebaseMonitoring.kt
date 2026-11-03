package cloud.elsa.app.monitoring

import android.content.Context
import android.os.Bundle
import cloud.elsa.app.core.data.monitoring.AnalyticsTracker
import cloud.elsa.app.core.data.monitoring.CrashReporter
import cloud.elsa.app.core.data.monitoring.NoOpAnalyticsTracker
import cloud.elsa.app.core.data.monitoring.NoOpCrashReporter
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Crashlytics. google-services.json 이 있어 Firebase 가 초기화됐을 때만 쓴다(푸시와 같은 방식).
 * 앱 코드는 Kermit 로그를 쓰고, warn 이상이 KMP CrashReporterLogWriter 를 거쳐 여기로 온다.
 */
class FirebaseCrashReporter private constructor() : CrashReporter {
    private val crashlytics = FirebaseCrashlytics.getInstance()

    override val isEnabled = true
    override fun log(message: String) = crashlytics.log(message)
    override fun recordException(throwable: Throwable, message: String?) {
        message?.let(crashlytics::log)
        crashlytics.recordException(throwable)
    }
    override fun setUserId(id: String?) = crashlytics.setUserId(id.orEmpty())

    companion object {
        fun createOrNoOp(context: Context): CrashReporter =
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseCrashReporter() else NoOpCrashReporter
    }
}

/** Firebase Analytics. 설정 파일이 없으면 NoOp */
class FirebaseAnalyticsTracker private constructor(context: Context) : AnalyticsTracker {
    private val analytics = FirebaseAnalytics.getInstance(context)

    override val isEnabled = true
    override fun logEvent(name: String, params: Map<String, String>) {
        analytics.logEvent(name, Bundle().apply { params.forEach { (key, value) -> putString(key, value) } })
    }
    override fun setUserId(id: String?) = analytics.setUserId(id)

    companion object {
        fun createOrNoOp(context: Context): AnalyticsTracker =
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAnalyticsTracker(context) else NoOpAnalyticsTracker
    }
}
