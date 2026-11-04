package io.github.namjh654.papy.designsystem.component

import androidx.compose.ui.semantics.Role
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing

/**
 * 목록 한 줄: [leading](아이콘·아바타) + 제목·부제(각 한·두 줄) + [trailing](시간·배지·스위치 등).
 * [onClick] 이 있으면 누를 수 있다. 대화 목록·게시글·사람 목록 등 목록 화면의 기본 행. iOS `PapyListItem` 과 같다.
 * 설정 화면은 값·화살표가 정해진 [PapySettingsRow] 를 쓴다.
 */
@Composable
fun PapyListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PapySize.controlHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = PapySpacing.xl, vertical = PapySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(PapySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PapySpacing.xxs)) {
            Text(
                text = title,
                style = PapyTheme.typography.bodyStrong,
                color = PapyTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = PapyTheme.typography.bodySmall,
                    color = PapyTheme.colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PapyListItemPreview() = PapyTheme {
    Column(modifier = Modifier.background(PapyTheme.colors.background)) {
        PapyListItem(
            title = "엘사",
            subtitle = "내일 회의 자료 공유드려요. 확인 부탁드립니다.",
            leading = null,
            trailing = { Text("오후 3:20", style = PapyTheme.typography.caption, color = PapyTheme.colors.onSurfaceTertiary) },
            onClick = {},
        )
        PapyListItem(
            title = "공지사항",
            leading = {
                Icon(
                    painter = painterResource(R.drawable.papy_ic_notification),
                    contentDescription = null,
                    modifier = Modifier.size(PapySize.icon),
                    tint = PapyTheme.colors.onSurfaceVariant,
                )
            },
            onClick = {},
        )
    }
}
