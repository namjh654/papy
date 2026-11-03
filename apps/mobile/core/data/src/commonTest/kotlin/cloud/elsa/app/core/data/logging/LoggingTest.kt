package cloud.elsa.app.core.data.logging

import cloud.elsa.app.core.data.monitoring.CrashReporter
import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggingTest {

    private class RecordingReporter(override val isEnabled: Boolean = true) : CrashReporter {
        val logs = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        override fun log(message: String) { logs += message }
        override fun recordException(throwable: Throwable, message: String?) { errors += throwable }
        override fun setUserId(id: String?) = Unit
    }

    @Test
    fun crashWriterTakesWarnAndAboveAndRecordsErrors() {
        val reporter = RecordingReporter()
        val writer = CrashReporterLogWriter(reporter)

        assertFalse(writer.isLoggable("T", Severity.Info))
        assertTrue(writer.isLoggable("T", Severity.Warn))

        writer.log(Severity.Warn, "slow", "Http", null)
        val boom = IllegalStateException("boom")
        writer.log(Severity.Error, "failed", "Login", boom)

        assertEquals(listOf("W/Http: slow", "E/Login: failed"), reporter.logs)
        assertEquals(listOf<Throwable>(boom), reporter.errors)
    }

    @Test
    fun disabledReporterIsNotLoggable() {
        assertFalse(CrashReporterLogWriter(RecordingReporter(isEnabled = false)).isLoggable("T", Severity.Error))
    }

    @Test
    fun envLevelMapsToSeverity() {
        assertEquals(Severity.Debug, severityOf("debug"))
        assertEquals(Severity.Warn, severityOf("WARN"))
        assertEquals(Severity.Assert, severityOf("none"))
        assertEquals(Severity.Info, severityOf("unknown"))
    }
}
