package io.github.namjh654.papy.core.data.security

import io.github.namjh654.papy.core.data.session.AuthStore
import io.github.namjh654.papy.core.data.storage.FakeLocalStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockTest {

    private val storage = FakeLocalStorage()
    private val authStore = AuthStore()
    private var clock = 0L

    private fun TestScope.appLock() = AppLock(storage, authStore, backgroundScope, now = { clock })

    @Test
    fun offByDefault() = runTest(UnconfinedTestDispatcher()) {
        val lock = appLock()
        authStore.authenticated()
        assertFalse(lock.isLocked.value)
    }

    @Test
    fun enabledLockLocksOnColdStartWhileLoggedIn() = runTest(UnconfinedTestDispatcher()) {
        appLock().setEnabled(true)

        val restarted = appLock() // 앱을 새로 열었다
        assertFalse(restarted.isLocked.value) // 세션 복원 전
        authStore.authenticated()
        assertTrue(restarted.isLocked.value)

        restarted.unlock()
        assertFalse(restarted.isLocked.value)
    }

    @Test
    fun locksOnlyAfterGracePeriodInBackground() = runTest(UnconfinedTestDispatcher()) {
        authStore.authenticated()
        val lock = appLock()
        lock.setEnabled(true)

        lock.onBackground()
        clock += AppLock.GRACE_MILLIS - 1 // 사진 고르기 등 잠깐
        lock.onForeground()
        assertFalse(lock.isLocked.value)

        lock.onBackground()
        clock += AppLock.GRACE_MILLIS
        lock.onForeground()
        assertTrue(lock.isLocked.value)
    }

    @Test
    fun logoutTurnsItOffAndReloginStartsUnlocked() = runTest(UnconfinedTestDispatcher()) {
        authStore.authenticated()
        val lock = appLock()
        lock.setEnabled(true)
        lock.onBackground(); clock += AppLock.GRACE_MILLIS; lock.onForeground()
        assertTrue(lock.isLocked.value)

        authStore.unauthenticated() // 세션 만료 → 로그인 화면
        authStore.authenticated()
        assertFalse(lock.isLocked.value)

        lock.onSessionEnding()
        assertFalse(lock.isEnabled.value)
        assertFalse(storage.getString(AppLock.KEY_ENABLED).toBoolean())
    }
}
