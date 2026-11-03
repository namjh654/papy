package cloud.elsa.app.screen.community

import androidx.compose.ui.semantics.Role
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.attachment.AttachmentFailure
import cloud.elsa.app.attachment.rememberAttachmentPicker
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.designsystem.component.ElsaAction
import cloud.elsa.app.designsystem.component.ElsaActionSheet
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaFormColumn
import cloud.elsa.app.designsystem.component.ElsaInlineMessage
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaRemoteImage
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.community.PostImage
import cloud.elsa.app.feature.community.editor.PostEditorEvent
import cloud.elsa.app.feature.community.editor.PostEditorViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 글쓰기·고치기(20-3): 여러 줄 입력(글자 수 n/2000), 사진 줄(최대 4장, 빼기), 사진 추가는 사진·카메라 메뉴, 하단 고정 올리기/저장.
 * @param postId 고칠 글, 새 글은 null
 */
@Composable
fun PostEditorScreen(
    postId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    // Koin 인자는 null 을 담지 못해 새 글은 "" (CommunityModule)
    val viewModel: PostEditorViewModel = koinViewModel(key = "editor-${postId.orEmpty()}") { parametersOf(postId.orEmpty()) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberElsaToastState()
    val scope = rememberCoroutineScope()
    // 새로 고른 사진의 기기 미리보기(key → Uri)
    val previews = remember { mutableStateMapOf<String, Uri>() }
    var showAdd by remember { mutableStateOf(false) }

    val limitText = stringResource(R.string.error_too_many_images)
    val deniedText = stringResource(R.string.chat_attach_denied)
    val unavailableText = stringResource(R.string.chat_camera_unavailable)
    val tooLargeText = stringResource(R.string.chat_attach_too_large)
    val unreadableText = stringResource(R.string.chat_attach_unreadable)

    val picker = rememberAttachmentPicker(
        maxPhotos = state.maxImages,
        onPicked = { input, preview ->
            val key = viewModel.addImage(input)
            if (key != null) previews[key] = preview else scope.launch { toast.show(limitText, ElsaMessageType.Error) }
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
            is PostEditorEvent.Saved -> onSaved()
        }
    }

    ElsaScaffold(
        title = stringResource(if (state.isEditing) R.string.community_edit_title else R.string.community_new_title),
        onBack = onBack,
        toastState = toast,
    ) { padding ->
        ElsaFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                ElsaButton(
                    text = stringResource(if (state.isEditing) R.string.community_save else R.string.community_post),
                    onClick = viewModel::save,
                    enabled = state.canSave || state.isSaving,
                    isLoading = state.isSaving,
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xs)) {
                BasicTextField(
                    value = state.content,
                    onValueChange = viewModel::contentChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .clip(RoundedCornerShape(ElsaRadius.lg))
                        .background(ElsaTheme.colors.fill)
                        .padding(ElsaSpacing.lg),
                    textStyle = ElsaTheme.typography.body.copy(color = ElsaTheme.colors.onSurface),
                    cursorBrush = SolidColor(ElsaTheme.colors.primary),
                    decorationBox = { inner ->
                        Box {
                            if (state.content.isEmpty()) {
                                Text(text = stringResource(R.string.community_content_placeholder), style = ElsaTheme.typography.body, color = ElsaTheme.colors.onSurfaceTertiary)
                            }
                            inner()
                        }
                    },
                )
                Text(
                    text = "${state.content.length}/${state.maxContent}",
                    style = ElsaTheme.typography.caption,
                    color = if (state.content.length > state.maxContent) ElsaTheme.colors.error else ElsaTheme.colors.onSurfaceTertiary,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm)) {
                itemsIndexed(state.images, key = { i, image -> if (image is PostImage.Local) image.key else "remote-$i" }) { index, image ->
                    Thumbnail(
                        image = image,
                        preview = (image as? PostImage.Local)?.let { previews[it.key] },
                        onRemove = { viewModel.removeImage(index) },
                    )
                }
                if (state.canAddImage) {
                    item(key = "add") {
                        Column(
                            modifier = Modifier
                                .size(ElsaSize.imageThumbnail)
                                .clip(RoundedCornerShape(ElsaRadius.md))
                                .background(ElsaTheme.colors.fill)
                                .clickable { showAdd = true },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(painter = painterResource(R.drawable.elsa_ic_image), contentDescription = null, tint = ElsaTheme.colors.onSurfaceVariant)
                            Text(text = "${state.images.size}/${state.maxImages}", style = ElsaTheme.typography.caption, color = ElsaTheme.colors.onSurfaceVariant)
                        }
                    }
                }
            }

            state.error?.let { ElsaInlineMessage(message = it.message(), type = ElsaMessageType.Error) }
        }
    }

    if (showAdd) {
        ElsaActionSheet(
            title = stringResource(R.string.community_add_photo),
            actions = listOf(
                ElsaAction(stringResource(R.string.attach_photo), R.drawable.elsa_ic_image, onClick = picker.pickPhoto),
                ElsaAction(stringResource(R.string.attach_camera), R.drawable.elsa_ic_camera, onClick = picker.takePhoto),
            ),
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun Thumbnail(image: PostImage, preview: Uri?, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(ElsaSize.imageThumbnail)) {
        val shape = RoundedCornerShape(ElsaRadius.md)
        Box(modifier = Modifier.size(ElsaSize.imageThumbnail).clip(shape).background(ElsaTheme.colors.fill)) {
            when {
                preview != null -> AsyncImage(model = preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(ElsaSize.imageThumbnail))
                image is PostImage.Remote && image.url.isNotBlank() -> ElsaRemoteImage(url = image.url, contentDescription = null, modifier = Modifier.size(ElsaSize.imageThumbnail))
            }
        }
        // 보이는 원은 24, 누르는 영역은 40(작은 손가락·접근성)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(ElsaTheme.colors.scrim), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.elsa_ic_close), contentDescription = stringResource(R.string.community_remove_photo), tint = ElsaTheme.colors.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
