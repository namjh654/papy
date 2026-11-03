package cloud.elsa.app.screen.downloads

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
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.core.data.files.DownloadedFile
import cloud.elsa.app.designsystem.component.ElsaAction
import cloud.elsa.app.designsystem.component.ElsaActionSheet
import cloud.elsa.app.designsystem.component.ElsaAlert
import cloud.elsa.app.designsystem.component.ElsaAlertDialog
import cloud.elsa.app.designsystem.component.ElsaEmptyState
import cloud.elsa.app.designsystem.component.ElsaListItem
import cloud.elsa.app.designsystem.component.ElsaLoadingIndicator
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.format.ElsaFormat
import cloud.elsa.app.designsystem.format.currentLocale
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.feature.settings.downloads.DownloadsEvent
import cloud.elsa.app.feature.settings.downloads.DownloadsViewModel
import cloud.elsa.app.files.FileOpener
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
    val toast = rememberElsaToastState()
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<DownloadedFile?>(null) }
    var alert by remember { mutableStateOf<ElsaAlert?>(null) }
    val noApp = stringResource(R.string.file_no_app)
    val deleted = stringResource(R.string.file_deleted)

    EventEffect(viewModel.events) { event ->
        when (event) {
            is DownloadsEvent.Open -> if (!FileOpener.open(context, event.file)) scope.launch { toast.show(noApp, ElsaMessageType.Info) }
            is DownloadsEvent.Share -> FileOpener.share(context, event.file)
            DownloadsEvent.Deleted -> scope.launch { toast.show(deleted, ElsaMessageType.Success) }
        }
    }

    val deleteAllTitle = stringResource(R.string.file_delete_all_title)
    val deleteAllText = stringResource(R.string.file_delete_all)
    val cancelText = stringResource(R.string.common_cancel)

    ElsaScaffold(
        onBack = onBack,
        toastState = toast,
        actions = {
            if (state.files.isNotEmpty()) {
                IconButton(onClick = {
                    alert = ElsaAlert(title = deleteAllTitle, confirmText = deleteAllText, dismissText = cancelText, isDestructive = true, onConfirm = viewModel::deleteAll)
                }) {
                    Icon(painter = painterResource(R.drawable.elsa_ic_delete), contentDescription = deleteAllText, tint = ElsaTheme.colors.onSurface)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                ElsaLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                DownloadsList(files = state.files, totalSize = state.totalSize, onOpen = viewModel::open, onMore = { menuFor = it })
            }
        }
    }

    menuFor?.let { file ->
        ElsaActionSheet(
            title = file.name,
            actions = listOf(
                ElsaAction(label = stringResource(R.string.file_open)) { viewModel.open(file) },
                ElsaAction(label = stringResource(R.string.file_share)) { viewModel.share(file) },
                ElsaAction(label = stringResource(R.string.file_delete), destructive = true) { viewModel.delete(file) },
            ),
            onDismiss = { menuFor = null },
        )
    }
    ElsaAlertDialog(alert = alert, onDismiss = { alert = null })
}

/** 목록(미리보기 대상): 큰 제목(전체 크기) → 파일 한 줄씩, 없으면 빈 화면 */
@Composable
fun DownloadsList(files: List<DownloadedFile>, totalSize: Long, onOpen: (DownloadedFile) -> Unit, onMore: (DownloadedFile) -> Unit) {
    val locale = currentLocale
    LazyColumn {
        item(key = "header") {
            ElsaScreenHeader(
                title = stringResource(R.string.file_title),
                description = stringResource(R.string.file_description, ElsaFormat.fileSize(totalSize, locale)),
                modifier = Modifier.padding(ElsaSize.screenPadding),
            )
        }
        if (files.isEmpty()) item(key = "empty") { ElsaEmptyState(title = stringResource(R.string.file_empty)) }
        items(files, key = { it.url }) { file ->
            ElsaListItem(
                title = file.name,
                subtitle = "${ElsaFormat.fileSize(file.size, locale)} · ${ElsaFormat.date(file.savedAt, locale)}",
                leading = { Icon(painter = painterResource(R.drawable.elsa_ic_file), contentDescription = null, tint = ElsaTheme.colors.onSurfaceVariant, modifier = Modifier.size(ElsaSize.icon)) },
                trailing = {
                    IconButton(onClick = { onMore(file) }) {
                        Icon(painter = painterResource(R.drawable.elsa_ic_more), contentDescription = stringResource(R.string.community_more), tint = ElsaTheme.colors.onSurfaceTertiary)
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
private fun DownloadsListPreview() = ElsaTheme {
    Column(modifier = Modifier.background(ElsaTheme.colors.background)) {
        DownloadsList(
            files = listOf(DownloadedFile("u1", "회의록.pdf", "application/pdf", "/x", 1_234_567, 0), DownloadedFile("u2", "guide.pdf", "application/pdf", "/y", 1_024, 0)),
            totalSize = 1_235_591,
            onOpen = {},
            onMore = {},
        )
    }
}
