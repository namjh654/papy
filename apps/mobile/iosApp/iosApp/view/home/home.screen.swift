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
        ElsaScrollScreen {
            VStack(alignment: .leading, spacing: ElsaSpacing.xs) {
                Text(String(format: String(localized: "home_today"), ElsaFormat.dateLong(Date(), locale: locale)))
                    .elsaFont(ElsaFont.bodySmall)
                    .foregroundStyle(ElsaColor.onSurfaceVariant)
                Text(greeting)
                    .elsaFont(ElsaFont.headline)
                    .foregroundStyle(ElsaColor.onBackground)
            }
            .padding(.top, ElsaSpacing.xl)

            Text("home_section_menu")
                .elsaFont(ElsaFont.bodyStrong)
                .foregroundStyle(ElsaColor.onSurfaceVariant)
                .padding(.horizontal, ElsaSpacing.xs)
                .padding(.top, ElsaSpacing.lg)

            ElsaMenuCard(
                title: String(localized: "home_menu_example"),
                description: String(localized: "home_menu_example_description"),
                action: onExampleTap
            )

            ElsaMenuCard(
                title: String(localized: "notice_title"),
                description: String(localized: "home_menu_notice_description"),
                action: onNoticeTap
            )

            ElsaMenuCard(
                title: String(localized: "inbox_title"),
                description: String(localized: "home_menu_inbox_description"),
                action: onInboxTap
            )

            ElsaMenuCard(
                title: String(localized: "search_title"),
                description: String(localized: "home_menu_search_description"),
                action: onSearchTap
            )

            ElsaMenuCard(
                title: String(localized: "permission_title"),
                description: String(localized: "home_menu_permission_description"),
                action: onPermissionTap
            )

            ElsaMenuCard(
                title: String(localized: "home_menu_gallery"),
                description: String(localized: "home_menu_gallery_description"),
                action: onComponentGalleryTap
            )
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

