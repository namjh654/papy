package io.github.namjh654.papy.feature.search

import io.github.namjh654.papy.core.common.paging.Page
import io.github.namjh654.papy.core.data.network.BaseApi
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String? = null,
)

@Serializable
internal data class SearchPageResponse(
    val items: List<SearchResult> = emptyList(),
    val nextPage: String? = null,
)

/**
 * 검색 API 계약(가정). 서버 연동은 [RemoteSearchApi], 서버 없이 개발할 때는 [SampleSearchApi] — di/SearchModule.kt 에서 정한다.
 */
interface SearchApi {
    suspend fun search(query: String, page: String?): Page<SearchResult>
}

internal class RemoteSearchApi(
    private val baseApi: BaseApi,
) : SearchApi {
    // {BASE_URL}/api/search?q=…&page=N
    override suspend fun search(query: String, page: String?): Page<SearchResult> {
        val params = buildMap {
            put("q", query)
            page?.let { put("page", it) }
        }
        val response: SearchPageResponse = baseApi.get("/api/search", query = params)
        return Page(items = response.items, nextKey = response.nextPage)
    }
}

/** 예시 목록(Sample 1~45)에서 제목에 검색어가 든 항목을 찾는다. id 는 예시 상세와 같다 */
internal class SampleSearchApi : SearchApi {
    override suspend fun search(query: String, page: String?): Page<SearchResult> {
        val matched = (1..TOTAL).map { "Sample $it" to it }.filter { (title, _) -> title.contains(query, ignoreCase = true) }
        val number = page?.toIntOrNull() ?: 1
        val items = matched.drop((number - 1) * PAGE_SIZE).take(PAGE_SIZE)
            .map { (title, n) -> SearchResult(id = "$n", title = title, subtitle = "Example item $n") }
        return Page(items = items, nextKey = if (number * PAGE_SIZE < matched.size) "${number + 1}" else null)
    }

    private companion object {
        const val TOTAL = 45
        const val PAGE_SIZE = 15
    }
}
