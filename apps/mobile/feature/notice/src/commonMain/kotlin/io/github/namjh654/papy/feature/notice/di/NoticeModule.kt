package io.github.namjh654.papy.feature.notice.di

import io.github.namjh654.papy.feature.notice.NoticeApi
import io.github.namjh654.papy.feature.notice.NoticeRepository
import io.github.namjh654.papy.feature.notice.RemoteNoticeApi
import io.github.namjh654.papy.feature.notice.NoticeViewModel
import io.github.namjh654.papy.feature.notice.SampleNoticeApi
import io.github.namjh654.papy.feature.notice.detail.NoticeDetailViewModel
import org.koin.dsl.module

/** feature:notice 의 Koin 모듈 */
val noticeModule = module {
    // 서버가 준비되면 아래 줄을 이것으로 바꾼다: single<NoticeApi> { RemoteNoticeApi(baseApi = get()) }
    single<NoticeApi> { SampleNoticeApi() }
    single { NoticeRepository(api = get()) }
    factory { NoticeViewModel(repository = get()) }
    factory { (id: String) -> NoticeDetailViewModel(id = id, repository = get()) }
}
