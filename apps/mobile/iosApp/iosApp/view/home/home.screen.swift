import SwiftUI
import Core

struct HomeScreen: View {

    let onComponentGalleryTap: () -> Void
    let onExampleTap: () -> Void
    let onNoticeTap: () -> Void
    let onInboxTap: () -> Void
    let onSearchTap: () -> Void
    let onPermissionTap: () -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.homeViewModel(),
        state: \.uiState
    )

    @Environment(\.locale) private var locale

    private var greeting: String {
        let name = model.state.displayName
        return name.isEmpty
            ? String(localized: "home_greeting_anonymous")
            : String(format: String(localized: "home_greeting"), name)
    }

    var body: some View {
        // 목록·대시보드 화면: 회색 그룹 배경 위에 카드
        PapyScrollScreen {
            VStack(alignment: .leading, spacing: PapySpacing.xs) {
                Text(String(format: String(localized: "home_today"), PapyFormat.dateLong(Date(), locale: locale)))
                    .papyFont(PapyFont.bodySmall)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
                Text(greeting)
                    .papyFont(PapyFont.headline)
                    .foregroundStyle(PapyColor.onBackground)
            }
            .padding(.top, PapySpacing.xl)

            Text("home_section_menu")
                .papyFont(PapyFont.bodyStrong)
                .foregroundStyle(PapyColor.onSurfaceVariant)
                .padding(.horizontal, PapySpacing.xs)
                .padding(.top, PapySpacing.lg)

            PapyMenuCard(
                title: String(localized: "home_menu_example"),
                description: String(localized: "home_menu_example_description"),
                action: onExampleTap
            )

            PapyMenuCard(
                title: String(localized: "notice_title"),
                description: String(localized: "home_menu_notice_description"),
                action: onNoticeTap
            )

            PapyMenuCard(
                title: String(localized: "inbox_title"),
                description: String(localized: "home_menu_inbox_description"),
                action: onInboxTap
            )

            PapyMenuCard(
                title: String(localized: "search_title"),
                description: String(localized: "home_menu_search_description"),
                action: onSearchTap
            )

            PapyMenuCard(
                title: String(localized: "permission_title"),
                description: String(localized: "home_menu_permission_description"),
                action: onPermissionTap
            )

            PapyMenuCard(
                title: String(localized: "home_menu_gallery"),
                description: String(localized: "home_menu_gallery_description"),
                action: onComponentGalleryTap
            )
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

