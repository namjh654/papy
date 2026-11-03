package cloud.elsa.app.feature.chat

import cloud.elsa.app.core.common.paging.Page
import cloud.elsa.app.core.data.config.AppConfig
import cloud.elsa.app.core.data.network.BaseApi
import cloud.elsa.app.core.data.media.AttachmentInput
import cloud.elsa.app.core.data.media.AttachmentKind
import cloud.elsa.app.core.data.network.UploadFile
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * 대화방 한 개(목록의 한 줄).
 * Swift: `Conversation`(프로퍼티 이름 그대로), `lastMessageAt` 은 epoch 밀리초(`Int64`, 없으면 nil → `KotlinLong?`)
 */
@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val avatarUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: Long? = null,
    val unreadCount: Int = 0,
)

/** 서버에 올라간 첨부. [url] 이 비어 있으면 아직 볼 수 없는 첨부(목업 등) — 화면은 기기에 남은 미리보기나 아이콘을 쓴다 */
@Serializable
data class Attachment(
    val kind: AttachmentKind,
    val name: String,
    val url: String,
    val size: Long,
    val mimeType: String,
)

/** 답장한 원문 미리보기(22-1). [text] 가 없으면 첨부([attachmentKind]) — 화면이 "사진"·"파일"로 보인다 */
@Serializable
data class ReplyPreview(
    val messageId: String,
    val senderId: String,
    val text: String? = null,
    val attachmentKind: AttachmentKind? = null,
)

/**
 * 메시지 한 개. [clientId] 는 보낸 기기가 정한 값으로, 낙관적 전송 말풍선과 서버 응답·실시간 수신을 하나로 맞추는 데 쓴다.
 * [sentAt] 은 epoch 밀리초.
 */
@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String? = null,
    val attachment: Attachment? = null,
    val sentAt: Long,
    val clientId: String? = null,
    /** 답장이면 원문 미리보기 */
    val replyTo: ReplyPreview? = null,
)

/**
 * 메시지 한 묶음(오래된 것 → 새 것 순서). [hasOlder]: 더 위에 불러올 메시지가 있는지.
 * [peerReadAt]: 상대가 어디까지 읽었는지(epoch 밀리초) — 내 메시지 중 이 시각까지 보낸 것은 읽음.
 */
@Serializable
data class MessagesResponse(
    val items: List<Message> = emptyList(),
    val hasOlder: Boolean = false,
    val peerReadAt: Long? = null,
    /** 상대가 입력 중(폴링 수신용, 22-1) */
    val peerTyping: Boolean = false,
)

@Serializable
data class SendMessageRequest(
    val clientId: String,
    val text: String? = null,
    val attachment: Attachment? = null,
    /** 답장할 원문 id */
    val replyToId: String? = null,
)

@Serializable
internal data class SearchResponse(val items: List<Message> = emptyList())

@Serializable
internal data class ConversationPageResponse(
    val items: List<Conversation> = emptyList(),
    val nextPage: String? = null,
)

@Serializable
internal data class MarkReadRequest(val messageId: String)

@Serializable
internal data class DirectChatRequest(val userId: String)

/**
 * 메시지 API 계약(가정, DEC-125 — docs/known-limitations.md §1). 서버 연동은 [RemoteChatApi], 서버 없이 개발할 때는 [SampleChatApi].
 * 어느 쪽을 쓸지는 di/ChatModule.kt 한 곳에서 정한다.
 */
interface ChatApi {
    /** `GET /api/chats?page=N` → `{items, nextPage}`. @param page null 이면 첫 페이지 */
    suspend fun conversations(page: String?): Page<Conversation>

    /** `GET /api/chats/{id}` → [Conversation] — 링크·푸시로 대화방을 바로 열 때 이름을 알아낸다(21-3) */
    suspend fun conversation(id: String): Conversation

    /** `POST /api/chats/direct {userId}` → [Conversation] — 그 사람과의 1:1 대화(있으면 그것, 없으면 새로, 25-2) */
    suspend fun direct(userId: String): Conversation

    /**
     * `GET /api/chats/{id}/messages` → [MessagesResponse].
     * [before] 가 있으면 그 메시지보다 오래된 한 묶음(위로 불러오기), [after] 가 있으면 그 뒤에 온 것 전부(폴링 수신), 둘 다 없으면 최신 한 묶음
     */
    suspend fun messages(conversationId: String, before: String? = null, after: String? = null): MessagesResponse

    /** `POST /api/chats/{id}/messages {clientId, text?, attachment?}` → [Message]. 같은 clientId 는 서버가 한 번만 만든다 */
    suspend fun send(conversationId: String, request: SendMessageRequest): Message

    /** `POST /api/chats/{id}/attachments` (multipart `file`) → [Attachment] */
    suspend fun upload(conversationId: String, input: AttachmentInput): Attachment

    /** `POST /api/chats/{id}/read {messageId}` → 204 */
    suspend fun markRead(conversationId: String, messageId: String)

    /** `POST /api/chats/{id}/typing` → 204 — 내가 입력 중(상대 화면에 점 3개) */
    suspend fun sendTyping(conversationId: String)

    /** `GET /api/chats/{id}/messages/search?q=` → `{items}` (새 것 → 오래된 것, 최대 50) */
    suspend fun search(conversationId: String, query: String): List<Message>
}

internal class RemoteChatApi(
    private val baseApi: BaseApi,
) : ChatApi {

    override suspend fun conversations(page: String?): Page<Conversation> {
        val response: ConversationPageResponse = baseApi.get("/api/chats", query = page?.let { mapOf("page" to it) }.orEmpty())
        return Page(items = response.items, nextKey = response.nextPage)
    }

    override suspend fun conversation(id: String): Conversation = baseApi.get("/api/chats/$id")

    override suspend fun direct(userId: String): Conversation = baseApi.post("/api/chats/direct", body = DirectChatRequest(userId))

    override suspend fun messages(conversationId: String, before: String?, after: String?): MessagesResponse =
        baseApi.get(
            "/api/chats/$conversationId/messages",
            query = buildMap {
                before?.let { put("before", it) }
                after?.let { put("after", it) }
            },
        )

    override suspend fun send(conversationId: String, request: SendMessageRequest): Message =
        baseApi.post("/api/chats/$conversationId/messages", body = request)

    override suspend fun upload(conversationId: String, input: AttachmentInput): Attachment =
        baseApi.upload(
            "/api/chats/$conversationId/attachments",
            files = listOf(UploadFile("file", input.name, input.mimeType, input.bytes)),
            fields = mapOf("kind" to input.kind.serialName),
        )

    override suspend fun markRead(conversationId: String, messageId: String) {
        baseApi.post<MarkReadRequest, Unit>("/api/chats/$conversationId/read", body = MarkReadRequest(messageId))
    }

    override suspend fun sendTyping(conversationId: String) {
        baseApi.post<Unit, Unit>("/api/chats/$conversationId/typing", body = Unit)
    }

    override suspend fun search(conversationId: String, query: String): List<Message> =
        baseApi.get<SearchResponse>("/api/chats/$conversationId/messages/search", query = mapOf("q" to query)).items
}

internal val AttachmentKind.serialName: String
    get() = when (this) {
        AttachmentKind.Image -> "image"
        AttachmentKind.File -> "file"
    }

/**
 * 서버 없이 화면을 확인하기 위한 예시 데이터(서버 응답 대용이라 문구 리소스가 아니다).
 * 대화 3개, 첫 대화는 메시지 60개(30개씩 위로 불러오기). 보내면 2초 뒤 상대 답장이 온다(폴링 수신으로 보인다).
 * 첨부는 올라간 척만 하고 url 을 비워 둔다 → 화면은 기기의 미리보기를 쓴다.
 */
internal class SampleChatApi(
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ChatApi {

    private val conversations = mutableListOf(
        Conversation(id = "c1", title = "Elsa", lastMessage = "See you tomorrow!", unreadCount = 2),
        Conversation(id = "c2", title = "Design team", lastMessage = "Uploaded the new icons."),
        Conversation(id = "c3", title = "Olaf", lastMessage = "☃️"),
    )
    private val messages = mutableMapOf<String, MutableList<Message>>()
    private var peerReadAt: Long? = null
    private var sequence = 0

    private fun store(conversationId: String): MutableList<Message> = messages.getOrPut(conversationId) {
        sampleHistory(conversationId).also { history ->
            // 받아서 열어 볼 파일(30-1): 목업 서버(scripts/orca.sh mock)의 예시 PDF. 서버가 없으면 받기 실패 안내가 보인다
            if (conversationId == "c1" && AppConfig.API_BASE_URL.isNotBlank()) {
                history += Message(
                    id = "c1-file",
                    conversationId = "c1",
                    senderId = PEER,
                    attachment = Attachment(
                        kind = AttachmentKind.File,
                        name = "guide.pdf",
                        url = "${AppConfig.API_BASE_URL.trimEnd('/')}/__mock/files/guide.pdf",
                        size = SAMPLE_PDF_SIZE,
                        mimeType = "application/pdf",
                    ),
                    sentAt = now() - MINUTE / 2,
                )
            }
        }
    }

    private fun sampleHistory(conversationId: String): MutableList<Message> {
        val base = now() - HISTORY * MINUTE
        // 프로필에서 처음 연 1:1 대화(dm-)는 비어 있다
        return MutableList(if (conversationId == "c1") HISTORY else if (conversationId.startsWith("dm-")) 0 else 3) { i ->
            val mine = i % 3 == 2
            Message(
                id = "$conversationId-h$i",
                conversationId = conversationId,
                senderId = if (mine) ME else PEER,
                text = "Sample message ${i + 1}",
                // 앞쪽 절반은 하루 전 → 날짜 구분선, 1분 간격 → 같은 사람이 잇달아 보낸 것은 한 묶음
                sentAt = base - (if (i < HISTORY / 2) DAY else 0) + i * MINUTE,
            )
        }
    }

    override suspend fun conversations(page: String?): Page<Conversation> {
        val withTime = conversations.map { c -> c.copy(lastMessageAt = store(c.id).lastOrNull()?.sentAt) }
        return Page(items = withTime, nextKey = null)
    }

    override suspend fun conversation(id: String): Conversation =
        conversations.first { it.id == id }.copy(lastMessageAt = store(id).lastOrNull()?.sentAt)

    /** 커뮤니티 예시 사용자(u1 Elsa → 기존 대화 c1, 나머지는 새 대화) */
    override suspend fun direct(userId: String): Conversation {
        if (userId == "u1") return conversation("c1")
        conversations.firstOrNull { it.id == "dm-$userId" }?.let { return it }
        val created = Conversation(id = "dm-$userId", title = SAMPLE_NAMES[userId] ?: userId)
        conversations += created
        return created
    }

    override suspend fun messages(conversationId: String, before: String?, after: String?): MessagesResponse {
        val all = store(conversationId).filter { it.sentAt <= now() }
        // 아직 시각이 오지 않은 답장이 있으면 상대가 입력 중
        val typing = store(conversationId).any { it.sentAt > now() }
        if (after != null) {
            val index = all.indexOfFirst { it.id == after }
            return MessagesResponse(items = all.drop(index + 1), peerReadAt = peerReadAt, peerTyping = typing)
        }
        val end = before?.let { id -> all.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: all.size
        val start = (end - PAGE_SIZE).coerceAtLeast(0)
        return MessagesResponse(items = all.subList(start, end).toList(), hasOlder = start > 0, peerReadAt = peerReadAt)
    }

    override suspend fun send(conversationId: String, request: SendMessageRequest): Message {
        val list = store(conversationId)
        list.firstOrNull { it.clientId == request.clientId }?.let { return it }
        val sent = Message(
            id = "$conversationId-s${sequence++}",
            conversationId = conversationId,
            senderId = ME,
            text = request.text,
            attachment = request.attachment,
            sentAt = now(),
            clientId = request.clientId,
            replyTo = request.replyToId?.let { id -> list.firstOrNull { it.id == id }?.toPreview() },
        )
        list += sent
        // 상대가 읽고 답한다 — 2초 뒤 시각으로 넣어 두면 그때 폴링에 잡힌다
        val replyAt = sent.sentAt + REPLY_DELAY
        peerReadAt = replyAt
        list += Message(
            id = "$conversationId-r${sequence++}",
            conversationId = conversationId,
            senderId = PEER,
            text = if (request.attachment != null) "Got it 👍" else "Re: ${request.text.orEmpty()}",
            sentAt = replyAt,
        )
        return sent
    }

    override suspend fun upload(conversationId: String, input: AttachmentInput): Attachment =
        Attachment(kind = input.kind, name = input.name, url = "", size = input.size, mimeType = input.mimeType)

    override suspend fun markRead(conversationId: String, messageId: String) = Unit

    override suspend fun sendTyping(conversationId: String) = Unit

    override suspend fun search(conversationId: String, query: String): List<Message> =
        store(conversationId).filter { it.sentAt <= now() && it.text.orEmpty().contains(query, ignoreCase = true) }.reversed().take(50)

    companion object {
        /** 예시 데이터의 "나". 로그인한 사용자 id 가 이 값이 아니어도 예시는 그 사용자를 나로 본다(ChatModule) */
        const val ME = "me"
        private const val PEER = "peer"
        private const val HISTORY = 60
        private const val PAGE_SIZE = 30
        private const val MINUTE = 60_000L
        private const val DAY = 24 * 60 * MINUTE
        private const val REPLY_DELAY = 2_000L
        /** 목업 서버 예시 PDF 크기(대략, 말풍선 표시용) */
        private const val SAMPLE_PDF_SIZE = 1_024L
        /** 커뮤니티 예시 사용자 이름(SampleCommunityApi 와 같은 id) */
        private val SAMPLE_NAMES = mapOf("u1" to "Elsa", "u2" to "Anna", "u3" to "Olaf")
    }
}

/** 원문 → 답장 미리보기 */
fun Message.toPreview(): ReplyPreview =
    ReplyPreview(messageId = id, senderId = senderId, text = text, attachmentKind = attachment?.kind)
