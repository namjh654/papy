package cloud.elsa.app.feature.settings.downloads

import cloud.elsa.app.core.common.viewmodel.UiEvent
import cloud.elsa.app.core.data.files.DownloadedFile

sealed interface DownloadsEvent : UiEvent {
    /** 연다(Android FileProvider·iOS QuickLook) */
    data class Open(val file: DownloadedFile) : DownloadsEvent

    /** 다른 앱으로 보내기·기기에 저장(공유 시트) */
    data class Share(val file: DownloadedFile) : DownloadsEvent

    data object Deleted : DownloadsEvent
}
