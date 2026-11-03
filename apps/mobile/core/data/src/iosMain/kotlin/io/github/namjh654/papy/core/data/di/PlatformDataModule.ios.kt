package io.github.namjh654.papy.core.data.di

import io.github.namjh654.papy.core.data.connectivity.IOSNetworkMonitor
import io.github.namjh654.papy.core.data.connectivity.NetworkMonitor
import io.github.namjh654.papy.core.data.database.IOSSqlDriverFactory
import io.github.namjh654.papy.core.data.database.SqlDriverFactory
import io.github.namjh654.papy.core.data.files.DownloadDirectory
import io.github.namjh654.papy.core.data.files.iosDownloadDirectory
import io.github.namjh654.papy.core.data.permission.IOSPermissionController
import io.github.namjh654.papy.core.data.permission.PermissionController
import io.github.namjh654.papy.core.data.storage.IOSLocalStorage
import io.github.namjh654.papy.core.data.storage.IOSSecureStorage
import io.github.namjh654.papy.core.data.storage.LocalStorage
import io.github.namjh654.papy.core.data.storage.SecureStorage
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDataModule: Module = module {
    single<LocalStorage> { IOSLocalStorage() }
    single<SecureStorage> { IOSSecureStorage() }
    single<NetworkMonitor> { IOSNetworkMonitor() }
    single<SqlDriverFactory> { IOSSqlDriverFactory() }
    // 받은 파일 보관(30-1): Application Support/downloads(사용자에게 보이지 않는 앱 폴더)
    single { DownloadDirectory(iosDownloadDirectory()) }
    single<PermissionController> { IOSPermissionController() }
}
