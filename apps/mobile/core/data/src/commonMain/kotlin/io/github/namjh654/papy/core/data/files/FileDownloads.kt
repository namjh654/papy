package io.github.namjh654.papy.core.data.files

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.error.toAppError
import io.github.namjh654.papy.core.data.database.LocalCache
import io.github.namjh654.papy.core.data.database.databaseDispatcher
import io.github.namjh654.papy.core.data.serialization.AppJson
import io.github.namjh654.papy.core.data.storage.LocalStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * 기기에 받아 둔 파일(30-1). [path] 는 앱 전용 폴더 안의 절대 경로 — 플랫폼이 열거나(Android FileProvider, iOS QuickLook) 공유한다.
 * Swift: `DownloadedFile` — `size`·`savedAt` 은 `Int64`
 */
@Serializable
data class DownloadedFile(
    val url: String,
    val name: String,
    val mimeType: String,
    val path: String,
    val size: Long,
    val savedAt: Long,
)

/** 받는 중·실패(받기를 마치면 상태에서 빠지고 [FileDownloads.files] 에 들어간다). Swift: `onEnum(of:)` */
sealed interface DownloadState {
    /** [progress] 0..1, 서버가 크기를 주지 않으면 null(돌아가는 표시) */
    data class Downloading(val progress: Float?) : DownloadState

    data class Failed(val error: UiError) : DownloadState
}

/** 파일 바이트를 받아 [sink] 로 흘려 보낸다(서버 연동은 [KtorFileTransport], 테스트는 대역) */
fun interface FileTransport {
    /** @param onProgress (받은 바이트, 전체 바이트 — 모르면 null) */
    suspend fun download(url: String, sink: okio.BufferedSink, onProgress: (Long, Long?) -> Unit)
}

/**
 * 파일 받기·보관(30-1, DEC-136): 채팅 첨부 등 서버 파일을 앱 전용 폴더에 받아 두고 다시 열 때 네트워크 없이 연다.
 * - 같은 주소는 한 번만 받는다(받는 중에 다시 눌러도 하나). 다 받기 전에는 `.part` 로 두었다가 끝나면 이름을 바꾼다
 * - 목록(이름·크기·받은 시각)은 LocalStorage 에, 파일은 `<downloads>/<주소 해시>/<이름>`
 * - 로그아웃(다른 사용자 로그인)하면 모두 지운다([LocalCache])
 *
 * Swift: `IOSAppGraph.shared.fileDownloads()`
 */
class FileDownloads(
    private val transport: FileTransport,
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val storage: LocalStorage,
    /** 파일 읽기·쓰기. 테스트는 EmptyCoroutineContext */
    private val context: CoroutineContext = databaseDispatcher,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : LocalCache {

    private val _files = MutableStateFlow<List<DownloadedFile>>(emptyList())
    /** 받아 둔 파일(최근 것 먼저) */
    val files: StateFlow<List<DownloadedFile>> = _files.asStateFlow()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    /** 주소 → 받는 중·실패 */
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    /** 저장된 목록을 읽는다(파일이 지워졌으면 목록에서도 뺀다). 처음 쓸 때 한 번 */
    suspend fun load() = mutex.withLock { ensureLoaded() }

    /** 받아 둔 파일(없으면 null) */
    suspend fun file(url: String): DownloadedFile? {
        load()
        return _files.value.firstOrNull { it.url == url }
    }

    /**
     * 받는다. 이미 받아 두었으면 그것을, 받는 중이면 아무것도 하지 않고 null.
     * 실패는 [states] 에 남기고 던진다(화면이 안내).
     */
    suspend fun download(url: String, name: String, mimeType: String): DownloadedFile? {
        file(url)?.let { return it }
        if (_states.value[url] is DownloadState.Downloading) return null
        setState(url, DownloadState.Downloading(null))
        val folder = directory / url.encodeUtf8().sha256().hex().take(HASH_LENGTH)
        val target = folder / safeName(name)
        val part = folder / "${safeName(name)}.part"
        try {
            withContext(context) { fileSystem.createDirectories(folder) }
            var lastReported = -1f
            // fileSystem.write { } 블록은 suspend 를 부를 수 없어 sink 를 직접 열고 닫는다
            val buffered = withContext(context) { fileSystem.sink(part).buffer() }
            try {
                transport.download(url, buffered) { read, total ->
                    val progress = total?.takeIf { it > 0 }?.let { (read.toFloat() / it).coerceIn(0f, 1f) }
                    // 1% 단위로만 알린다(상태를 너무 자주 바꾸지 않게)
                    if (progress == null || progress - lastReported >= 0.01f || progress == 1f) {
                        lastReported = progress ?: lastReported
                        setState(url, DownloadState.Downloading(progress))
                    }
                }
            } finally {
                withContext(context) { buffered.close() }
            }
            val saved = withContext(context) {
                fileSystem.atomicMove(part, target)
                DownloadedFile(url = url, name = name, mimeType = mimeType, path = target.toString(), size = fileSystem.metadata(target).size ?: 0, savedAt = now())
            }
            mutex.withLock {
                _files.value = listOf(saved) + _files.value.filterNot { it.url == url }
                saveIndex()
            }
            setState(url, null)
            return saved
        } catch (e: CancellationException) {
            withContext(context) { fileSystem.delete(part, mustExist = false) }
            setState(url, null)
            throw e
        } catch (e: Throwable) {
            withContext(context) { fileSystem.delete(part, mustExist = false) }
            val error = e.toAppError()
            setState(url, DownloadState.Failed(error.toUiError()))
            throw error
        }
    }

    suspend fun delete(url: String) = mutex.withLock {
        val file = _files.value.firstOrNull { it.url == url } ?: return@withLock
        withContext(context) { fileSystem.deleteRecursively(file.path.toPath().parent ?: file.path.toPath()) }
        _files.value = _files.value.filterNot { it.url == url }
        saveIndex()
    }

    /** 받은 파일 전체 크기(바이트) */
    val totalSize: Long get() = _files.value.sumOf { it.size }

    override suspend fun clear() = mutex.withLock {
        withContext(context) { fileSystem.deleteRecursively(directory) }
        _files.value = emptyList()
        _states.value = emptyMap()
        storage.remove(KEY_INDEX)
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val saved = storage.getString(KEY_INDEX)?.let { runCatching { AppJson.decodeFromString<List<DownloadedFile>>(it) }.getOrNull() }.orEmpty()
        val existing = withContext(context) { saved.filter { fileSystem.exists(it.path.toPath()) } }
        _files.value = existing
        if (existing.size != saved.size) saveIndex()
    }

    private suspend fun saveIndex() = storage.setString(KEY_INDEX, AppJson.encodeToString(_files.value))

    private fun setState(url: String, state: DownloadState?) =
        _states.update { if (state == null) it - url else it + (url to state) }

    companion object {
        internal const val KEY_INDEX = "downloads.index"
        private const val HASH_LENGTH = 16

        /** 경로에 쓸 수 없는 글자를 바꾼다(서버가 준 이름 그대로 보여 주되 파일 이름은 안전하게) */
        internal fun safeName(name: String): String =
            name.replace(Regex("""[/\\:*?"<>|\u0000-\u001f]"""), "_").trim().ifEmpty { "file" }.take(120)
    }
}
