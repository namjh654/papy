import SwiftUI

enum PapyButtonStyleKind { case primary, secondary, outline, text, destructive }

enum PapyButtonSize { case large, small }

/// 공통 버튼. Android `PapyButton` 과 같은 style/size/isLoading 의미를 가진다.
///
/// - `isLoading`: 스피너를 표시하고 탭을 무시한다(색은 유지).
/// - 비활성화는 `enabled:` 또는 SwiftUI `.disabled(_:)` 둘 다 가능.
struct PapyButton: View {
    let title: String
    var style: PapyButtonStyleKind = .primary
    var size: PapyButtonSize = .large
    var enabled = true
    var isLoading = false
    var fullWidth = true
    var icon: String? = nil
    let action: () -> Void

    @Environment(\.isEnabled) private var environmentEnabled

    init(
        _ title: String,
        style: PapyButtonStyleKind = .primary,
        size: PapyButtonSize = .large,
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
            HStack(spacing: PapySpacing.sm) {
                if isLoading {
                    ProgressView()
                        .tint(foreground)
                        .controlSize(.small)
                } else if let icon {
                    PapyIcon(icon)
                }
                Text(title)
                    .papyFont(size == .large ? PapyFont.button : PapyFont.label)
                    .lineLimit(1)
            }
            .foregroundStyle(foreground)
            .padding(.horizontal, size == .large ? PapySpacing.xl : PapySpacing.lg)
            .frame(
                maxWidth: fullWidth ? .infinity : nil,
                minHeight: size == .large ? PapySize.controlHeight : PapySize.controlHeightSmall
            )
            .background(background, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                if style == .outline {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .strokeBorder(isActive ? PapyColor.outlineStrong : PapyColor.outline, lineWidth: PapySize.border)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PapyPressableButtonStyle())
        .disabled(!enabled)
    }

    private var cornerRadius: CGFloat { size == .large ? PapyRadius.lg : PapyRadius.md }

    private var background: Color {
        guard isActive else {
            return style == .outline || style == .text ? .clear : PapyColor.disabled
        }
        switch style {
        case .primary: return PapyColor.primary
        case .secondary: return PapyColor.fill
        case .outline, .text: return .clear
        case .destructive: return PapyColor.error
        }
    }

    private var foreground: Color {
        guard isActive else { return PapyColor.onDisabled }
        switch style {
        case .primary: return PapyColor.onPrimary
        case .secondary: return PapyColor.onSurface
        case .outline, .text: return PapyColor.primary
        case .destructive: return PapyColor.onError
        }
    }
}

/// 눌림 효과(살짝 축소 + 투명도).
struct PapyPressableButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: PapyMotion.fast), value: configuration.isPressed)
    }
}
