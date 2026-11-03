package cloud.elsa.app.core.data.files

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.data.storage.FakeLocalStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FileDownloadsTest {

    private val fs = FakeFileSystem()
    private val storage = FakeLocalStorage()
    private val dir = "/app/downloads".toPath()
    private var calls = 0
    private var fail: Throwable? = null
    private var gate: CompletableDeferred<Unit>? = null

    private val transport = FileTransport { url, sink, onProgress ->
        calls++
        fail?.let { throw it }
        val bytes = "hello $url".encodeToByteArray()
        onProgress(0, bytes.size.toLong())
        gate?.await()
        sink.write(bytes)
        onProgress(bytes.size.toLong(), bytes.size.toLong())
    }

    private fun downloads() = FileDownloads(transport, fs, dir, storage, context = EmptyCoroutineContext, now = { 7 })

    @Test
    fun downloadsOnceAndKeepsFile() = runTest {
        val d = downloads()
        val file = d.download("https://cdn/a.pdf", "a.pdf", "application/pdf")!!

        assertEquals("hello https://cdn/a.pdf", fs.read(file.path.toPath()) { readUtf8() })
        assertEquals(23, file.size)
        assertEquals(listOf(file), d.files.value)

        d.download("https://cdn/a.pdf", "a.pdf", "application/pdf") // 이미 있다
        assertEquals(1, calls)
        assertTrue(d.states.value.isEmpty())
    }

    @Test
    fun listSurvivesRestartAndDropsMissingFiles() = runTest {
        val first = downloads()
        val a = first.download("u1", "a.txt", "text/plain")!!
        first.download("u2", "b.txt", "text/plain")
        fs.delete(a.path.toPath()) // 기기에서 지워졌다

        val restarted = downloads()
        assertNull(restarted.file("u1"))
        assertEquals(listOf("u2"), restarted.files.value.map { it.url })
    }

    @Test
    fun failureIsReportedAndLeavesNoPartFile() = runTest {
        fail = AppError.Http(404, "")
        val d = downloads()

        assertFailsWith<AppError.Http> { d.download("u", "x.pdf", "application/pdf") }
        assertIs<DownloadState.Failed>(d.states.value["u"])
        assertTrue(fs.listRecursively(dir).none { it.name.endsWith(".part") })

        fail = null
        d.download("u", "x.pdf", "application/pdf") // 다시 받으면 실패 상태가 지워진다
        assertNull(d.states.value["u"])
    }

    @Test
    fun secondTapWhileDownloadingDoesNothing() = runTest(UnconfinedTestDispatcher()) {
        gate = CompletableDeferred()
        val d = downloads()
        val job = launch { d.download("u", "x.pdf", "application/pdf") }
        assertIs<DownloadState.Downloading>(d.states.value["u"])

        assertNull(d.download("u", "x.pdf", "application/pdf"))
        gate!!.complete(Unit)
        job.join()
        assertEquals(1, calls)
    }

    @Test
    fun deleteAndClear() = runTest {
        val d = downloads()
        d.download("u1", "a.txt", "text/plain")
        d.download("u2", "b.txt", "text/plain")

        d.delete("u1")
        assertEquals(listOf("u2"), d.files.value.map { it.url })

        d.clear() // 로그아웃
        assertTrue(d.files.value.isEmpty())
        assertTrue(!fs.exists(dir))
        assertNull(storage.getString(FileDownloads.KEY_INDEX))
    }

    @Test
    fun unsafeNamesAreCleaned() {
        assertEquals("a_b_.txt", FileDownloads.safeName("a/b:.txt"))
        assertEquals("file", FileDownloads.safeName("  "))
    }
}
