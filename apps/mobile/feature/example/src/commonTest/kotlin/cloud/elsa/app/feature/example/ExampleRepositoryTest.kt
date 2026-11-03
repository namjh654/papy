package cloud.elsa.app.feature.example

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExampleRepositoryTest {

    @Test
    fun sampleApiPagesUntilLastPage() = runTest {
        val repository = ExampleRepository(SampleExampleApi())

        val first = repository.items(page = null)
        assertEquals("1", first.items.first().id)
        assertEquals("2", first.nextKey)

        val last = repository.items(page = "3")
        assertEquals("45", last.items.last().id)
        assertNull(last.nextKey)
    }

    @Test
    fun setFavoriteSavesAndAnnouncesChange() = runTest {
        val repository = ExampleRepository(SampleExampleApi())

        repository.changes.test {
            val saved = repository.setFavorite("3", favorite = true)

            assertTrue(saved.favorite)
            assertEquals(saved, awaitItem())
            assertTrue(repository.item("3").favorite)
        }
    }

    @Test
    fun unknownIdIsNotFound() = runTest {
        val error = assertFailsWith<AppError.Http> { ExampleRepository(SampleExampleApi()).item("999") }
        assertEquals(404, error.statusCode)
    }
}
