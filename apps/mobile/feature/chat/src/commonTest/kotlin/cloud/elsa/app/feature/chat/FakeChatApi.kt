package cloud.elsa.app.feature.chat

import cloud.elsa.app.core.common.paging.Page
import cloud.elsa.app.core.data.media.AttachmentInput

internal fun message(id: String, sender: String = "peer", at: Long = 0, text: String = id, clientId: String? = null) =
    Message(id = id, conversationId = "c1", senderId = sender, text = text, sentAt = at, clientId = clientId)

/** 호출을 기록하고 정해 둔 응답을 주는 대역 */
internal class FakeChatApi : ChatApi {
    var conversationPages: (String?) -> Page<Conversation> = { Page(emptyList()) }
    var messagesResult: (before: String?, after: String?) -> MessagesResponse = { _, _ -> MessagesResponse() }
    var sendError: Throwable? = null
    var uploadError: Throwable? = null
    var sentAt: Long = 1_000

    val sent = mutableListOf<SendMessageRequest>()
    val uploaded = mutableListOf<AttachmentInput>()
    val read = mutableListOf<String>()

    override suspend fun conversations(page: String?) = conversationPages(page)

    var conversationResult: (String) -> Conversation = { Conversation(id = it, title = "Room $it") }
    override suspend fun conversation(id: String) = conversationResult(id)

    var directResult: (String) -> Conversation = { Conversation(id = "dm-$it", title = "User $it") }
    val directRequests = mutableListOf<String>()
    override suspend fun direct(userId: String): Conversation { directRequests += userId; return directResult(userId) }

    override suspend fun messages(conversationId: String, before: String?, after: String?) = messagesResult(before, after)

    override suspend fun send(conversationId: String, request: SendMessageRequest): Message {
        sendError?.let { throw it }
        sent += request
        return Message(
            id = "server-${request.clientId}",
            conversationId = conversationId,
            senderId = "me",
            text = request.text,
            attachment = request.attachment,
            sentAt = sentAt,
            clientId = request.clientId,
        )
    }

    override suspend fun upload(conversationId: String, input: AttachmentInput): Attachment {
        uploadError?.let { throw it }
        uploaded += input
        return Attachment(kind = input.kind, name = input.name, url = "https://cdn/${input.name}", size = input.size, mimeType = input.mimeType)
    }

    override suspend fun markRead(conversationId: String, messageId: String) {
        read += messageId
    }

    var typingSent = 0
    var searchResult: (String) -> List<Message> = { emptyList() }
    override suspend fun sendTyping(conversationId: String) { typingSent++ }
    override suspend fun search(conversationId: String, query: String) = searchResult(query)
}
