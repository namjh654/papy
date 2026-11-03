package io.github.namjh654.papy.feature.example

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.paging.Page
import io.github.namjh654.papy.core.data.network.BaseApi
import kotlinx.serialization.Serializable

@Serializable
data class ExampleItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    /** 상세 화면 본문 */
    val body: String? = null,
    /** 상세 화면에서 바꾸는 값(수정 → 목록 반영 예시) */
    val favorite: Boolean = false,
)

/** 서버 응답 한 페이지 (가정 계약: `GET /api/example?page=N` → `{items, nextPage}`) */
@Serializable
internal data class ExamplePageResponse(
    val items: List<ExampleItem> = emptyList(),
    val nextPage: String? = null,
)

@Serializable
internal data class ExampleFavoriteRequest(val favorite: Boolean)

/**
 * 예시 기능 API 계약. 서버 연동은 [RemoteExampleApi], 서버 없이 개발할 때는 [SampleExampleApi].
 * 어느 쪽을 쓸지는 di/ExampleModule.kt 한 곳에서 정한다.
 */
interface ExampleApi {
    /** @param page null 이면 첫 페이지 */
    suspend fun items(page: String?): Page<ExampleItem>

    /** 상세 한 건 */
    suspend fun item(id: String): ExampleItem

    /** 수정 예시. 바뀐 항목을 돌려준다 */
    suspend fun setFavorite(id: String, favorite: Boolean): ExampleItem
}

internal class RemoteExampleApi(
    private val baseApi: BaseApi,
) : ExampleApi {
    // {BASE_URL}/api/example?page=N
    override suspend fun items(page: String?): Page<ExampleItem> {
        val response: ExamplePageResponse = baseApi.get("/api/example", query = page?.let { mapOf("page" to it) }.orEmpty())
        return Page(items = response.items, nextKey = response.nextPage)
    }

    // {BASE_URL}/api/example/{id}
    override suspend fun item(id: String): ExampleItem = baseApi.get("/api/example/$id")

    // {BASE_URL}/api/example/{id}/favorite
    override suspend fun setFavorite(id: String, favorite: Boolean): ExampleItem =
        baseApi.put("/api/example/$id/favorite", ExampleFavoriteRequest(favorite))
}

/** 서버 없이 화면을 확인하기 위한 예시 데이터(서버 응답 대용이라 문구 리소스가 아니다). 3페이지 × 15개 */
internal class SampleExampleApi : ExampleApi {
    private val favorites = mutableSetOf<String>()

    override suspend fun items(page: String?): Page<ExampleItem> {
        val number = page?.toIntOrNull() ?: 1
        val items = (1..PAGE_SIZE).map { i -> sample((number - 1) * PAGE_SIZE + i) }
        return Page(items = items, nextKey = if (number < LAST_PAGE) "${number + 1}" else null)
    }

    override suspend fun item(id: String): ExampleItem {
        val n = id.toIntOrNull()?.takeIf { it in 1..PAGE_SIZE * LAST_PAGE } ?: throw AppError.Http(404, "")
        return sample(n)
    }

    override suspend fun setFavorite(id: String, favorite: Boolean): ExampleItem {
        if (favorite) favorites += id else favorites -= id
        return item(id)
    }

    private fun sample(n: Int) = ExampleItem(
        id = "$n",
        title = "Sample $n",
        subtitle = if (n == 1) "RemoteExampleApi" else null,
        body = "Sample $n detail. Replace SampleExampleApi with RemoteExampleApi in ExampleModule when the server is ready.",
        favorite = "$n" in favorites,
    )

    private companion object {
        const val PAGE_SIZE = 15
        const val LAST_PAGE = 3
    }
}
