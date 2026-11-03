package io.github.namjh654.papy.feature.notice

import io.github.namjh654.papy.core.common.error.AppError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NoticeRepositoryTest {

    private val repository = NoticeRepository(SampleNoticeApi())

    @Test
    fun sampleApiPagesUntilLastPage() = runTest {
        val first = repository.items(page = null)
        assertEquals("1", first.items.first().id)
        assertEquals("2", first.nextKey)
        assertNull(first.items.first().body) // 목록에는 본문이 없다

        val last = repository.items(page = "2")
        assertEquals("20", last.items.last().id)
        assertNull(last.nextKey)
    }

    @Test
    fun itemHasBodyAndUnknownIdIsNotFound() = runTest {
        assertNotNull(repository.item("3").body)

        val error = assertFailsWith<AppError.Http> { repository.item("999") }
        assertEquals(404, error.statusCode)
    }
}
