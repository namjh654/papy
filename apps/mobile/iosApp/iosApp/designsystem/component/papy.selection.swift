import SwiftUI

/// 라벨까지 탭 영역인 체크박스. (Android `PapyCheckbox`)
struct PapyCheckbox: View {
    let label: String
    @Binding var isOn: Bool
    var description: String? = nil

    @Environment(\.isEnabled) private var isEnabled
    /// 글자와 같이 커지는 상자(Android 는 sp 로 같은 동작)
    @ScaledMetric(relativeTo: .body) private var boxSize = PapySize.checkbox

    var body: some View {
        Button {
            isOn.toggle()
        } label: {
            HStack(alignment: description == nil ? .center : .top, spacing: PapySpacing.sm) {
                Image(systemName: isOn ? "checkmark.square.fill" : "square")
                    .font(.system(size: boxSize))
                    .foregroundStyle(
                        !isEnabled ? PapyColor.onDisabled : (isOn ? PapyColor.primary : PapyColor.outlineStrong)
                    )
                PapySelectionLabel(label: label, description: description)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// 라벨 오른쪽 스위치. (Android `PapySwitch`)
struct PapyToggle: View {
    let label: String
    @Binding var isOn: Bool
    var description: String? = nil

    var body: some View {
        Toggle(isOn: $isOn) {
            PapySelectionLabel(label: label, description: description)
        }
        .tint(PapyColor.primary)
        .frame(minHeight: 44)
    }
}

/// 단일 선택 목록. (Android `PapyRadioGroup`)
struct PapyRadioGroup<Option: Hashable>: View {
    var label: String? = nil
    let options: [Option]
    @Binding var selection: Option?
    let optionLabel: (Option) -> String

    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let label {
                PapyFieldLabel(text: label)
            }
            ForEach(options, id: \.self) { option in
                let selected = option == selection
                Button {
                    selection = option
                } label: {
                    HStack(spacing: PapySpacing.sm) {
                        Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                            .font(.system(size: PapySize.checkbox))
                            .foregroundStyle(
                                !isEnabled ? PapyColor.onDisabled : (selected ? PapyColor.primary : PapyColor.outlineStrong)
                            )
                        PapySelectionLabel(label: optionLabel(option), description: nil)
                        Spacer(minLength: 0)
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }
}

private struct PapySelectionLabel: View {
    let label: String
    let description: String?

    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        VStack(alignment: .leading, spacing: PapySpacing.xxs) {
            Text(label)
                .papyFont(PapyFont.body)
                .foregroundStyle(isEnabled ? PapyColor.onSurface : PapyColor.onDisabled)
            if let description {
                Text(description)
                    .papyFont(PapyFont.caption)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
            }
        }
    }
}
