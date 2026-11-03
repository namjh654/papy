import SwiftUI

// MARK: - Section

/// 설정 묶음: 작은 제목 + 무테 카드 안의 행들. 행 사이에는 ElsaSettingsDivider. (Android `ElsaSettingsSection`)
struct ElsaSettingsSection<Content: View>: View {
    var title: String? = nil
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: ElsaSpacing.sm) {
            if let title {
                Text(title)
                    .elsaFont(ElsaFont.bodyStrong)
                    .foregroundStyle(ElsaColor.onSurfaceVariant)
                    .padding(.horizontal, ElsaSpacing.xs)
                    .accessibilityAddTraits(.isHeader)
            }
            VStack(spacing: 0) {
                content()
            }
            .background(ElsaColor.surface, in: RoundedRectangle(cornerRadius: ElsaRadius.xl, style: .continuous))
        }
    }
}

// MARK: - Row

/// 설정 한 줄: 제목 + 오른쪽 값(+ 누를 수 있으면 화살표). `destructive` 는 로그아웃처럼 위험한 동작. (Android `ElsaSettingsRow`)
struct ElsaSettingsRow: View {
    let title: String
    var value: String? = nil
    var destructive = false
    var action: (() -> Void)? = nil

    var body: some View {
        if let action {
            Button(action: action) { content }
                .buttonStyle(ElsaPressableButtonStyle())
        } else {
            content
        }
    }

    private var content: some View {
        HStack(spacing: ElsaSpacing.sm) {
            Text(title)
                .elsaFont(ElsaFont.body)
                .foregroundStyle(destructive ? ElsaColor.error : ElsaColor.onSurface)
            Spacer(minLength: ElsaSpacing.sm)
            if let value {
                Text(value)
                    .elsaFont(ElsaFont.body)
                    .foregroundStyle(ElsaColor.onSurfaceVariant)
            }
            if action != nil && !destructive {
                Image(systemName: "chevron.forward")
                    .font(.system(size: ElsaSize.iconSmall))
                    .foregroundStyle(ElsaColor.onSurfaceTertiary)
            }
        }
        .padding(.horizontal, ElsaSpacing.xl)
        .frame(minHeight: ElsaSize.controlHeight)
        .contentShape(Rectangle())
    }
}

/// 설정 한 줄 스위치(알림 종류·앱 잠금): 제목(+설명) + 오른쪽 스위치. (Android `ElsaSettingsSwitchRow`)
struct ElsaSettingsToggleRow: View {
    let title: String
    var description: String? = nil
    @Binding var isOn: Bool

    var body: some View {
        ElsaToggle(label: title, isOn: $isOn, description: description)
            .padding(.horizontal, ElsaSpacing.xl)
            .padding(.vertical, ElsaSpacing.sm)
    }
}

/// 설정 행 사이 구분선
struct ElsaSettingsDivider: View {
    var body: some View {
        Rectangle()
            .fill(ElsaColor.divider)
            .frame(height: 1)
            .padding(.horizontal, ElsaSpacing.xl)
    }
}

// MARK: - Option Sheet

extension View {
    /// 하나를 고르는 바텀시트 (테마·언어 등). 고르면 `onSelect` 후 닫힌다. (Android `ElsaOptionSheet`)
    func elsaOptionSheet<Option: Hashable>(
        isPresented: Binding<Bool>,
        title: String,
        options: [Option],
        selected: Option,
        label: @escaping (Option) -> String,
        onSelect: @escaping (Option) -> Void
    ) -> some View {
        elsaBottomSheet(isPresented: isPresented, title: title) {
                ForEach(options, id: \.self) { option in
                    Button {
                        onSelect(option)
                        isPresented.wrappedValue = false
                    } label: {
                        HStack {
                            Text(label(option))
                                .elsaFont(ElsaFont.body)
                                .foregroundStyle(ElsaColor.onSurface)
                            Spacer()
                            if option == selected {
                                ElsaIcon("elsa_ic_check")
                                    .foregroundStyle(ElsaColor.primary)
                            }
                        }
                        .padding(.horizontal, ElsaSpacing.xl)
                        .frame(minHeight: ElsaSize.controlHeight)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(ElsaPressableButtonStyle())
                }
        }
    }
}
