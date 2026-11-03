package io.github.namjh654.papy.feature.chat

import app.cash.turbine.test
import io.github.namjh654.papy.core.common.paging.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val conversation = Conversation(id = "c1", title = "Papy")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsConversationsAndMoreAtEnd() = runTest {
        val api = FakeChatApi().apply {
            conversationPages = { page ->
                if (page == null) Page(listOf(conversation), nextKey = "2") else Page(listOf(conversation.copy(id = "c2")))
            }
        }
        val vm = ChatViewModel(ChatRepository(api, testChatCache()))
        assertEquals(listOf("c1"), vm.uiState.value.list.items.map { it.id })

        vm.loadMore()

        assertEquals(listOf("c1", "c2"), vm.uiState.value.list.items.map { it.id })
    }

    @Test
    fun selectingOpensTheRoom() = runTest {
        val vm = ChatViewModel(ChatRepository(FakeChatApi(), testChatCache()))
        vm.events.test {
            vm.conversationClicked(conversation)
            assertEquals(ChatEvent.ConversationSelected(conversation), awaitItem())
        }
    }

    @Test
    fun readingARoomClearsItsBadgeInTheList() = runTest {
        val api = FakeChatApi().apply { conversationPages = { Page(listOf(conversation.copy(unreadCount = 4))) } }
        val repository = ChatRepository(api, testChatCache())
        val vm = ChatViewModel(repository)

        repository.markRead("c1", "m9")

        assertEquals(0, vm.uiState.value.list.items.single().unreadCount)
    }
}
