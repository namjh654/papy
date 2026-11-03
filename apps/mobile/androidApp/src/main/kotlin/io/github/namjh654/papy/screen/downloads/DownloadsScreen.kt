package io.github.namjh654.papy.screen.downloads

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.core.data.files.DownloadedFile
import io.github.namjh654.papy.designsystem.component.PapyAction
import io.github.namjh654.papy.designsystem.component.PapyActionSheet
import io.github.namjh654.papy.designsystem.component.PapyAlert
import io.github.namjh654.papy.designsystem.component.PapyAlertDialog
import io.github.namjh654.papy.designsystem.component.PapyEmptyState
import io.github.namjh654.papy.designsystem.component.PapyListItem
import io.github.namjh654.papy.designsystem.component.PapyLoadingIndicator
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.format.PapyFormat
import io.github.namjh654.papy.designsystem.format.currentLocale
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.feature.settings.downloads.DownloadsEvent
import io.github.namjh654.papy.feature.settings.downloads.DownloadsViewModel
import io.github.namjh654.papy.files.FileOpener
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 설정 → 저장한 파일(30-1): 받아 둔 파일(이름·크기·받은 날짜), 누르면 열기, 더보기 → 공유·지우기, 위 "모두 지우기".
 * iOS `downloads.screen.swift`
 */
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val viewModel: DownloadsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = rememberPapyToastState()
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<DownloadedFile?>(null) }
    var alert by remember { mutableStateOf<PapyAlert?>(null) }
    val noApp = stringResource(R.string.file_no_app)
    val deleted = stringResource(R.string.file_deleted)

    EventEffect(viewModel.events) { event ->
        when (event) {
            is DownloadsEvent.Open -> if (!FileOpener.open(context, event.file)) scope.launch { toast.show(noApp, PapyMessageType.Info) }
            is DownloadsEvent.Share -> FileOpener.share(context, event.file)
            DownloadsEvent.Deleted -> scope.launch { toast.show(deleted, PapyMessageType.Success) }
        }
    }

    val deleteAllTitle = stringResource(R.string.file_delete_all_title)
    val deleteAllText = stringResource(R.string.file_delete_all)
    val cancelText = stringResource(R.string.common_cancel)

    PapyScaffold(
        onBack = onBack,
        toastState = toast,
        actions = {
            if (state.files.isNotEmpty()) {
                IconButton(onClick = {
                    alert = PapyAlert(title = deleteAllTitle, confirmText = deleteAllText, dismissText = cancelText, isDestructive = true, onConfirm = viewModel::deleteAll)
                }) {
                    Icon(painter = painterResource(R.drawable.papy_ic_delete), contentDescription = deleteAllText, tint = PapyTheme.colors.onSurface)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                PapyLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                DownloadsList(files = state.files, totalSize = state.totalSize, onOpen = viewModel::open, onMore = { menuFor = it })
            }
        }
    }

    menuFor?.let { file ->
        PapyActionSheet(
            title = file.name,
            actions = listOf(
                PapyAction(label = stringResource(R.string.file_open)) { viewModel.open(file) },
                PapyAction(label = stringResource(R.string.file_share)) { viewModel.share(file) },
                PapyAction(label = stringResource(R.string.file_delete), destructive = true) { viewModel.delete(file) },
            ),
            onDismiss = { menuFor = null },
        )
    }
    PapyAlertDialog(alert = alert, onDismiss = { alert = null })
}

/** 목록(미리보기 대상): 큰 제목(전체 크기) → 파일 한 줄씩, 없으면 빈 화면 */
@Composable
fun DownloadsList(files: List<DownloadedFile>, totalSize: Long, onOpen: (DownloadedFile) -> Unit, onMore: (DownloadedFile) -> Unit) {
    val locale = currentLocale
    LazyColumn {
        item(key = "header") {
            PapyScreenHeader(
                title = stringResource(R.string.file_title),
                description = stringResource(R.string.file_description, PapyFormat.fileSize(totalSize, locale)),
                modifier = Modifier.padding(PapySize.screenPadding),
            )
        }
        if (files.isEmpty()) item(key = "empty") { PapyEmptyState(title = stringResource(R.string.file_empty)) }
        items(files, key = { it.url }) { file ->
            PapyListItem(
                title = file.name,
                subtitle = "${PapyFormat.fileSize(file.size, locale)} · ${PapyFormat.date(file.savedAt, locale)}",
                leading = { Icon(painter = painterResource(R.drawable.papy_ic_file), contentDescription = null, tint = PapyTheme.colors.onSurfaceVariant, modifier = Modifier.size(PapySize.icon)) },
                trailing = {
                    IconButton(onClick = { onMore(file) }) {
                        Icon(painter = painterResource(R.drawable.papy_ic_more), contentDescription = stringResource(R.string.community_more), tint = PapyTheme.colors.onSurfaceTertiary)
                    }
                },
                onClick = { onOpen(file) },
            )
        }
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DownloadsListPreview() = PapyTheme {
    Column(modifier = Modifier.background(PapyTheme.colors.background)) {
        DownloadsList(
            files = listOf(DownloadedFile("u1", "회의록.pdf", "application/pdf", "/x", 1_234_567, 0), DownloadedFile("u2", "guide.pdf", "application/pdf", "/y", 1_024, 0)),
            totalSize = 1_235_591,
            onOpen = {},
            onMore = {},
        )
    }
}
