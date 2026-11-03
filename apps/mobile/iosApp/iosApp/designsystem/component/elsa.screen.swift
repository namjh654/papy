import SwiftUI

// MARK: - Scroll Screen

/// 스크롤 화면 틀: 배경(목록·대시보드는 grouped) + 세로 스크롤 + 화면 여백. 탭 루트·설정처럼 입력이 없는 화면에 쓴다.
/// 입력 폼은 ElsaFormScrollView, 긴 목록은 ElsaPagedList. (Android `ElsaScrollScreen`)
struct ElsaScrollScreen<Content: View>: View {
    var grouped = true
    var spacing: CGFloat = ElsaSpacing.md
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: spacing) {
                content()
            }
            .padding(ElsaSize.screenPadding)
        }
        .elsaScreenBackground(grouped: grouped)
    }
}

// MARK: - Menu Card

/// 다른 화면으로 가는 카드 한 줄: 제목 + 설명 + 오른쪽 화살표 (Android `ElsaMenuCard`)
struct ElsaMenuCard: View {
    let title: String
    var description: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ElsaCard {
                HStack {
                    VStack(alignment: .leading, spacing: ElsaSpacing.xxs) {
                        Text(title)
                            .elsaFont(ElsaFont.subtitle)
                        if let description {
                            Text(description)
                                .elsaFont(ElsaFont.bodySmall)
                                .foregroundStyle(ElsaColor.onSurfaceVariant)
                        }
                    }
                    Spacer()
                    Image(systemName: "chevron.forward")
                        .foregroundStyle(ElsaColor.onSurfaceTertiary)
                }
            }
        }
        .buttonStyle(ElsaPressableButtonStyle())
    }
}
