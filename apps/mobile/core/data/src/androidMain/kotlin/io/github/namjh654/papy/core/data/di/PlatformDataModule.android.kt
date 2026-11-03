package io.github.namjh654.papy.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.namjh654.papy.core.data.connectivity.AndroidNetworkMonitor
import io.github.namjh654.papy.core.data.connectivity.NetworkMonitor
import io.github.namjh654.papy.core.data.database.AndroidSqlDriverFactory
import io.github.namjh654.papy.core.data.database.SqlDriverFactory
import io.github.namjh654.papy.core.data.files.DownloadDirectory
import io.github.namjh654.papy.core.data.permission.AndroidPermissionController
import io.github.namjh654.papy.core.data.permission.PermissionController
import io.github.namjh654.papy.core.data.storage.AndroidLocalStorage
import io.github.namjh654.papy.core.data.storage.AndroidSecureStorage
import io.github.namjh654.papy.core.data.storage.LocalStorage
import io.github.namjh654.papy.core.data.storage.SecureStorage
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

/** Preferences DataStore. 파일 이름을 바꾸면 기존 저장 데이터를 잃는다. Context 는 initAndroidKoin 의 androidContext. */
internal actual val platformDataModule: Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                get<Context>().filesDir.resolve("papy.storage.preferences_pb").absolutePath.toPath()
            }
        )
    }

    single<LocalStorage> { AndroidLocalStorage(dataStore = get()) }

    // 보안 저장소: 별도 DataStore 파일 + Keystore 암호화 (DataStore 는 파일당 인스턴스 하나)
    single<SecureStorage> {
        AndroidSecureStorage(
            dataStore = PreferenceDataStoreFactory.createWithPath(
                produceFile = {
                    get<Context>().filesDir.resolve("papy.secure.preferences_pb").absolutePath.toPath()
                }
            )
        )
    }

    single<NetworkMonitor> { AndroidNetworkMonitor(context = get()) }

    // 받은 파일 보관(30-1): 앱 전용 폴더 — FileProvider(res/xml/file_paths.xml 의 downloads)로 다른 앱에 연다
    single { DownloadDirectory(get<Context>().filesDir.resolve("downloads").absolutePath) }

    // 기능 모듈 로컬 DB(SQLDelight) 드라이버
    single<SqlDriverFactory> { AndroidSqlDriverFactory(context = get()) }

    // 런타임 권한 — MainActivity 가 bind(activity) 로 권한 창을 연결한다
    single<PermissionController> { AndroidPermissionController(context = get()) }
}
