package io.github.namjh654.papy.feature.chat

import io.github.namjh654.papy.core.common.paging.Page
import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.data.media.AttachmentInput
import io.github.namjh654.papy.core.data.media.AttachmentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ChatRepositoryTest {

    private val api = FakeChatApi()
    private val cache = testChatCache()
    private val repository = ChatRepository(api, cache)

    @Test
    fun sendsTextWithClientId() = runTest {
        val sent = repository.send("c1", clientId = "k1", text = "hi")

        assertEquals(listOf(SendMessageRequest(clientId = "k1", text = "hi")), api.sent)
        assertEquals("k1", sent.clientId)
    }

    @Test
    fun attachmentIsUploadedThenSentAsMessage() = runTest {
        val input = AttachmentInput("a.jpg", "image/jpeg", AttachmentKind.Image, ByteArray(3))

        val sent = repository.sendAttachment("c1", clientId = "k2", input = input)

        assertEquals(listOf(input), api.uploaded)
        assertEquals("https://cdn/a.jpg", sent.attachment?.url)
        assertEquals(3, sent.attachment?.size)
    }

    @Test
    fun failedUploadDoesNotSend() = runTest {
        api.uploadError = AppError.Http(500, "")

        runCatching { repository.sendAttachment("c1", "k3", AttachmentInput("f.pdf", "application/pdf", AttachmentKind.File, ByteArray(1))) }

        assertTrue(api.sent.isEmpty())
    }

    @Test
    fun markReadIgnoresFailure() = runTest {
        val failing = object : ChatApi by api {
            override suspend fun markRead(conversationId: String, messageId: String) = throw AppError.Http(500, "")
        }
        ChatRepository(failing, testChatCache()).markRead("c1", "m1") // 던지지 않는다
    }

    @Test
    fun unreadTotalFollowsConversationsAndReading() = runTest {
        api.conversationPages = { Page(listOf(Conversation("c1", "A", unreadCount = 2), Conversation("c2", "B", unreadCount = 3))) }

        repository.conversations(null)
        assertEquals(5, repository.unreadTotal.value)

        repository.markRead("c1", "m1")
        assertEquals(3, repository.unreadTotal.value)
        assertEquals(0, repository.unread.value["c1"])
    }

    @Test
    fun refreshUnreadIgnoresFailure() = runTest {
        api.conversationPages = { throw AppError.Http(500, "") }
        repository.refreshUnread() // 던지지 않는다
        assertEquals(0, repository.unreadTotal.value)
    }

    // ---------------------------------------------------------------- 로컬 DB(DEC-130)

    @Test
    fun firstPageOfConversationsIsSaved() = runTest {
        api.conversationPages = { page -> Page(listOf(Conversation("c$page", "A")), nextKey = "2") }

        repository.conversations(null)
        repository.conversations("2") // 다음 페이지는 저장하지 않는다

        assertEquals(listOf("cnull"), repository.cachedConversations().map { it.id })
    }

    @Test
    fun receivedAndSentMessagesAreSavedInOrder() = runTest {
        api.messagesResult = { _, _ -> MessagesResponse(listOf(message("m2", at = 2), message("m1", at = 1))) }
        repository.messages("c1")
        repository.received(message("m3", at = 3))
        repository.send("c1", clientId = "k1", text = "hi")

        assertEquals(listOf("m1", "m2", "m3", "server-k1"), repository.cachedMessages("c1").map { it.id })
    }

    @Test
    fun queuedTextLeavesQueueOnlyWhenSent() = runTest {
        repository.enqueue(message("k1", sender = "me", clientId = "k1"))
        api.sendError = AppError.Network(RuntimeException("offline"))
        runCatching { repository.send("c1", clientId = "k1", text = "k1") }
        assertEquals(listOf("k1"), repository.pending("c1").map { it.clientId })

        api.sendError = null
        repository.send("c1", clientId = "k1", text = "k1")
        assertTrue(repository.pending("c1").isEmpty())
    }

    @Test
    fun keepsOnlyRecentMessagesPerConversation() = runTest {
        val many = (1..ChatCacheLimit + 5).map { message("m$it", at = it.toLong()) }
        api.messagesResult = { _, _ -> MessagesResponse(many) }
        repository.messages("c1")

        val saved = repository.cachedMessages("c1")
        assertEquals(ChatCacheLimit, saved.size)
        assertEquals("m6", saved.first().id) // 오래된 5개는 지워졌다
    }

    @Test
    fun clearRemovesEverything() = runTest {
        api.conversationPages = { Page(listOf(Conversation("c1", "A"))) }
        repository.conversations(null)
        repository.received(message("m1"))
        repository.enqueue(message("k1", clientId = "k1"))

        cache.clear()

        assertTrue(repository.cachedConversations().isEmpty())
        assertTrue(repository.cachedMessages("c1").isEmpty())
        assertTrue(repository.pending("c1").isEmpty())
    }

    private companion object {
        val ChatCacheLimit = io.github.namjh654.papy.feature.chat.cache.ChatCache.KEEP_MESSAGES.toInt()
    }
}
