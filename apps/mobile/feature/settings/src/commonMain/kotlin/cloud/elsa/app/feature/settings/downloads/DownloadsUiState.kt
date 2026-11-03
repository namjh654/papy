package cloud.elsa.app.feature.settings.downloads

import cloud.elsa.app.core.common.viewmodel.UiState
import cloud.elsa.app.core.data.files.DownloadedFile

/** 설정 → 저장한 파일(30-1): 받아 둔 파일 목록(최근 것 먼저)과 전체 크기 */
data class DownloadsUiState(
    val files: List<DownloadedFile> = emptyList(),
    val totalSize: Long = 0,
    val isLoading: Boolean = true,
) : UiState
