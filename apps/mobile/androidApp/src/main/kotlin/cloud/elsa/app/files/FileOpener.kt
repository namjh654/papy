package cloud.elsa.app.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import cloud.elsa.app.R
import cloud.elsa.app.core.data.files.DownloadedFile
import java.io.File

/**
 * 받아 둔 파일(30-1)을 다른 앱으로 연다·보낸다. 앱 전용 폴더라 FileProvider(`res/xml/file_paths.xml` 의 downloads)로 읽기 권한만 잠깐 준다.
 * iOS 는 QuickLook·공유 시트(`files/file.opener.swift`).
 */
object FileOpener {

    /** 열 앱이 없으면 false(화면이 안내) */
    fun open(context: Context, file: DownloadedFile): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, file), file.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** 공유 시트: 다른 앱으로 보내기·드라이브에 저장 등 */
    fun share(context: Context, file: DownloadedFile) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(file.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri(context, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.file_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun uri(context: Context, file: DownloadedFile) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(file.path))
}
