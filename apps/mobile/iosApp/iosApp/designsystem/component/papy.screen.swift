import SwiftUI

// MARK: - Scroll Screen

/// 스크롤 화면 틀: 배경(목록·대시보드는 grouped) + 세로 스크롤 + 화면 여백. 탭 루트·설정처럼 입력이 없는 화면에 쓴다.
/// 입력 폼은 PapyFormScrollView, 긴 목록은 PapyPagedList. (Android `PapyScrollScreen`)
struct PapyScrollScreen<Content: View>: View {
    var grouped = true
    var spacing: CGFloat = PapySpacing.md
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: spacing) {
                content()
            }
            .padding(PapySize.screenPadding)
        }
        .papyScreenBackground(grouped: grouped)
    }
}

// MARK: - Menu Card

/// 다른 화면으로 가는 카드 한 줄: 제목 + 설명 + 오른쪽 화살표 (Android `PapyMenuCard`)
struct PapyMenuCard: View {
    let title: String
    var description: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            PapyCard {
                HStack {
                    VStack(alignment: .leading, spacing: PapySpacing.xxs) {
                        Text(title)
                            .papyFont(PapyFont.subtitle)
                        if let description {
                            Text(description)
                                .papyFont(PapyFont.bodySmall)
                                .foregroundStyle(PapyColor.onSurfaceVariant)
                        }
                    }
                    Spacer()
                    Image(systemName: "chevron.forward")
                        .foregroundStyle(PapyColor.onSurfaceTertiary)
                }
            }
        }
        .buttonStyle(PapyPressableButtonStyle())
    }
}
