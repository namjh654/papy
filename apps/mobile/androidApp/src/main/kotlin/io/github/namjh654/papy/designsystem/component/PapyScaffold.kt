package io.github.namjh654.papy.designsystem.component

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing

/**
 * 화면 기본 틀: 상단 바(제목/뒤로가기/액션) + 토스트 호스트.
 * 시스템 바·키보드 inset 은 MainActivity 루트에서 처리하므로 여기서는 0 으로 둔다.
 */
@Composable
fun PapyScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    toastState: PapyToastState? = null,
    containerColor: Color = PapyTheme.colors.background,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (title != null || onBack != null) PapyTopBar(title, onBack, actions)
        },
        snackbarHost = { toastState?.let { PapyToastHost(it) } },
        containerColor = containerColor,
        contentColor = PapyTheme.colors.onBackground,
        contentWindowInsets = WindowInsets(0.dp),
        content = content,
    )
}

@Composable
fun PapyTopBar(
    title: String?,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PapySize.topBarHeight)
            .padding(horizontal = PapySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.papy_ic_arrow_back),
                    contentDescription = stringResource(R.string.common_back),
                    tint = PapyTheme.colors.onBackground,
                )
            }
        } else {
            Spacer(Modifier.width(PapySpacing.md))
        }
        Box(modifier = Modifier.weight(1f)) {
            title?.let {
                Text(
                    text = it,
                    style = PapyTheme.typography.subtitle,
                    color = PapyTheme.colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/**
 * 화면 상단의 큰 제목 + 설명. 본문 첫 요소로 쓴다(상단 바 제목은 작게). iOS `PapyScreenHeader` 와 대응.
 */
@Composable
fun PapyScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = PapySpacing.md),
        verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
    ) {
        // 화면 읽기: 제목으로 알린다(제목 사이를 건너뛰며 읽을 수 있다)
        Text(text = title, style = PapyTheme.typography.headline, color = PapyTheme.colors.onBackground, modifier = Modifier.semantics { heading() })
        description?.let {
            Text(text = it, style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant)
        }
    }
}
