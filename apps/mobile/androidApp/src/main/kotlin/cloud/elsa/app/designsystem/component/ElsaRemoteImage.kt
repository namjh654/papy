package cloud.elsa.app.designsystem.component

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cloud.elsa.app.designsystem.theme.ElsaTheme
import coil3.compose.AsyncImage

/**
 * 서버 이미지(URL). 불러오는 동안과 실패했을 때는 회색 바탕만 보인다(자리 크기는 modifier 로 정한다).
 * 화면은 Coil 을 직접 쓰지 않고 이 컴포넌트를 쓴다 — 이미지 라이브러리를 바꿔도 여기만 고친다. (iOS `ElsaRemoteImage`)
 */
@Composable
fun ElsaRemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier.background(ElsaTheme.colors.surfaceVariant),
        contentScale = contentScale,
    )
}

/**
 * 동그란 프로필 사진. 우선순위: 방금 고른 사진([picked]) → 서버 사진([url]) → 이름 첫 글자. (iOS `ElsaAvatar`)
 */
@Composable
fun ElsaAvatar(
    name: String,
    modifier: Modifier = Modifier,
    url: String? = null,
    picked: Uri? = null,
    size: Dp = 56.dp,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(ElsaTheme.colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when {
            picked != null -> AsyncImage(
                model = picked,
                contentDescription = name,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
            !url.isNullOrBlank() -> ElsaRemoteImage(url = url, contentDescription = name, modifier = Modifier.size(size))
            else -> Text(
                text = name.trim().take(1).uppercase(),
                style = if (size >= 72.dp) ElsaTheme.typography.headline else ElsaTheme.typography.subtitle,
                color = ElsaTheme.colors.onPrimaryContainer,
            )
        }
    }
}
