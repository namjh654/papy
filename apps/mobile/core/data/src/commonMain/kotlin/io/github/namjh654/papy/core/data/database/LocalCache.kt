package io.github.namjh654.papy.core.data.database

import io.github.namjh654.papy.core.data.session.SessionListener
import io.github.namjh654.papy.core.data.session.UserStore
import io.github.namjh654.papy.core.data.storage.LocalStorage
import kotlinx.coroutines.CancellationException

/**
 * 사용자마다 다른 로컬 데이터(기능 DB의 캐시·보낼 대기열). 기능 모듈이 구현해 Koin 에 등록한다:
 * `single { ChatCache(database = get()) } bind LocalCache::class` — 로그아웃 때 [LocalCacheCleaner] 가 모두 지운다.
 */
interface LocalCache {
    suspend fun clear()
}

/**
 * 세션에 맞춰 [LocalCache] 를 지운다: 로그아웃(탈퇴 포함) 직전, 그리고 지난번과 다른 사용자로 로그인했을 때
 * (토큰 만료 뒤 다른 계정 — 만료는 리스너를 부르지 않으므로 다음 로그인에서 확인한다).
 *
 * @param caches Koin 에 등록된 모든 [LocalCache] (기능 모듈이 늘면 자동 포함)
 */
internal class LocalCacheCleaner(
    private val caches: () -> List<LocalCache>,
    private val storage: LocalStorage,
    private val userStore: UserStore,
) : SessionListener {

    override suspend fun onSessionStarted() {
        val userId = userStore.user.value?.id ?: return
        val owner = storage.getString(KEY_OWNER)
        if (owner != null && owner != userId) clearAll()
        if (owner != userId) storage.setString(KEY_OWNER, userId)
    }

    override suspend fun onSessionEnding() {
        clearAll()
        storage.remove(KEY_OWNER)
    }

    private suspend fun clearAll() {
        caches().forEach { cache ->
            try {
                cache.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 한 기능의 실패가 다른 기능 정리·로그아웃을 막지 않게
            }
        }
    }

    companion object {
        /** 로컬 데이터가 누구 것인지 */
        internal const val KEY_OWNER = "cache.owner"
    }
}
