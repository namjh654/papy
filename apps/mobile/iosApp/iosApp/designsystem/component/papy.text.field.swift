import SwiftUI

/// 공통 텍스트 필드. 라벨은 위, 도움말/에러는 아래 (Android `PapyTextField` 와 동일 배치).
///
/// - `isSecure`: 비밀번호 입력 + 보기/숨기기 버튼
/// - `maxLength`: 초과 입력 차단 + 글자 수 표시
/// - `submitAction`: 키보드 리턴 키. 기본 `.next`(다음 필드), 마지막 필드는 `.done` + `onSubmit` (Android imeAction)
/// - `multiline`: 여러 줄 입력 (세로로 늘어남)
/// - 포커스 이동/키보드 스크롤은 `PapyFormScrollView` + `.papyFormField(_:focus:)` 로 처리한다.
struct PapyTextField: View {
    let label: String?
    @Binding var text: String
    var placeholder: String = ""
    var helperText: String? = nil
    var errorText: String? = nil
    var isSecure = false
    var multiline = false
    var maxLength: Int? = nil
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType? = nil
    var autocapitalization: TextInputAutocapitalization = .never
    var submitAction: PapySubmitAction = .next
    var icon: String? = nil
    var onSubmit: (() -> Void)? = nil

    @State private var isRevealed = false
    @State private var fieldID = UUID()
    @FocusState private var isFocused: Bool
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.papyFormCoordinator) private var form

    init(
        _ label: String?,
        text: Binding<String>,
        placeholder: String = "",
        helperText: String? = nil,
        errorText: String? = nil,
        isSecure: Bool = false,
        multiline: Bool = false,
        maxLength: Int? = nil,
        keyboardType: UIKeyboardType = .default,
        textContentType: UITextContentType? = nil,
        autocapitalization: TextInputAutocapitalization = .never,
        submitAction: PapySubmitAction = .next,
        icon: String? = nil,
        onSubmit: (() -> Void)? = nil
    ) {
        self.label = label
        self._text = text
        self.placeholder = placeholder
        self.helperText = helperText
        self.errorText = errorText
        self.isSecure = isSecure
        self.multiline = multiline
        self.maxLength = maxLength
        self.keyboardType = keyboardType
        self.textContentType = textContentType
        self.autocapitalization = autocapitalization
        self.submitAction = submitAction
        self.icon = icon
        self.onSubmit = onSubmit
    }

    var body: some View {
        VStack(alignment: .leading, spacing: PapySpacing.xs) {
            if let label {
                PapyFieldLabel(text: label)
            }

            HStack(spacing: PapySpacing.sm) {
                if let icon {
                    PapyIcon(icon)
                        .foregroundStyle(PapyColor.onSurfaceVariant)
                }

                input
                    .papyFont(PapyFont.body)
                    .foregroundStyle(isEnabled ? PapyColor.onSurface : PapyColor.onDisabled)
                    .tint(PapyColor.primary)
                    .focused($isFocused)
                    .keyboardType(keyboardType)
                    .textContentType(textContentType)
                    .textInputAutocapitalization(autocapitalization)
                    .autocorrectionDisabled(isSecure || keyboardType != .default)
                    .submitLabel(submitAction.label)
                    .onSubmit(handleSubmit)
                    .onChange(of: text) { _, newValue in
                        if let maxLength, newValue.count > maxLength {
                            text = String(newValue.prefix(maxLength))
                        }
                    }

                if isSecure {
                    Button {
                        isRevealed.toggle()
                    } label: {
                        PapyIcon(isRevealed ? "papy_ic_visibility_off" : "papy_ic_visibility")
                            .foregroundStyle(PapyColor.onSurfaceVariant)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(String(localized: isRevealed ? "field_password_hide" : "field_password_show"))
                }
            }
            .padding(.vertical, multiline ? PapySpacing.md : 0)
            .papyFieldBackground(isFocused: isFocused, isError: errorText != nil)
            .contentShape(Rectangle())
            .onTapGesture { isFocused = true }

            PapyFieldFooter(
                helperText: helperText,
                errorText: errorText,
                counter: maxLength.map { "\(text.count)/\($0)" }
            )
        }
        .modifier(PapyFormFieldRegistration(id: fieldID, focus: $isFocused, form: form))
    }

    /// '다음' 은 폼의 다음 필드로, 그 외(또는 마지막 필드)는 키보드를 닫는다.
    private func handleSubmit() {
        onSubmit?()
        if submitAction == .next, form?.focusNext(after: fieldID) == true { return }
        isFocused = false
    }

    @ViewBuilder
    private var input: some View {
        if isSecure && !isRevealed {
            SecureField(
                "",
                text: $text,
                prompt: Text(placeholder).foregroundStyle(PapyColor.onSurfaceTertiary)
            )
        } else if multiline {
            TextField(
                "",
                text: $text,
                prompt: Text(placeholder).foregroundStyle(PapyColor.onSurfaceTertiary),
                axis: .vertical
            )
            .lineLimit(3...8)
        } else {
            TextField(
                "",
                text: $text,
                prompt: Text(placeholder).foregroundStyle(PapyColor.onSurfaceTertiary)
            )
        }
    }
}

/// 탭하면 선택 UI(날짜/시간/목록 등)를 여는 읽기 전용 필드. PapyTextField 와 같은 모양.
struct PapySelectField: View {
    let label: String?
    let value: String?
    var placeholder: String? = nil
    var helperText: String? = nil
    var errorText: String? = nil
    var icon: String? = nil
    let onTap: () -> Void

    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        VStack(alignment: .leading, spacing: PapySpacing.xs) {
            if let label {
                PapyFieldLabel(text: label)
            }

            Button(action: onTap) {
                HStack {
                    Text(value ?? placeholder ?? String(localized: "common_select"))
                        .papyFont(PapyFont.body)
                        .foregroundStyle(
                            !isEnabled ? PapyColor.onDisabled
                                : (value == nil ? PapyColor.onSurfaceTertiary : PapyColor.onSurface)
                        )
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if let icon {
                        PapyIcon(icon)
                            .foregroundStyle(PapyColor.onSurfaceVariant)
                    }
                }
                .papyFieldBackground(isError: errorText != nil)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            PapyFieldFooter(helperText: helperText, errorText: errorText)
        }
    }
}

/// 키보드 리턴 키 종류. (Android `ImeAction` 대응)
enum PapySubmitAction {
    case next, done, go, search, send

    var label: SubmitLabel {
        switch self {
        case .next: .next
        case .done: .done
        case .go: .go
        case .search: .search
        case .send: .send
        }
    }
}
