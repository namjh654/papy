package io.github.namjh654.papy.designsystem.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import coil3.compose.AsyncImage

/**
 * 이미지 선택 + 썸네일 미리보기. 시스템 포토피커를 사용하므로 저장소 권한이 필요 없다.
 *
 * 선택 결과 Uri 는 임시 읽기 권한만 가진다. 업로드 전까지 화면에 두는 용도로 쓰고,
 * 오래 보관해야 하면 앱 저장소로 복사하거나 즉시 업로드한다.
 *
 * @param maxCount 1 이면 단일 선택, 2 이상이면 다중 선택
 */
@Composable
fun PapyImagePicker(
    images: List<Uri>,
    onImagesChange: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    maxCount: Int = 1,
    label: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    require(maxCount >= 1) { "maxCount 는 1 이상" }
    val remaining = maxCount - images.size

    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImagesChange((images + uri).distinct().take(maxCount))
    }
    // PickMultipleVisualMedia 는 maxItems > 1 이어야 한다. 남은 개수는 결과에서 잘라낸다.
    val multipleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxOf(2, maxCount)),
    ) { uris ->
        if (uris.isNotEmpty()) onImagesChange((images + uris).distinct().take(maxCount))
    }
    val openPicker = {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (remaining > 1) multipleLauncher.launch(request) else singleLauncher.launch(request)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PapySpacing.xs),
    ) {
        label?.let { PapyFieldLabel(it, enabled) }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm),
        ) {
            images.forEach { uri ->
                ImageThumbnail(
                    uri = uri,
                    enabled = enabled,
                    onRemove = { onImagesChange(images - uri) },
                )
            }
            if (remaining > 0) {
                AddImageTile(
                    count = images.size,
                    maxCount = maxCount,
                    enabled = enabled,
                    onClick = openPicker,
                )
            }
        }

        PapyFieldFooter(helperText = helperText, errorText = errorText, counter = null)
    }
}

@Composable
private fun ImageThumbnail(uri: Uri, enabled: Boolean, onRemove: () -> Unit) {
    val c = PapyTheme.colors
    Box(modifier = Modifier.size(PapySize.imageThumbnail)) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.image_selected),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(PapySize.imageThumbnail)
                .clip(RoundedCornerShape(PapyRadius.md))
                .background(c.surfaceVariant),
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(PapySpacing.xs)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(c.scrim)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.image_remove), onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.papy_ic_close),
                    contentDescription = stringResource(R.string.image_remove),
                    tint = c.surface,
                    modifier = Modifier.size(PapySize.iconSmall),
                )
            }
        }
    }
}

@Composable
private fun AddImageTile(count: Int, maxCount: Int, enabled: Boolean, onClick: () -> Unit) {
    val c = PapyTheme.colors
    Surface(
        modifier = Modifier.size(PapySize.imageThumbnail),
        shape = RoundedCornerShape(PapyRadius.md),
        color = if (enabled) c.surfaceVariant else c.disabled,
        border = BorderStroke(PapySize.border, c.outline),
    ) {
        Column(
            modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(R.string.image_add), onClick = onClick),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.papy_ic_add),
                contentDescription = stringResource(R.string.image_add),
                tint = if (enabled) c.onSurfaceVariant else c.onDisabled,
            )
            if (maxCount > 1) {
                Text(
                    text = "$count/$maxCount",
                    style = PapyTheme.typography.caption,
                    color = c.onSurfaceVariant,
                )
            }
        }
    }
}
