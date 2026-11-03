package io.github.namjh654.papy.feature.chat.realtime

import co.touchlab.kermit.Logger
import io.github.namjh654.papy.core.data.network.AccessTokenProvider
import io.github.namjh654.papy.core.data.serialization.AppJson
import io.github.namjh654.papy.feature.chat.ChatApi
import io.github.namjh654.papy.feature.chat.Message
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

private val log = Logger.withTag("Chat")

/** 대화방에 실시간으로 들어오는 것 */
sealed interface ChatRealtimeEvent {
    data class Received(val message: Message) : ChatRealtimeEvent

    /** 상대가 [readAt](epoch 밀리초)까지 읽었다 */
    data class PeerRead(val readAt: Long) : ChatRealtimeEvent

    /** 상대가 입력 중(22-1) — 화면은 몇 초 뒤 스스로 지운다 */
    data object PeerTyping : ChatRealtimeEvent
}

/**
 * 실시간 수신(DEC-126). env `chat.websocketUrl` 이 있으면 [WebSocketChatRealtime], 없으면 [PollingChatRealtime] (di/ChatModule.kt).
 * 대화방 화면이 보이는 동안만 [events] 를 모은다 — 끊기면 알아서 다시 붙는다.
 */
interface ChatRealtime {
    /** @param lastMessageId 지금 화면에 있는 마지막(서버) 메시지 id — 폴링은 그 뒤만 받는다 */
    fun events(conversationId: String, lastMessageId: () -> String?): Flow<ChatRealtimeEvent>
}

/** 서버가 실시간을 주지 않을 때: [intervalMillis] 마다 마지막 메시지 뒤를 묻는다. 실패하면 다음 차례에 다시 */
class PollingChatRealtime(
    private val api: ChatApi,
    private val intervalMillis: Long = 3_000,
) : ChatRealtime {

    override fun events(conversationId: String, lastMessageId: () -> String?): Flow<ChatRealtimeEvent> = flow {
        while (true) {
            delay(intervalMillis)
            val after = lastMessageId() ?: continue
            val response = try {
                api.messages(conversationId, after = after)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.d(e) { "폴링 실패, 다음 차례에 다시" }
                continue
            }
            response.items.forEach { emit(ChatRealtimeEvent.Received(it)) }
            response.peerReadAt?.let { emit(ChatRealtimeEvent.PeerRead(it)) }
            if (response.peerTyping) emit(ChatRealtimeEvent.PeerTyping)
        }
    }
}

/**
 * WebSocket 수신(가정 계약 — known-limitations §1): `{chat.websocketUrl}?conversationId=…` 에 토큰을 붙여 연결하고
 * 글자 프레임 `{"type":"message","message":{…}}` · `{"type":"read","readAt":…}` · `{"type":"typing"}` 을 받는다.
 * 끊기면 1초부터 두 배씩 최대 30초 기다렸다 다시 붙는다(붙는 데 성공하면 1초로 돌아간다).
 */
class WebSocketChatRealtime(
    httpClient: HttpClient,
    private val url: String,
    private val tokenProvider: AccessTokenProvider,
) : ChatRealtime {

    private val client = httpClient.config { install(WebSockets) }

    override fun events(conversationId: String, lastMessageId: () -> String?): Flow<ChatRealtimeEvent> = flow {
        var backoff = MIN_BACKOFF
        while (true) {
            try {
                val token = tokenProvider.accessToken()
                client.webSocket(urlString = url, request = {
                    parameter("conversationId", conversationId)
                    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }) {
                    backoff = MIN_BACKOFF
                    for (frame in incoming) {
                        if (frame is Frame.Text) parseRealtimeFrame(frame.readText())?.let { emit(it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.d(e) { "WebSocket 끊김, ${backoff}ms 뒤 다시" }
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
        }
    }

    private companion object {
        const val MIN_BACKOFF = 1_000L
        const val MAX_BACKOFF = 30_000L
    }
}

@Serializable
private data class RealtimeFrame(
    val type: String,
    val message: Message? = null,
    val readAt: Long? = null,
)

/** 서버 프레임 하나 → 이벤트. 모르는 종류·깨진 프레임은 버린다 */
internal fun parseRealtimeFrame(text: String): ChatRealtimeEvent? {
    val frame = runCatching { AppJson.decodeFromString<RealtimeFrame>(text) }.getOrNull() ?: return null
    return when (frame.type) {
        "message" -> frame.message?.let { ChatRealtimeEvent.Received(it) }
        "read" -> frame.readAt?.let { ChatRealtimeEvent.PeerRead(it) }
        "typing" -> ChatRealtimeEvent.PeerTyping
        else -> null
    }
}
