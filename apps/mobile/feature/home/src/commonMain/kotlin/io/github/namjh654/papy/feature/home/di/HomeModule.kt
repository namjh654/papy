package io.github.namjh654.papy.feature.home.di

import io.github.namjh654.papy.feature.home.HomeViewModel
import org.koin.dsl.module

/** feature:home 의 Koin 모듈 */
val homeModule = module {
    factory { HomeViewModel(userStore = get()) }
}
