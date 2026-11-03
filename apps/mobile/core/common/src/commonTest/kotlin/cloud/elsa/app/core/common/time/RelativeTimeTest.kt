package cloud.elsa.app.core.common.time

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    /** 2026-09-30T12:00:00Z */
    private val now = 1_790_769_600_000L

    private fun of(then: Long, zone: TimeZone = TimeZone.UTC) = RelativeTime.of(then, nowMillis = now, zone = zone)

    @Test
    fun underAMinuteIsJustNow() {
        assertEquals(RelativeTime.JustNow, of(now))
        assertEquals(RelativeTime.JustNow, of(now - 59_000))
    }

    @Test
    fun minutesAndHours() {
        assertEquals(RelativeTime.Minutes(1), of(now - minute))
        assertEquals(RelativeTime.Minutes(59), of(now - 59 * minute))
        assertEquals(RelativeTime.Hours(1), of(now - hour))
        assertEquals(RelativeTime.Hours(23), of(now - 23 * hour))
    }

    @Test
    fun daysAreCountedByCalendarDate() {
        assertEquals(RelativeTime.Days(1), of(now - day))
        // 37시간 전(9/28 23:00Z)은 하루 반이지만 달력으로는 이틀 전
        assertEquals(RelativeTime.Days(2), of(now - 37 * hour))
        assertEquals(RelativeTime.Days(6), of(now - 6 * day))
    }

    @Test
    fun timeZoneChangesTheDate() {
        val seoul = TimeZone.of("Asia/Seoul") // 지금 = 9/30 21:00 KST
        val then = now - 40 * hour // 9/28 20:00Z = 9/29 05:00 KST

        assertEquals(RelativeTime.Days(2), of(then, TimeZone.UTC))
        assertEquals(RelativeTime.Days(1), of(then, seoul))
    }

    @Test
    fun aWeekOrMoreAndFutureShowDate() {
        assertEquals(RelativeTime.Date(now - 7 * day), of(now - 7 * day))
        assertEquals(RelativeTime.Date(now + hour), of(now + hour))
    }
}
