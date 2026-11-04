package io.github.namjh654.papy.feature.documents

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentDocumentsTest {
    @Test
    fun newestFirstDedupedCappedAndPersisted() = runTest {
        val storage = MemoryStorage()
        var clock = 0L
        val recents = RecentDocuments(storage, now = { ++clock })
        repeat(RecentDocuments.MAX + 5) { recents.add("r$it", "f$it.hwp", DocumentKind.Hwp) }
        recents.add("r3", "f3.hwp", DocumentKind.Hwp)
        assertEquals(RecentDocuments.MAX, recents.items.value.size)
        assertEquals("r3", recents.items.value.first().ref)
        assertEquals(1, recents.items.value.count { it.ref == "r3" })
        // 같은 파일이 다른 참조로 오면 새 참조로 바꾼다
        recents.add("other-uri", "f3.hwp", DocumentKind.Hwp)
        assertEquals(listOf("other-uri"), recents.items.value.filter { it.name == "f3.hwp" }.map { it.ref })
        recents.add("r3", "f3.hwp", DocumentKind.Hwp)
        val items = recents.items.value
        // 다시 읽어도 같다
        val again = RecentDocuments(storage, now = { 0L })
        assertEquals(items, again.load())
        again.remove("r3")
        assertEquals(RecentDocuments.MAX - 1, again.items.value.size)
        again.clear()
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun brokenStorageStartsEmpty() = runTest {
        val storage = MemoryStorage().apply { values["documents.recent"] = "{not json" }
        assertTrue(RecentDocuments(storage, now = { 0L }).load().isEmpty())
    }
}
