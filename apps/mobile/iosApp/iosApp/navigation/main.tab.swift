import Foundation

/// 하단 탭(순서 = 표시 순서). Android `MainTab` 과 같은 탭을 둔다.
///
/// 탭 추가 (docs/conventions.md §5): ① case 한 줄 + title·icon ② AppNavigation 의 `root(for:)` 에 루트 화면
/// ③ Localizable.xcstrings `tab_<name>`. `newFeature <name> --tab` 이 ①~③ 과 Android 쪽을 함께 한다.
enum MainTab: Hashable, CaseIterable {
    case home
    case example
    case chat
    case community
    // scaffold:tabs — newFeature --tab 이 이 줄 위에 탭을 추가한다
    case settings

    var title: String.LocalizationValue {
        switch self {
        case .home: "tab_home"
        case .example: "tab_example"
        case .chat: "tab_chat"
        case .community: "tab_community"
        // scaffold:tab-titles
        case .settings: "tab_settings"
        }
    }

    /// 아이콘 에셋(design/icons, DEC-118) — Android `MainTab` 과 같은 아이콘
    var icon: String {
        switch self {
        case .home: "papy_ic_tab_home"
        case .example: "papy_ic_tab_list"
        case .chat: "papy_ic_tab_chat"
        case .community: "papy_ic_tab_community"
        // scaffold:tab-images
        case .settings: "papy_ic_tab_settings"
        }
    }
}
