import SwiftUI

/// 디자인 시스템 컴포넌트 갤러리. 새 컴포넌트를 추가하면 여기에도 예시를 추가한다.
/// Android `ComponentGalleryScreen` 과 같은 순서/내용을 유지한다.
struct ComponentGalleryScreen: View {

    private enum Gender: CaseIterable {
        case male, female, none

        var label: String {
            switch self {
            case .male: String(localized: "gallery_gender_male")
            case .female: String(localized: "gallery_gender_female")
            case .none: String(localized: "gallery_gender_none")
            }
        }
    }

    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var memo = ""
    @State private var agreed = false
    @State private var notification = true
    @State private var gender: Gender?
    @State private var birthDate: Date?
    @State private var alarmTime: Date?
    @State private var images: [ElsaPickedImage] = []
    @State private var alert: ElsaAlert?
    @State private var toast: ElsaToast?
    @State private var isLoading = false
    @State private var showSheet = false
    @State private var galleryQuery = ""
    @State private var chip = 0
    @State private var segment = 0
    @State private var showActions = false

    @Environment(\.locale) private var locale

    private var emailError: String? {
        !email.isEmpty && !email.contains("@") ? String(localized: "gallery_email_error") : nil
    }

    /// 로케일 포맷 예시 기준 시각 (15:05)
    private var sampleTime: Date {
        Calendar.current.date(bySettingHour: 15, minute: 5, second: 0, of: Date()) ?? Date()
    }

    var body: some View {
        ElsaFormScrollView {
            section("gallery_section_button")
            ElsaButton("Primary") {}
            ElsaButton("Secondary", style: .secondary) {}
            ElsaButton("Outline", style: .outline) {}
            ElsaButton("Destructive", style: .destructive) {}
            ElsaButton("Loading", isLoading: true) {}
            ElsaButton("Disabled", enabled: false) {}
            HStack(spacing: ElsaSpacing.sm) {
                ElsaButton("Small", size: .small, fullWidth: false) {}
                ElsaButton("Text", style: .text, size: .small, fullWidth: false) {}
            }

            section("gallery_section_text_field")
            ElsaTextField(
                String(localized: "gallery_name"),
                text: $name,
                placeholder: String(localized: "gallery_name_placeholder"),
                maxLength: 20
            )
            ElsaTextField(
                String(localized: "gallery_email"),
                text: $email,
                placeholder: "name@example.com",
                helperText: String(localized: "gallery_email_helper"),
                errorText: emailError,
                keyboardType: .emailAddress,
                textContentType: .emailAddress
            )
            ElsaTextField(String(localized: "field_password"), text: $password, isSecure: true)
            ElsaTextField(String(localized: "gallery_memo"), text: $memo, multiline: true, maxLength: 200, submitAction: .done)
            ElsaTextField(String(localized: "gallery_disabled"), text: .constant(String(localized: "gallery_disabled_value")))
                .disabled(true)

            section("gallery_section_selection")
            ElsaCheckbox(
                label: String(localized: "gallery_agree"),
                isOn: $agreed,
                description: String(localized: "gallery_required")
            )
            ElsaToggle(
                label: String(localized: "gallery_notification"),
                isOn: $notification,
                description: String(localized: "gallery_notification_description")
            )
            ElsaRadioGroup(
                label: String(localized: "gallery_gender"),
                options: Gender.allCases,
                selection: $gender,
                optionLabel: { $0.label }
            )

            section("gallery_section_date_time")
            ElsaDatePickerField(label: String(localized: "gallery_birth_date"), date: $birthDate, range: Date.distantPast...Date())
            ElsaTimePickerField(label: String(localized: "gallery_alarm_time"), time: $alarmTime)

            section("gallery_section_image")
            ElsaImagePicker(
                label: String(format: String(localized: "gallery_photos"), 3),
                images: $images,
                maxCount: 3,
                helperText: String(localized: "gallery_photos_helper")
            )

            section("gallery_section_message")
            ElsaInlineMessage(message: String(localized: "gallery_message_info"), type: .info)
            ElsaInlineMessage(message: String(localized: "gallery_message_success"), type: .success)
            ElsaInlineMessage(
                message: String(localized: "gallery_message_warning"),
                type: .warning,
                title: String(localized: "gallery_message_warning_title")
            )
            ElsaInlineMessage(message: String(localized: "gallery_message_error"), type: .error)
            ElsaButton(String(localized: "gallery_toast"), style: .secondary) {
                toast = ElsaToast(message: String(localized: "gallery_toast_saved"), type: .success)
            }
            ElsaButton(String(localized: "gallery_toast_action"), style: .secondary) {
                toast = ElsaToast(
                    message: String(localized: "gallery_toast_deleted"),
                    type: .info,
                    actionLabel: String(localized: "gallery_undo")
                ) {
                    toast = ElsaToast(message: String(localized: "gallery_toast_restored"), type: .success)
                }
            }
            ElsaButton(String(localized: "gallery_dialog"), style: .outline) {
                alert = ElsaAlert(
                    title: String(localized: "gallery_dialog_title"),
                    message: String(localized: "gallery_dialog_message"),
                    confirmText: String(localized: "gallery_delete"),
                    dismissText: String(localized: "common_cancel"),
                    isDestructive: true,
                    onConfirm: { toast = ElsaToast(message: String(localized: "gallery_toast_deleted"), type: .error) }
                )
            }

            section("gallery_section_loading_card")
            ElsaButton(String(localized: "gallery_loading_overlay"), style: .outline) {
                Task {
                    isLoading = true
                    try? await Task.sleep(for: .seconds(2))
                    isLoading = false
                }
            }
            // 카드는 그룹 배경 위에 놓는 컴포넌트라 예시도 그 위에 둔다
            ElsaCard {
                Text("gallery_card_title").elsaFont(ElsaFont.subtitle)
                Text("gallery_card_description")
                    .elsaFont(ElsaFont.bodySmall)
                    .foregroundStyle(ElsaColor.onSurfaceVariant)
            }
            .padding(ElsaSpacing.md)
            .background(ElsaColor.backgroundGrouped, in: RoundedRectangle(cornerRadius: ElsaRadius.xl, style: .continuous))

            section("gallery_section_list_sheet")
            VStack(spacing: 0) {
                ElsaListItem(
                    title: String(localized: "gallery_list_title"),
                    subtitle: String(localized: "gallery_list_subtitle"),
                    action: { toast = ElsaToast(message: String(localized: "gallery_list_title"), type: .info) },
                    leading: { ElsaAvatar(name: String(localized: "gallery_list_title"), size: 48) },
                    trailing: {
                        Text("gallery_list_time")
                            .elsaFont(ElsaFont.caption)
                            .foregroundStyle(ElsaColor.onSurfaceTertiary)
                    }
                )
                ElsaSettingsDivider()
                ElsaListItem(
                    title: String(localized: "gallery_list_notice"),
                    action: { toast = ElsaToast(message: String(localized: "gallery_list_title"), type: .info) },
                    leading: { ElsaIcon("elsa_ic_notification").foregroundStyle(ElsaColor.onSurfaceVariant) }
                )
            }
            .background(ElsaColor.surface, in: RoundedRectangle(cornerRadius: ElsaRadius.xl, style: .continuous))
            ElsaButton(String(localized: "gallery_sheet"), style: .secondary) { showSheet = true }
            ElsaButton(String(localized: "gallery_action_sheet"), style: .secondary) { showActions = true }

            section("gallery_section_chips")
            ElsaSearchBar(text: $galleryQuery, onSearch: {})
            ElsaChipGroup {
                ForEach(Array(["gallery_chip_all", "gallery_chip_popular", "gallery_chip_new"].enumerated()), id: \.offset) { i, key in
                    ElsaChip(label: String(localized: String.LocalizationValue(key)), selected: chip == i, action: { chip = i })
                }
            }
            ElsaSegmentedTabs(
                options: [String(localized: "gallery_segment_list"), String(localized: "gallery_segment_grid")],
                selectedIndex: segment,
                onSelect: { segment = $0 }
            )
            ElsaSkeletonRow().padding(.horizontal, -ElsaSpacing.xl)

            section("gallery_section_format")
            formatRow("gallery_format_date", ElsaFormat.dateLong(Date(), locale: locale))
            formatRow("gallery_format_time", ElsaFormat.time(sampleTime, locale: locale, is24Hour: false))
            formatRow("gallery_format_number", ElsaFormat.number(1234567.89, locale: locale))
            formatRow("gallery_format_percent", ElsaFormat.percent(0.256, locale: locale))
        }
        .elsaScreenBackground()
        .navigationTitle(String(localized: "gallery_title"))
        .navigationBarTitleDisplayMode(.inline)
        .elsaAlert($alert)
        .elsaBottomSheet(isPresented: $showSheet, title: String(localized: "gallery_sheet_title")) {
            Text("gallery_sheet_body")
                .elsaFont(ElsaFont.body)
                .foregroundStyle(ElsaColor.onSurfaceVariant)
                .padding(.horizontal, ElsaSpacing.xl)
        }
        .elsaActionSheet(isPresented: $showActions, title: String(localized: "attach_title"), actions: [
            ElsaAction(label: String(localized: "attach_photo"), icon: "elsa_ic_image") {
                toast = ElsaToast(message: String(localized: "attach_photo"), type: .info)
            },
            ElsaAction(label: String(localized: "attach_camera"), icon: "elsa_ic_camera") {
                toast = ElsaToast(message: String(localized: "attach_camera"), type: .info)
            },
            ElsaAction(label: String(localized: "attach_file"), icon: "elsa_ic_file") {
                toast = ElsaToast(message: String(localized: "attach_file"), type: .info)
            },
            ElsaAction(label: String(localized: "gallery_delete"), icon: "elsa_ic_delete", isDestructive: true) {
                toast = ElsaToast(message: String(localized: "gallery_toast_deleted"), type: .error)
            },
        ])
        .elsaToast($toast)
        .elsaLoadingOverlay(isLoading, message: String(localized: "common_processing"))
    }

    private func section(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .elsaFont(ElsaFont.title)
            .foregroundStyle(ElsaColor.onBackground)
            .padding(.top, ElsaSpacing.sm)
    }

    private func formatRow(_ key: LocalizedStringKey, _ value: String) -> some View {
        HStack {
            Text(key)
                .elsaFont(ElsaFont.body)
                .foregroundStyle(ElsaColor.onSurfaceVariant)
            Spacer()
            Text(value)
                .elsaFont(ElsaFont.bodyStrong)
                .foregroundStyle(ElsaColor.onSurface)
        }
    }
}
