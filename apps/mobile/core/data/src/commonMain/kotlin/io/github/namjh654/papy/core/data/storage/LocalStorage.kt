package io.github.namjh654.papy.core.data.storage

/**
 * 플랫폼 독립 Key-Value 저장소 계약입니다.
 *
 * KMP는 인터페이스만 정의하고, 실제 저장 방식은 플랫폼이 Koin으로 주입합니다.
 * - Android: DataStore (AndroidLocalStorage)
 * - iOS: NSUserDefaults (IOSLocalStorage)
 *
 * 값은 문자열로만 저장하므로 플랫폼 구현을 Keychain/EncryptedStorage 등으로
 * 교체해도 Business Layer는 영향을 받지 않습니다.
 */
interface LocalStorage {

    suspend fun getString(key: String): String?

    suspend fun setString(key: String, value: String)

    suspend fun remove(key: String)

    suspend fun clear()
}

// ============================================================
// Typed helpers
// ============================================================

suspend fun LocalStorage.getBoolean(key: String): Boolean? =
    getString(key)?.toBooleanStrictOrNull()

suspend fun LocalStorage.setBoolean(key: String, value: Boolean) =
    setString(key, value.toString())

suspend fun LocalStorage.getInt(key: String): Int? =
    getString(key)?.toIntOrNull()

suspend fun LocalStorage.setInt(key: String, value: Int) =
    setString(key, value.toString())

suspend fun LocalStorage.getLong(key: String): Long? =
    getString(key)?.toLongOrNull()

suspend fun LocalStorage.setLong(key: String, value: Long) =
    setString(key, value.toString())

/** null 또는 공백이면 키를 제거하고, 아니면 저장합니다. */
suspend fun LocalStorage.setStringOrRemove(key: String, value: String?) {
    if (value.isNullOrBlank()) remove(key) else setString(key, value)
}
