package cloud.elsa.app.designsystem.component

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSpacing

/**
 * 테두리·그림자 없는 카드. `backgroundGrouped` 배경(목록·대시보드 화면) 위에 놓는다.
 */
@Composable
fun ElsaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(ElsaSpacing.xl),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(ElsaSpacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ElsaRadius.xl),
        color = ElsaTheme.colors.surface,
        contentColor = ElsaTheme.colors.onSurface,
    ) {
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
