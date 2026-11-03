package cloud.elsa.app.feature.chat.room

import cloud.elsa.app.feature.chat.message
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageGroupingTest {

    private val utc = TimeZone.UTC
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private fun item(id: String, sender: String, at: Long) = ChatMessage(message(id, sender, at), isMine = sender == "me")

    @Test
    fun consecutiveMessagesFromSameSenderFormOneGroup() {
        val rows = buildRows(
            listOf(item("1", "peer", 0), item("2", "peer", minute), item("3", "peer", 2 * minute), item("4", "me", 3 * minute)),
            utc,
        )
        val bubbles = rows.filterIsInstance<ChatRow.Bubble>()

        assertEquals(
            listOf(BubblePosition.First, BubblePosition.Middle, BubblePosition.Last, BubblePosition.Single),
            bubbles.map { it.position },
        )
        // 시간은 묶음의 마지막에만
        assertEquals(listOf(false, false, true, true), bubbles.map { it.showTime })
    }

    @Test
    fun gapLongerThanWindowStartsNewGroup() {
        val rows = buildRows(listOf(item("1", "peer", 0), item("2", "peer", GROUP_WINDOW_MILLIS + 1)), utc)

        assertEquals(
            listOf(BubblePosition.Single, BubblePosition.Single),
            rows.filterIsInstance<ChatRow.Bubble>().map { it.position },
        )
    }

    @Test
    fun dateHeaderWhenDayChangesAndGroupBreaks() {
        val rows = buildRows(listOf(item("1", "peer", day - minute), item("2", "peer", day + minute)), utc)

        assertEquals(listOf("date-0", "1", "date-$day", "2"), rows.map { it.key })
        assertEquals(BubblePosition.Single, (rows[3] as ChatRow.Bubble).position)
    }

    @Test
    fun emptyMessagesMakeNoRows() {
        assertEquals(emptyList(), buildRows(emptyList(), utc))
    }
}
