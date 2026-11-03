import SwiftUI

// MARK: - Alert

/// 확인/취소 알림 내용. 화면 상태로 `PapyAlert?` 를 들고 `.papyAlert($alert)` 로 표시한다.
/// (Android `PapyAlert` + `PapyAlertDialog`)
struct PapyAlert: Identifiable {
    let id = UUID()
    var title: String
    var message: String? = nil
    /// nil 이면 공통 '확인' 문구
    var confirmText: String? = nil
    var dismissText: String? = nil
    var isDestructive = false
    var onConfirm: () -> Void = {}
}

extension View {
    func papyAlert(_ alert: Binding<PapyAlert?>) -> some View {
        self.alert(
            alert.wrappedValue?.title ?? "",
            isPresented: Binding(
                get: { alert.wrappedValue != nil },
                set: { if !$0 { alert.wrappedValue = nil } }
            ),
            presenting: alert.wrappedValue
        ) { item in
            if let dismissText = item.dismissText {
                Button(dismissText, role: .cancel) {}
            }
            Button(item.confirmText ?? String(localized: "common_confirm"), role: item.isDestructive ? ButtonRole.destructive : nil) {
                item.onConfirm()
            }
        } message: { item in
            if let message = item.message {
                Text(message)
            }
        }
    }
}

// MARK: - Toast

/// 잠깐 보였다 사라지는 알림. 화면 상태로 `PapyToast?` 를 들고 `.papyToast($toast)` 로 표시한다.
/// (Android `PapyToastState.show`)
struct PapyToast: Identifiable, Equatable {
    let id = UUID()
    var message: String
    var type: PapyMessageType = .info
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil

    static func == (lhs: PapyToast, rhs: PapyToast) -> Bool { lhs.id == rhs.id }
}

extension View {
    func papyToast(_ toast: Binding<PapyToast?>) -> some View {
        modifier(PapyToastModifier(toast: toast))
    }
}

private struct PapyToastModifier: ViewModifier {
    @Binding var toast: PapyToast?
    @State private var dismissTask: Task<Void, Never>?

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let toast {
                    PapyToastView(toast: toast) { self.toast = nil }
                        .padding(.horizontal, PapySpacing.lg)
                        .padding(.bottom, PapySpacing.lg)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .id(toast.id)
                }
            }
            .animation(.spring(duration: PapyMotion.normal), value: toast?.id)
            .onChange(of: toast?.id) { _, id in
                dismissTask?.cancel()
                guard let id else { return }
                let duration = toast?.actionLabel == nil ? PapyMotion.toast : PapyMotion.toast * 2
                dismissTask = Task { @MainActor in
                    try? await Task.sleep(for: .seconds(duration))
                    if !Task.isCancelled, self.toast?.id == id { self.toast = nil }
                }
            }
    }
}

private struct PapyToastView: View {
    let toast: PapyToast
    let onClose: () -> Void

    var body: some View {
        HStack(spacing: PapySpacing.sm) {
            PapyIcon(toast.type.icon)
                .foregroundStyle(toast.type.accent)
            Text(toast.message)
                .papyFont(PapyFont.bodySmall)
                .foregroundStyle(PapyColor.surface)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let actionLabel = toast.actionLabel {
                Button {
                    toast.action?()
                    onClose()
                } label: {
                    Text(actionLabel)
                        .papyFont(PapyFont.label)
                        .foregroundStyle(PapyColor.primaryContainer)
                }
            }
        }
        .padding(.horizontal, PapySpacing.lg)
        .padding(.vertical, PapySpacing.md)
        // 어떤 배경 위에서도 보이도록 반전 색 사용, 종류는 아이콘 색으로 구분 (Android 동일)
        .background(PapyColor.onSurface, in: RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous))
        .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
        .accessibilityElement(children: .combine)
        // UI 테스트가 화면 전체를 훑지 않고 바로 찾는다(2.5초 안에 사라진다)
        .accessibilityIdentifier("papy.toast")
    }
}

// MARK: - Inline Message

/// 화면 안에 고정 표시하는 알림 박스 (폼 에러 요약, 안내 문구 등). (Android `PapyInlineMessage`)
struct PapyInlineMessage: View {
    let message: String
    var type: PapyMessageType = .info
    var title: String? = nil

    var body: some View {
        HStack(alignment: title == nil ? .center : .top, spacing: PapySpacing.sm) {
            PapyIcon(type.icon)
                .foregroundStyle(type.accent)
            VStack(alignment: .leading, spacing: PapySpacing.xxs) {
                if let title {
                    Text(title).papyFont(PapyFont.subtitle)
                }
                Text(message).papyFont(PapyFont.bodySmall)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundStyle(type.onContainer)
        .padding(.horizontal, PapySpacing.lg)
        .padding(.vertical, PapySpacing.md)
        .background(type.container, in: RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous))
    }
}
