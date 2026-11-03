package io.github.namjh654.papy.core.common.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * "방금 전 · 5분 전 · 3시간 전 · 2일 전 · (그보다 오래되면 날짜)" 를 정하는 규칙. 문구는 화면이 만든다
 * (core:ui `RelativeTime.label()`), 여기서는 종류와 숫자만 정한다.
 */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val count: Int) : RelativeTime
    data class Hours(val count: Int) : RelativeTime

    /** 달력 기준 며칠 전(1~6) */
    data class Days(val count: Int) : RelativeTime

    /** 일주일 이상 지났거나 미래 → 날짜로 보여준다 */
    data class Date(val epochMillis: Long) : RelativeTime

    companion object {
        private const val MAX_DAYS = 6

        /** 지금·기기 시간대 기준. Swift 는 기본 인자를 쓸 수 없어 이것을 부른다: `RelativeTimeCompanion.shared.since(epochMillis:)` (인터페이스의 companion 은 별도 클래스로 노출) */
        fun since(epochMillis: Long): RelativeTime = of(epochMillis)

        /**
         * @param epochMillis 대상 시각(UTC epoch millis — 서버가 주는 값)
         * @param nowMillis 테스트에서 고정한다
         */
        fun of(
            epochMillis: Long,
            nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
            zone: TimeZone = TimeZone.currentSystemDefault(),
        ): RelativeTime {
            val then = Instant.fromEpochMilliseconds(epochMillis)
            val now = Instant.fromEpochMilliseconds(nowMillis)
            val elapsed = now - then
            return when {
                elapsed.isNegative() -> Date(epochMillis)
                elapsed < 1.minutes -> JustNow
                elapsed < 1.hours -> Minutes(elapsed.inWholeMinutes.toInt())
                elapsed < 24.hours -> Hours(elapsed.inWholeHours.toInt())
                else -> {
                    // "며칠 전"은 지난 시간이 아니라 달력 날짜 차이로 센다(어제 밤 11시 → 오늘 새벽 = 1일 전)
                    val days = then.toLocalDateTime(zone).date.daysUntil(now.toLocalDateTime(zone).date)
                    if (days <= MAX_DAYS) Days(days.coerceAtLeast(1)) else Date(epochMillis)
                }
            }
        }
    }
}
