import SwiftUI

/// 날짜 선택 필드. 탭하면 달력 시트가 열린다. (Android `ElsaDatePickerField`)
struct ElsaDatePickerField: View {
    let label: String?
    @Binding var date: Date?
    var placeholder: String? = nil
    var helperText: String? = nil
    var errorText: String? = nil
    /// 선택 가능 범위 (포함)
    var range: ClosedRange<Date>? = nil

    @State private var isPresented = false
    @State private var draft = Date()
    @Environment(\.locale) private var locale

    private var resolvedPlaceholder: String { placeholder ?? String(localized: "common_select_date") }

    var body: some View {
        ElsaSelectField(
            label: label,
            value: date.map { ElsaFormat.date($0, locale: locale) },
            placeholder: resolvedPlaceholder,
            helperText: helperText,
            errorText: errorText,
            icon: "elsa_ic_calendar"
        ) {
            draft = date ?? clamp(Date())
            isPresented = true
        }
        .sheet(isPresented: $isPresented) {
            ElsaPickerSheet(
                title: label ?? resolvedPlaceholder,
                onCancel: { isPresented = false },
                onConfirm: {
                    date = draft
                    isPresented = false
                }
            ) {
                Group {
                    if let range {
                        DatePicker("", selection: $draft, in: range, displayedComponents: .date)
                    } else {
                        DatePicker("", selection: $draft, displayedComponents: .date)
                    }
                }
                .datePickerStyle(.graphical)
                .labelsHidden()
                .tint(ElsaColor.primary)
            }
            .presentationDetents([.medium, .large])
        }
    }

    private func clamp(_ value: Date) -> Date {
        guard let range else { return value }
        return min(max(value, range.lowerBound), range.upperBound)
    }
}

/// 시간 선택 필드. 탭하면 휠 시트가 열린다. (Android `ElsaTimePickerField`)
///
/// 값은 `Date` 의 시/분만 사용한다.
struct ElsaTimePickerField: View {
    let label: String?
    @Binding var time: Date?
    var placeholder: String? = nil
    var helperText: String? = nil
    var errorText: String? = nil
    /// nil 이면 기기 설정(12/24시간)을 따른다
    var is24Hour: Bool? = nil

    @State private var isPresented = false
    @State private var draft = Date()
    @Environment(\.locale) private var locale

    private var resolvedPlaceholder: String { placeholder ?? String(localized: "common_select_time") }

    var body: some View {
        ElsaSelectField(
            label: label,
            value: time.map { ElsaFormat.time($0, locale: locale, is24Hour: is24Hour) },
            placeholder: resolvedPlaceholder,
            helperText: helperText,
            errorText: errorText,
            icon: "elsa_ic_clock"
        ) {
            draft = time ?? Date()
            isPresented = true
        }
        .sheet(isPresented: $isPresented) {
            ElsaPickerSheet(
                title: label ?? resolvedPlaceholder,
                onCancel: { isPresented = false },
                onConfirm: {
                    time = draft
                    isPresented = false
                }
            ) {
                DatePicker("", selection: $draft, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    // 24시간제 강제가 필요하면 24시간 표기 로케일을 사용
                    .environment(\.locale, is24Hour == true ? Locale(identifier: "en_GB") : locale)
            }
            .presentationDetents([.height(320)])
        }
    }
}

/// 선택 UI 를 담는 하단 시트 (제목 + 취소/확인).
struct ElsaPickerSheet<Content: View>: View {
    let title: String
    let onCancel: () -> Void
    let onConfirm: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        NavigationStack {
            content()
                .padding(.horizontal, ElsaSpacing.lg)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(String(localized: "common_cancel"), action: onCancel)
                            .foregroundStyle(ElsaColor.onSurfaceVariant)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(String(localized: "common_confirm"), action: onConfirm)
                            .foregroundStyle(ElsaColor.primary)
                    }
                }
        }
    }
}
