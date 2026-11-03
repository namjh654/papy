package cloud.elsa.app.feature.chat.cache

import cloud.elsa.app.core.data.database.LocalCache
import cloud.elsa.app.core.data.database.databaseDispatcher
import cloud.elsa.app.core.data.serialization.AppJson
import cloud.elsa.app.feature.chat.Conversation
import cloud.elsa.app.feature.chat.Message
import cloud.elsa.app.feature.chat.db.ChatDatabase
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * 메시지 로컬 DB(SQLDelight `Chat.sq`, DEC-130): 대화 목록 첫 페이지, 대화방별 최근 메시지, 보내지 못한 글 메시지(대기열).
 * [ChatRepository][cloud.elsa.app.feature.chat.ChatRepository] 만 쓴다. 로그아웃하면 [clear].
 */
class ChatCache(
    private val database: ChatDatabase,
    /** DB 를 읽고 쓰는 곳. 테스트는 EmptyCoroutineContext(부른 코루틴에서 그대로 — 테스트 스케줄러 순서 유지) */
    private val context: CoroutineContext = databaseDispatcher,
) : LocalCache {

    private val queries get() = database.chatQueries

    suspend fun conversations(): List<Conversation> = read { queries.conversations().executeAsList().map(AppJson::decodeFromString) }

    /** 첫 페이지를 통째로 바꾼다 */
    suspend fun saveConversations(items: List<Conversation>) = write {
        queries.deleteConversations()
        items.forEachIndexed { index, item -> queries.insertConversation(item.id, index.toLong(), AppJson.encodeToString(item)) }
    }

    /** 오래된 것 → 새 것 */
    suspend fun messages(conversationId: String): List<Message> =
        read { queries.latestMessages(conversationId, KEEP_MESSAGES).executeAsList().map(AppJson::decodeFromString) }

    /** 같은 id 는 바꾸고, 대화방마다 최근 [KEEP_MESSAGES] 개만 남긴다 */
    suspend fun saveMessages(conversationId: String, items: List<Message>) {
        if (items.isEmpty()) return
        write {
            items.forEach { queries.upsertMessage(it.id, conversationId, it.sentAt, AppJson.encodeToString(it)) }
            queries.trimMessages(conversationId, KEEP_MESSAGES)
        }
    }

    /** 보내지 못한 글 메시지(보낸 순서) */
    suspend fun pending(conversationId: String): List<Message> =
        read { queries.outbox(conversationId).executeAsList().map(AppJson::decodeFromString) }

    suspend fun addPending(message: Message) = write {
        queries.insertOutbox(message.clientId ?: message.id, message.conversationId, message.sentAt, AppJson.encodeToString(message))
    }

    suspend fun removePending(clientId: String) = write { queries.deleteOutbox(clientId) }

    override suspend fun clear() = write {
        queries.deleteConversations()
        queries.deleteMessages()
        queries.deleteAllOutbox()
    }

    private suspend fun <T> read(block: () -> T): T = withContext(context) { block() }

    private suspend fun write(block: () -> Unit) = withContext(context) { database.transaction { block() } }

    companion object {
        /** 대화방마다 남기는 최근 메시지 수 */
        const val KEEP_MESSAGES = 200L
    }
}
