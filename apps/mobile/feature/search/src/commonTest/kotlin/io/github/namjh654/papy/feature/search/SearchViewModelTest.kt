package io.github.namjh654.papy.feature.search

import app.cash.turbine.test
import io.github.namjh654.papy.core.common.paging.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val queries = mutableListOf<String>()
    private val api = object : SearchApi {
        override suspend fun search(query: String, page: String?): Page<SearchResult> {
            queries += query
            return Page(if (query == "none") emptyList() else listOf(SearchResult(id = "7", title = "Result for $query")))
        }
    }
    private val viewModel by lazy { SearchViewModel(SearchRepository(api, MemoryStorage())) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun typingSearchesOnceAfterInputStops() = runTest(dispatcher) {
        viewModel.queryChanged("a")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS - 1)
        viewModel.queryChanged("ab") // 멈추기 전에 또 입력 → 앞의 대기는 취소
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS - 1)
        assertTrue(queries.isEmpty())

        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf("ab"), queries)
        assertEquals("Result for ab", viewModel.uiState.value.results.items.single().title)
        assertTrue(viewModel.uiState.value.recent.isEmpty()) // 입력 중 자동 검색은 최근 검색어에 남기지 않는다
    }

    @Test
    fun submitSearchesImmediatelyAndRemembers() = runTest(dispatcher) {
        viewModel.queryChanged("kotlin")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf("kotlin"), queries) // 대기 중이던 자동 검색이 또 나가지 않는다
        assertEquals(listOf("kotlin"), viewModel.uiState.value.recent)
    }

    @Test
    fun clearingInputReturnsToRecent() = runTest(dispatcher) {
        viewModel.queryChanged("kotlin")
        viewModel.submit()
        advanceUntilIdle()

        viewModel.queryChanged("")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showRecent)
        assertTrue(viewModel.uiState.value.results.items.isEmpty())
        assertNull(viewModel.uiState.value.searched)
    }

    @Test
    fun noResultsIsEmptyState() = runTest(dispatcher) {
        viewModel.queryChanged("none")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.results.isEmpty)
        assertEquals("none", viewModel.uiState.value.searched)
    }

    @Test
    fun recentClickSearchesAndMovesToFront() = runTest(dispatcher) {
        viewModel.queryChanged("first"); viewModel.submit(); advanceUntilIdle()
        viewModel.queryChanged("second"); viewModel.submit(); advanceUntilIdle()

        viewModel.recentClicked("first")
        advanceUntilIdle()

        assertEquals("first", viewModel.uiState.value.query)
        assertEquals(listOf("first", "second"), viewModel.uiState.value.recent)

        viewModel.removeRecent("second")
        advanceUntilIdle()
        assertEquals(listOf("first"), viewModel.uiState.value.recent)
    }

    @Test
    fun resultClickSendsIdAndRemembersQuery() = runTest(dispatcher) {
        viewModel.queryChanged("ab")
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.itemClicked(viewModel.uiState.value.results.items.single())
            assertEquals(SearchEvent.ItemSelected("7"), awaitItem())
        }
        assertEquals(listOf("ab"), viewModel.uiState.value.recent) // 결과를 눌렀으니 남긴다
    }
}
