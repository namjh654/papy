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
    @State private var images: [PapyPickedImage] = []
    @State private var alert: PapyAlert?
    @State private var toast: PapyToast?
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
        PapyFormScrollView {
            section("gallery_section_button")
            PapyButton("Primary") {}
            PapyButton("Secondary", style: .secondary) {}
            PapyButton("Outline", style: .outline) {}
            PapyButton("Destructive", style: .destructive) {}
            PapyButton("Loading", isLoading: true) {}
            PapyButton("Disabled", enabled: false) {}
            HStack(spacing: PapySpacing.sm) {
                PapyButton("Small", size: .small, fullWidth: false) {}
                PapyButton("Text", style: .text, size: .small, fullWidth: false) {}
            }

            section("gallery_section_text_field")
            PapyTextField(
                String(localized: "gallery_name"),
                text: $name,
                placeholder: String(localized: "gallery_name_placeholder"),
                maxLength: 20
            )
            PapyTextField(
                String(localized: "gallery_email"),
                text: $email,
                placeholder: "name@example.com",
                helperText: String(localized: "gallery_email_helper"),
                errorText: emailError,
                keyboardType: .emailAddress,
                textContentType: .emailAddress
            )
            PapyTextField(String(localized: "field_password"), text: $password, isSecure: true)
            PapyTextField(String(localized: "gallery_memo"), text: $memo, multiline: true, maxLength: 200, submitAction: .done)
            PapyTextField(String(localized: "gallery_disabled"), text: .constant(String(localized: "gallery_disabled_value")))
                .disabled(true)

            section("gallery_section_selection")
            PapyCheckbox(
                label: String(localized: "gallery_agree"),
                isOn: $agreed,
                description: String(localized: "gallery_required")
            )
            PapyToggle(
                label: String(localized: "gallery_notification"),
                isOn: $notification,
                description: String(localized: "gallery_notification_description")
            )
            PapyRadioGroup(
                label: String(localized: "gallery_gender"),
                options: Gender.allCases,
                selection: $gender,
                optionLabel: { $0.label }
            )

            section("gallery_section_date_time")
            PapyDatePickerField(label: String(localized: "gallery_birth_date"), date: $birthDate, range: Date.distantPast...Date())
            PapyTimePickerField(label: String(localized: "gallery_alarm_time"), time: $alarmTime)

            section("gallery_section_image")
            PapyImagePicker(
                label: String(format: String(localized: "gallery_photos"), 3),
                images: $images,
                maxCount: 3,
                helperText: String(localized: "gallery_photos_helper")
            )

            section("gallery_section_message")
            PapyInlineMessage(message: String(localized: "gallery_message_info"), type: .info)
            PapyInlineMessage(message: String(localized: "gallery_message_success"), type: .success)
            PapyInlineMessage(
                message: String(localized: "gallery_message_warning"),
                type: .warning,
                title: String(localized: "gallery_message_warning_title")
            )
            PapyInlineMessage(message: String(localized: "gallery_message_error"), type: .error)
            PapyButton(String(localized: "gallery_toast"), style: .secondary) {
                toast = PapyToast(message: String(localized: "gallery_toast_saved"), type: .success)
            }
            PapyButton(String(localized: "gallery_toast_action"), style: .secondary) {
                toast = PapyToast(
                    message: String(localized: "gallery_toast_deleted"),
                    type: .info,
                    actionLabel: String(localized: "gallery_undo")
                ) {
                    toast = PapyToast(message: String(localized: "gallery_toast_restored"), type: .success)
                }
            }
            PapyButton(String(localized: "gallery_dialog"), style: .outline) {
                alert = PapyAlert(
                    title: String(localized: "gallery_dialog_title"),
                    message: String(localized: "gallery_dialog_message"),
                    confirmText: String(localized: "gallery_delete"),
                    dismissText: String(localized: "common_cancel"),
                    isDestructive: true,
                    onConfirm: { toast = PapyToast(message: String(localized: "gallery_toast_deleted"), type: .error) }
                )
            }

            section("gallery_section_loading_card")
            PapyButton(String(localized: "gallery_loading_overlay"), style: .outline) {
                Task {
                    isLoading = true
                    try? await Task.sleep(for: .seconds(2))
                    isLoading = false
                }
            }
            // 카드는 그룹 배경 위에 놓는 컴포넌트라 예시도 그 위에 둔다
            PapyCard {
                Text("gallery_card_title").papyFont(PapyFont.subtitle)
                Text("gallery_card_description")
                    .papyFont(PapyFont.bodySmall)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
            }
            .padding(PapySpacing.md)
            .background(PapyColor.backgroundGrouped, in: RoundedRectangle(cornerRadius: PapyRadius.xl, style: .continuous))

            section("gallery_section_list_sheet")
            VStack(spacing: 0) {
                PapyListItem(
                    title: String(localized: "gallery_list_title"),
                    subtitle: String(localized: "gallery_list_subtitle"),
                    action: { toast = PapyToast(message: String(localized: "gallery_list_title"), type: .info) },
                    leading: { PapyAvatar(name: String(localized: "gallery_list_title"), size: 48) },
                    trailing: {
                        Text("gallery_list_time")
                            .papyFont(PapyFont.caption)
                            .foregroundStyle(PapyColor.onSurfaceTertiary)
                    }
                )
                PapySettingsDivider()
                PapyListItem(
                    title: String(localized: "gallery_list_notice"),
                    action: { toast = PapyToast(message: String(localized: "gallery_list_title"), type: .info) },
                    leading: { PapyIcon("papy_ic_notification").foregroundStyle(PapyColor.onSurfaceVariant) }
                )
            }
            .background(PapyColor.surface, in: RoundedRectangle(cornerRadius: PapyRadius.xl, style: .continuous))
            PapyButton(String(localized: "gallery_sheet"), style: .secondary) { showSheet = true }
            PapyButton(String(localized: "gallery_action_sheet"), style: .secondary) { showActions = true }

            section("gallery_section_chips")
            PapySearchBar(text: $galleryQuery, onSearch: {})
            PapyChipGroup {
                ForEach(Array(["gallery_chip_all", "gallery_chip_popular", "gallery_chip_new"].enumerated()), id: \.offset) { i, key in
                    PapyChip(label: String(localized: String.LocalizationValue(key)), selected: chip == i, action: { chip = i })
                }
            }
            PapySegmentedTabs(
                options: [String(localized: "gallery_segment_list"), String(localized: "gallery_segment_grid")],
                selectedIndex: segment,
                onSelect: { segment = $0 }
            )
            PapySkeletonRow().padding(.horizontal, -PapySpacing.xl)

            section("gallery_section_format")
            formatRow("gallery_format_date", PapyFormat.dateLong(Date(), locale: locale))
            formatRow("gallery_format_time", PapyFormat.time(sampleTime, locale: locale, is24Hour: false))
            formatRow("gallery_format_number", PapyFormat.number(1234567.89, locale: locale))
            formatRow("gallery_format_percent", PapyFormat.percent(0.256, locale: locale))
        }
        .papyScreenBackground()
        .navigationTitle(String(localized: "gallery_title"))
        .navigationBarTitleDisplayMode(.inline)
        .papyAlert($alert)
        .papyBottomSheet(isPresented: $showSheet, title: String(localized: "gallery_sheet_title")) {
            Text("gallery_sheet_body")
                .papyFont(PapyFont.body)
                .foregroundStyle(PapyColor.onSurfaceVariant)
                .padding(.horizontal, PapySpacing.xl)
        }
        .papyActionSheet(isPresented: $showActions, title: String(localized: "attach_title"), actions: [
            PapyAction(label: String(localized: "attach_photo"), icon: "papy_ic_image") {
                toast = PapyToast(message: String(localized: "attach_photo"), type: .info)
            },
            PapyAction(label: String(localized: "attach_camera"), icon: "papy_ic_camera") {
                toast = PapyToast(message: String(localized: "attach_camera"), type: .info)
            },
            PapyAction(label: String(localized: "attach_file"), icon: "papy_ic_file") {
                toast = PapyToast(message: String(localized: "attach_file"), type: .info)
            },
            PapyAction(label: String(localized: "gallery_delete"), icon: "papy_ic_delete", isDestructive: true) {
                toast = PapyToast(message: String(localized: "gallery_toast_deleted"), type: .error)
            },
        ])
        .papyToast($toast)
        .papyLoadingOverlay(isLoading, message: String(localized: "common_processing"))
    }

    private func section(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .papyFont(PapyFont.title)
            .foregroundStyle(PapyColor.onBackground)
            .padding(.top, PapySpacing.sm)
    }

    private func formatRow(_ key: LocalizedStringKey, _ value: String) -> some View {
        HStack {
            Text(key)
                .papyFont(PapyFont.body)
                .foregroundStyle(PapyColor.onSurfaceVariant)
            Spacer()
            Text(value)
                .papyFont(PapyFont.bodyStrong)
                .foregroundStyle(PapyColor.onSurface)
        }
    }
}
