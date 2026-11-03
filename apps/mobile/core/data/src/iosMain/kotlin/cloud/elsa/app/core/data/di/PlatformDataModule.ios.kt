package cloud.elsa.app.core.data.di

import cloud.elsa.app.core.data.connectivity.IOSNetworkMonitor
import cloud.elsa.app.core.data.connectivity.NetworkMonitor
import cloud.elsa.app.core.data.database.IOSSqlDriverFactory
import cloud.elsa.app.core.data.database.SqlDriverFactory
import cloud.elsa.app.core.data.files.DownloadDirectory
import cloud.elsa.app.core.data.files.iosDownloadDirectory
import cloud.elsa.app.core.data.permission.IOSPermissionController
import cloud.elsa.app.core.data.permission.PermissionController
import cloud.elsa.app.core.data.storage.IOSLocalStorage
import cloud.elsa.app.core.data.storage.IOSSecureStorage
import cloud.elsa.app.core.data.storage.LocalStorage
import cloud.elsa.app.core.data.storage.SecureStorage
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
