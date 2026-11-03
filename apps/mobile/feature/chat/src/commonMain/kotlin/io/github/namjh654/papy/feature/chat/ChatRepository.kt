package io.github.namjh654.papy.feature.chat

import io.github.namjh654.papy.core.common.paging.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.namjh654.papy.core.data.media.AttachmentInput
import io.github.namjh654.papy.feature.chat.cache.ChatCache

/**
 * 메시지 데이터. 서버가 원본이고 로컬 DB([ChatCache], DEC-130)는 다시 켰을 때·오프라인일 때 바로 보일 것을 둔다:
 * 받은 대화 목록 첫 페이지·메시지는 저장하고, 글 메시지는 서버에 갈 때까지 대기열에 남긴다(앱을 꺼도 남는다).
 */
class ChatRepository(
    private val api: ChatApi,
    private val cache: ChatCache,
) {
    private val _unread = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** 대화방별 안 읽은 수(목록을 불러올 때 채우고, 방을 읽으면 0) */
    val unread: StateFlow<Map<String, Int>> = _unread.asStateFlow()

    private val _unreadTotal = MutableStateFlow(0)
    /** 메시지 탭 배지(21-4). Swift: `IOSAppGraph.shared.chatUnreadTotal()` */
    val unreadTotal: StateFlow<Int> = _unreadTotal.asStateFlow()

    suspend fun conversations(page: String?): Page<Conversation> {
        val result = api.conversations(page)
        // 첫 페이지면 새로, 다음 페이지면 더한다
        val base = if (page == null) emptyMap() else _unread.value
        setUnread(base + result.items.associate { it.id to it.unreadCount })
        if (page == null) saving { cache.saveConversations(result.items) }
        return result
    }

    /** 저장해 둔 대화 목록 첫 페이지(목록을 받기 전에 보인다) */
    suspend fun cachedConversations(): List<Conversation> = reading { cache.conversations() }

    suspend fun conversation(id: String): Conversation = api.conversation(id)

    /** 프로필의 "메시지 보내기": 그 사람과의 1:1 대화(있으면 그것, 없으면 서버가 만든다) */
    suspend fun direct(userId: String): Conversation = api.direct(userId)

    /** 메인에 들어올 때 한 번: 메시지 탭을 열기 전에도 배지가 보이게. 실패해도 조용히(다음에 목록을 열면 채워진다) */
    suspend fun refreshUnread() {
        runCatching { conversations(page = null) }
    }

    /** 최신 한 묶음, [before] 가 있으면 그보다 오래된 한 묶음. 받은 것은 저장한다 */
    suspend fun messages(conversationId: String, before: String? = null): MessagesResponse {
        val response = api.messages(conversationId, before = before)
        saving { cache.saveMessages(conversationId, response.items) }
        return response
    }

    /** 저장해 둔 최근 메시지(오래된 것 → 새 것) — 대화방을 열면 서버 응답 전에 보인다 */
    suspend fun cachedMessages(conversationId: String): List<Message> = reading { cache.messages(conversationId) }

    /** 실시간으로 받은 메시지도 저장한다(다시 열었을 때 보이게) */
    suspend fun received(message: Message) = saving { cache.saveMessages(message.conversationId, listOf(message)) }

    /** 보내기 전에 대기열에 넣는다(글 메시지만 — 첨부 원본은 저장하지 않는다) */
    suspend fun enqueue(message: Message) = saving { cache.addPending(message) }

    /** 지난번에 보내지 못한 글 메시지(보낸 순서) */
    suspend fun pending(conversationId: String): List<Message> = reading { cache.pending(conversationId) }

    /** 실패한 말풍선을 지웠다 → 대기열에서도 */
    suspend fun discard(clientId: String) = saving { cache.removePending(clientId) }

    /** 서버에 갔으면 대기열에서 빼고 메시지로 저장한다 */
    suspend fun send(conversationId: String, clientId: String, text: String, replyToId: String? = null): Message {
        val sent = api.send(conversationId, SendMessageRequest(clientId = clientId, text = text, replyToId = replyToId))
        saving {
            cache.removePending(clientId)
            cache.saveMessages(conversationId, listOf(sent))
        }
        return sent
    }

    /** 첨부: 올린 뒤 그 첨부를 담은 메시지를 보낸다. 다시 보내면 처음(업로드)부터 */
    suspend fun sendAttachment(conversationId: String, clientId: String, input: AttachmentInput, replyToId: String? = null): Message {
        val attachment = api.upload(conversationId, input)
        val sent = api.send(conversationId, SendMessageRequest(clientId = clientId, attachment = attachment, replyToId = replyToId))
        saving { cache.saveMessages(conversationId, listOf(sent)) }
        return sent
    }

    /** 입력 중 알림은 실패해도 조용히 */
    suspend fun sendTyping(conversationId: String) {
        runCatching { api.sendTyping(conversationId) }
    }

    suspend fun search(conversationId: String, query: String): List<Message> = api.search(conversationId, query)

    /** 읽음 표시는 실패해도 화면에 알리지 않는다(다음 메시지 때 다시 보낸다) */
    suspend fun markRead(conversationId: String, messageId: String) {
        if ((_unread.value[conversationId] ?: 0) > 0) setUnread(_unread.value + (conversationId to 0))
        runCatching { api.markRead(conversationId, messageId) }
    }

    /** 로컬 DB 는 보조: 읽지 못하면 없는 것으로, 쓰지 못하면 넘어간다(서버 흐름을 막지 않는다) */
    private suspend fun <T> reading(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        emptyList()
    }

    private suspend fun saving(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    private fun setUnread(map: Map<String, Int>) {
        _unread.value = map
        _unreadTotal.value = map.values.sum()
    }
}
