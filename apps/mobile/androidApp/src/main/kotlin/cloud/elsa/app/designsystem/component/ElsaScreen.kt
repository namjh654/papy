package cloud.elsa.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

/**
 * 스크롤 화면 틀: 배경(목록·대시보드는 grouped) + 세로 스크롤 + 화면 여백. 탭 루트·설정처럼 입력이 없는 화면에 쓴다.
 * 입력 폼은 ElsaFormColumn, 긴 목록은 ElsaPagedList. (iOS `ElsaScrollScreen`)
 */
@Composable
fun ElsaScrollScreen(
    modifier: Modifier = Modifier,
    grouped: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(ElsaSpacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (grouped) ElsaTheme.colors.backgroundGrouped else ElsaTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(ElsaSize.screenPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** 다른 화면으로 가는 카드 한 줄: 제목 + 설명 + 오른쪽 화살표 (iOS `ElsaMenuCard`) */
@Composable
fun ElsaMenuCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    ElsaCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xxs),
            ) {
                Text(text = title, style = ElsaTheme.typography.subtitle)
                description?.let {
                    Text(text = it, style = ElsaTheme.typography.bodySmall, color = ElsaTheme.colors.onSurfaceVariant)
                }
            }
            Icon(
                painter = painterResource(R.drawable.elsa_ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(ElsaSize.icon),
                tint = ElsaTheme.colors.onSurfaceTertiary,
            )
        }
    }
}
