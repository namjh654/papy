package cloud.elsa.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import cloud.elsa.app.R

/**
 * 하단 탭(순서 = 표시 순서). iOS `MainTab` 과 같은 탭을 둔다.
 *
 * 탭 추가 (docs/conventions.md §5): ① 여기에 한 줄 ② AndroidNavHost 에 composable(루트 화면, onBack 없음)
 * ③ strings.xml `tab_<name>` ④ 아이콘 drawable. `newFeature <name> --tab` 이 ①~③ 과 iOS 쪽을 함께 한다.
 */
enum class MainTab(
    val route: AndroidRoute,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Home(AndroidRoute.Home, R.string.tab_home, R.drawable.elsa_ic_tab_home),
    Example(AndroidRoute.Example, R.string.tab_example, R.drawable.elsa_ic_tab_list),
    Chat(AndroidRoute.Chat, R.string.tab_chat, R.drawable.elsa_ic_tab_chat),
    Community(AndroidRoute.Community, R.string.tab_community, R.drawable.elsa_ic_tab_community),
    // scaffold:tabs — newFeature --tab 이 이 줄 위에 탭을 추가한다
    Settings(AndroidRoute.Settings, R.string.tab_settings, R.drawable.elsa_ic_tab_settings),
}
