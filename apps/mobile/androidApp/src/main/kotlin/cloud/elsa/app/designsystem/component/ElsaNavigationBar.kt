package cloud.elsa.app.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize

/** 하단 탭 한 칸 */
data class ElsaTabItem(
    val label: String,
    @DrawableRes val icon: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** 0 보다 크면 아이콘 위 빨간 숫자 배지(99+) */
    val badge: Int = 0,
)

/** 하단 탭 바 (iOS TabView 대응). 탭 목록은 navigation/MainTab.kt */
@Composable
fun ElsaNavigationBar(
    items: List<ElsaTabItem>,
    modifier: Modifier = Modifier,
) {
    val c = ElsaTheme.colors
    NavigationBar(modifier = modifier, containerColor = c.surface, contentColor = c.onSurface) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                icon = {
                    BadgedBox(
                        badge = {
                            if (item.badge > 0) {
                                Badge(containerColor = c.error, contentColor = c.onError) {
                                    Text(text = if (item.badge > 99) "99+" else "${item.badge}", style = ElsaTheme.typography.caption)
                                }
                            }
                        },
                    ) {
                        Icon(painter = painterResource(item.icon), contentDescription = null, modifier = Modifier.size(ElsaSize.icon))
                    }
                },
                // 큰 글자 설정에서도 한 줄(넘치면 말줄임) — 탭 이름이 두 줄로 갈라지지 않게
                label = { Text(text = item.label, style = ElsaTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.onSurface,
                    selectedTextColor = c.onSurface,
                    unselectedIconColor = c.onSurfaceTertiary,
                    unselectedTextColor = c.onSurfaceTertiary,
                    indicatorColor = c.fill,
                ),
            )
        }
    }
}
