package io.github.namjh654.papy.feature.inbox

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InboxRepositoryTest {

    private val repository = InboxRepository(SampleInboxApi())

    @Test
    fun markReadIsReflectedInNextLoad() = runTest {
        assertEquals(false, repository.items(null).items.first { it.id == "1" }.read)

        repository.markRead("1")

        assertTrue(repository.items(null).items.first { it.id == "1" }.read)
    }

    @Test
    fun markAllReadMarksEverything() = runTest {
        repository.markAllRead()

        assertTrue(repository.items(null).items.all { it.read })
    }
}
