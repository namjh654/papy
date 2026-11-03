package io.github.namjh654.papy.feature.notice

import io.github.namjh654.papy.core.common.paging.Page

/** 공지사항 데이터. 읽음 표시 저장·캐시가 생기면 여기에 둔다. */
class NoticeRepository(
    private val api: NoticeApi,
) {
    suspend fun items(page: String?): Page<NoticeItem> = api.items(page)

    suspend fun item(id: String): NoticeItem = api.item(id)
}
