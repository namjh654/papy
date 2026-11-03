package cloud.elsa.app.feature.inbox.di

import cloud.elsa.app.feature.inbox.InboxApi
import cloud.elsa.app.feature.inbox.InboxRepository
import cloud.elsa.app.feature.inbox.InboxViewModel
import cloud.elsa.app.feature.inbox.SampleInboxApi
import org.koin.dsl.module

/** feature:inbox 의 Koin 모듈 */
val inboxModule = module {
    // 서버가 준비되면 아래 줄을 이것으로 바꾼다: single<InboxApi> { RemoteInboxApi(baseApi = get()) }
    single<InboxApi> { SampleInboxApi() }
    single { InboxRepository(api = get()) }
    factory { InboxViewModel(repository = get()) }
}
