package io.github.namjh654.papy.core.data.storage

/** 플랫폼 Storage 없이 Business Layer 를 검증하기 위한 in-memory 구현 */
open class FakeLocalStorage : LocalStorage {
    val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String) = values[key]
    override suspend fun setString(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
    override suspend fun clear() = values.clear()
}

class FakeSecureStorage : FakeLocalStorage(), SecureStorage
