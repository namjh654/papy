package io.github.namjh654.papy.feature.chat.room

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** 같은 사람이 이 시간 안에 잇달아 보낸 메시지는 한 묶음(텔레그램과 같은 5분) */
internal const val GROUP_WINDOW_MILLIS = 5 * 60_000L

/**
 * 메시지(오래된 것 → 새 것) → 목록 줄. 날짜가 바뀌면 구분선, 같은 사람·같은 날·[groupWindowMillis] 안이면 한 묶음.
 * 두 플랫폼이 같은 규칙으로 그리도록 KMP 에서 정한다.
 */
internal fun buildRows(
    messages: List<ChatMessage>,
    timeZone: TimeZone,
    groupWindowMillis: Long = GROUP_WINDOW_MILLIS,
): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 4)
    messages.forEachIndexed { i, current ->
        val previous = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        val day = current.day(timeZone)
        if (previous == null || previous.day(timeZone) != day) {
            rows += ChatRow.DateHeader(day.atStartOfDayIn(timeZone).toEpochMilliseconds())
        }
        val joinsPrevious = previous != null && sameGroup(previous, current, timeZone, groupWindowMillis)
        val joinsNext = next != null && sameGroup(current, next, timeZone, groupWindowMillis)
        val position = when {
            joinsPrevious && joinsNext -> BubblePosition.Middle
            joinsPrevious -> BubblePosition.Last
            joinsNext -> BubblePosition.First
            else -> BubblePosition.Single
        }
        rows += ChatRow.Bubble(item = current, position = position, showTime = !joinsNext)
    }
    return rows
}

private fun sameGroup(a: ChatMessage, b: ChatMessage, timeZone: TimeZone, window: Long): Boolean =
    a.message.senderId == b.message.senderId &&
        b.message.sentAt - a.message.sentAt in 0..window &&
        a.day(timeZone) == b.day(timeZone)

private fun ChatMessage.day(timeZone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(message.sentAt).toLocalDateTime(timeZone).date
