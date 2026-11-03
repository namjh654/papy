package io.github.namjh654.papy.core.data.storage

import platform.Foundation.NSUserDefaults

/**
 * NSUserDefaults 기반 [LocalStorage] 구현입니다.
 *
 * 앱 전용 suite를 사용하므로 [clear]가 시스템/서드파티 키를 건드리지 않습니다.
 * 토큰을 Keychain으로 옮길 때는 이 클래스만 교체하면 됩니다.
 */
internal class IOSLocalStorage(
    private val suiteName: String = DEFAULT_SUITE_NAME,
) : LocalStorage {

    private val defaults: NSUserDefaults =
        NSUserDefaults(suiteName = suiteName)

    override suspend fun getString(key: String): String? =
        defaults.stringForKey(key)

    override suspend fun setString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override suspend fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    override suspend fun clear() {
        defaults.removePersistentDomainForName(suiteName)
    }

    companion object {
        const val DEFAULT_SUITE_NAME = "io.github.namjh654.papy.storage"
    }
}
