package io.github.namjh654.papy.feature.notice

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.paging.Page
import io.github.namjh654.papy.core.data.network.BaseApi
import kotlinx.serialization.Serializable

@Serializable
data class NoticeItem(
    val id: String,
    val title: String,
    /** 게시 시각(UTC epoch millis). 목록은 `RelativeTime.of(...).label()`(3일 전), 상세는 `PapyFormat.date` */
    val publishedAt: Long = 0,
    /** 목록에 "새 글" 표시 */
    val isNew: Boolean = false,
    /** 상세 본문(목록 응답에는 없을 수 있다) */
    val body: String? = null,
)

/** 서버 응답 한 페이지 (가정 계약: `GET /api/notices?page=N` → `{items, nextPage}`) */
@Serializable
internal data class NoticePageResponse(
    val items: List<NoticeItem> = emptyList(),
    val nextPage: String? = null,
)

/**
 * 공지사항 API 계약. 서버 연동은 [RemoteNoticeApi], 서버 없이 개발할 때는 [SampleNoticeApi].
 * 어느 쪽을 쓸지는 di/NoticeModule.kt 한 곳에서 정한다.
 */
interface NoticeApi {
    /** @param page null 이면 첫 페이지 */
    suspend fun items(page: String?): Page<NoticeItem>

    /** 상세 한 건(본문 포함) */
    suspend fun item(id: String): NoticeItem
}

internal class RemoteNoticeApi(
    private val baseApi: BaseApi,
) : NoticeApi {
    // {BASE_URL}/api/notices?page=N
    override suspend fun items(page: String?): Page<NoticeItem> {
        val response: NoticePageResponse = baseApi.get("/api/notices", query = page?.let { mapOf("page" to it) }.orEmpty())
        return Page(items = response.items, nextKey = response.nextPage)
    }

    // {BASE_URL}/api/notices/{id}
    override suspend fun item(id: String): NoticeItem = baseApi.get("/api/notices/$id")
}

/** 서버 없이 화면을 확인하기 위한 예시 데이터(서버 응답 대용이라 문구 리소스가 아니다). 2페이지 × 10개 */
internal class SampleNoticeApi : NoticeApi {
    override suspend fun items(page: String?): Page<NoticeItem> {
        val number = page?.toIntOrNull() ?: 1
        val items = (1..PAGE_SIZE).map { i -> sample((number - 1) * PAGE_SIZE + i).copy(body = null) }
        return Page(items = items, nextKey = if (number < LAST_PAGE) "${number + 1}" else null)
    }

    override suspend fun item(id: String): NoticeItem {
        val n = id.toIntOrNull()?.takeIf { it in 1..PAGE_SIZE * LAST_PAGE } ?: throw AppError.Http(404, "")
        return sample(n)
    }

    private fun sample(n: Int) = NoticeItem(
        id = "$n",
        title = "Notice $n",
        publishedAt = NEWEST - (n - 1) * 9 * HOUR,
        isNew = n <= 2,
        body = "This is the body of notice $n.\n\nReplace SampleNoticeApi with RemoteNoticeApi in NoticeModule when the server is ready.",
    )

    private companion object {
        const val PAGE_SIZE = 10
        const val LAST_PAGE = 2
        const val HOUR = 60 * 60 * 1000L

        /** 2026-09-30T00:00:00Z */
        const val NEWEST = 1_790_726_400_000L
    }
}
