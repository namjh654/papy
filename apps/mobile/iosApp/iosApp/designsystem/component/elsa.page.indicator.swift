import SwiftUI

/// 쪽 표시 점(온보딩·이미지 넘기기). 지금 쪽은 길고 진한 점. (Android `ElsaPageIndicator`)
struct ElsaPageIndicator: View {
    let pageCount: Int
    let currentPage: Int

    var body: some View {
        HStack(spacing: ElsaSpacing.sm) {
            ForEach(0..<pageCount, id: \.self) { index in
                Capsule()
                    .fill(index == currentPage ? ElsaColor.primary : ElsaColor.outline)
                    .frame(width: index == currentPage ? 20 : 8, height: 8)
            }
        }
        .animation(.easeOut(duration: ElsaMotion.fast), value: currentPage)
        .accessibilityHidden(true)
    }
}
