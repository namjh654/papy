import SwiftUI

/// 목록 한 줄: `leading`(아이콘·아바타) + 제목·부제(각 한·두 줄) + `trailing`(시간·배지·스위치 등).
/// `action` 이 있으면 누를 수 있다. 대화 목록·게시글·사람 목록 등 목록 화면의 기본 행. (Android `ElsaListItem`)
/// 설정 화면은 값·화살표가 정해진 `ElsaSettingsRow` 를 쓴다.
struct ElsaListItem<Leading: View, Trailing: View>: View {
    let title: String
    var subtitle: String? = nil
    var action: (() -> Void)? = nil
    @ViewBuilder var leading: () -> Leading
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        if let action {
            Button(action: action) { content }
                .buttonStyle(ElsaPressableButtonStyle())
        } else {
            content
        }
    }

    private var content: some View {
        HStack(spacing: ElsaSpacing.md) {
            leading()
            VStack(alignment: .leading, spacing: ElsaSpacing.xxs) {
                Text(title)
                    .elsaFont(ElsaFont.bodyStrong)
                    .foregroundStyle(ElsaColor.onSurface)
                    .lineLimit(1)
                if let subtitle {
                    Text(subtitle)
                        .elsaFont(ElsaFont.bodySmall)
                        .foregroundStyle(ElsaColor.onSurfaceVariant)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
            }
            Spacer(minLength: 0)
            trailing()
        }
        .padding(.horizontal, ElsaSpacing.xl)
        .padding(.vertical, ElsaSpacing.md)
        .frame(minHeight: ElsaSize.controlHeight)
        .contentShape(Rectangle())
    }
}

extension ElsaListItem where Leading == EmptyView {
    init(title: String, subtitle: String? = nil, action: (() -> Void)? = nil, @ViewBuilder trailing: @escaping () -> Trailing) {
        self.init(title: title, subtitle: subtitle, action: action, leading: { EmptyView() }, trailing: trailing)
    }
}

extension ElsaListItem where Trailing == EmptyView {
    init(title: String, subtitle: String? = nil, action: (() -> Void)? = nil, @ViewBuilder leading: @escaping () -> Leading) {
        self.init(title: title, subtitle: subtitle, action: action, leading: leading, trailing: { EmptyView() })
    }
}

extension ElsaListItem where Leading == EmptyView, Trailing == EmptyView {
    init(title: String, subtitle: String? = nil, action: (() -> Void)? = nil) {
        self.init(title: title, subtitle: subtitle, action: action, leading: { EmptyView() }, trailing: { EmptyView() })
    }
}

#Preview("목록 행") {
    VStack(spacing: 0) {
        ElsaListItem(
            title: "엘사",
            subtitle: "내일 회의 자료 공유드려요. 확인 부탁드립니다.",
            action: {},
            leading: { ElsaAvatar(name: "엘사", size: 48) },
            trailing: {
                Text("오후 3:20")
                    .elsaFont(ElsaFont.caption)
                    .foregroundStyle(ElsaColor.onSurfaceTertiary)
            }
        )
        ElsaListItem(title: "공지사항", action: {}, leading: {
            ElsaIcon("elsa_ic_notification").foregroundStyle(ElsaColor.onSurfaceVariant)
        })
    }
    .elsaScreenBackground()
}
