package cloud.elsa.app.feature.search.di

import cloud.elsa.app.feature.search.SearchApi
import cloud.elsa.app.feature.search.SearchRepository
import cloud.elsa.app.feature.search.SearchViewModel
import cloud.elsa.app.feature.search.SampleSearchApi
import org.koin.dsl.module

/** feature:search 의 Koin 모듈 */
val searchModule = module {
    // 서버가 준비되면 아래 줄을 이것으로 바꾼다: single<SearchApi> { RemoteSearchApi(baseApi = get()) }
    single<SearchApi> { SampleSearchApi() }
    single { SearchRepository(api = get(), storage = get()) }
    factory { SearchViewModel(repository = get()) }
}
