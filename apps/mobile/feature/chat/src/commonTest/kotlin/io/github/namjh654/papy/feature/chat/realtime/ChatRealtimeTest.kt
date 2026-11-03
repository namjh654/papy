package io.github.namjh654.papy.feature.chat.realtime

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.feature.chat.FakeChatApi
import io.github.namjh654.papy.feature.chat.MessagesResponse
import io.github.namjh654.papy.feature.chat.message
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatRealtimeTest {

    @Test
    fun parsesMessageAndReadFrames() {
        val received = parseRealtimeFrame(
            """{"type":"message","message":{"id":"m1","conversationId":"c1","senderId":"peer","text":"hi","sentAt":5}}""",
        )
        assertEquals(ChatRealtimeEvent.Received(message("m1", at = 5, text = "hi")), received)
        assertEquals(ChatRealtimeEvent.PeerRead(9), parseRealtimeFrame("""{"type":"read","readAt":9}"""))
        assertEquals(ChatRealtimeEvent.PeerTyping, parseRealtimeFrame("""{"type":"typing"}"""))
    }

    @Test
    fun unknownOrBrokenFramesAreIgnored() {
        assertNull(parseRealtimeFrame("""{"type":"presence"}"""))
        assertNull(parseRealtimeFrame("not json"))
    }

    @Test
    fun pollingAsksAfterLastMessageAndSurvivesFailure() = runTest {
        var calls = 0
        val api = FakeChatApi().apply {
            messagesResult = { _, after ->
                calls++
                if (calls == 1) throw AppError.Network(IllegalStateException("offline"))
                MessagesResponse(items = listOf(message("after-$after")), peerReadAt = 3)
            }
        }
        val events = PollingChatRealtime(api, intervalMillis = 10).events("c1") { "m9" }.take(2).toList()

        assertEquals(
            listOf(ChatRealtimeEvent.Received(message("after-m9")), ChatRealtimeEvent.PeerRead(3)),
            events,
        )
    }
}
