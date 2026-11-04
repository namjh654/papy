package io.github.namjh654.papy.feature.documents

import io.github.namjh654.papy.core.data.storage.LocalStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 최근 문서 하나. [ref] 는 플랫폼 파일 참조, [name] 은 보이는 파일 이름 */
@Serializable
data class RecentDocument(
    val ref: String,
    val name: String,
    val kind: DocumentKind,
    val openedAt: Long,
)

/** 이 기기에만 저장하는 최근 문서 목록(최근 순, 최대 [MAX]개). 같은 이름·형식은 한 줄로 합친다. */
class RecentDocuments(
    private val storage: LocalStorage,
    private val now: () -> Long,
) {
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<RecentDocument>>(emptyList())
    val items: StateFlow<List<RecentDocument>> = _items.asStateFlow()
    private var loaded = false

    suspend fun load(): List<RecentDocument> = mutex.withLock {
        if (!loaded) {
            _items.value = storage.getString(KEY)
                ?.let { runCatching { json.decodeFromString<List<RecentDocument>>(it) }.getOrNull() }
                .orEmpty()
            loaded = true
        }
        _items.value
    }

    suspend fun add(ref: String, name: String, kind: DocumentKind) {
        load()
        // 같은 파일이 다른 참조로 올 수 있다(Android 파일 앱의 "최근"·"다운로드"는 URI 가 다르다) → 이름·형식이 같으면 하나로.
        update { list ->
            (listOf(RecentDocument(ref, name, kind, now())) + list.filter { it.ref != ref && !(it.name == name && it.kind == kind) })
                .take(MAX)
        }
    }

    suspend fun remove(ref: String) {
        load()
        update { list -> list.filter { it.ref != ref } }
    }

    suspend fun clear() {
        load()
        update { emptyList() }
    }

    private suspend fun update(change: (List<RecentDocument>) -> List<RecentDocument>) = mutex.withLock {
        val next = change(_items.value)
        _items.value = next
        if (next.isEmpty()) storage.remove(KEY) else storage.setString(KEY, json.encodeToString(next))
    }

    companion object {
        const val MAX = 20
        private const val KEY = "documents.recent"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
