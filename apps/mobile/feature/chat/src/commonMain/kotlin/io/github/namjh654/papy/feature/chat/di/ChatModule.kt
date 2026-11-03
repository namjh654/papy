package io.github.namjh654.papy.feature.chat.di

import io.github.namjh654.papy.core.data.config.AppConfig
import io.github.namjh654.papy.core.data.connectivity.NetworkMonitor
import io.github.namjh654.papy.core.data.database.LocalCache
import io.github.namjh654.papy.core.data.database.SqlDriverFactory
import io.github.namjh654.papy.core.data.flags.FeatureFlags
import io.github.namjh654.papy.core.data.flags.FeatureKey
import io.github.namjh654.papy.core.data.session.UserStore
import io.github.namjh654.papy.feature.chat.ChatApi
import io.github.namjh654.papy.feature.chat.ChatRepository
import io.github.namjh654.papy.feature.chat.ChatViewModel
import io.github.namjh654.papy.feature.chat.SampleChatApi
import io.github.namjh654.papy.feature.chat.cache.ChatCache
import io.github.namjh654.papy.feature.chat.db.ChatDatabase
import io.github.namjh654.papy.feature.chat.realtime.ChatRealtime
import io.github.namjh654.papy.feature.chat.realtime.PollingChatRealtime
import io.github.namjh654.papy.feature.chat.realtime.WebSocketChatRealtime
import io.github.namjh654.papy.feature.chat.room.ChatRoomViewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** feature:chat 의 Koin 모듈 */
val chatModule = module {
    // 서버가 준비되면 이 줄과 아래 myId 를 바꾼다: single<ChatApi> { RemoteChatApi(baseApi = get()) }
    single<ChatApi> { SampleChatApi() }

    // 로컬 DB(DEC-130): 로그아웃하면 LocalCache 로 지워진다
    single { ChatDatabase(get<SqlDriverFactory>().create(ChatDatabase.Schema, "chat")) }
    single { ChatCache(database = get()) } bind LocalCache::class
    single { ChatRepository(api = get(), cache = get()) }
    // 실시간(DEC-126): env chat.websocketUrl 이 있으면 WebSocket, 없으면 폴링
    single<ChatRealtime> {
        val url = AppConfig.CHAT_WEBSOCKET_URL
        if (url.isNotBlank()) WebSocketChatRealtime(httpClient = get(), url = url, tokenProvider = get()) else PollingChatRealtime(api = get())
    }
    factory { ChatViewModel(repository = get()) }
    // 대화방: (대화 id, 이름) — 프로필에서 사람으로 열면 ("", 이름, 사용자 id)
    factory { params ->
        val userStore = get<UserStore>()
        ChatRoomViewModel(
            conversationId = params.get(0),
            title = params.get(1),
            peerUserId = params.values.getOrNull(2) as? String,
            repository = get(),
            realtime = get(),
            // 예시 데이터는 "나"가 고정 id — 서버 연동 시 { userStore.user.value?.id }
            myId = { if (get<ChatApi>() is SampleChatApi) SampleChatApi.ME else userStore.user.value?.id },
            online = get<NetworkMonitor>().isOnline,
            attachmentsEnabled = get<FeatureFlags>().observe(FeatureKey.ChatAttachments),
            downloads = get(),
        )
    }
}
