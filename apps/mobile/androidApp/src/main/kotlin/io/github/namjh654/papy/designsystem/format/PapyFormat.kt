package io.github.namjh654.papy.designsystem.format

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 로케일 기준 날짜·시간·숫자 포맷. 화면에서 패턴 문자열("yyyy.MM.dd" 등)을 직접 쓰지 않는다.
 * iOS 는 `PapyFormat`(Foundation FormatStyle) 이 같은 역할을 한다.
 *
 * 로케일은 [currentLocale] (앱별 언어 설정 반영) 을 넘긴다.
 */
object PapyFormat {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    /** 예) ko: 2026. 9. 29. / en: Sep 29, 2026 */
    fun date(millis: Long, locale: Locale, timeZone: TimeZone = TimeZone.getDefault()): String =
        pattern(locale, "yMMMd", timeZone).format(Date(millis))

    /** 예) ko: 2026년 9월 29일 화요일 / en: Tuesday, September 29, 2026 */
    fun dateLong(millis: Long, locale: Locale, timeZone: TimeZone = TimeZone.getDefault()): String =
        pattern(locale, "yMMMMEEEEd", timeZone).format(Date(millis))

    /** DatePicker 값(UTC 자정 millis) 표시용 */
    fun utcDate(utcMillis: Long, locale: Locale): String = date(utcMillis, locale, utc)

    /** 예) ko: 오후 3:05 / en: 3:05 PM, 24시간: 15:05 */
    fun time(hour: Int, minute: Int, locale: Locale, is24Hour: Boolean): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return pattern(locale, if (is24Hour) "Hm" else "hm", TimeZone.getDefault()).format(calendar.time)
    }

    /** 시각(epoch 밀리초)의 시:분 — 메시지 시간 등. 예) ko: 오후 3:05 / en: 3:05 PM */
    fun time(millis: Long, locale: Locale, is24Hour: Boolean): String =
        pattern(locale, if (is24Hour) "Hm" else "hm", TimeZone.getDefault()).format(Date(millis))

    /** 파일 크기. 예) 820 B · 12.4 KB · 1.2 MB (1024 단위) */
    fun fileSize(bytes: Long, locale: Locale): String {
        if (bytes < 1024) return "${number(bytes, locale)} B"
        val units = listOf("KB", "MB", "GB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        val formatted = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(value)
        return "$formatted ${units[unit]}"
    }

    /** 예) ko/en: 1,234,567.5 · de: 1.234.567,5 */
    fun number(value: Number, locale: Locale): String = NumberFormat.getNumberInstance(locale).format(value)

    /** 0.25 → 25% */
    fun percent(fraction: Double, locale: Locale): String = NumberFormat.getPercentInstance(locale).format(fraction)

    private fun pattern(locale: Locale, skeleton: String, timeZone: TimeZone) =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).apply { this.timeZone = timeZone }
}

/** 현재 화면 구성의 로케일(시스템 또는 앱별 언어 설정). */
val currentLocale: Locale
    @Composable @ReadOnlyComposable
    get() = LocalConfiguration.current.locales[0]
