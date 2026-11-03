package io.github.namjh654.papy.feature.search

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchRepositoryTest {

    private val storage = MemoryStorage()
    private val repository = SearchRepository(SampleSearchApi(), storage)

    @Test
    fun sampleSearchFiltersAndPages() = runTest {
        val first = repository.search(" sample 1 ", page = null) // Sample 1, 10~19 → 11개
        assertEquals(11, first.items.size)
        assertNull(first.nextKey)

        val all = repository.search("sample", page = null)
        assertEquals(15, all.items.size)
        assertEquals("2", all.nextKey)

        assertTrue(repository.search("zzz", page = null).items.isEmpty())
    }

    @Test
    fun recentKeepsNewestFirstWithoutDuplicates() = runTest {
        repository.addRecent("apple")
        repository.addRecent("banana")
        val list = repository.addRecent(" Apple ") // 대소문자만 다른 같은 검색어 → 맨 앞으로

        assertEquals(listOf("Apple", "banana"), list)
        assertEquals(list, repository.recent()) // 저장돼 있다
    }

    @Test
    fun recentIsLimitedAndCanBeRemovedOrCleared() = runTest {
        (1..12).forEach { repository.addRecent("q$it") }
        assertEquals(10, repository.recent().size)
        assertEquals("q12", repository.recent().first())

        assertEquals(9, repository.removeRecent("q12").size)
        repository.clearRecent()
        assertTrue(repository.recent().isEmpty())
    }

    @Test
    fun brokenStoredValueIsTreatedAsEmpty() = runTest {
        storage.values["search.recent"] = "not json"

        assertTrue(repository.recent().isEmpty())
    }
}
