import SwiftUI

/// 쪽 표시 점(온보딩·이미지 넘기기). 지금 쪽은 길고 진한 점. (Android `PapyPageIndicator`)
struct PapyPageIndicator: View {
    let pageCount: Int
    let currentPage: Int

    var body: some View {
        HStack(spacing: PapySpacing.sm) {
            ForEach(0..<pageCount, id: \.self) { index in
                Capsule()
                    .fill(index == currentPage ? PapyColor.primary : PapyColor.outline)
                    .frame(width: index == currentPage ? 20 : 8, height: 8)
            }
        }
        .animation(.easeOut(duration: PapyMotion.fast), value: currentPage)
        .accessibilityHidden(true)
    }
}
