import SwiftUI

/// 라벨까지 탭 영역인 체크박스. (Android `ElsaCheckbox`)
struct ElsaCheckbox: View {
    let label: String
    @Binding var isOn: Bool
    var description: String? = nil

    @Environment(\.isEnabled) private var isEnabled
    /// 글자와 같이 커지는 상자(Android 는 sp 로 같은 동작)
    @ScaledMetric(relativeTo: .body) private var boxSize = ElsaSize.checkbox

    var body: some View {
        Button {
            isOn.toggle()
        } label: {
            HStack(alignment: description == nil ? .center : .top, spacing: ElsaSpacing.sm) {
                Image(systemName: isOn ? "checkmark.square.fill" : "square")
                    .font(.system(size: boxSize))
                    .foregroundStyle(
                        !isEnabled ? ElsaColor.onDisabled : (isOn ? ElsaColor.primary : ElsaColor.outlineStrong)
                    )
                ElsaSelectionLabel(label: label, description: description)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// 라벨 오른쪽 스위치. (Android `ElsaSwitch`)
struct ElsaToggle: View {
    let label: String
    @Binding var isOn: Bool
    var description: String? = nil

    var body: some View {
        Toggle(isOn: $isOn) {
            ElsaSelectionLabel(label: label, description: description)
        }
        .tint(ElsaColor.primary)
        .frame(minHeight: 44)
    }
}

/// 단일 선택 목록. (Android `ElsaRadioGroup`)
struct ElsaRadioGroup<Option: Hashable>: View {
    var label: String? = nil
    let options: [Option]
    @Binding var selection: Option?
    let optionLabel: (Option) -> String

    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let label {
                ElsaFieldLabel(text: label)
            }
            ForEach(options, id: \.self) { option in
                let selected = option == selection
                Button {
                    selection = option
                } label: {
                    HStack(spacing: ElsaSpacing.sm) {
                        Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                            .font(.system(size: ElsaSize.checkbox))
                            .foregroundStyle(
                                !isEnabled ? ElsaColor.onDisabled : (selected ? ElsaColor.primary : ElsaColor.outlineStrong)
                            )
                        ElsaSelectionLabel(label: optionLabel(option), description: nil)
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

private struct ElsaSelectionLabel: View {
    let label: String
    let description: String?

    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        VStack(alignment: .leading, spacing: ElsaSpacing.xxs) {
            Text(label)
                .elsaFont(ElsaFont.body)
                .foregroundStyle(isEnabled ? ElsaColor.onSurface : ElsaColor.onDisabled)
            if let description {
                Text(description)
                    .elsaFont(ElsaFont.caption)
                    .foregroundStyle(ElsaColor.onSurfaceVariant)
            }
        }
    }
}
