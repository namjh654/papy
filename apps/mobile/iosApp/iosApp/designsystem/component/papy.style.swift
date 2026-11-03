import SwiftUI

// MARK: - Typography

extension View {
    /// 토큰 타이포그래피 적용 (크기·굵기 + 줄 높이 근사치).
    func papyFont(_ style: PapyTextStyle) -> some View {
        modifier(PapyFontModifier(style: style))
    }
}

/// 글꼴 + 줄 간격. 시스템 글자 크기(Dynamic Type)가 커지면 줄 간격도 같은 비율로 늘린다(DEC-128)
private struct PapyFontModifier: ViewModifier {
    let style: PapyTextStyle
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    func body(content: Content) -> some View {
        let spacing = max(0, style.lineHeight - style.size * 1.2)
        let metrics = UIFontMetrics(forTextStyle: style.relativeTo.uiTextStyle)
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dynamicTypeSize))
        content
            .font(style.font)
            .lineSpacing(metrics.scaledValue(for: spacing, compatibleWith: traits))
    }
}

private extension Font.TextStyle {
    var uiTextStyle: UIFont.TextStyle {
        switch self {
        case .largeTitle: .largeTitle
        case .title: .title1
        case .title2: .title2
        case .title3: .title3
        case .headline: .headline
        case .subheadline: .subheadline
        case .callout: .callout
        case .footnote: .footnote
        case .caption: .caption1
        case .caption2: .caption2
        default: .body
        }
    }
}

// MARK: - Message Type

/// 알림 종류. 토스트·인라인 메시지가 같은 색/아이콘 규칙을 쓴다. (Android `PapyMessageType`)
enum PapyMessageType {
    case info, success, warning, error

    var accent: Color {
        switch self {
        case .info: PapyColor.info
        case .success: PapyColor.success
        case .warning: PapyColor.warning
        case .error: PapyColor.error
        }
    }

    var container: Color {
        switch self {
        case .info: PapyColor.infoContainer
        case .success: PapyColor.successContainer
        case .warning: PapyColor.warningContainer
        case .error: PapyColor.errorContainer
        }
    }

    var onContainer: Color {
        switch self {
        case .info: PapyColor.onInfoContainer
        case .success: PapyColor.onSuccessContainer
        case .warning: PapyColor.onWarningContainer
        case .error: PapyColor.onErrorContainer
        }
    }

    /// 아이콘 에셋(DEC-118) — Android `PapyMessageType.icon` 과 같다
    var icon: String {
        switch self {
        case .info: "papy_ic_info"
        case .success: "papy_ic_success"
        case .warning: "papy_ic_warning"
        case .error: "papy_ic_error"
        }
    }
}

// MARK: - Field Parts (TextField / SelectField / Picker 공용)

struct PapyFieldLabel: View {
    let text: String
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        Text(text)
            .papyFont(PapyFont.label)
            .foregroundStyle(isEnabled ? PapyColor.onSurfaceVariant : PapyColor.onDisabled)
    }
}

struct PapyFieldFooter: View {
    let helperText: String?
    let errorText: String?
    var counter: String? = nil

    var body: some View {
        let message = errorText ?? helperText
        if message != nil || counter != nil {
            HStack(alignment: .top) {
                Text(message ?? "")
                    .foregroundStyle(errorText != nil ? PapyColor.error : PapyColor.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let counter {
                    Text(counter)
                        .foregroundStyle(PapyColor.onSurfaceVariant)
                }
            }
            .papyFont(PapyFont.caption)
        }
    }
}

/// 입력 필드 공통 배경. 테두리 없는 채움형이고, 포커스(primary)·오류(error)일 때만 테두리를 그린다.
struct PapyFieldBackground: ViewModifier {
    var isFocused = false
    var isError = false
    @Environment(\.isEnabled) private var isEnabled

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous)
        content
            .padding(.horizontal, PapySpacing.lg)
            .frame(minHeight: PapySize.controlHeight)
            .background(isEnabled ? PapyColor.fill : PapyColor.disabled, in: shape)
            .overlay {
                if isError || isFocused {
                    shape.strokeBorder(isError ? PapyColor.error : PapyColor.primary, lineWidth: PapySize.borderFocused)
                }
            }
            .animation(.easeOut(duration: PapyMotion.fast), value: isFocused)
    }
}

extension View {
    func papyFieldBackground(isFocused: Bool = false, isError: Bool = false) -> some View {
        modifier(PapyFieldBackground(isFocused: isFocused, isError: isError))
    }
}
