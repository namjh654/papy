package cloud.elsa.app.feature.chat.di

import cloud.elsa.app.core.data.config.AppConfig
import cloud.elsa.app.core.data.connectivity.NetworkMonitor
import cloud.elsa.app.core.data.database.LocalCache
import cloud.elsa.app.core.data.database.SqlDriverFactory
import cloud.elsa.app.core.data.flags.FeatureFlags
import cloud.elsa.app.core.data.flags.FeatureKey
import cloud.elsa.app.core.data.session.UserStore
import cloud.elsa.app.feature.chat.ChatApi
import cloud.elsa.app.feature.chat.ChatRepository
import cloud.elsa.app.feature.chat.ChatViewModel
import cloud.elsa.app.feature.chat.SampleChatApi
import cloud.elsa.app.feature.chat.cache.ChatCache
import cloud.elsa.app.feature.chat.db.ChatDatabase
import cloud.elsa.app.feature.chat.realtime.ChatRealtime
import cloud.elsa.app.feature.chat.realtime.PollingChatRealtime
import cloud.elsa.app.feature.chat.realtime.WebSocketChatRealtime
import cloud.elsa.app.feature.chat.room.ChatRoomViewModel
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
