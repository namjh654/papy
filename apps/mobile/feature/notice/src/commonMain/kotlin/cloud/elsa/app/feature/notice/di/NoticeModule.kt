package cloud.elsa.app.feature.notice.di

import cloud.elsa.app.feature.notice.NoticeApi
import cloud.elsa.app.feature.notice.NoticeRepository
import cloud.elsa.app.feature.notice.RemoteNoticeApi
import cloud.elsa.app.feature.notice.NoticeViewModel
import cloud.elsa.app.feature.notice.SampleNoticeApi
import cloud.elsa.app.feature.notice.detail.NoticeDetailViewModel
import org.koin.dsl.module

/** feature:notice 의 Koin 모듈 */
val noticeModule = module {
    // 서버가 준비되면 아래 줄을 이것으로 바꾼다: single<NoticeApi> { RemoteNoticeApi(baseApi = get()) }
    single<NoticeApi> { SampleNoticeApi() }
    single { NoticeRepository(api = get()) }
    factory { NoticeViewModel(repository = get()) }
    factory { (id: String) -> NoticeDetailViewModel(id = id, repository = get()) }
}
