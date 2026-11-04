package io.github.namjh654.papy.screen.documents

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.component.PapyBottomSheet
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyErrorState
import io.github.namjh654.papy.designsystem.component.PapyLoadingIndicator
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapyTextField
import io.github.namjh654.papy.designsystem.component.PapyToastHost
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.engine.AndroidDocumentEngine
import io.github.namjh654.papy.feature.documents.DocumentEngine
import io.github.namjh654.papy.feature.documents.DocumentError
import io.github.namjh654.papy.feature.documents.PageSizePt
import io.github.namjh654.papy.feature.documents.viewer.ViewerPhase
import io.github.namjh654.papy.feature.documents.viewer.ViewerUiState
import io.github.namjh654.papy.feature.documents.viewer.ViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** 확대 상한. 그림은 화면 너비 × [RENDER_OVERSAMPLE] 로 그려 두고 확대는 화면에서 늘린다. */
private const val MAX_ZOOM = 4f
private const val RENDER_OVERSAMPLE = 1.5f
/** 쪽 그림 한 장의 최대 너비(px) — 메모리 */
private const val MAX_RENDER_WIDTH = 2400

@Composable
fun ViewerScreen(ref: String, name: String, onBack: () -> Unit) {
    val viewModel: ViewerViewModel = koinViewModel(key = ref) { parametersOf(ref, name) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberPapyToastState()

    PapyScaffold(
        title = name,
        onBack = onBack,
        toastState = toast,
        containerColor = PapyTheme.colors.backgroundGrouped,
        actions = {
            if (state.phase == ViewerPhase.Ready) {
                IconButton(onClick = viewModel::showText) {
                    Icon(painterResource(R.drawable.papy_ic_copy), contentDescription = stringResource(R.string.viewer_text), tint = PapyTheme.colors.onSurface)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.phase) {
                ViewerPhase.Opening -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PapyLoadingIndicator()
                    Text(stringResource(R.string.viewer_opening), style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant, modifier = Modifier.padding(top = PapySpacing.md))
                }
                ViewerPhase.NeedsPassword -> PasswordForm(state, viewModel::submitPassword)
                ViewerPhase.Failed -> PapyErrorState(message = errorMessage(state.error), onRetry = viewModel::retry, modifier = Modifier.align(Alignment.Center))
                ViewerPhase.Ready -> Pages(state, viewModel::pageVisible)
            }
            if (state.phase == ViewerPhase.Ready && state.showLoss) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(PapySpacing.sm)
                        .clip(RoundedCornerShape(12))
                        .background(PapyTheme.colors.onSurface)
                        .padding(start = PapySpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.viewer_loss, state.document?.droppedObjects ?: 0),
                        style = PapyTheme.typography.bodySmall,
                        color = PapyTheme.colors.surface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    IconButton(onClick = viewModel::dismissLoss) {
                        Icon(painterResource(R.drawable.papy_ic_close), contentDescription = stringResource(R.string.common_close), tint = PapyTheme.colors.surface)
                    }
                }
            }
        }
    }

    state.text?.let { text ->
        val clipboard = LocalClipboardManager.current
        val copied = stringResource(R.string.viewer_text_copied)
        val scope = rememberCoroutineScope()
        PapyBottomSheet(onDismiss = viewModel::hideText, title = stringResource(R.string.viewer_text_title, text.page + 1)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = PapySpacing.lg, vertical = PapySpacing.sm)) { when {
                text.text == null -> PapyLoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
                text.unavailable -> Text(stringResource(R.string.viewer_text_unavailable), style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant)
                else -> {
                    SelectionContainer(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(text.text.orEmpty(), style = PapyTheme.typography.body, color = PapyTheme.colors.onSurface)
                    }
                    PapyButton(
                        text = stringResource(R.string.viewer_text_copy),
                        onClick = {
                            clipboard.setText(AnnotatedString(text.text.orEmpty()))
                            scope.launch { toast.show(copied) }
                        },
                        modifier = Modifier.padding(top = PapySpacing.md),
                    )
                }
            } }
        }
    }
}

@Composable
private fun errorMessage(error: DocumentError?): String = stringResource(
    when (error) {
        DocumentError.Unreadable -> R.string.viewer_error_unreadable
        DocumentError.Unsupported, DocumentError.PasswordRequired -> R.string.viewer_error_unsupported
        DocumentError.Invalid -> R.string.viewer_error_invalid
        DocumentError.TooLarge -> R.string.viewer_error_too_large
        DocumentError.Internal, null -> R.string.viewer_error_internal
    },
)

@Composable
private fun PasswordForm(state: ViewerUiState, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(PapySpacing.lg), verticalArrangement = Arrangement.spacedBy(PapySpacing.md)) {
        PapyScreenHeader(
            title = stringResource(R.string.viewer_password_title),
            description = stringResource(R.string.viewer_password_message, state.name),
        )
        PapyTextField(
            value = password,
            onValueChange = { password = it },
            isSecure = true,
            keyboardType = KeyboardType.Password,
            errorText = if (state.wrongPassword) stringResource(R.string.viewer_password_wrong) else null,
        )
        PapyButton(
            text = stringResource(R.string.viewer_password_open),
            onClick = { onSubmit(password) },
            enabled = password.isNotEmpty(),
        )
    }
}

@Composable
private fun Pages(state: ViewerUiState, onPageVisible: (Int) -> Unit) {
    val engine = koinInject<DocumentEngine>() as? AndroidDocumentEngine ?: return
    val session = state.document?.session ?: return
    val listState = rememberLazyListState()
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoomChange, pan, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = if (zoom == 1f) Offset.Zero else offset + pan
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect(onPageVisible)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val renderWidth = (widthPx * RENDER_OVERSAMPLE).toInt().coerceIn(1, MAX_RENDER_WIDTH)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // 두 번 누르면 확대·되돌리기
                    detectTapGestures(onDoubleTap = {
                        zoom = if (zoom > 1f) 1f else 2f
                        offset = Offset.Zero
                    })
                }
                .transformable(transform, lockRotationOnZoomPan = true)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                },
            contentPadding = PaddingValues(PapySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
        ) {
            itemsIndexed(state.pageSizes, key = { index, _ -> index }) { index, size ->
                Page(engine, session, index, state.pageCount, size, renderWidth)
            }
        }
        Text(
            text = stringResource(R.string.viewer_page_indicator, state.currentPage + 1, state.pageCount),
            style = PapyTheme.typography.caption,
            color = PapyTheme.colors.surface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(PapySpacing.md)
                .clip(RoundedCornerShape(50))
                .background(PapyTheme.colors.onSurface.copy(alpha = 0.8f))
                .padding(horizontal = PapySpacing.md, vertical = PapySpacing.xs),
        )
    }
}

@Composable
private fun Page(engine: AndroidDocumentEngine, session: Long, index: Int, pageCount: Int, size: PageSizePt, renderWidth: Int) {
    val bitmap by produceState<Result<Bitmap?>?>(initialValue = null, session, index, renderWidth) {
        value = Result.success(withContext(Dispatchers.Default) { engine.render(session, index, renderWidth) })
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio((size.width / size.height).coerceIn(0.1f, 10f))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap?.getOrNull()
        when {
            image != null -> Image(image.asImageBitmap(), contentDescription = stringResource(R.string.viewer_page_indicator, index + 1, pageCount), modifier = Modifier.fillMaxSize())
            bitmap == null -> PapyLoadingIndicator()
            else -> Text(stringResource(R.string.viewer_page_failed), style = PapyTheme.typography.caption, color = Color.Gray)
        }
    }
}
