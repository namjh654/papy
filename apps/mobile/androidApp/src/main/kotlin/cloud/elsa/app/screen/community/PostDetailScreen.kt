package cloud.elsa.app.screen.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.designsystem.component.ElsaAction
import cloud.elsa.app.designsystem.component.ElsaActionSheet
import cloud.elsa.app.designsystem.component.ElsaAlert
import cloud.elsa.app.designsystem.component.ElsaAlertDialog
import cloud.elsa.app.designsystem.component.ElsaAuthorRow
import cloud.elsa.app.designsystem.component.ElsaChatInputBar
import cloud.elsa.app.designsystem.component.ElsaCommentRow
import cloud.elsa.app.designsystem.component.ElsaErrorState
import cloud.elsa.app.designsystem.component.ElsaImageGrid
import cloud.elsa.app.designsystem.component.ElsaLikeButton
import cloud.elsa.app.designsystem.component.ElsaLoadingIndicator
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaPostImage
import cloud.elsa.app.designsystem.component.ElsaReactionButton
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.community.Comment
import cloud.elsa.app.feature.community.Post
import cloud.elsa.app.feature.community.ReportReason
import cloud.elsa.app.feature.community.detail.PostDetailEvent
import cloud.elsa.app.feature.community.detail.PostDetailViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** 지금 열린 메뉴 */
private sealed interface DetailMenu {
    data object Post : DetailMenu
    data class OfComment(val comment: Comment) : DetailMenu
    data class Report(val comment: Comment?) : DetailMenu
}

/**
 * 글 상세(20-3): 글쓴이·본문 전체·사진·좋아요/댓글 수, 댓글 목록, 아래 댓글 입력(키보드 위).
 * 위 더보기: 내 글이면 수정·삭제, 남의 글이면 신고·차단. 댓글 더보기도 같다(내 댓글은 삭제).
 */
@Composable
fun PostDetailScreen(
    postId: String,
    onEdit: (postId: String) -> Unit,
    onClosed: () -> Unit,
    onAuthorSelected: (userId: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: PostDetailViewModel = koinViewModel(key = "post-$postId") { parametersOf(postId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberElsaToastState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<DetailMenu?>(null) }
    var alert by remember { mutableStateOf<ElsaAlert?>(null) }

    val reportedText = stringResource(R.string.community_reported)
    val context = LocalContext.current

    EventEffect(viewModel.events) { event ->
        when (event) {
            is PostDetailEvent.EditRequested -> onEdit(event.post.id)
            PostDetailEvent.Closed -> onClosed()
            PostDetailEvent.Reported -> scope.launch { toast.show(reportedText, ElsaMessageType.Success) }
            is PostDetailEvent.ActionFailed -> scope.launch { toast.show(event.error.message(context), ElsaMessageType.Error) }
        }
    }

    val deleteTitle = stringResource(R.string.community_delete_title)
    val deleteCommentTitle = stringResource(R.string.community_delete_comment_title)
    val deleteMessage = stringResource(R.string.community_delete_message)
    val deleteText = stringResource(R.string.community_delete)
    val blockTitle = stringResource(R.string.community_block_title)
    val blockMessage = stringResource(R.string.community_block_message)
    val blockText = stringResource(R.string.community_block)
    val cancelText = stringResource(R.string.common_cancel)

    fun confirmBlock(userId: String) {
        alert = ElsaAlert(title = blockTitle, message = blockMessage, confirmText = blockText, dismissText = cancelText, isDestructive = true) {
            viewModel.block(userId)
        }
    }

    ElsaScaffold(
        onBack = onBack,
        toastState = toast,
        actions = {
            if (state.post != null) {
                IconButton(onClick = { menu = DetailMenu.Post }) {
                    Icon(painter = painterResource(R.drawable.elsa_ic_more), contentDescription = stringResource(R.string.community_more), tint = ElsaTheme.colors.onSurface)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            val post = state.post
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    post == null && state.error != null -> ElsaErrorState(
                        message = state.error!!.message(),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    post == null -> ElsaLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                    else -> LazyColumn(contentPadding = PaddingValues(horizontal = ElsaSize.screenPadding, vertical = ElsaSpacing.md)) {
                        item(key = "post") { PostBody(post = post, onLike = viewModel::likeClicked, onAuthorClick = { onAuthorSelected(post.author.id) }) }
                        item(key = "comments-title") {
                            Column {
                                HorizontalDivider(color = ElsaTheme.colors.divider)
                                Text(
                                    text = stringResource(R.string.community_comments),
                                    style = ElsaTheme.typography.bodyStrong,
                                    color = ElsaTheme.colors.onSurface,
                                    modifier = Modifier.padding(top = ElsaSpacing.lg, bottom = ElsaSpacing.xs),
                                )
                            }
                        }
                        if (state.comments.isEmpty()) {
                            item(key = "comments-empty") {
                                Text(
                                    text = stringResource(R.string.community_comment_empty),
                                    style = ElsaTheme.typography.bodySmall,
                                    color = ElsaTheme.colors.onSurfaceTertiary,
                                    modifier = Modifier.padding(vertical = ElsaSpacing.md),
                                )
                            }
                        }
                        items(state.comments, key = { it.id }) { comment ->
                            ElsaCommentRow(
                                authorName = comment.author.name,
                                avatarUrl = comment.author.avatarUrl,
                                time = postTime(comment.createdAt),
                                content = comment.content,
                                onMore = { menu = DetailMenu.OfComment(comment) },
                                onAuthorClick = { onAuthorSelected(comment.author.id) },
                            )
                        }
                    }
                }
            }
            if (post != null) {
                ElsaChatInputBar(
                    value = state.commentInput,
                    onValueChange = viewModel::commentInputChanged,
                    onSend = viewModel::sendComment,
                    onAttach = null,
                    canSend = state.canSendComment,
                    placeholder = stringResource(R.string.community_comment_placeholder),
                )
            }
        }
    }

    val current = menu
    val post = state.post
    if (current != null && post != null) {
        val actions: List<ElsaAction> = when (current) {
            DetailMenu.Post -> if (post.isMine) {
                listOf(
                    ElsaAction(stringResource(R.string.community_edit), R.drawable.elsa_ic_edit) { viewModel.editClicked() },
                    ElsaAction(deleteText, R.drawable.elsa_ic_delete, destructive = true) {
                        alert = ElsaAlert(title = deleteTitle, message = deleteMessage, confirmText = deleteText, dismissText = cancelText, isDestructive = true) {
                            viewModel.deletePost()
                        }
                    },
                )
            } else {
                othersActions(onReport = { menu = DetailMenu.Report(null) }, onBlock = { confirmBlock(post.author.id) })
            }
            is DetailMenu.OfComment -> if (current.comment.isMine) {
                listOf(
                    ElsaAction(deleteText, R.drawable.elsa_ic_delete, destructive = true) {
                        alert = ElsaAlert(title = deleteCommentTitle, message = deleteMessage, confirmText = deleteText, dismissText = cancelText, isDestructive = true) {
                            viewModel.deleteComment(current.comment)
                        }
                    },
                )
            } else {
                othersActions(onReport = { menu = DetailMenu.Report(current.comment) }, onBlock = { confirmBlock(current.comment.author.id) })
            }
            is DetailMenu.Report -> reportReasonActions { reason: ReportReason ->
                val comment = current.comment
                if (comment == null) viewModel.reportPost(reason) else viewModel.reportComment(comment, reason)
            }
        }
        ElsaActionSheet(
            title = if (current is DetailMenu.Report) stringResource(R.string.community_report_title) else null,
            actions = actions,
            // 메뉴 항목이 다음 메뉴(신고 사유)를 열 수 있으므로, 닫힐 때 그대로일 때만 지운다
            onDismiss = { if (menu == current) menu = null },
        )
    }

    ElsaAlertDialog(alert = alert, onDismiss = { alert = null })
}

/** 글 본문(미리보기 대상) */
@Composable
private fun PostBody(post: Post, onLike: () -> Unit, onAuthorClick: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.md), modifier = Modifier.padding(bottom = ElsaSpacing.sm)) {
        ElsaAuthorRow(name = post.author.name, avatarUrl = post.author.avatarUrl, time = postTime(post.createdAt), onMore = null, onAuthorClick = onAuthorClick)
        Text(text = post.content, style = ElsaTheme.typography.body, color = ElsaTheme.colors.onSurface)
        ElsaImageGrid(count = post.imageUrls.size) { index, modifier -> ElsaPostImage(url = post.imageUrls[index], modifier = modifier) }
        Row(horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.xs), modifier = Modifier.offset(x = -ElsaSpacing.sm)) {
            ElsaLikeButton(liked = post.likedByMe, count = post.likeCount, onClick = onLike)
            ElsaReactionButton(icon = R.drawable.elsa_ic_comment, count = post.commentCount, contentDescription = stringResource(R.string.community_comments), onClick = {})
        }
    }
}
