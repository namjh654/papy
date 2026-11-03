package io.github.namjh654.papy.feature.search

import io.github.namjh654.papy.core.data.storage.LocalStorage

internal class MemoryStorage : LocalStorage {
    val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String) = values[key]
    override suspend fun setString(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
    override suspend fun clear() = values.clear()
}
