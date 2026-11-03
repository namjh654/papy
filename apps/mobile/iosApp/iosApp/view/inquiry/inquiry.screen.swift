import SwiftUI
import Core

/// 설정 → 문의하기(25-3): 종류(칩) → 내용(여러 줄, 글자 수) → 답장 받을 이메일 → 하단 고정 "보내기". 보내면 안내 후 닫는다. (Android `InquiryScreen`)
struct InquiryScreen: View {

    let onDone: () -> Void

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.inquiryViewModel(),
        state: \.uiState
    )
    @State private var alert: ElsaAlert?

    private var viewModel: InquiryViewModel { model.viewModel }
    private var state: InquiryUiState { model.state }

    var body: some View {
        ElsaFormScrollView {
            ElsaScreenHeader(
                title: String(localized: "inquiry_title"),
                description: String(localized: "inquiry_description")
            )
            InquiryCategories(selected: state.category) { viewModel.categorySelected(category: $0) }
            ElsaTextField(
                String(localized: "inquiry_content"),
                text: Binding(get: { state.content }, set: { viewModel.contentChanged(value: $0) }),
                placeholder: String(localized: "inquiry_content_placeholder"),
                multiline: true,
                maxLength: Int(state.maxContent),
                autocapitalization: .sentences
            )
            ElsaTextField(
                String(localized: "field_email"),
                text: Binding(get: { state.email }, set: { viewModel.emailChanged(value: $0) }),
                placeholder: String(localized: "field_email_placeholder"),
                helperText: String(localized: "inquiry_email_helper"),
                keyboardType: .emailAddress,
                textContentType: .emailAddress,
                submitAction: .done
            )
            if let error = state.error {
                ElsaInlineMessage(message: error.localizedMessage, type: .error)
            }
        } bottomBar: {
            ElsaButton(
                String(localized: "inquiry_submit"),
                enabled: state.canSubmit || state.isSubmitting,
                isLoading: state.isSubmitting
            ) {
                viewModel.submit()
            }
        }
        .elsaScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .elsaAlert($alert)
        .task {
            for await event in viewModel.events {
                switch onEnum(of: event) {
                case .submitted:
                    alert = ElsaAlert(
                        title: String(localized: "inquiry_sent_title"),
                        message: String(localized: "inquiry_sent_message"),
                        onConfirm: onDone
                    )
                }
            }
        }
    }
}

/// 문의 종류 칩
struct InquiryCategories: View {
    let selected: InquiryCategory?
    let onSelect: (InquiryCategory) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ElsaSpacing.sm) {
            Text("inquiry_category")
                .elsaFont(ElsaFont.label)
                .foregroundStyle(ElsaColor.onSurfaceVariant)
            ElsaFlowLayout(spacing: ElsaSpacing.xs, lineSpacing: ElsaSpacing.xs) {
                ForEach(InquiryCategory.allCases, id: \.self) { category in
                    ElsaChip(label: label(category), selected: category == selected, action: { onSelect(category) })
                }
            }
        }
    }

    private func label(_ category: InquiryCategory) -> String {
        switch category {
        case .account: String(localized: "inquiry_category_account")
        case .bug: String(localized: "inquiry_category_bug")
        case .suggestion: String(localized: "inquiry_category_suggestion")
        case .other: String(localized: "inquiry_category_other")
        }
    }
}

#Preview("문의 종류") {
    InquiryCategories(selected: .bug, onSelect: { _ in }).padding()
}
