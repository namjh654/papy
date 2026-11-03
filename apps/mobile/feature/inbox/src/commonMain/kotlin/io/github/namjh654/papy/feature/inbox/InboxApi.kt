package io.github.namjh654.papy.feature.inbox

import io.github.namjh654.papy.core.common.paging.Page
import io.github.namjh654.papy.core.data.network.BaseApi
import kotlinx.serialization.Serializable

/**
 * 받은 알림 한 건.
 *
 * @property link 눌렀을 때 열 화면(딥링크와 같은 형식: `notice/3`, `papy://example`). 없으면 읽음 처리만 한다
 */
@Serializable
data class InboxItem(
    val id: String,
    val title: String,
    val body: String? = null,
    /** 받은 시각(UTC epoch millis) */
    val createdAt: Long = 0,
    val read: Boolean = false,
    val link: String? = null,
)

@Serializable
internal data class InboxPageResponse(
    val items: List<InboxItem> = emptyList(),
    val nextPage: String? = null,
)

/**
 * 알림함 API 계약(가정). 푸시로 보낸 알림을 서버가 보관해 목록으로 준다(푸시를 놓쳐도 여기서 볼 수 있다).
 * 서버 연동은 [RemoteInboxApi], 서버 없이 개발할 때는 [SampleInboxApi] — di/InboxModule.kt 에서 정한다.
 */
interface InboxApi {
    suspend fun items(page: String?): Page<InboxItem>
    suspend fun markRead(id: String)
    suspend fun markAllRead()
}

internal class RemoteInboxApi(
    private val baseApi: BaseApi,
) : InboxApi {
    // {BASE_URL}/api/notifications?page=N
    override suspend fun items(page: String?): Page<InboxItem> {
        val response: InboxPageResponse = baseApi.get("/api/notifications", query = page?.let { mapOf("page" to it) }.orEmpty())
        return Page(items = response.items, nextKey = response.nextPage)
    }

    // {BASE_URL}/api/notifications/{id}/read
    override suspend fun markRead(id: String): Unit = baseApi.post("/api/notifications/$id/read", EmptyBody)

    // {BASE_URL}/api/notifications/read-all
    override suspend fun markAllRead(): Unit = baseApi.post("/api/notifications/read-all", EmptyBody)

    @Serializable
    private object EmptyBody
}

/** 서버 없이 화면을 확인하기 위한 예시 데이터. 읽음 상태는 앱이 떠 있는 동안만 유지된다 */
internal class SampleInboxApi : InboxApi {
    private val read = mutableSetOf("4", "5", "6")
    private var allRead = false

    override suspend fun items(page: String?): Page<InboxItem> = Page(
        items = listOf(
            sample(1, "New notice", "Tap to open notice 3.", link = "notice/3", hoursAgo = 1),
            sample(2, "Example updated", "Tap to open example item 2.", link = "example/2", hoursAgo = 5),
            sample(3, "Welcome", "Thanks for signing up.", link = null, hoursAgo = 26),
            sample(4, "Notices", "See all notices.", link = "notice", hoursAgo = 50),
            sample(5, "Settings", "Try the dark theme.", link = "settings", hoursAgo = 100),
            sample(6, "Old notification", null, link = null, hoursAgo = 400),
        ),
    )

    override suspend fun markRead(id: String) {
        read += id
    }

    override suspend fun markAllRead() {
        allRead = true
    }

    private fun sample(n: Int, title: String, body: String?, link: String?, hoursAgo: Int) = InboxItem(
        id = "$n",
        title = title,
        body = body,
        createdAt = NOW - hoursAgo * HOUR,
        read = allRead || "$n" in read,
        link = link,
    )

    private companion object {
        const val HOUR = 60 * 60 * 1000L

        /** 2026-09-30T00:00:00Z */
        const val NOW = 1_790_726_400_000L
    }
}
