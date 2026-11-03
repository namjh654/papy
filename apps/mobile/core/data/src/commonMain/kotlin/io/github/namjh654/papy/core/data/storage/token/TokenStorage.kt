package io.github.namjh654.papy.core.data.storage.token

import io.github.namjh654.papy.core.data.storage.LocalStorage
import io.github.namjh654.papy.core.data.storage.SecureStorage

/**
 * Access/Refresh 토큰 저장소. 값은 [SecureStorage] 에만 둔다.
 *
 * 마이그레이션: 이전 버전은 평문 [LocalStorage] 에 같은 키로 저장했다. 첫 접근 때 한 번
 * 평문 값을 보안 저장소로 옮기고 평문을 지운다(기존 로그인 유지, 평문 잔류 없음).
 */
class TokenStorage(
    private val secureStorage: SecureStorage,
    private val legacyStorage: LocalStorage,
) {

    private var migrated = false

    suspend fun setAccessToken(token: String) {
        migrateIfNeeded()
        secureStorage.setString(TokenStorageKey.ACCESS_TOKEN, token)
    }

    suspend fun getAccessToken(): String? {
        migrateIfNeeded()
        return secureStorage.getString(TokenStorageKey.ACCESS_TOKEN)
    }

    suspend fun setRefreshToken(token: String) {
        migrateIfNeeded()
        secureStorage.setString(TokenStorageKey.REFRESH_TOKEN, token)
    }

    suspend fun getRefreshToken(): String? {
        migrateIfNeeded()
        return secureStorage.getString(TokenStorageKey.REFRESH_TOKEN)
    }

    suspend fun clear() {
        migrateIfNeeded()
        secureStorage.remove(TokenStorageKey.ACCESS_TOKEN)
        secureStorage.remove(TokenStorageKey.REFRESH_TOKEN)
    }

    private suspend fun migrateIfNeeded() {
        if (migrated) return
        for (key in TokenStorageKey.ALL) {
            val plain = legacyStorage.getString(key) ?: continue
            if (secureStorage.getString(key) == null) secureStorage.setString(key, plain)
            legacyStorage.remove(key)
        }
        migrated = true
    }
}
