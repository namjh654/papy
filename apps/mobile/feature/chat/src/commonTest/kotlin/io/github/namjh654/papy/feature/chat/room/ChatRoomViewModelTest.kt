package io.github.namjh654.papy.feature.chat.room

import io.github.namjh654.papy.feature.chat.Conversation
import app.cash.turbine.test
import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.data.media.AttachmentInput
import io.github.namjh654.papy.core.data.media.AttachmentKind
import io.github.namjh654.papy.feature.chat.ChatRepository
import io.github.namjh654.papy.feature.chat.FakeChatApi
import io.github.namjh654.papy.feature.chat.MessagesResponse
import io.github.namjh654.papy.feature.chat.message
import io.github.namjh654.papy.feature.chat.testChatCache
import io.github.namjh654.papy.feature.chat.realtime.ChatRealtime
import io.github.namjh654.papy.feature.chat.realtime.ChatRealtimeEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomViewModelTest {

    private val api = FakeChatApi()
    private val incoming = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 8)
    private var ids = 0
    /** 같은 테스트 안의 ViewModel 들이 같은 로컬 DB 를 쓴다(앱을 다시 연 것처럼) */
    private val cache = testChatCache()

    private fun viewModel(
        online: StateFlow<Boolean> = MutableStateFlow(true),
        conversationId: String = "c1",
        peerUserId: String? = null,
    ) = ChatRoomViewModel(
        conversationId = conversationId,
        title = "Papy",
        repository = ChatRepository(api, cache),
        realtime = object : ChatRealtime {
            override fun events(conversationId: String, lastMessageId: () -> String?) = incoming
        },
        myId = { "me" },
        timeZone = TimeZone.UTC,
        now = { 500 },
        newClientId = { "k${++ids}" },
        online = online,
        peerUserId = peerUserId,
    )

    private val ChatRoomViewModel.bubbles get() = uiState.value.rows.filterIsInstance<ChatRow.Bubble>().map { it.item }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsLatestAndMarksPeerMessageRead() = runTest {
        api.messagesResult = { _, _ -> MessagesResponse(listOf(message("m1", "peer", 1), message("m2", "me", 2)), hasOlder = true, peerReadAt = 2) }

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.hasOlder)
        assertEquals(listOf("m1", "m2"), vm.bubbles.map { it.message.id })
        assertTrue(vm.bubbles[1].isRead) // 상대가 2 까지 읽음
        assertEquals(listOf("m1"), api.read)
    }

    @Test
    fun firstLoadFailureShowsError() = runTest {
        api.messagesResult = { _, _ -> throw AppError.Http(500, "") }

        val vm = viewModel()

        assertEquals(ErrorCode.ServerError, vm.uiState.value.error?.code)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun sendIsOptimisticThenReplacedByServerMessage() = runTest {
        val vm = viewModel()
        vm.inputChanged("  hello ")

        vm.events.test {
            vm.send()
            assertEquals(ChatRoomEvent.ScrollToBottom, awaitItem())
        }

        assertEquals("", vm.uiState.value.input)
        val bubble = vm.bubbles.single()
        assertEquals("k1", bubble.key) // 서버 메시지로 바뀌어도 key 는 그대로
        assertEquals("server-k1", bubble.message.id)
        assertEquals(SendState.Sent, bubble.sendState)
        assertEquals("hello", api.sent.single().text)
    }

    @Test
    fun failedSendCanBeRetriedOrDeleted() = runTest {
        val vm = viewModel()
        api.sendError = AppError.Http(500, "")
        vm.inputChanged("a")
        vm.send()
        assertEquals(SendState.Failed, vm.bubbles.single().sendState)

        api.sendError = null
        vm.retry("k1")
        assertEquals(SendState.Sent, vm.bubbles.single().sendState)

        api.sendError = AppError.Http(500, "")
        vm.inputChanged("b")
        vm.send()
        vm.deleteFailed("k2")
        assertEquals(listOf("k1"), vm.bubbles.map { it.key })
    }

    @Test
    fun attachmentUploadsThenSendsAndKeepsKey() = runTest {
        val vm = viewModel()

        val key = vm.sendAttachment(AttachmentInput("p.jpg", "image/jpeg", AttachmentKind.Image, ByteArray(2)))

        val bubble = vm.bubbles.single()
        assertEquals(key, bubble.key)
        assertEquals("https://cdn/p.jpg", bubble.message.attachment?.url)
        assertEquals(1, api.uploaded.size)
    }

    @Test
    fun failedAttachmentRetryUploadsAgain() = runTest {
        val vm = viewModel()
        api.uploadError = AppError.Http(500, "")
        val key = vm.sendAttachment(AttachmentInput("f.pdf", "application/pdf", AttachmentKind.File, ByteArray(1)))
        assertEquals(SendState.Failed, vm.bubbles.single().sendState)

        api.uploadError = null
        vm.retry(key)

        assertEquals(SendState.Sent, vm.bubbles.single().sendState)
        assertEquals(1, api.uploaded.size)
    }

    @Test
    fun realtimeDeduplicatesByIdAndClientId() = runTest {
        api.messagesResult = { _, _ -> MessagesResponse(listOf(message("m1", "peer", 1))) }
        val vm = viewModel()
        vm.inputChanged("mine")
        vm.send()

        incoming.emit(ChatRealtimeEvent.Received(message("m1", "peer", 1))) // 이미 있음
        incoming.emit(ChatRealtimeEvent.Received(message("server-k1", "me", 1_000, text = "mine", clientId = "k1"))) // 내 것의 메아리
        incoming.emit(ChatRealtimeEvent.Received(message("m3", "peer", 2_000)))

        assertEquals(listOf("m1", "k1", "m3"), vm.bubbles.map { it.key })
        assertEquals(listOf("m1", "m3"), api.read) // 새로 받은 상대 메시지만 읽음
    }

    @Test
    fun peerReadMarksMyMessages() = runTest {
        api.messagesResult = { _, _ -> MessagesResponse(listOf(message("m1", "me", 1), message("m2", "me", 5))) }
        val vm = viewModel()

        incoming.emit(ChatRealtimeEvent.PeerRead(1))

        assertEquals(listOf(true, false), vm.bubbles.map { it.isRead })
    }

    @Test
    fun loadOlderPrependsAndStopsAtTop() = runTest {
        api.messagesResult = { before, _ ->
            if (before == null) {
                MessagesResponse(listOf(message("m3", at = 3)), hasOlder = true)
            } else {
                MessagesResponse(listOf(message("m1", at = 1), message("m2", at = 2)), hasOlder = false)
            }
        }
        val vm = viewModel()

        vm.loadOlder()

        assertEquals(listOf("m1", "m2", "m3"), vm.bubbles.map { it.key })
        assertFalse(vm.uiState.value.hasOlder)
        assertFalse(vm.uiState.value.isLoadingOlder)
    }

    @Test
    fun titleIsLoadedWhenOpenedFromALink() = runTest {
        api.conversationResult = { Conversation(id = it, title = "Loaded") }
        val vm = ChatRoomViewModel(
            conversationId = "c1",
            title = "",
            repository = ChatRepository(api, testChatCache()),
            realtime = object : ChatRealtime {
                override fun events(conversationId: String, lastMessageId: () -> String?) = incoming
            },
            myId = { "me" },
        )
        assertEquals("Loaded", vm.uiState.value.title)
    }

    @Test
    fun replySendsQuoteAndClearsBar() = runTest {
        api.messagesResult = { _, _ -> MessagesResponse(listOf(message("m1", "peer", 1, text = "question"))) }
        val vm = viewModel()

        vm.replyClicked("m1")
        assertEquals("m1", vm.uiState.value.replyingTo?.key)
        vm.inputChanged("answer")
        vm.send()

        assertEquals(null, vm.uiState.value.replyingTo)
        assertEquals("m1", api.sent.single().replyToId)
        val mine = vm.bubbles.last()
        assertEquals("question", mine.message.replyTo?.text)
        assertFalse(mine.replyIsMine)
    }

    @Test
    fun peerTypingShowsThenHidesAfterTimeoutOrMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel()

        incoming.emit(ChatRealtimeEvent.PeerTyping)
        assertTrue(vm.uiState.value.isPeerTyping)
        advanceTimeBy(5_001)
        assertFalse(vm.uiState.value.isPeerTyping)

        incoming.emit(ChatRealtimeEvent.PeerTyping)
        incoming.emit(ChatRealtimeEvent.Received(message("m9", "peer", 9)))
        assertFalse(vm.uiState.value.isPeerTyping)
    }

    @Test
    fun typingIsSentAtMostEveryThreeSeconds() = runTest {
        var clock = 10_000L
        val vm = ChatRoomViewModel(
            conversationId = "c1", title = "Papy", repository = ChatRepository(api, testChatCache()),
            realtime = object : ChatRealtime {
                override fun events(conversationId: String, lastMessageId: () -> String?) = incoming
            },
            myId = { "me" }, timeZone = TimeZone.UTC, now = { clock },
        )
        vm.inputChanged("h")
        vm.inputChanged("he")
        clock += 3_000
        vm.inputChanged("hel")
        vm.inputChanged("")

        assertEquals(2, api.typingSent)
    }

    @Test
    fun searchDebouncesAndJumpsLoadingOlderPages() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        api.messagesResult = { before, _ ->
            when (before) {
                null -> MessagesResponse(listOf(message("m3", at = 3)), hasOlder = true)
                "m3" -> MessagesResponse(listOf(message("m2", at = 2)), hasOlder = true)
                else -> MessagesResponse(listOf(message("m1", at = 1, text = "needle")), hasOlder = false)
            }
        }
        api.searchResult = { q -> if (q == "needle") listOf(message("m1", at = 1, text = "needle")) else emptyList() }
        val vm = viewModel()

        vm.openSearch()
        vm.searchQueryChanged("needle")
        assertTrue(vm.uiState.value.searchResults.isEmpty()) // 아직 기다리는 중
        advanceTimeBy(301)
        assertEquals(listOf("m1"), vm.uiState.value.searchResults.map { it.id })

        vm.events.test {
            vm.searchResultClicked(vm.uiState.value.searchResults.single())
            assertEquals(ChatRoomEvent.ScrollToMessage("m1"), awaitItem())
        }
        assertFalse(vm.uiState.value.isSearchOpen)
        assertEquals(listOf("m1", "m2", "m3"), vm.bubbles.map { it.key })
    }

    @Test
    fun jumpToUnknownMessageTellsNotFound() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.replyQuoteClicked("ghost")
            assertEquals(ChatRoomEvent.MessageNotFound, awaitItem())
        }
    }

    // ---------------------------------------------------------------- 로컬 DB(DEC-130)

    @Test
    fun savedMessagesShowWhenOffline() = runTest {
        api.messagesResult = { _, _ -> MessagesResponse(listOf(message("m1", "peer", 1), message("m2", "me", 2))) }
        viewModel()

        api.messagesResult = { _, _ -> throw AppError.Network(RuntimeException("offline")) }
        val reopened = viewModel()

        assertEquals(listOf("m1", "m2"), reopened.bubbles.map { it.message.id })
        assertEquals(null, reopened.uiState.value.error) // 전체 오류 대신 저장된 메시지
        assertFalse(reopened.uiState.value.isLoading)
    }

    @Test
    fun unsentTextSurvivesRestartAndIsSentOnOpen() = runTest {
        api.sendError = AppError.Network(RuntimeException("offline"))
        val vm = viewModel()
        vm.inputChanged("later")
        vm.send()
        assertEquals(SendState.Failed, vm.bubbles.single().sendState)

        api.sendError = null
        val reopened = viewModel()

        val bubble = reopened.bubbles.single()
        assertEquals("k1", bubble.key)
        assertEquals(SendState.Sent, bubble.sendState)
        assertEquals(listOf("later"), api.sent.map { it.text })
        assertTrue(ChatRepository(api, cache).pending("c1").isEmpty())
    }

    @Test
    fun deletedFailedMessageLeavesQueue() = runTest {
        api.sendError = AppError.Network(RuntimeException("offline"))
        val vm = viewModel()
        vm.inputChanged("x")
        vm.send()
        vm.deleteFailed("k1")

        assertTrue(ChatRepository(api, cache).pending("c1").isEmpty())
    }

    @Test
    fun reconnectResendsFailedMessages() = runTest {
        val online = MutableStateFlow(true)
        api.sendError = AppError.Network(RuntimeException("offline"))
        val vm = viewModel(online)
        online.value = false
        vm.inputChanged("hi")
        vm.send()
        assertEquals(SendState.Failed, vm.bubbles.single().sendState)

        api.sendError = null
        online.value = true

        assertEquals(SendState.Sent, vm.bubbles.single().sendState)
    }

    // ---------------------------------------------------------------- 1:1 대화(25-2)

    @Test
    fun openingByUserFindsDirectConversationThenSendsThere() = runTest {
        val vm = viewModel(conversationId = "", peerUserId = "u2")

        assertEquals(listOf("u2"), api.directRequests)
        assertEquals("User u2", vm.uiState.value.title)
        vm.inputChanged("hi")
        vm.send()
        assertEquals("server-k1", vm.bubbles.single().message.id)
        assertEquals("dm-u2", vm.bubbles.single().message.conversationId)
    }

    @Test
    fun directFailureShowsErrorAndRetryWorks() = runTest {
        api.directResult = { throw AppError.Http(500, "") }
        val vm = viewModel(conversationId = "", peerUserId = "u2")
        assertEquals(ErrorCode.ServerError, vm.uiState.value.error?.code)
        vm.inputChanged("x")
        vm.send() // 대화가 정해지기 전에는 보내지 않는다
        assertTrue(api.sent.isEmpty())

        api.directResult = { Conversation(id = "dm-$it", title = "Anna") }
        vm.load()
        assertEquals("Anna", vm.uiState.value.title)
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun serverCanHideAttachments() = runTest {
        val enabled = MutableStateFlow(true)
        val vm = ChatRoomViewModel(
            conversationId = "c1", title = "Papy", repository = ChatRepository(api, cache),
            realtime = object : ChatRealtime {
                override fun events(conversationId: String, lastMessageId: () -> String?) = incoming
            },
            myId = { "me" }, attachmentsEnabled = enabled,
        )
        assertTrue(vm.uiState.value.attachmentsEnabled)
        enabled.value = false
        assertFalse(vm.uiState.value.attachmentsEnabled)
    }

    // ---------------------------------------------------------------- 파일 받기(30-1)

    private var fileFail: Throwable? = null
    private val downloads = io.github.namjh654.papy.core.data.files.FileDownloads(
        transport = { url, sink, _ -> fileFail?.let { throw it }; sink.writeUtf8("pdf $url") },
        fileSystem = okio.fakefilesystem.FakeFileSystem(),
        directory = okio.Path.Companion.run { "/downloads".toPath() },
        storage = object : io.github.namjh654.papy.core.data.storage.LocalStorage {
            private val map = mutableMapOf<String, String>()
            override suspend fun getString(key: String) = map[key]
            override suspend fun setString(key: String, value: String) { map[key] = value }
            override suspend fun remove(key: String) { map.remove(key) }
            override suspend fun clear() = map.clear()
        },
        context = kotlin.coroutines.EmptyCoroutineContext,
    )

    private fun fileMessage() = message("f1", "peer", 3).copy(
        text = null,
        attachment = io.github.namjh654.papy.feature.chat.Attachment(AttachmentKind.File, "guide.pdf", "https://cdn/guide.pdf", 10, "application/pdf"),
    )

    private fun fileViewModel() = ChatRoomViewModel(
        conversationId = "c1", title = "Papy", repository = ChatRepository(api, cache),
        realtime = object : ChatRealtime {
            override fun events(conversationId: String, lastMessageId: () -> String?) = incoming
        },
        myId = { "me" }, downloads = downloads,
    )

    @Test
    fun tappingFileDownloadsThenOpens() = runTest {
        val vm = fileViewModel()
        vm.events.test {
            vm.fileClicked(fileMessage())
            val open = awaitItem()
            assertTrue(open is ChatRoomEvent.OpenFile && open.file.name == "guide.pdf")
        }
        assertTrue("https://cdn/guide.pdf" in vm.uiState.value.downloadedUrls)

        vm.events.test { // 두 번째는 받지 않고 바로 연다
            vm.fileClicked(fileMessage())
            assertTrue(awaitItem() is ChatRoomEvent.OpenFile)
        }
    }

    @Test
    fun fileFailureTellsAndShowsFailedState() = runTest {
        fileFail = AppError.Http(404, "")
        val vm = fileViewModel()
        vm.events.test {
            vm.fileClicked(fileMessage())
            assertTrue(awaitItem() is ChatRoomEvent.FileFailed)
        }
        assertTrue(vm.uiState.value.fileStates["https://cdn/guide.pdf"] is io.github.namjh654.papy.core.data.files.DownloadState.Failed)
    }

    @Test
    fun reopeningWithSavedMessagesScrollsToLatestAfterRefresh() = runTest {
        api.messagesResult = { _, _ -> MessagesResponse((1..40).map { message("m$it", "peer", it.toLong()) }) }
        viewModel().events.test {
            expectNoEvents() // 처음 열 때는 저장된 것이 없다 → 목록이 처음부터 아래에 붙는다
        }

        api.messagesResult = { _, _ -> MessagesResponse((31..41).map { message("m$it", "peer", it.toLong()) }) }
        val reopened = viewModel() // 저장된 40개 → 받은 11개로 바뀐다
        reopened.events.test {
            assertEquals(ChatRoomEvent.ScrollToBottom, awaitItem())
        }
        assertEquals("m41", reopened.bubbles.last().message.id)
    }
}
