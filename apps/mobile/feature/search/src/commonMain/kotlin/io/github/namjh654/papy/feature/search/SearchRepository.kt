package io.github.namjh654.papy.feature.search

import io.github.namjh654.papy.core.common.paging.Page
import io.github.namjh654.papy.core.data.serialization.AppJson
import io.github.namjh654.papy.core.data.storage.LocalStorage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * 검색 + 최근 검색어. 최근 검색어는 기기에 저장한다(최신이 앞, 중복 없이 [MAX_RECENT] 개까지).
 */
class SearchRepository(
    private val api: SearchApi,
    private val storage: LocalStorage,
) {
    suspend fun search(query: String, page: String?): Page<SearchResult> = api.search(query.trim(), page)

    suspend fun recent(): List<String> {
        val raw = storage.getString(KEY_RECENT) ?: return emptyList()
        // 저장 형식이 깨졌으면 없는 것으로 본다
        return runCatching { AppJson.decodeFromString(SERIALIZER, raw) }.getOrDefault(emptyList())
    }

    /** 검색어를 맨 앞에 넣는다. 돌려주는 값은 바뀐 목록 */
    suspend fun addRecent(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return recent()
        return save((listOf(trimmed) + recent().filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_RECENT))
    }

    suspend fun removeRecent(query: String): List<String> = save(recent() - query)

    suspend fun clearRecent() {
        storage.remove(KEY_RECENT)
    }

    private suspend fun save(list: List<String>): List<String> {
        storage.setString(KEY_RECENT, AppJson.encodeToString(SERIALIZER, list))
        return list
    }

    private companion object {
        const val KEY_RECENT = "search.recent"
        const val MAX_RECENT = 10
        val SERIALIZER = ListSerializer(String.serializer())
    }
}
