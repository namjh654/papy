package cloud.elsa.app.feature.chat.room

import androidx.lifecycle.viewModelScope
import cloud.elsa.app.core.common.error.toAppError
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.common.viewmodel.UiEvent
import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.data.files.DownloadedFile
import cloud.elsa.app.core.data.files.FileDownloads
import cloud.elsa.app.core.data.media.AttachmentInput
import cloud.elsa.app.feature.chat.Attachment
import cloud.elsa.app.feature.chat.ChatRepository
import cloud.elsa.app.feature.chat.Message
import cloud.elsa.app.feature.chat.realtime.ChatRealtime
import cloud.elsa.app.feature.chat.realtime.ChatRealtimeEvent
import cloud.elsa.app.feature.chat.toPreview
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

sealed interface ChatRoomEvent : UiEvent {
    /** 내가 보냈거나 맨 아래를 보고 있을 때 새 메시지가 왔다 — 맨 아래로 */
    data object ScrollToBottom : ChatRoomEvent

    /** 검색 결과·인용을 눌렀다 — 그 말풍선으로 옮기고 잠깐 강조(22-1) */
    data class ScrollToMessage(val key: String) : ChatRoomEvent

    /** 너무 오래전 메시지라 찾지 못했다 */
    data object MessageNotFound : ChatRoomEvent

    /** 받아 둔 파일을 연다(Android FileProvider·iOS QuickLook) — 30-1 */
    data class OpenFile(val file: DownloadedFile) : ChatRoomEvent

    /** 파일을 받지 못했다 */
    data class FileFailed(val error: UiError) : ChatRoomEvent
}

/**
 * 대화방(19-1): 최신 메시지 → 위로 이전 메시지, 보내기는 낙관적(바로 말풍선 → 서버 응답으로 교체, 실패면 다시 보내기·지우기),
 * 첨부는 올린 뒤 보낸다, 실시간 수신은 [ChatRealtime](id·clientId 로 중복 제거), 읽음 표시.
 * 로컬 DB(DEC-130): 열면 저장된 최근 메시지를 먼저 보여 주고, 지난번에 못 보낸 글 메시지는 다시 보낸다.
 * 서버에서 받지 못해도(오프라인) 저장된 메시지는 그대로 두고, 다시 연결되면([online]) 실패한 메시지를 보낸다.
 *
 * 프로필의 "메시지 보내기"(25-2)는 대화 id 없이 [peerUserId] 로 열린다 → 처음 불러올 때 1:1 대화를 찾거나 만든다.
 *
 * Swift: `IOSAppGraph.shared.chatRoomViewModel(conversationId:title:)`, 프로필에서는 `directChatRoomViewModel(userId:name:)`.
 * 첨부 미리보기: [sendAttachment] 가 돌려주는 key 로 플랫폼이 기기의 사진을 들고 있다가 말풍선에 보여 준다(`ChatMessage.key` 와 같다).
 */
class ChatRoomViewModel(
    conversationId: String,
    title: String,
    private val repository: ChatRepository,
    private val realtime: ChatRealtime,
    /** 지금 사용자 id — 내 메시지 판별 */
    private val myId: () -> String?,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newClientId: () -> String = { Uuid.random().toString() },
    /** 네트워크 연결(NetworkMonitor.isOnline) — 다시 연결되면 실패한 메시지를 보낸다 */
    private val online: StateFlow<Boolean> = MutableStateFlow(true),
    /** 대화 id 없이 사람으로 열 때(프로필의 메시지 보내기) — [conversationId] 는 "" */
    private val peerUserId: String? = null,
    /** 첨부 버튼 보이기(FeatureKey.ChatAttachments) */
    attachmentsEnabled: Flow<Boolean> = flowOf(true),
    /** 파일 첨부 받기·보관(30-1). null 이면 파일을 눌러도 아무 일 없다(테스트) */
    private val downloads: FileDownloads? = null,
) : StatefulViewModel<ChatRoomUiState, ChatRoomEvent>(ChatRoomUiState(title = title)) {

    val uiState: StateFlow<ChatRoomUiState> = state
    val events: Flow<ChatRoomEvent> = eventFlow

    /** 대화 id — 사람으로 열면 처음 불러올 때 정해진다 */
    private var conversationId: String = conversationId

    /** 대화 id 가 정해지면 실시간 수신을 시작한다 */
    private val conversationReady = CompletableDeferred<Unit>().apply { if (conversationId.isNotBlank()) complete(Unit) }

    /** 오래된 것 → 새 것. 화면 줄([ChatRoomUiState.rows])은 이것에서 만든다 */
    private var messages: List<ChatMessage> = emptyList()
    private var peerReadAt: Long? = null

    /** 다시 보낼 때 쓸 원본(첨부는 다시 올린다) */
    private val pendingAttachments = mutableMapOf<String, AttachmentInput>()

    /** 입력 중 알림: 마지막으로 보낸 시각(3초에 한 번) */
    private var lastTypingSentAt = 0L
    private var typingTimeout: Job? = null
    private var searchJob: Job? = null

    init {
        load()
        launch {
            conversationReady.await()
            realtime.events(conversationId, lastMessageId = ::lastServerMessageId).collect(::onRealtime)
        }
        launch { online.drop(1).filter { it }.collect { retryFailed() } }
        launch { attachmentsEnabled.collect { enabled -> setState { copy(attachmentsEnabled = enabled) } } }
        downloads?.let { d ->
            launch { d.states.collect { states -> setState { copy(fileStates = states) } } }
            launch { d.files.collect { files -> setState { copy(downloadedUrls = files.mapTo(HashSet()) { it.url }) } } }
            launch { d.load() }
        }
    }

    /** 파일 말풍선을 눌렀다: 받아 두었으면 열고, 아니면 받은 뒤 연다. 받는 중이면 아무것도 하지 않는다 */
    fun fileClicked(message: Message) {
        val attachment = message.attachment?.takeIf { it.url.isNotBlank() } ?: return
        val d = downloads ?: return
        viewModelScope.launch {
            try {
                val file = d.download(attachment.url, attachment.name, attachment.mimeType) ?: return@launch
                sendEvent(ChatRoomEvent.OpenFile(file))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendEvent(ChatRoomEvent.FileFailed(e.toAppError().toUiError()))
            }
        }
    }

    /** 처음 불러오기(전체 오류 화면의 다시 시도도 이것) */
    fun load() = launch {
        setState { copy(isLoading = true, error = null) }
        if (!conversationReady.isCompleted) {
            val peer = peerUserId ?: return@launch
            val conversation = repository.direct(peer)
            conversationId = conversation.id
            setState { copy(title = conversation.title) }
            conversationReady.complete(Unit)
        }
        // 링크·푸시로 열리면 이름 없이 온다 → 대화 정보로 채운다
        if (currentState.title.isBlank()) {
            runCatching { repository.conversation(conversationId) }.getOrNull()?.let { setState { copy(title = it.title) } }
        }
        val showedSaved = messages.isEmpty() && showSaved()
        val response = try {
            repository.messages(conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 저장된 메시지가 있으면 그대로 둔다(오프라인 안내는 루트의 배너) — 없으면 전체 오류
            if (messages.isEmpty()) throw e
            setState { copy(isLoading = false) }
            return@launch
        }
        peerReadAt = response.peerReadAt
        val fresh = response.items.map(::toChatMessage)
        val arrived = fresh.mapNotNullTo(HashSet()) { it.message.clientId }
        // 보내는 중·실패한 말풍선은 서버 응답에 없으면 그대로 아래에 둔다
        val outgoing = messages.filter { it.sendState != SendState.Sent && it.key !in arrived }
        update(fresh + outgoing) { copy(isLoading = false, hasOlder = response.hasOlder) }
        // 저장된 메시지를 받은 목록으로 바꾸면 줄 수가 달라져 보던 자리가 위에 남을 수 있다 → 맨 아래(가장 최근)로
        if (showedSaved) trySendEvent(ChatRoomEvent.ScrollToBottom)
        markLatestRead()
    }

    /** 저장된 최근 메시지와 지난번에 못 보낸 글 메시지를 먼저 보여 주고, 못 보낸 것은 다시 보낸다. 보여 줬으면 true */
    private suspend fun showSaved(): Boolean {
        val saved = repository.cachedMessages(conversationId).map(::toChatMessage)
        val sentKeys = saved.mapNotNullTo(HashSet()) { it.message.clientId }
        val pending = repository.pending(conversationId)
            .filter { it.clientId !in sentKeys }
            .map { toChatMessage(it).copy(sendState = SendState.Sending) }
        if (saved.isEmpty() && pending.isEmpty()) return false
        update(saved + pending) { copy(isLoading = false) }
        pending.forEach { launch { deliver(it.key) } }
        return true
    }

    /** 맨 위에 닿았을 때 */
    fun loadOlder() = launch { loadOlderPage() }

    /** 한 묶음 더 위로. 불러왔으면 true */
    private suspend fun loadOlderPage(): Boolean {
        val state = currentState
        val oldest = messages.firstOrNull { it.sendState == SendState.Sent }?.message?.id ?: return false
        if (!state.hasOlder || state.isLoadingOlder || state.isLoading) return false
        setState { copy(isLoadingOlder = true) }
        return try {
            val response = repository.messages(conversationId, before = oldest)
            val known = messages.mapTo(HashSet()) { it.message.id }
            val older = response.items.filter { it.id !in known }.map(::toChatMessage)
            update(older + messages) { copy(isLoadingOlder = false, hasOlder = response.hasOlder) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 다음에 맨 위에 닿으면 다시 시도한다
            setState { copy(isLoadingOlder = false) }
            false
        }
    }

    /** 입력할 때마다. 글자가 있으면 상대에게 "입력 중"을 3초에 한 번 알린다 */
    fun inputChanged(value: String) {
        setState { copy(input = value) }
        if (value.isNotBlank() && now() - lastTypingSentAt >= TYPING_SEND_INTERVAL) {
            lastTypingSentAt = now()
            launch { repository.sendTyping(conversationId) }
        }
    }

    // ---------------------------------------------------------------- 답장(22-1)

    /** 길게 눌러 "답장"·밀어서 답장 */
    fun replyClicked(key: String) {
        val target = messages.firstOrNull { it.key == key && it.sendState == SendState.Sent } ?: return
        setState { copy(replyingTo = target) }
    }

    fun cancelReply() = setState { copy(replyingTo = null) }

    /** 인용 블록을 눌렀다 → 원문으로 */
    fun replyQuoteClicked(messageId: String) = launch { jumpTo(messageId) }

    // ---------------------------------------------------------------- 검색(22-1)

    fun openSearch() = setState { copy(isSearchOpen = true) }

    fun closeSearch() {
        searchJob?.cancel()
        setState { copy(isSearchOpen = false, searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    /** 입력이 [SEARCH_DEBOUNCE] 동안 멈추면 찾는다 */
    fun searchQueryChanged(value: String) {
        setState { copy(searchQuery = value) }
        searchJob?.cancel()
        if (value.isBlank()) {
            setState { copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            setState { copy(isSearching = true) }
            val results = runCatching { repository.search(conversationId, value.trim()) }.getOrDefault(emptyList())
            setState { copy(searchResults = results, isSearching = false) }
        }
    }

    fun searchResultClicked(message: Message) = launch {
        closeSearch()
        jumpTo(message.id)
    }

    /** 그 메시지가 아직 안 불러온 위쪽이면 [MAX_JUMP_PAGES] 묶음까지 더 불러와 찾는다 */
    private suspend fun jumpTo(messageId: String) {
        var pages = 0
        while (messages.none { it.message.id == messageId } && pages < MAX_JUMP_PAGES && loadOlderPage()) pages++
        val target = messages.firstOrNull { it.message.id == messageId }
        sendEvent(if (target != null) ChatRoomEvent.ScrollToMessage(target.key) else ChatRoomEvent.MessageNotFound)
    }

    fun send() {
        val text = currentState.input.trim()
        if (text.isEmpty() || !conversationReady.isCompleted) return
        val clientId = newClientId()
        setState { copy(input = "") }
        val message = addOutgoing(clientId, text = text, attachment = null)
        launch {
            // 앱을 꺼도 남게 대기열에 넣고 보낸다(서버에 가면 저장소가 뺀다)
            repository.enqueue(message)
            deliver(clientId)
        }
    }

    /**
     * 사진·카메라·파일을 보낸다. 올리는 동안은 보내는 중 말풍선(서버 url 없음)이 보인다.
     * @return 말풍선 key — 플랫폼은 이 key 로 기기 미리보기를 들고 있는다
     */
    fun sendAttachment(input: AttachmentInput): String {
        val clientId = newClientId()
        if (!conversationReady.isCompleted) return clientId
        pendingAttachments[clientId] = input
        val placeholder = Attachment(kind = input.kind, name = input.name, url = "", size = input.size, mimeType = input.mimeType)
        addOutgoing(clientId, text = null, attachment = placeholder)
        launch { deliver(clientId) }
        return clientId
    }

    /** 실패한 말풍선 → 다시 보내기 */
    fun retry(key: String) {
        val target = messages.firstOrNull { it.key == key && it.sendState == SendState.Failed } ?: return
        replace(key) { it.copy(sendState = SendState.Sending) }
        launch { deliver(target.key) }
    }

    /** 실패한 말풍선 → 지우기 */
    fun deleteFailed(key: String) {
        pendingAttachments.remove(key)
        update(messages.filterNot { it.key == key && it.sendState == SendState.Failed })
        launch { repository.discard(key) }
    }

    /** 다시 연결됐다 → 실패한 말풍선을 모두 다시 보낸다(첨부는 이번 실행에서 고른 것만 남아 있다) */
    private fun retryFailed() {
        messages.filter { it.sendState == SendState.Failed && (it.message.attachment == null || it.key in pendingAttachments) }
            .forEach { retry(it.key) }
    }

    private fun addOutgoing(clientId: String, text: String?, attachment: Attachment?): Message {
        val reply = currentState.replyingTo
        val message = Message(
            id = clientId,
            conversationId = conversationId,
            senderId = myId().orEmpty(),
            text = text,
            attachment = attachment,
            sentAt = now(),
            clientId = clientId,
            replyTo = reply?.message?.toPreview(),
        )
        setState { copy(replyingTo = null) }
        update(messages + toChatMessage(message).copy(sendState = SendState.Sending))
        trySendEvent(ChatRoomEvent.ScrollToBottom)
        return message
    }

    private suspend fun deliver(clientId: String) {
        val outgoing = messages.firstOrNull { it.key == clientId } ?: return
        try {
            val input = pendingAttachments[clientId]
            val replyToId = outgoing.message.replyTo?.messageId
            val sent = if (input != null) {
                repository.sendAttachment(conversationId, clientId, input, replyToId)
            } else {
                repository.send(conversationId, clientId, outgoing.message.text.orEmpty(), replyToId)
            }
            pendingAttachments.remove(clientId)
            merge(sent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            replace(clientId) { it.copy(sendState = SendState.Failed) }
        }
    }

    private suspend fun onRealtime(event: ChatRealtimeEvent) {
        when (event) {
            is ChatRealtimeEvent.Received -> {
                if (event.message.conversationId != conversationId) return
                repository.received(event.message)
                val isNew = merge(event.message)
                if (!isMine(event.message)) stopPeerTyping()
                if (isNew && !isMine(event.message)) {
                    sendEvent(ChatRoomEvent.ScrollToBottom)
                    repository.markRead(conversationId, event.message.id)
                }
            }
            is ChatRealtimeEvent.PeerRead -> {
                peerReadAt = maxOf(peerReadAt ?: 0, event.readAt)
                update(messages.map(::withRead))
            }
            ChatRealtimeEvent.PeerTyping -> {
                setState { copy(isPeerTyping = true) }
                typingTimeout?.cancel()
                typingTimeout = viewModelScope.launch {
                    delay(TYPING_VISIBLE)
                    setState { copy(isPeerTyping = false) }
                }
            }
        }
    }

    private fun stopPeerTyping() {
        typingTimeout?.cancel()
        if (currentState.isPeerTyping) setState { copy(isPeerTyping = false) }
    }

    /**
     * 서버 메시지를 목록에 넣는다: 같은 clientId(내가 보낸 것) 나 같은 id 가 있으면 그 자리를 바꾸고, 없으면 시간 순서 자리에 넣는다.
     * @return 새로 들어간 메시지면 true
     */
    private fun merge(message: Message): Boolean {
        val index = messages.indexOfFirst {
            it.message.id == message.id || (message.clientId != null && it.message.clientId == message.clientId)
        }
        // 서버가 인용 미리보기를 빼고 돌려줘도 보낼 때 만든 것을 지킨다
        val keepReply = if (index >= 0 && message.replyTo == null) messages[index].message.replyTo else null
        val item = toChatMessage(if (keepReply != null) message.copy(replyTo = keepReply) else message)
        if (index >= 0) {
            update(messages.toMutableList().also { it[index] = item })
            return false
        }
        val insertAt = messages.indexOfLast { it.message.sentAt <= message.sentAt } + 1
        update(messages.toMutableList().also { it.add(insertAt, item) })
        return true
    }

    private fun replace(key: String, transform: (ChatMessage) -> ChatMessage) =
        update(messages.map { if (it.key == key) transform(it) else it })

    private fun update(next: List<ChatMessage>, reduce: ChatRoomUiState.() -> ChatRoomUiState = { this }) {
        messages = next
        val rows = buildRows(next, timeZone)
        setState { reduce().copy(rows = rows) }
    }

    private suspend fun markLatestRead() {
        messages.lastOrNull { !it.isMine && it.sendState == SendState.Sent }?.let {
            repository.markRead(conversationId, it.message.id)
        }
    }

    private fun lastServerMessageId(): String? = messages.lastOrNull { it.sendState == SendState.Sent }?.message?.id

    private fun isMine(message: Message): Boolean = message.senderId == myId()

    private fun toChatMessage(message: Message): ChatMessage = withRead(
        ChatMessage(message, isMine = isMine(message), replyIsMine = message.replyTo?.senderId?.let { it == myId() } ?: false),
    )

    private fun withRead(item: ChatMessage): ChatMessage {
        val readAt = peerReadAt ?: return item
        return if (item.isMine && item.sendState == SendState.Sent) item.copy(isRead = item.message.sentAt <= readAt) else item
    }

    private companion object {
        /** 상대 입력 중 표시를 유지하는 시간 */
        const val TYPING_VISIBLE = 5_000L
        /** 내 입력 중 알림 간격 */
        const val TYPING_SEND_INTERVAL = 3_000L
        const val SEARCH_DEBOUNCE = 300L
        /** 검색 결과로 갈 때 위로 더 불러오는 최대 묶음 수 */
        const val MAX_JUMP_PAGES = 10
    }

    /** 첫 불러오기 실패만 화면 전체 오류로 */
    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
