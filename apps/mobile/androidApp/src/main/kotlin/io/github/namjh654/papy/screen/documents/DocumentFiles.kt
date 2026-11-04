package io.github.namjh654.papy.screen.documents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** 파일 고르기에 보이는 형식 */
val DOCUMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/x-hwp",
    "application/haansofthwp",
    "application/vnd.hancom.hwp",
    "application/hwp+zip",
    "application/vnd.hancom.hwpx",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    // 많은 파일 앱이 HWP·HWPX 를 형식 없이(octet-stream) 알린다
    "application/octet-stream",
)

/** 보이는 파일 이름(없으면 URI 마지막 부분) */
fun Context.displayName(uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document"

/** 다음에 앱을 다시 켜도 최근 문서로 열 수 있게 읽기 권한을 유지한다(문서 공급자가 허락할 때만) */
fun Context.keepReadAccess(uri: Uri) {
    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}
