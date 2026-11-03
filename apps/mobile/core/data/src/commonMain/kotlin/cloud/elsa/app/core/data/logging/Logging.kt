package cloud.elsa.app.core.data.logging

import cloud.elsa.app.core.data.config.AppConfig
import cloud.elsa.app.core.data.monitoring.CrashReporter
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

/**
 * 앱 로그 설정(Kermit). initKoin 이 한 번 부른다. 코드는 `Logger.withTag("Login").d { "…" }` 처럼 쓴다.
 *
 * - 수준: env `log.level`(dev debug · staging info · prod warn)
 * - 콘솔(Logcat / Xcode): env `log.console` 이 true 일 때만 (prod false)
 * - 크래시 리포터: warn 이상은 항상 [CrashReporterLogWriter] 로 — prod 에서는 여기로만 간다
 */
fun configureLogging(crashReporter: CrashReporter) {
    val writers = buildList {
        if (AppConfig.LOG_CONSOLE) add(platformLogWriter())
        add(CrashReporterLogWriter(crashReporter))
    }
    Logger.setLogWriters(writers)
    Logger.setMinSeverity(severityOf(AppConfig.LOG_LEVEL))
    Logger.setTag("App")
}

/** env 값 → Kermit Severity. none 이면 Assert 만(사실상 끔) */
internal fun severityOf(level: String): Severity = when (level.lowercase()) {
    "verbose" -> Severity.Verbose
    "debug" -> Severity.Debug
    "info" -> Severity.Info
    "warn" -> Severity.Warn
    "error" -> Severity.Error
    "none" -> Severity.Assert
    else -> Severity.Info
}

/** warn 이상 → 크래시 리포터 기록, throwable 이 있는 error 이상 → non-fatal 보고 */
class CrashReporterLogWriter(
    private val crashReporter: CrashReporter,
    private val minSeverity: Severity = Severity.Warn,
) : LogWriter() {

    override fun isLoggable(tag: String, severity: Severity): Boolean =
        crashReporter.isEnabled && severity >= minSeverity

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        crashReporter.log("${severity.name.first()}/$tag: $message")
        if (throwable != null && severity >= Severity.Error) crashReporter.recordException(throwable, message)
    }
}
