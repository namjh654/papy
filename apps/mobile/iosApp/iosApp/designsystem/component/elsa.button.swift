import SwiftUI

enum ElsaButtonStyleKind { case primary, secondary, outline, text, destructive }

enum ElsaButtonSize { case large, small }

/// 공통 버튼. Android `ElsaButton` 과 같은 style/size/isLoading 의미를 가진다.
///
/// - `isLoading`: 스피너를 표시하고 탭을 무시한다(색은 유지).
/// - 비활성화는 `enabled:` 또는 SwiftUI `.disabled(_:)` 둘 다 가능.
struct ElsaButton: View {
    let title: String
    var style: ElsaButtonStyleKind = .primary
    var size: ElsaButtonSize = .large
    var enabled = true
    var isLoading = false
    var fullWidth = true
    var icon: String? = nil
    let action: () -> Void

    @Environment(\.isEnabled) private var environmentEnabled

    init(
        _ title: String,
        style: ElsaButtonStyleKind = .primary,
        size: ElsaButtonSize = .large,
        enabled: Bool = true,
        isLoading: Bool = false,
        fullWidth: Bool = true,
        icon: String? = nil,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.style = style
        self.size = size
        self.enabled = enabled
        self.isLoading = isLoading
        self.fullWidth = fullWidth
        self.icon = icon
        self.action = action
    }

    private var isActive: Bool { enabled && environmentEnabled }

    var body: some View {
        Button {
            if !isLoading { action() }
        } label: {
            HStack(spacing: ElsaSpacing.sm) {
                if isLoading {
                    ProgressView()
                        .tint(foreground)
                        .controlSize(.small)
                } else if let icon {
                    ElsaIcon(icon)
                }
                Text(title)
                    .elsaFont(size == .large ? ElsaFont.button : ElsaFont.label)
                    .lineLimit(1)
            }
            .foregroundStyle(foreground)
            .padding(.horizontal, size == .large ? ElsaSpacing.xl : ElsaSpacing.lg)
            .frame(
                maxWidth: fullWidth ? .infinity : nil,
                minHeight: size == .large ? ElsaSize.controlHeight : ElsaSize.controlHeightSmall
            )
            .background(background, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                if style == .outline {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .strokeBorder(isActive ? ElsaColor.outlineStrong : ElsaColor.outline, lineWidth: ElsaSize.border)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(ElsaPressableButtonStyle())
        .disabled(!enabled)
    }

    private var cornerRadius: CGFloat { size == .large ? ElsaRadius.lg : ElsaRadius.md }

    private var background: Color {
        guard isActive else {
            return style == .outline || style == .text ? .clear : ElsaColor.disabled
        }
        switch style {
        case .primary: return ElsaColor.primary
        case .secondary: return ElsaColor.fill
        case .outline, .text: return .clear
        case .destructive: return ElsaColor.error
        }
    }

    private var foreground: Color {
        guard isActive else { return ElsaColor.onDisabled }
        switch style {
        case .primary: return ElsaColor.onPrimary
        case .secondary: return ElsaColor.onSurface
        case .outline, .text: return ElsaColor.primary
        case .destructive: return ElsaColor.onError
        }
    }
}

/// 눌림 효과(살짝 축소 + 투명도).
struct ElsaPressableButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: ElsaMotion.fast), value: configuration.isPressed)
    }
}
