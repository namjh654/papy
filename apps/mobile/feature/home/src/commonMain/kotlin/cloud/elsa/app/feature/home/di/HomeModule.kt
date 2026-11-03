package cloud.elsa.app.feature.home.di

import cloud.elsa.app.feature.home.HomeViewModel
import org.koin.dsl.module

/** feature:home 의 Koin 모듈 */
val homeModule = module {
    factory { HomeViewModel(userStore = get()) }
}
