package cloud.elsa.app.feature.settings.downloads

import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.data.files.DownloadedFile
import cloud.elsa.app.core.data.files.FileDownloads
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 저장한 파일(30-1): 채팅 등에서 받아 둔 파일을 보고 열기·공유·지우기, 모두 지우기. 파일은 로그아웃하면 지워진다.
 * Swift: `IOSAppGraph.shared.downloadsViewModel()`
 */
class DownloadsViewModel(
    private val downloads: FileDownloads,
) : StatefulViewModel<DownloadsUiState, DownloadsEvent>(DownloadsUiState()) {

    val uiState: StateFlow<DownloadsUiState> = state
    val events: Flow<DownloadsEvent> = eventFlow

    init {
        launch { downloads.files.collect { files -> setState { copy(files = files, totalSize = files.sumOf { it.size }) } } }
        launch {
            downloads.load()
            setState { copy(isLoading = false) }
        }
    }

    fun open(file: DownloadedFile) = launch { sendEvent(DownloadsEvent.Open(file)) }

    fun share(file: DownloadedFile) = launch { sendEvent(DownloadsEvent.Share(file)) }

    fun delete(file: DownloadedFile) = launch {
        downloads.delete(file.url)
        sendEvent(DownloadsEvent.Deleted)
    }

    /** 화면의 확인 다이얼로그 뒤에 부른다 */
    fun deleteAll() = launch {
        downloads.clear()
        sendEvent(DownloadsEvent.Deleted)
    }
}
