package io.github.namjh654.papy.feature.example.di

import io.github.namjh654.papy.feature.example.ExampleApi
import io.github.namjh654.papy.feature.example.ExampleRepository
import io.github.namjh654.papy.feature.example.RemoteExampleApi
import io.github.namjh654.papy.feature.example.ExampleViewModel
import io.github.namjh654.papy.feature.example.SampleExampleApi
import io.github.namjh654.papy.feature.example.detail.ExampleDetailViewModel
import org.koin.dsl.module

/** feature:example 의 Koin 모듈 */
val exampleModule = module {
    // 서버가 준비되면 아래 줄을 이것으로 바꾼다: single<ExampleApi> { RemoteExampleApi(baseApi = get()) }
    single<ExampleApi> { SampleExampleApi() }
    single { ExampleRepository(api = get()) }
    factory { ExampleViewModel(repository = get()) }
    // 인자가 있는 화면: parametersOf(id)
    factory { (id: String) -> ExampleDetailViewModel(id = id, repository = get()) }
}
