package io.github.namjh654.papy.feature.inbox

import io.github.namjh654.papy.core.common.paging.Page

/** 알림함 데이터. 안 읽은 개수 캐시·배지가 필요해지면 여기에 둔다. */
class InboxRepository(
    private val api: InboxApi,
) {
    suspend fun items(page: String?): Page<InboxItem> = api.items(page)

    suspend fun markRead(id: String) = api.markRead(id)

    suspend fun markAllRead() = api.markAllRead()
}
