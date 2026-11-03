package io.github.namjh654.papy.core.data.files

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Application Support/downloads — 앱 폴더라 사용자 파일 앱에는 보이지 않는다(공유·"파일에 저장"으로 내보낸다) */
@OptIn(ExperimentalForeignApi::class)
internal fun iosDownloadDirectory(): String {
    val base = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    return requireNotNull(base?.path) { "Application Support 폴더 없음" } + "/downloads"
}
