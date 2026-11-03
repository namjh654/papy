package cloud.elsa.app.feature.settings.downloads

import app.cash.turbine.test
import cloud.elsa.app.core.data.files.FileDownloads
import cloud.elsa.app.feature.settings.MemoryStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val downloads = FileDownloads(
        transport = { url, sink, _ -> sink.writeUtf8("data of $url") },
        fileSystem = FakeFileSystem(),
        directory = "/downloads".toPath(),
        storage = MemoryStorage(),
        context = EmptyCoroutineContext,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listsDownloadedFilesWithTotalSize() = runTest {
        downloads.download("u1", "a.pdf", "application/pdf")
        downloads.download("u2", "b.txt", "text/plain")
        val vm = DownloadsViewModel(downloads)

        val state = vm.uiState.value
        assertEquals(listOf("b.txt", "a.pdf"), state.files.map { it.name }) // 최근 것 먼저
        assertEquals(state.files.sumOf { it.size }, state.totalSize)
        assertFalse(state.isLoading)
    }

    @Test
    fun openShareDeleteAndDeleteAll() = runTest {
        val file = downloads.download("u1", "a.pdf", "application/pdf")!!
        downloads.download("u2", "b.txt", "text/plain")
        val vm = DownloadsViewModel(downloads)

        vm.events.test {
            vm.open(file)
            assertEquals(DownloadsEvent.Open(file), awaitItem())
            vm.share(file)
            assertEquals(DownloadsEvent.Share(file), awaitItem())
            vm.delete(file)
            assertEquals(DownloadsEvent.Deleted, awaitItem())
            assertEquals(listOf("u2"), vm.uiState.value.files.map { it.url })
            vm.deleteAll()
            assertEquals(DownloadsEvent.Deleted, awaitItem())
        }
        assertTrue(vm.uiState.value.files.isEmpty())
    }
}
