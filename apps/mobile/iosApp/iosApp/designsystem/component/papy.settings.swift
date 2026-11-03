import SwiftUI

// MARK: - Section

/// 설정 묶음: 작은 제목 + 무테 카드 안의 행들. 행 사이에는 PapySettingsDivider. (Android `PapySettingsSection`)
struct PapySettingsSection<Content: View>: View {
    var title: String? = nil
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: PapySpacing.sm) {
            if let title {
                Text(title)
                    .papyFont(PapyFont.bodyStrong)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
                    .padding(.horizontal, PapySpacing.xs)
                    .accessibilityAddTraits(.isHeader)
            }
            VStack(spacing: 0) {
                content()
            }
            .background(PapyColor.surface, in: RoundedRectangle(cornerRadius: PapyRadius.xl, style: .continuous))
        }
    }
}

// MARK: - Row

/// 설정 한 줄: 제목 + 오른쪽 값(+ 누를 수 있으면 화살표). `destructive` 는 로그아웃처럼 위험한 동작. (Android `PapySettingsRow`)
struct PapySettingsRow: View {
    let title: String
    var value: String? = nil
    var destructive = false
    var action: (() -> Void)? = nil

    var body: some View {
        if let action {
            Button(action: action) { content }
                .buttonStyle(PapyPressableButtonStyle())
        } else {
            content
        }
    }

    private var content: some View {
        HStack(spacing: PapySpacing.sm) {
            Text(title)
                .papyFont(PapyFont.body)
                .foregroundStyle(destructive ? PapyColor.error : PapyColor.onSurface)
            Spacer(minLength: PapySpacing.sm)
            if let value {
                Text(value)
                    .papyFont(PapyFont.body)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
            }
            if action != nil && !destructive {
                Image(systemName: "chevron.forward")
                    .font(.system(size: PapySize.iconSmall))
                    .foregroundStyle(PapyColor.onSurfaceTertiary)
            }
        }
        .padding(.horizontal, PapySpacing.xl)
        .frame(minHeight: PapySize.controlHeight)
        .contentShape(Rectangle())
    }
}

/// 설정 한 줄 스위치(알림 종류·앱 잠금): 제목(+설명) + 오른쪽 스위치. (Android `PapySettingsSwitchRow`)
struct PapySettingsToggleRow: View {
    let title: String
    var description: String? = nil
    @Binding var isOn: Bool

    var body: some View {
        PapyToggle(label: title, isOn: $isOn, description: description)
            .padding(.horizontal, PapySpacing.xl)
            .padding(.vertical, PapySpacing.sm)
    }
}

/// 설정 행 사이 구분선
struct PapySettingsDivider: View {
    var body: some View {
        Rectangle()
            .fill(PapyColor.divider)
            .frame(height: 1)
            .padding(.horizontal, PapySpacing.xl)
    }
}

// MARK: - Option Sheet

extension View {
    /// 하나를 고르는 바텀시트 (테마·언어 등). 고르면 `onSelect` 후 닫힌다. (Android `PapyOptionSheet`)
    func papyOptionSheet<Option: Hashable>(
        isPresented: Binding<Bool>,
        title: String,
        options: [Option],
        selected: Option,
        label: @escaping (Option) -> String,
        onSelect: @escaping (Option) -> Void
    ) -> some View {
        papyBottomSheet(isPresented: isPresented, title: title) {
                ForEach(options, id: \.self) { option in
                    Button {
                        onSelect(option)
                        isPresented.wrappedValue = false
                    } label: {
                        HStack {
                            Text(label(option))
                                .papyFont(PapyFont.body)
                                .foregroundStyle(PapyColor.onSurface)
                            Spacer()
                            if option == selected {
                                PapyIcon("papy_ic_check")
                                    .foregroundStyle(PapyColor.primary)
                            }
                        }
                        .padding(.horizontal, PapySpacing.xl)
                        .frame(minHeight: PapySize.controlHeight)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PapyPressableButtonStyle())
                }
        }
    }
}
