import SwiftUI

/// 디자인 시스템 아이콘(DEC-118) — `design/icons` 에서 생성된 에셋 `papy_ic_<이름>`(템플릿 이미지).
/// 색은 `.foregroundStyle`, 크기는 토큰. Android `Icon(painterResource(R.drawable.papy_ic_<이름>), modifier = Modifier.size(…))` 와 같다.
struct PapyIcon: View {
    private let name: String
    private let size: CGFloat

    init(_ name: String, size: CGFloat = PapySize.icon) {
        self.name = name
        self.size = size
    }

    var body: some View {
        Image(name)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// 기본 뒤로 버튼을 대신할 때(확인을 거치거나 이전 단계로 갈 때) 쓰는 같은 모양의 버튼 — 시스템 관례 아이콘·글꼴 그대로
struct PapyBackButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: PapySpacing.xs) {
                Image(systemName: "chevron.backward")
                    .fontWeight(.semibold)
                Text("common_back")
            }
        }
        .accessibilityLabel(String(localized: "common_back"))
    }
}
