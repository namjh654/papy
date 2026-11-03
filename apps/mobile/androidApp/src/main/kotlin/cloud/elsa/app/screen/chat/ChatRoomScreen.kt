package cloud.elsa.app.screen.chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.core.data.files.DownloadState
import cloud.elsa.app.files.FileOpener
import cloud.elsa.app.attachment.AttachmentFailure
import cloud.elsa.app.attachment.rememberAttachmentPicker
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.core.data.media.AttachmentKind
import cloud.elsa.app.designsystem.component.ElsaAction
import cloud.elsa.app.designsystem.component.ElsaActionSheet
import cloud.elsa.app.designsystem.component.ElsaChatInputBar
import cloud.elsa.app.designsystem.component.ElsaDateSeparator
import cloud.elsa.app.designsystem.component.ElsaEmptyState
import cloud.elsa.app.designsystem.component.ElsaErrorState
import cloud.elsa.app.designsystem.component.ElsaImageViewer
import cloud.elsa.app.designsystem.component.ElsaListItem
import cloud.elsa.app.designsystem.component.ElsaLoadingIndicator
import cloud.elsa.app.designsystem.component.ElsaMessageBubble
import cloud.elsa.app.designsystem.component.ElsaMessageFile
import cloud.elsa.app.designsystem.component.ElsaMessageImage
import cloud.elsa.app.designsystem.component.ElsaMessageText
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaReplyBar
import cloud.elsa.app.designsystem.component.ElsaReplyQuote
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScrollToBottomButton
import cloud.elsa.app.designsystem.component.ElsaSearchBar
import cloud.elsa.app.designsystem.component.ElsaTypingIndicator
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.format.ElsaFormat
import cloud.elsa.app.designsystem.format.currentLocale
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.chat.Message
import cloud.elsa.app.feature.chat.room.BubblePosition
import cloud.elsa.app.feature.chat.room.ChatMessage
import cloud.elsa.app.feature.chat.room.ChatRoomEvent
import cloud.elsa.app.feature.chat.room.ChatRoomViewModel
import cloud.elsa.app.feature.chat.room.ChatRow
import cloud.elsa.app.feature.chat.room.SendState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 대화방(19-3): 아래부터 쌓이는 말풍선(reverseLayout), 맨 위에서 이전 메시지, 위로 스크롤하면 "맨 아래로" 버튼,
 * 입력창은 키보드 위에 붙는다. 첨부(사진·카메라·파일)는 액션 메뉴, 말풍선을 길게 누르면 답장·복사·다시 보내기·지우기.
 * 22: 오른쪽으로 밀어서 답장, 인용을 누르면 원문으로(잠깐 강조), 사진을 누르면 전체 화면, 위 돋보기로 대화 검색, 상대 입력 중 점 3개.
 */
@Composable
fun ChatRoomScreen(
    conversationId: String,
    title: String,
    onBack: () -> Unit,
    /** 프로필의 "메시지 보내기"(25-2): 대화 id 없이 사람으로 연다 → KMP 가 1:1 대화를 찾거나 만든다 */
    peerUserId: String? = null,
) {
    val viewModel: ChatRoomViewModel = koinViewModel(key = conversationId.ifEmpty { "dm-$peerUserId" }) {
        if (peerUserId != null) parametersOf(conversationId, title, peerUserId) else parametersOf(conversationId, title)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val toast = rememberElsaToastState()
    val listState = rememberLazyListState()

    // 보내는 중 사진의 기기 미리보기(말풍선 key → Uri). 서버 주소가 생겨도 이 화면에서는 계속 쓴다(깜빡임 없음)
    val previews = remember { mutableStateMapOf<String, Uri>() }
    var showAttach by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }
    var viewerModel by remember { mutableStateOf<Any?>(null) }
    var highlightKey by remember { mutableStateOf<String?>(null) }
    val searchFocus = remember { FocusRequester() }
    val notFoundText = stringResource(R.string.chat_not_found)
    val meName = stringResource(R.string.chat_reply_me)
    val photoText = stringResource(R.string.chat_attachment_photo)
    val fileText = stringResource(R.string.chat_attachment_file)
    fun replyName(isMine: Boolean) = if (isMine) meName else state.title
    fun previewText(text: String?, kind: AttachmentKind?) = text ?: if (kind == AttachmentKind.Image) photoText else fileText

    val copiedText = stringResource(R.string.chat_copied)
    val deniedText = stringResource(R.string.chat_attach_denied)
    val tooLargeText = stringResource(R.string.chat_attach_too_large)
    val unreadableText = stringResource(R.string.chat_attach_unreadable)
    val unavailableText = stringResource(R.string.chat_camera_unavailable)

    val picker = rememberAttachmentPicker(
        onPicked = { input, preview ->
            val key = viewModel.sendAttachment(input)
            if (input.kind == AttachmentKind.Image) previews[key] = preview
        },
        onFailed = { failure ->
            val text = when (failure) {
                AttachmentFailure.PermissionDenied -> deniedText
                AttachmentFailure.Unavailable -> unavailableText
                AttachmentFailure.TooLarge -> tooLargeText
                AttachmentFailure.Unreadable -> unreadableText
            }
            scope.launch { toast.show(text, ElsaMessageType.Error) }
        },
    )

    EventEffect(viewModel.events) { event ->
        when (event) {
            ChatRoomEvent.ScrollToBottom -> scope.launch { listState.animateScrollToItem(0) }
            is ChatRoomEvent.ScrollToMessage -> scope.launch {
                // reverseLayout + 맨 아래 입력 중 줄이 있으면 한 칸 밀린다
                val offset = if (state.isPeerTyping) 1 else 0
                val index = state.rows.asReversed().indexOfFirst { it.key == event.key }
                if (index >= 0) listState.animateScrollToItem(index + offset)
                highlightKey = event.key
                delay(1_500)
                highlightKey = null
            }
            ChatRoomEvent.MessageNotFound -> scope.launch { toast.show(notFoundText, ElsaMessageType.Info) }
            is ChatRoomEvent.OpenFile -> if (!FileOpener.open(context, event.file)) scope.launch { toast.show(context.getString(R.string.file_no_app), ElsaMessageType.Info) }
            is ChatRoomEvent.FileFailed -> scope.launch { toast.show(event.error.message(context), ElsaMessageType.Error) }
        }
    }

    // reverseLayout 이라 목록 끝 = 화면 맨 위 → 이전 메시지
    val nearTop by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearTop, state.hasOlder) { if (nearTop && state.hasOlder) viewModel.loadOlder() }
    val awayFromBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    val locale = currentLocale
    val is24Hour = DateFormat.is24HourFormat(context)

    BackHandler(enabled = state.isSearchOpen, onBack = viewModel::closeSearch)

    ElsaScaffold(
        title = if (state.isSearchOpen) null else state.title,
        onBack = if (state.isSearchOpen) viewModel::closeSearch else onBack,
        toastState = toast,
        containerColor = ElsaTheme.colors.backgroundGrouped,
        actions = {
            if (state.isSearchOpen) {
                ElsaSearchBar(
                    value = state.searchQuery,
                    onValueChange = viewModel::searchQueryChanged,
                    onSearch = {},
                    placeholder = stringResource(R.string.chat_search_placeholder),
                    modifier = Modifier.weight(1f).padding(end = ElsaSpacing.sm).focusRequester(searchFocus),
                )
                // 열자마자 입력
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
            } else {
                IconButton(onClick = viewModel::openSearch) {
                    Icon(painter = painterResource(R.drawable.elsa_ic_search), contentDescription = stringResource(R.string.chat_search), tint = ElsaTheme.colors.onSurface)
                }
            }
        },
    ) { padding ->
        if (state.isSearchOpen) {
            SearchResults(state.searchResults, state.searchQuery, state.isSearching, locale = currentLocale, onClick = viewModel::searchResultClicked, modifier = Modifier.padding(padding))
            return@ElsaScaffold
        }
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when {
                    state.isLoading && state.rows.isEmpty() -> ElsaLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null && state.rows.isEmpty() -> ElsaErrorState(
                        message = state.error!!.message(),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    state.rows.isEmpty() -> ElsaEmptyState(title = stringResource(R.string.chat_room_empty), modifier = Modifier.align(Alignment.Center))
                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = ElsaSpacing.md, vertical = ElsaSpacing.sm),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.isPeerTyping) item(key = "typing") { ElsaTypingIndicator(modifier = Modifier.padding(top = ElsaSpacing.sm)) }
                        items(state.rows.asReversed(), key = { it.key }) { row ->
                            when (row) {
                                is ChatRow.DateHeader -> ElsaDateSeparator(ElsaFormat.dateLong(row.dayStartMillis, locale))
                                is ChatRow.Bubble -> MessageRow(
                                    row = row,
                                    time = if (row.showTime) ElsaFormat.time(row.item.message.sentAt, locale, is24Hour) else null,
                                    preview = previews[row.item.key],
                                    // 파일: 받아 두고 연다(30-1) — 받는 중 표시는 fileStates
                                    onOpenFile = { viewModel.fileClicked(row.item.message) },
                                    fileState = row.item.message.attachment?.url?.let { state.fileStates[it] },
                                    fileSaved = row.item.message.attachment?.url in state.downloadedUrls,
                                    onLongClick = { menuFor = row.item },
                                    onRetry = { viewModel.retry(row.item.key) },
                                    replyName = row.item.message.replyTo?.let { replyName(row.item.replyIsMine) },
                                    replyText = row.item.message.replyTo?.let { previewText(it.text, it.attachmentKind) },
                                    onQuoteClick = { row.item.message.replyTo?.let { viewModel.replyQuoteClicked(it.messageId) } },
                                    onReply = { viewModel.replyClicked(row.item.key) },
                                    onImageClick = { model -> viewerModel = model },
                                    highlighted = highlightKey == row.item.key,
                                )
                            }
                        }
                    }
                }
                ElsaScrollToBottomButton(
                    visible = awayFromBottom,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(ElsaSpacing.lg),
                )
            }
            state.replyingTo?.let { target ->
                ElsaReplyBar(
                    name = replyName(target.isMine),
                    text = previewText(target.message.text, target.message.attachment?.kind),
                    onCancel = viewModel::cancelReply,
                )
            }
            ElsaChatInputBar(
                value = state.input,
                onValueChange = viewModel::inputChanged,
                onSend = viewModel::send,
                // 서버가 첨부를 끄면 버튼이 없다(FeatureKey.ChatAttachments)
                onAttach = if (state.attachmentsEnabled) ({ showAttach = true }) else null,
                canSend = state.canSend,
            )
        }
    }

    if (showAttach) {
        ElsaActionSheet(
            title = stringResource(R.string.attach_title),
            actions = listOf(
                ElsaAction(stringResource(R.string.attach_photo), R.drawable.elsa_ic_image, onClick = picker.pickPhoto),
                ElsaAction(stringResource(R.string.attach_camera), R.drawable.elsa_ic_camera, onClick = picker.takePhoto),
                ElsaAction(stringResource(R.string.attach_file), R.drawable.elsa_ic_file, onClick = picker.pickFile),
            ),
            onDismiss = { showAttach = false },
        )
    }

    viewerModel?.let { model -> ElsaImageViewer(model = model, onDismiss = { viewerModel = null }) }

    menuFor?.let { item ->
        val actions = buildList {
            if (item.sendState == SendState.Sent) {
                add(ElsaAction(stringResource(R.string.chat_reply), R.drawable.elsa_ic_reply) { viewModel.replyClicked(item.key) })
            }
            item.message.text?.let { text ->
                add(
                    ElsaAction(stringResource(R.string.chat_copy), R.drawable.elsa_ic_copy) {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                            toast.show(copiedText, ElsaMessageType.Success)
                        }
                    },
                )
            }
            if (item.sendState == SendState.Failed) {
                add(ElsaAction(stringResource(R.string.chat_retry), R.drawable.elsa_ic_reply) { viewModel.retry(item.key) })
                add(ElsaAction(stringResource(R.string.chat_delete), R.drawable.elsa_ic_delete, destructive = true) { viewModel.deleteFailed(item.key) })
            }
        }
        if (actions.isEmpty()) {
            menuFor = null
        } else {
            ElsaActionSheet(actions = actions, onDismiss = { menuFor = null })
        }
    }
}

/** 말풍선 한 줄: 묶음의 첫 줄은 위 간격을 넓게(묶음 사이), 안쪽은 좁게 */
@Composable
private fun MessageRow(
    row: ChatRow.Bubble,
    time: String?,
    preview: Uri?,
    onOpenFile: () -> Unit,
    fileState: DownloadState?,
    fileSaved: Boolean,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
    replyName: String?,
    replyText: String?,
    onQuoteClick: () -> Unit,
    onReply: () -> Unit,
    onImageClick: (Any) -> Unit,
    highlighted: Boolean,
) {
    val item = row.item
    // 오른쪽으로 밀어 답장(보낸 말풍선만): 64dp 넘게 밀었다 놓으면
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val canReply = item.sendState == SendState.Sent
    val attachment = item.message.attachment
    val startsGroup = row.position == BubblePosition.First || row.position == BubblePosition.Single
    val isImage = attachment?.kind == AttachmentKind.Image
    val highlight by animateColorAsState(
        if (highlighted) ElsaTheme.colors.primary.copy(alpha = 0.15f) else Color.Transparent,
        label = "highlight",
    )
    ElsaMessageBubble(
        isMine = item.isMine,
        position = row.position,
        modifier = Modifier
            .padding(top = if (startsGroup) ElsaSpacing.sm else ElsaSpacing.xxs)
            .background(highlight, RoundedCornerShape(ElsaRadius.md))
            .graphicsLayer { translationX = drag }
            .then(
                if (canReply) {
                    Modifier.pointerInput(item.key) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (drag > threshold) onReply()
                                drag = 0f
                            },
                            onDragCancel = { drag = 0f },
                            onHorizontalDrag = { _, dx -> drag = (drag + dx).coerceIn(0f, threshold * 1.3f) },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        time = time,
        sendState = item.sendState,
        isRead = item.isRead,
        fillContent = isImage,
        onClick = when {
            item.sendState == SendState.Failed -> onRetry
            attachment != null && !isImage && attachment.url.isNotBlank() -> onOpenFile
            isImage && (preview != null || attachment!!.url.isNotBlank()) -> ({ onImageClick(preview ?: attachment!!.url) })
            else -> null
        },
        onLongClick = onLongClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xs)) {
            if (replyName != null && replyText != null) {
                ElsaReplyQuote(
                    name = replyName,
                    text = replyText,
                    onClick = onQuoteClick,
                    modifier = if (isImage) Modifier.padding(ElsaSpacing.xs) else Modifier,
                )
            }
            when {
                attachment == null -> ElsaMessageText(item.message.text.orEmpty())
                isImage -> ElsaMessageImage(url = attachment.url, preview = preview, contentDescription = stringResource(R.string.chat_attachment_photo))
                else -> {
                    val size = ElsaFormat.fileSize(attachment.size, currentLocale)
                    ElsaMessageFile(
                        name = attachment.name,
                        size = when {
                            fileState is DownloadState.Failed -> stringResource(R.string.file_download_failed_short)
                            fileSaved -> stringResource(R.string.file_saved_caption, size)
                            else -> size
                        },
                        downloading = fileState is DownloadState.Downloading,
                        progress = (fileState as? DownloadState.Downloading)?.progress,
                    )
                }
            }
        }
    }
}

/** 대화 검색 결과(22-4): 메시지 한 줄 + 날짜. 누르면 그 메시지로 */
@Composable
private fun SearchResults(
    results: List<Message>,
    query: String,
    isSearching: Boolean,
    locale: java.util.Locale,
    onClick: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(ElsaTheme.colors.background)) {
        when {
            isSearching && results.isEmpty() -> ElsaLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            query.isNotBlank() && results.isEmpty() -> ElsaEmptyState(title = stringResource(R.string.chat_search_empty), modifier = Modifier.align(Alignment.Center))
            else -> LazyColumn {
                items(results, key = { it.id }) { message ->
                    ElsaListItem(
                        title = message.text.orEmpty(),
                        subtitle = ElsaFormat.date(message.sentAt, locale),
                        onClick = { onClick(message) },
                    )
                }
            }
        }
    }
}
