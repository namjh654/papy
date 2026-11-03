import SwiftUI
import PhotosUI
import Core

/// 프로필 편집: 사진(선택 → 저장 때 업로드) + 이름·이메일, 하단 저장 버튼.
/// 저장하지 않은 변경이 있으면 나갈 때 확인한다. (Android `ProfileScreen`)
struct ProfileScreen: View {

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.profileViewModel(),
        state: \.uiState
    )

    @Environment(\.dismiss) private var dismiss

    @State private var toast: PapyToast?
    @State private var alert: PapyAlert?
    @State private var showPicker = false
    @State private var selection: PhotosPickerItem?
    /// 방금 고른 사진(저장되면 서버 주소로 바뀐다)
    @State private var preview: UIImage?

    private var viewModel: ProfileViewModel { model.viewModel }
    private var state: ProfileUiState { model.state }

    /// 올리기 전에 줄이는 긴 변 길이(px)
    private let uploadSize: CGFloat = 1024

    var body: some View {
        PapyFormScrollView {
            VStack(spacing: PapySpacing.sm) {
                PapyAvatar(name: state.name, url: state.avatarUrl, picked: preview, size: 96)
                PapyButton(
                    String(localized: "profile_change_photo"),
                    style: .text,
                    size: .small,
                    enabled: !state.isSaving,
                    fullWidth: false
                ) {
                    showPicker = true
                }
            }
            .frame(maxWidth: .infinity)

            PapyTextField(
                String(localized: "field_name"),
                text: Binding(get: { state.name }, set: { viewModel.nameChanged(value: $0) }),
                placeholder: String(localized: "field_name_placeholder"),
                maxLength: 20,
                textContentType: .name,
                autocapitalization: .words
            )

            PapyTextField(
                String(localized: "field_email"),
                text: Binding(get: { state.email }, set: { viewModel.emailChanged(value: $0) }),
                placeholder: String(localized: "field_email_placeholder"),
                keyboardType: .emailAddress,
                textContentType: .emailAddress,
                submitAction: .done
            )

            if let error = state.error {
                PapyInlineMessage(message: error.localizedMessage, type: .error)
            }
        } bottomBar: {
            PapyButton(
                String(localized: "profile_save"),
                enabled: state.canSave || state.isSaving,
                isLoading: state.isSaving
            ) {
                viewModel.save()
            }
        }
        .papyScreenBackground()
        .navigationTitle(String(localized: "profile_title"))
        .navigationBarTitleDisplayMode(.inline)
        // 저장하지 않은 변경이 있으면 기본 뒤로(밀어서 닫기 포함) 대신 확인을 거치는 버튼을 단다
        .navigationBarBackButtonHidden(state.isDirty)
        .toolbar {
            if state.isDirty {
                ToolbarItem(placement: .topBarLeading) {
                    PapyBackButton { confirmDiscard() }
                }
            }
        }
        // 시스템 사진 선택기(권한 불필요)
        .photosPicker(isPresented: $showPicker, selection: $selection, matching: .images)
        .onChange(of: selection) { _, item in
            guard let item else { return }
            Task { await load(item) }
        }
        .papyToast($toast)
        .papyAlert($alert)
        .task {
            await observeEvents()
        }
    }

    private func confirmDiscard() {
        alert = PapyAlert(
            title: String(localized: "profile_discard_title"),
            message: String(localized: "profile_discard_message"),
            confirmText: String(localized: "profile_discard_confirm"),
            dismissText: String(localized: "common_cancel"),
            isDestructive: true,
            onConfirm: { dismiss() }
        )
    }

    /// 고른 사진을 줄여 JPEG 로 만들고(업로드 크기 제한), 미리보기와 KMP 에 넘긴다
    private func load(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else { return }
        let scale = min(1, uploadSize / max(image.size.width, image.size.height))
        let target = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let resized = image.preparingThumbnail(of: target) ?? image
        guard let jpeg = resized.jpegData(compressionQuality: 0.8) else { return }
        preview = resized
        viewModel.avatarPicked(bytes: IOSAppGraph.shared.bytes(data: jpeg))
    }
}


// MARK: - Event Observation

private extension ProfileScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .saved:
                preview = nil
                selection = nil
                toast = PapyToast(message: String(localized: "profile_saved"), type: .success)
            }
        }
    }
}
