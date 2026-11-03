package io.github.namjh654.papy.core.data.storage

import io.github.namjh654.papy.core.data.storage.token.TokenStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStorageTest {

    private val legacy = FakeLocalStorage()
    private val secure = FakeSecureStorage()

    @Test
    fun migratesPlainTokensToSecureStorageOnFirstAccess() = runTest {
        legacy.setString("token.access", "old-access")
        legacy.setString("token.refresh", "old-refresh")
        legacy.setString("user.profile", "{}")

        val tokens = TokenStorage(secure, legacy)

        assertEquals("old-access", tokens.getAccessToken())
        assertEquals("old-refresh", tokens.getRefreshToken())
        assertNull(legacy.getString("token.access"))
        assertNull(legacy.getString("token.refresh"))
        assertEquals("{}", legacy.getString("user.profile")) // 토큰 외 값은 그대로
    }

    @Test
    fun newTokensAreWrittenOnlyToSecureStorage() = runTest {
        val tokens = TokenStorage(secure, legacy)
        tokens.setAccessToken("new")

        assertEquals("new", secure.getString("token.access"))
        assertTrue(legacy.values.isEmpty())
    }

    @Test
    fun secureValueWinsOverStalePlainValue() = runTest {
        secure.setString("token.access", "secure")
        legacy.setString("token.access", "stale")

        assertEquals("secure", TokenStorage(secure, legacy).getAccessToken())
        assertNull(legacy.getString("token.access"))
    }

    @Test
    fun clearRemovesTokensEverywhere() = runTest {
        legacy.setString("token.refresh", "old")
        val tokens = TokenStorage(secure, legacy)
        tokens.setAccessToken("a")

        tokens.clear()

        assertNull(tokens.getAccessToken())
        assertNull(tokens.getRefreshToken())
        assertTrue(legacy.values.isEmpty())
    }
}
