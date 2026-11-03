package cloud.elsa.app.core.data.monitoring

/**
 * 크래시 리포터 계약. 플랫폼이 구현을 넘긴다(Firebase Crashlytics — 설정 파일이 있을 때만). 기본은 [NoOpCrashReporter].
 * 앱 코드는 직접 부르기보다 Kermit 로그(`Logger.e(throwable) { … }`)를 쓴다 → [CrashReporterLogWriter] 가 여기로 보낸다.
 * Swift: `class FirebaseCrashReporter: NSObject, CrashReporter`
 */
interface CrashReporter {
    val isEnabled: Boolean

    /** 다음 크래시·오류 보고에 함께 붙는 기록(breadcrumb) */
    fun log(message: String)

    /** 앱은 계속 도는 오류(non-fatal) */
    fun recordException(throwable: Throwable, message: String?)

    /** 로그인 사용자 id. 로그아웃이면 null */
    fun setUserId(id: String?)
}

object NoOpCrashReporter : CrashReporter {
    override val isEnabled = false
    override fun log(message: String) = Unit
    override fun recordException(throwable: Throwable, message: String?) = Unit
    override fun setUserId(id: String?) = Unit
}

/**
 * 이벤트(분석) 계약. 플랫폼이 구현을 넘긴다(Firebase Analytics — 설정 파일이 있을 때만). 기본은 [NoOpAnalyticsTracker].
 * 이벤트 이름·파라미터 키는 snake_case, 값은 문자열. ViewModel 에서 Koin 으로 받아 쓴다.
 */
interface AnalyticsTracker {
    val isEnabled: Boolean

    fun logEvent(name: String, params: Map<String, String>)

    fun setUserId(id: String?)
}

object NoOpAnalyticsTracker : AnalyticsTracker {
    override val isEnabled = false
    override fun logEvent(name: String, params: Map<String, String>) = Unit
    override fun setUserId(id: String?) = Unit
}
