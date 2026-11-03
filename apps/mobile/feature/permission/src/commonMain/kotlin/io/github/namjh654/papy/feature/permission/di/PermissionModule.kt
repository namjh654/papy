package io.github.namjh654.papy.feature.permission.di

import io.github.namjh654.papy.feature.permission.PermissionViewModel
import org.koin.dsl.module

/** feature:permission 의 Koin 모듈. PermissionController 는 core:data 가 플랫폼별로 제공한다 */
val permissionModule = module {
    factory { PermissionViewModel(permissions = get()) }
}
