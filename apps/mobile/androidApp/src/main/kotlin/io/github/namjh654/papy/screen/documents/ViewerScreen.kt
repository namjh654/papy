package io.github.namjh654.papy.screen.documents

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
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
import io.github.namjh654.papy.feature.documents.PageBox
import io.github.namjh654.papy.feature.documents.PageSizePt
import io.github.namjh654.papy.feature.documents.viewer.ViewerEvent
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.namjh654.papy.feature.documents.CaretMove
import io.github.namjh654.papy.feature.documents.viewer.StyleToggle
import io.github.namjh654.papy.feature.documents.viewer.TableCommand
import io.github.namjh654.papy.feature.documents.viewer.ObjectSelection
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.drawWithContent
import io.github.namjh654.papy.feature.documents.viewer.ViewerPhase
import io.github.namjh654.papy.feature.documents.viewer.ViewerUiState
import io.github.namjh654.papy.feature.documents.viewer.ViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** 고른 문단·선택 표시 색(반투명 파랑), 커서 색 */
private val HIGHLIGHT = Color(0x553D5AFE)
private val CARET = Color(0xFF3D5AFE)
/** 숨긴 입력칸의 맨 앞 글자. 이 글자가 지워지면 "앞 글자 지우기"로 본다(입력기가 빈 칸에서는 지우기를 보내지 않는다) */
private const val SENTINEL = "\u200B"
private val EMPTY_INPUT = TextFieldValue(SENTINEL, selection = androidx.compose.ui.text.TextRange(1))
/** 도구 막대 글자 색 */
private val TEXT_COLORS = listOf("#000000" to R.string.viewer_color_black, "#e53935" to R.string.viewer_color_red, "#1e63d6" to R.string.viewer_color_blue)

/** 확대 상한. 손가락을 떼고 잠시 뒤 확대 배율에 맞춰 보이는 쪽을 다시 그린다(그동안은 이전 그림을 늘려 보인다). */
private const val MAX_ZOOM = 4f
private const val RENDER_OVERSAMPLE = 1.25f
/** 쪽 그림 한 장의 최대 픽셀 수(약 48MB, RGBA) — 메모리 */
private const val MAX_RENDER_PIXELS = 12_000_000f
/** 확대가 멈춘 뒤 다시 그리기까지 기다리는 시간 */
private const val RERENDER_DELAY_MS = 250L

@Composable
fun ViewerScreen(ref: String, name: String, onBack: () -> Unit) {
    val viewModel: ViewerViewModel = koinViewModel(key = ref) { parametersOf(ref, name) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberPapyToastState()
    val saved = stringResource(R.string.viewer_saved)
    val saveFailed = stringResource(R.string.viewer_save_failed)
    val readOnly = stringResource(R.string.viewer_save_readonly)
    val editFailed = stringResource(R.string.viewer_edit_failed)
    val exported = stringResource(R.string.viewer_exported)
    val exportFailed = stringResource(R.string.viewer_export_failed)
    val insertInCell = stringResource(R.string.viewer_insert_in_cell)
    val pictureFailed = stringResource(R.string.viewer_picture_failed)
    val context = androidx.compose.ui.platform.LocalContext.current
    // 그림 넣기: 사진 고르기(권한 없이 고른 사진만 읽는다). 픽셀 크기를 읽어 넣을 크기를 정한다
    val picturePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) } }
        viewModel.insertPicture(uri.toString(), bounds.outWidth, bounds.outHeight)
    }
    var tableDialog by remember { mutableStateOf(false) }
    // PDF 내보내기: 사용자가 저장할 곳을 고른다(Storage Access Framework)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.exportPdf(uri.toString())
    }

    val scope = rememberCoroutineScope()
    EventEffect(viewModel.events) { event ->
        when (event) {
            ViewerEvent.Saved -> scope.launch { toast.show(saved, PapyMessageType.Success) }
            is ViewerEvent.SaveFailed -> scope.launch {
                toast.show(if (event.error == DocumentError.ReadOnly) readOnly else saveFailed, PapyMessageType.Error)
            }
            ViewerEvent.EditFailed -> scope.launch { toast.show(editFailed, PapyMessageType.Error) }
            is ViewerEvent.Exported -> scope.launch { toast.show(exported, PapyMessageType.Success) }
            is ViewerEvent.ExportFailed -> scope.launch { toast.show(exportFailed, PapyMessageType.Error) }
            ViewerEvent.InsertNeedsBody -> scope.launch { toast.show(insertInCell, PapyMessageType.Error) }
            ViewerEvent.PictureFailed -> scope.launch { toast.show(pictureFailed, PapyMessageType.Error) }
            ViewerEvent.Exit -> onBack()
        }
    }
    // 저장하지 않은 변경이 있으면 뒤로 가기 전에 묻는다
    BackHandler(enabled = state.dirty) { viewModel.requestExit() }

    PapyScaffold(
        title = name,
        onBack = viewModel::requestExit,
        toastState = toast,
        containerColor = PapyTheme.colors.backgroundGrouped,
        actions = {
            if (state.phase == ViewerPhase.Ready) {
                if (state.editing && state.canUndo) {
                    IconButton(onClick = viewModel::undo) {
                        Icon(painterResource(R.drawable.papy_ic_reply), contentDescription = stringResource(R.string.viewer_undo), tint = PapyTheme.colors.onSurface)
                    }
                }
                if (state.dirty) {
                    PapyButton(
                        text = stringResource(R.string.viewer_save),
                        onClick = viewModel::save,
                        size = PapyButtonSize.Small,
                        fullWidth = false,
                        isLoading = state.saving,
                    )
                }
                if (state.canEdit) {
                    IconButton(onClick = viewModel::toggleEditing) {
                        Icon(
                            painterResource(if (state.editing) R.drawable.papy_ic_check else R.drawable.papy_ic_edit),
                            contentDescription = stringResource(if (state.editing) R.string.viewer_edit_done else R.string.viewer_edit),
                            tint = if (state.editing) PapyTheme.colors.primary else PapyTheme.colors.onSurface,
                        )
                    }
                }
                if (state.canExportPdf && !state.editing) {
                    IconButton(onClick = { exportLauncher.launch(state.pdfName) }, enabled = !state.exporting) {
                        Icon(painterResource(R.drawable.papy_ic_send), contentDescription = stringResource(R.string.viewer_export_pdf), tint = PapyTheme.colors.onSurface)
                    }
                }
                if (!state.editing && state.outline.isNotEmpty()) {
                    IconButton(onClick = viewModel::showOutline) {
                        Icon(painterResource(R.drawable.papy_ic_tab_list), contentDescription = stringResource(R.string.viewer_outline), tint = PapyTheme.colors.onSurface)
                    }
                }
                if (!state.editing) {
                    IconButton(onClick = viewModel::showSearch) {
                        Icon(painterResource(R.drawable.papy_ic_search), contentDescription = stringResource(R.string.viewer_search), tint = PapyTheme.colors.onSurface)
                    }
                }
                IconButton(onClick = viewModel::showText) {
                    Icon(painterResource(R.drawable.papy_ic_copy), contentDescription = stringResource(R.string.viewer_text), tint = PapyTheme.colors.onSurface)
                }
            }
        },
    ) { padding ->
        // 숨긴 입력칸: 맨 앞 SENTINEL 뒤 글자 중 [sent] 만큼은 이미 문서에 넣었다. 입력칸을 글자마다 비우면
        // 입력기 상태와 어긋나 글자가 겹치므로, 바뀐 만큼만 문서에 보내고 입력칸은 가끔만 비운다.
        var input by remember { mutableStateOf(EMPTY_INPUT) }
        var sent by remember { mutableStateOf("") }
        val body = input.text.removePrefix(SENTINEL)
        val composition = input.composition
        // 입력기가 아직 조합 중인 글자(쪽 위에 띄워 보인다)
        val pending = if (composition == null) "" else body.substring((composition.start - SENTINEL.length).coerceIn(0, body.length))
        /** 조합 중이던 글자를 마저 넣고 입력칸을 비운다(다른 곳을 누르거나 도구를 쓰기 전에) */
        val flush = {
            if (pending.isNotEmpty()) viewModel.type(pending)
            input = EMPTY_INPUT
            sent = ""
        }
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
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
                ViewerPhase.Ready -> Pages(
                    state = state,
                    composing = pending.takeIf { it.isNotEmpty() },
                    onPageVisible = viewModel::pageVisible,
                    gestures = PageGestures(
                        tap = { page, x, y -> flush(); viewModel.tap(page, x, y) },
                        doubleTap = { page, x, y -> flush(); viewModel.doubleTap(page, x, y) },
                        longPress = { page, x, y -> flush(); viewModel.longPress(page, x, y) },
                    ),
                )
            }
            if (state.phase == ViewerPhase.Ready && state.editing && state.caret != null) {
                EditInput(
                    value = input,
                    onValue = { value ->
                        val text = value.text
                        if (!text.startsWith(SENTINEL)) {
                            // 맨 앞 글자까지 지워졌다 → 넣은 글자 + 그 앞 한 글자를 지운다
                            repeat(sent.length + 1) { viewModel.backspace() }
                            text.replace(SENTINEL, "").takeIf { it.isNotEmpty() }?.let(viewModel::type)
                            input = EMPTY_INPUT
                            sent = ""
                        } else {
                            val all = text.removePrefix(SENTINEL)
                            // 조합이 끝난 부분(조합 중인 글자는 아직 넣지 않는다)
                            val stable = value.composition?.let { all.substring(0, (it.start - SENTINEL.length).coerceIn(0, all.length)) } ?: all
                            val common = sent.commonPrefixWith(stable).length
                            repeat(sent.length - common) { viewModel.backspace() }
                            stable.substring(common).takeIf { it.isNotEmpty() }?.let(viewModel::type)
                            sent = stable
                            // 조합이 없을 때 가끔 비운다(입력칸이 한없이 길어지지 않게)
                            if (value.composition == null && sent.length > 64) {
                                input = EMPTY_INPUT
                                sent = ""
                            } else {
                                input = value
                            }
                        }
                    },
                    onMove = { move -> flush(); viewModel.moveCaret(move) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
                Column(Modifier.align(Alignment.BottomCenter)) {
                    if (state.selected?.isTable == true) TableBar(viewModel, flush)
                    FormatBar(
                        state, viewModel, flush, Modifier,
                        onInsertTable = { flush(); tableDialog = true },
                        onInsertPicture = {
                            flush()
                            picturePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
            }
            if (state.phase == ViewerPhase.Ready && state.editing && state.selected?.isPicture == true) {
                PictureBar(viewModel, Modifier.align(Alignment.BottomCenter))
            }
            if (state.phase == ViewerPhase.Ready && state.editing && state.caret == null && state.paragraph == null && state.selected == null) {
                Text(
                    stringResource(R.string.viewer_edit_hint),
                    style = PapyTheme.typography.bodySmall,
                    color = PapyTheme.colors.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(PapySpacing.sm)
                        .clip(RoundedCornerShape(50))
                        .background(PapyTheme.colors.primary)
                        .padding(horizontal = PapySpacing.md, vertical = PapySpacing.xs),
                )
            }
            if (state.phase == ViewerPhase.Ready && state.showUnsupported) {
                Notice(
                    text = listOfNotNull(
                        state.unsupported.placeholders.takeIf { it > 0 }?.let { stringResource(R.string.viewer_unsupported_placeholders, it) },
                        state.unsupported.missingImages.takeIf { it > 0 }?.let { stringResource(R.string.viewer_unsupported_images, it) },
                    ).joinToString(" "),
                    onClose = viewModel::dismissUnsupported,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            if (state.phase == ViewerPhase.Ready && state.showLoss) {
                Notice(
                    text = stringResource(R.string.viewer_loss, state.document?.droppedObjects ?: 0),
                    onClose = viewModel::dismissLoss,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }

    if (state.showOutline) {
        PapyBottomSheet(onDismiss = viewModel::hideOutline, title = stringResource(R.string.viewer_outline)) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                itemsIndexed(state.outline) { _, entry ->
                    Text(
                        entry.title,
                        style = PapyTheme.typography.body,
                        color = if (entry.page == null) PapyTheme.colors.onSurfaceVariant else PapyTheme.colors.onSurface,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = entry.page != null) { entry.page?.let(viewModel::openOutlineEntry) }
                            .padding(start = PapySpacing.lg + PapySpacing.md * entry.depth.coerceAtMost(6), end = PapySpacing.lg)
                            .padding(vertical = PapySpacing.sm),
                    )
                }
            }
        }
    }

    if (state.showSearch) {
        val search = state.search
        // 창이 열리면 바로 칠 수 있게 입력칸에 초점을 둔다
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        PapyBottomSheet(onDismiss = viewModel::hideSearch, title = stringResource(R.string.viewer_search)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = PapySpacing.lg)) {
                PapyTextField(
                    value = search?.query.orEmpty(),
                    onValueChange = viewModel::searchQuery,
                    placeholder = stringResource(R.string.viewer_search_hint),
                    modifier = Modifier.focusRequester(focus),
                )
                val hits = search?.hits
                when {
                    search == null || search.query.isBlank() -> Unit
                    hits == null -> PapyLoadingIndicator(Modifier.align(Alignment.CenterHorizontally).padding(PapySpacing.md))
                    hits.isEmpty() -> Text(stringResource(R.string.viewer_search_none), style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant, modifier = Modifier.padding(vertical = PapySpacing.md))
                    else -> {
                        Text(
                            stringResource(if (search.more) R.string.viewer_search_more else R.string.viewer_search_count, hits.size),
                            style = PapyTheme.typography.caption,
                            color = PapyTheme.colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = PapySpacing.sm),
                        )
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                            itemsIndexed(hits) { _, hit ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.openHit(hit.page) }
                                        .padding(vertical = PapySpacing.sm),
                                ) {
                                    Text(
                                        stringResource(R.string.viewer_search_page, hit.page + 1),
                                        style = PapyTheme.typography.bodySmall,
                                        color = PapyTheme.colors.onSurfaceVariant,
                                        modifier = Modifier.widthIn(min = 44.dp),
                                    )
                                    Text(
                                        buildAnnotatedString {
                                            append(hit.before)
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = PapyTheme.colors.primary.copy(alpha = 0.18f))) { append(hit.matched) }
                                            append(hit.after)
                                        },
                                        style = PapyTheme.typography.bodySmall,
                                        color = PapyTheme.colors.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.paragraph?.let { hit ->
        var draft by remember(hit) { mutableStateOf(hit.text) }
        PapyBottomSheet(onDismiss = viewModel::cancelParagraph, title = stringResource(R.string.viewer_edit_title)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = PapySpacing.lg, vertical = PapySpacing.sm)) {
                PapyTextField(value = draft, onValueChange = { draft = it }, singleLine = false, minLines = 3)
                PapyButton(
                    text = stringResource(R.string.viewer_edit_apply),
                    onClick = { viewModel.applyParagraph(draft) },
                    modifier = Modifier.padding(top = PapySpacing.md),
                )
            }
        }
    }

    if (tableDialog) {
        TableDialog(onDismiss = { tableDialog = false }, onInsert = { rows, cols -> tableDialog = false; viewModel.insertTable(rows, cols) })
    }

    if (state.confirmExit) {
        AlertDialog(
            onDismissRequest = viewModel::cancelExit,
            title = { Text(stringResource(R.string.viewer_exit_title)) },
            text = { Text(stringResource(R.string.viewer_exit_message)) },
            confirmButton = { TextButton(onClick = viewModel::save) { Text(stringResource(R.string.viewer_save)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::cancelExit) { Text(stringResource(R.string.common_cancel)) }
                    TextButton(onClick = viewModel::discardAndExit) { Text(stringResource(R.string.viewer_exit_discard)) }
                }
            },
        )
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

/** 쪽 위에 뜨는 안내(닫기 단추) */
@Composable
private fun Notice(text: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(PapySpacing.sm)
            .clip(RoundedCornerShape(12))
            .background(PapyTheme.colors.onSurface)
            .padding(start = PapySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = PapyTheme.typography.bodySmall, color = PapyTheme.colors.surface, modifier = Modifier.weight(1f, fill = false))
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.papy_ic_close), contentDescription = stringResource(R.string.common_close), tint = PapyTheme.colors.surface)
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
        DocumentError.Internal, DocumentError.ReadOnly, null -> R.string.viewer_error_internal
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

/** 편집 모드에서 쪽을 누르는 동작(쪽 번호, pt 좌표) */
private class PageGestures(
    val tap: (Int, Float, Float) -> Unit,
    val doubleTap: (Int, Float, Float) -> Unit,
    val longPress: (Int, Float, Float) -> Unit,
)

@Composable
private fun Pages(state: ViewerUiState, composing: String?, onPageVisible: (Int) -> Unit, gestures: PageGestures) {
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
    // 목차·찾기에서 고른 쪽으로 옮긴다
    LaunchedEffect(state.jump) {
        state.jump?.let { jump -> listState.scrollToItem(jump.page.coerceIn(0, (state.pageSizes.size - 1).coerceAtLeast(0))) }
    }
    // 그릴 배율: 확대가 멈추고 [RERENDER_DELAY_MS] 뒤의 값(손가락을 움직이는 동안 매번 다시 그리지 않는다)
    var renderZoom by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        snapshotFlow { zoom }.distinctUntilChanged().collectLatest {
            delay(RERENDER_DELAY_MS)
            renderZoom = it
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val renderWidth = (widthPx * RENDER_OVERSAMPLE * renderZoom).toInt().coerceAtLeast(1)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.editing) {
                    // 두 번 누르면 확대·되돌리기(편집 모드에서는 단어 고르기라 쓰지 않는다)
                    if (!state.editing) {
                        detectTapGestures(onDoubleTap = {
                            zoom = if (zoom > 1f) 1f else 2f
                            offset = Offset.Zero
                        })
                    }
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
                Page(
                    engine = engine,
                    session = session,
                    index = index,
                    pageCount = state.pageCount,
                    size = size,
                    renderWidth = renderWidth,
                    revision = state.revision,
                    highlight = (state.selectionBoxes + state.paragraph?.boxes.orEmpty()).filter { it.page == index },
                    selected = state.selected?.takeIf { state.editing && it.target.box.page == index },
                    caret = state.caret?.box?.takeIf { it.page == index && state.editing },
                    composing = composing,
                    gestures = if (state.editing) gestures else null,
                )
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
private fun Page(
    engine: AndroidDocumentEngine,
    session: Long,
    index: Int,
    pageCount: Int,
    size: PageSizePt,
    renderWidth: Int,
    revision: Int,
    highlight: List<PageBox>,
    selected: ObjectSelection?,
    caret: PageBox?,
    composing: String?,
    gestures: PageGestures?,
) {
    // 픽셀 상한 안에서 너비를 정한다(세로로 긴 쪽은 더 작게)
    val aspect = (size.height / size.width.coerceAtLeast(1f)).coerceIn(0.1f, 10f)
    val width = minOf(renderWidth, kotlin.math.sqrt(MAX_RENDER_PIXELS / aspect).toInt()).coerceAtLeast(1)
    // 새 너비로 다시 그리는 동안 이전 그림을 그대로 둔다(깜박이지 않게)
    var bitmap by remember(session, index) { mutableStateOf<Result<Bitmap?>?>(null) }
    LaunchedEffect(session, index, width, revision) {
        val rendered = withContext(Dispatchers.Default) { engine.render(session, index, width) }
        if (rendered != null || bitmap?.getOrNull() == null) bitmap = Result.success(rendered)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio((size.width / size.height).coerceIn(0.1f, 10f))
            .background(Color.White)
            .then(
                if (gestures == null) Modifier
                else Modifier.pointerInput(gestures, size, index) {
                    // 화면 좌표 → 쪽 좌표(pt)
                    fun pt(offset: Offset) = offset.x / this.size.width * size.width to offset.y / this.size.height * size.height
                    detectTapGestures(
                        onTap = { pt(it).let { (x, y) -> gestures.tap(index, x, y) } },
                        onDoubleTap = { pt(it).let { (x, y) -> gestures.doubleTap(index, x, y) } },
                        onLongPress = { pt(it).let { (x, y) -> gestures.longPress(index, x, y) } },
                    )
                },
            )
            .drawWithContent {
                drawContent()
                val scale = this.size.width / size.width
                highlight.forEach { box ->
                    drawRect(
                        color = HIGHLIGHT,
                        topLeft = Offset(box.x * scale, box.y * scale),
                        size = androidx.compose.ui.geometry.Size(box.width * scale, box.height * scale),
                    )
                }
                // 고른 표·그림 테두리, 커서가 든 칸
                selected?.let { selection ->
                    val box = selection.target.box
                    val stroke = 2.dp.toPx()
                    selection.cell?.let { cell ->
                        drawRect(
                            color = HIGHLIGHT.copy(alpha = 0.18f),
                            topLeft = Offset(cell.box.x * scale, cell.box.y * scale),
                            size = androidx.compose.ui.geometry.Size(cell.box.width * scale, cell.box.height * scale),
                        )
                    }
                    drawRect(
                        color = CARET,
                        topLeft = Offset(box.x * scale, box.y * scale),
                        size = androidx.compose.ui.geometry.Size(box.width * scale, box.height * scale),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )
                }
                caret?.let { box ->
                    val width = 2.dp.toPx()
                    drawRect(
                        color = CARET,
                        topLeft = Offset(box.x * scale - width / 2, box.y * scale),
                        size = androidx.compose.ui.geometry.Size(width, box.height.coerceAtLeast(4f) * scale),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap?.getOrNull()
        when {
            image != null -> Image(image.asImageBitmap(), contentDescription = stringResource(R.string.viewer_page_indicator, index + 1, pageCount), modifier = Modifier.fillMaxSize())
            bitmap == null -> PapyLoadingIndicator()
            else -> Text(stringResource(R.string.viewer_page_failed), style = PapyTheme.typography.caption, color = Color.Gray)
        }
        // 입력기가 조합 중인 글자: 커서 자리에 띄워 보인다(넣으면 쪽에 그려진다)
        if (caret != null && composing != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val scale = constraints.maxWidth / size.width
                Text(
                    composing,
                    style = PapyTheme.typography.body,
                    color = Color.Black,
                    modifier = Modifier
                        .offset { IntOffset((caret.x * scale).toInt(), ((caret.y + caret.height) * scale).toInt()) }
                        .background(Color(0xFFFFF3B0))
                        .border(1.dp, CARET)
                        .padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/** 숨긴 입력칸: 키보드 글자를 받는다. 커서가 생기면 초점을 받아 키보드를 띄운다 */
@Composable
private fun EditInput(
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    onMove: (CaretMove) -> Unit,
    modifier: Modifier,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    BasicTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier
            .size(1.dp)
            .graphicsLayer { alpha = 0f }
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                // 하드웨어 키보드 화살표
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val move = when (event.key) {
                    Key.DirectionLeft -> CaretMove.Left
                    Key.DirectionRight -> CaretMove.Right
                    Key.DirectionUp -> CaretMove.Up
                    Key.DirectionDown -> CaretMove.Down
                    Key.MoveHome -> CaretMove.LineStart
                    Key.MoveEnd -> CaretMove.LineEnd
                    else -> return@onPreviewKeyEvent false
                }
                onMove(move)
                true
            },
    )
}

/** 편집 도구 막대: 글자 모양·크기·색, 범위 선택, 커서 이동 */
@Composable
private fun FormatBar(
    state: ViewerUiState,
    viewModel: ViewerViewModel,
    flush: () -> Unit,
    modifier: Modifier,
    onInsertTable: () -> Unit,
    onInsertPicture: () -> Unit,
) {
    val style = state.style
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PapyTheme.colors.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PapySpacing.sm, vertical = PapySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PapySpacing.xs),
    ) {
        @Composable
        fun Tool(label: String, description: Int, on: Boolean = false, weight: FontWeight? = null, italic: Boolean = false, decoration: TextDecoration? = null, action: () -> Unit) {
            TextButton(
                onClick = { flush(); action() },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) PapyTheme.colors.primary.copy(alpha = 0.15f) else Color.Transparent),
            ) {
                Text(
                    label,
                    style = PapyTheme.typography.body,
                    fontWeight = weight,
                    fontStyle = if (italic) FontStyle.Italic else null,
                    textDecoration = decoration,
                    color = if (on) PapyTheme.colors.primary else PapyTheme.colors.onSurface,
                    modifier = Modifier.described(description),
                )
            }
        }
        Tool("B", R.string.viewer_style_bold, on = style?.bold == true, weight = FontWeight.Bold) { viewModel.toggleStyle(StyleToggle.Bold) }
        Tool("I", R.string.viewer_style_italic, on = style?.italic == true, italic = true) { viewModel.toggleStyle(StyleToggle.Italic) }
        Tool("U", R.string.viewer_style_underline, on = style?.underline == true, decoration = TextDecoration.Underline) { viewModel.toggleStyle(StyleToggle.Underline) }
        Tool("S", R.string.viewer_style_strike, on = style?.strikethrough == true, decoration = TextDecoration.LineThrough) { viewModel.toggleStyle(StyleToggle.Strikethrough) }
        Tool("A−", R.string.viewer_font_smaller) { viewModel.changeFontSize(-1) }
        Text(style?.fontSize?.let { "${(it * 10).toInt() / 10f}" }.orEmpty(), style = PapyTheme.typography.caption, color = PapyTheme.colors.onSurfaceVariant)
        Tool("A+", R.string.viewer_font_larger) { viewModel.changeFontSize(1) }
        TEXT_COLORS.forEach { (hex, label) ->
            val description = stringResource(label)
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(2.dp, if (style?.color.equals(hex, ignoreCase = true)) PapyTheme.colors.primary else Color.Transparent, CircleShape)
                    .pointerInput(hex) { detectTapGestures { flush(); viewModel.setColor(hex) } }
                    .described(description),
            )
        }
        Tool("⇤⇥", R.string.viewer_select_extend, on = state.extending) { viewModel.toggleExtending() }
        if (state.selection != null) Tool("✕", R.string.viewer_select_clear) { viewModel.collapseSelection() }
        Tool("◀", R.string.viewer_caret_left) { viewModel.moveCaret(CaretMove.Left) }
        Tool("▶", R.string.viewer_caret_right) { viewModel.moveCaret(CaretMove.Right) }
        Tool("¶", R.string.viewer_edit_paragraph) {
            state.caret?.box?.let { viewModel.longPress(it.page, it.x, it.y + it.height / 2) }
        }
        Tool("▦", R.string.viewer_insert_table, action = onInsertTable)
        Tool("🖼", R.string.viewer_insert_picture, action = onInsertPicture)
    }
}

/** 도구 막대 단추(글자) */
@Composable
private fun BarButton(label: String, description: Int, action: () -> Unit) {
    TextButton(onClick = action) {
        Text(label, style = PapyTheme.typography.bodySmall, color = PapyTheme.colors.onSurface, maxLines = 1, softWrap = false, modifier = Modifier.described(description))
    }
}

/** 표 도구 막대: 커서가 든 칸 기준으로 행·열 넣기·지우기, 칸 합치기, 표 지우기 */
@Composable
private fun TableBar(viewModel: ViewerViewModel, flush: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PapyTheme.colors.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PapySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            TableCommand.RowAbove to R.string.viewer_table_row_above,
            TableCommand.RowBelow to R.string.viewer_table_row_below,
            TableCommand.ColumnLeft to R.string.viewer_table_col_left,
            TableCommand.ColumnRight to R.string.viewer_table_col_right,
            TableCommand.MergeRight to R.string.viewer_table_merge_right,
            TableCommand.MergeDown to R.string.viewer_table_merge_down,
            TableCommand.DeleteRow to R.string.viewer_table_delete_row,
            TableCommand.DeleteColumn to R.string.viewer_table_delete_col,
            TableCommand.DeleteTable to R.string.viewer_table_delete,
        ).forEach { (command, label) ->
            BarButton(stringResource(label), label) { flush(); viewModel.tableCommand(command) }
        }
    }
}

/** 그림 도구 막대: 크기, 지우기 */
@Composable
private fun PictureBar(viewModel: ViewerViewModel, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PapyTheme.colors.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PapySpacing.sm, vertical = PapySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PapySpacing.xs),
    ) {
        BarButton(stringResource(R.string.viewer_picture_smaller), R.string.viewer_picture_smaller) { viewModel.resizePicture(-1) }
        BarButton(stringResource(R.string.viewer_picture_larger), R.string.viewer_picture_larger) { viewModel.resizePicture(1) }
        BarButton(stringResource(R.string.viewer_picture_delete), R.string.viewer_picture_delete) { viewModel.deletePicture() }
        BarButton(stringResource(R.string.viewer_picture_done), R.string.viewer_picture_done) { viewModel.clearObject() }
    }
}

/** 표 넣기: 행·열 수 고르기 */
@Composable
private fun TableDialog(onDismiss: () -> Unit, onInsert: (Int, Int) -> Unit) {
    var rows by remember { mutableStateOf(3) }
    var cols by remember { mutableStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_insert_table)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PapySpacing.sm)) {
                Text(stringResource(R.string.viewer_table_size, rows, cols), style = PapyTheme.typography.body)
                @Composable
                fun Stepper(label: Int, value: Int, onChange: (Int) -> Unit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label), style = PapyTheme.typography.body, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onChange((value - 1).coerceAtLeast(1)) }) { Text("−") }
                        Text("$value", style = PapyTheme.typography.subtitle)
                        TextButton(onClick = { onChange((value + 1).coerceAtMost(20)) }) { Text("+") }
                    }
                }
                Stepper(R.string.viewer_table_rows, rows) { rows = it }
                Stepper(R.string.viewer_table_cols, cols) { cols = it }
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(rows, cols) }) { Text(stringResource(R.string.viewer_insert)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** 화면 낭독기용 이름 */
private fun Modifier.described(description: String) =
    this.then(Modifier.semantics { contentDescription = description })

@Composable
private fun Modifier.described(description: Int) = described(stringResource(description))
