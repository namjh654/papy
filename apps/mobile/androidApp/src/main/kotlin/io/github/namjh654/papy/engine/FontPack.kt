package io.github.namjh654.papy.engine

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 명조·굵게 대체 글꼴(Noto Serif KR Regular·Bold, Noto Sans KR Bold, OFL-1.1). 앱에 넣지 않고 사용자가 고를 때만 받는다.
 * 엔진은 앱에 넣은 한글 글꼴(noBackupFilesDir/fonts)과 같은 폴더의 글꼴을 모두 읽으므로 같은 폴더에 둔다.
 * 엔진은 앱을 처음 쓸 때 글꼴을 한 번 읽어 두므로, 받은 글꼴은 앱을 다시 열면 쓰인다.
 * 받는 곳: GitHub Releases(fonts-v1), 안 되면 jsDelivr(같은 태그). 받은 파일은 SHA-256 으로 확인한다.
 */
object FontPack {
    private data class Font(val file: String, val bytes: Long, val sha256: String)

    private val fonts = listOf(
        Font("NotoSerifKR-Regular.ttf", 8_434_736, "ad1800d5b4fd538aeab7d2d6e528dea3bd77a79744c9cf32c81072e7ec9a2b86"),
        Font("NotoSerifKR-Bold.ttf", 8_439_212, "e7b59b63b716f68996c042a376ac36c6fbefd151cca11b50025805655e9b3af7"),
        Font("NotoSansKR-Bold.ttf", 2_474_372, "7db7302f86db389083623d100701c1c9811c2ad11e88c27c1b4f99a0b3e9cb8c"),
    )
    private val sources = listOf(
        "https://github.com/namjh654/papy/releases/download/fonts-v1/",
        "https://cdn.jsdelivr.net/gh/namjh654/papy@fonts-v1/",
    )

    /** 받을 크기(바이트) */
    val totalBytes: Long = fonts.sumOf { it.bytes }

    fun folder(context: Context) = File(context.noBackupFilesDir, "fonts")

    fun installed(context: Context): Boolean = fonts.all { File(folder(context), it.file).length() == it.bytes }

    /** 모두 받는다. `progress` 는 0~1. 실패하면 예외(받다 만 파일은 남기지 않는다). */
    suspend fun download(context: Context, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val dir = folder(context).apply { mkdirs() }
        var done = 0L
        for (font in fonts) {
            val target = File(dir, font.file)
            if (target.length() == font.bytes) {
                done += font.bytes
                continue
            }
            val part = File(dir, "${font.file}.part")
            var lastError: Exception? = null
            for (base in sources) {
                try {
                    fetch(URL(base + font.file), part) { read -> progress((done + read).toFloat() / totalBytes) }
                    check(part.length() == font.bytes && sha256(part) == font.sha256) { "checksum mismatch" }
                    check(part.renameTo(target)) { "rename failed" }
                    lastError = null
                    break
                } catch (e: Exception) {
                    part.delete()
                    lastError = e
                }
            }
            lastError?.let { throw it }
            done += font.bytes
            progress(done.toFloat() / totalBytes)
        }
    }

    private fun fetch(url: URL, out: File, read: (Long) -> Unit) {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        total += n
                        read(total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
