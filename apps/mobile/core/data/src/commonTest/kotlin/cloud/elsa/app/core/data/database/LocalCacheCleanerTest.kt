package cloud.elsa.app.core.data.database

import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.core.data.session.UserStore
import cloud.elsa.app.core.data.storage.FakeLocalStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalCacheCleanerTest {

    private class CountingCache : LocalCache {
        var cleared = 0
        override suspend fun clear() { cleared++ }
    }

    private val cache = CountingCache()
    private val storage = FakeLocalStorage()
    private val userStore = UserStore()
    private val cleaner = LocalCacheCleaner(caches = { listOf(cache) }, storage = storage, userStore = userStore)

    @Test
    fun logoutClearsCaches() = runTest {
        userStore.setUser(UserModel(id = "a", name = "A"))
        cleaner.onSessionStarted()
        cleaner.onSessionEnding()
        assertEquals(1, cache.cleared)
        assertNull(storage.getString(LocalCacheCleaner.KEY_OWNER))
    }

    @Test
    fun sameUserKeepsCacheButOtherUserClearsIt() = runTest {
        userStore.setUser(UserModel(id = "a", name = "A"))
        cleaner.onSessionStarted()
        cleaner.onSessionStarted() // 같은 사용자 복원
        assertEquals(0, cache.cleared)

        // 토큰 만료 뒤 다른 계정으로 로그인
        userStore.setUser(UserModel(id = "b", name = "B"))
        cleaner.onSessionStarted()
        assertEquals(1, cache.cleared)
        assertEquals("b", storage.getString(LocalCacheCleaner.KEY_OWNER))
    }

    @Test
    fun failingCacheDoesNotStopOthers() = runTest {
        val failing = object : LocalCache {
            override suspend fun clear() = error("disk")
        }
        LocalCacheCleaner(caches = { listOf(failing, cache) }, storage = storage, userStore = userStore).onSessionEnding()
        assertEquals(1, cache.cleared)
    }
}
