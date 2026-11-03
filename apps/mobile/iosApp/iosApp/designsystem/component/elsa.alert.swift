import SwiftUI

// MARK: - Alert

/// 확인/취소 알림 내용. 화면 상태로 `ElsaAlert?` 를 들고 `.elsaAlert($alert)` 로 표시한다.
/// (Android `ElsaAlert` + `ElsaAlertDialog`)
struct ElsaAlert: Identifiable {
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
    func elsaAlert(_ alert: Binding<ElsaAlert?>) -> some View {
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

/// 잠깐 보였다 사라지는 알림. 화면 상태로 `ElsaToast?` 를 들고 `.elsaToast($toast)` 로 표시한다.
/// (Android `ElsaToastState.show`)
struct ElsaToast: Identifiable, Equatable {
    let id = UUID()
    var message: String
    var type: ElsaMessageType = .info
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil

    static func == (lhs: ElsaToast, rhs: ElsaToast) -> Bool { lhs.id == rhs.id }
}

extension View {
    func elsaToast(_ toast: Binding<ElsaToast?>) -> some View {
        modifier(ElsaToastModifier(toast: toast))
    }
}

private struct ElsaToastModifier: ViewModifier {
    @Binding var toast: ElsaToast?
    @State private var dismissTask: Task<Void, Never>?

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let toast {
                    ElsaToastView(toast: toast) { self.toast = nil }
                        .padding(.horizontal, ElsaSpacing.lg)
                        .padding(.bottom, ElsaSpacing.lg)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .id(toast.id)
                }
            }
            .animation(.spring(duration: ElsaMotion.normal), value: toast?.id)
            .onChange(of: toast?.id) { _, id in
                dismissTask?.cancel()
                guard let id else { return }
                let duration = toast?.actionLabel == nil ? ElsaMotion.toast : ElsaMotion.toast * 2
                dismissTask = Task { @MainActor in
                    try? await Task.sleep(for: .seconds(duration))
                    if !Task.isCancelled, self.toast?.id == id { self.toast = nil }
                }
            }
    }
}

private struct ElsaToastView: View {
    let toast: ElsaToast
    let onClose: () -> Void

    var body: some View {
        HStack(spacing: ElsaSpacing.sm) {
            ElsaIcon(toast.type.icon)
                .foregroundStyle(toast.type.accent)
            Text(toast.message)
                .elsaFont(ElsaFont.bodySmall)
                .foregroundStyle(ElsaColor.surface)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let actionLabel = toast.actionLabel {
                Button {
                    toast.action?()
                    onClose()
                } label: {
                    Text(actionLabel)
                        .elsaFont(ElsaFont.label)
                        .foregroundStyle(ElsaColor.primaryContainer)
                }
            }
        }
        .padding(.horizontal, ElsaSpacing.lg)
        .padding(.vertical, ElsaSpacing.md)
        // 어떤 배경 위에서도 보이도록 반전 색 사용, 종류는 아이콘 색으로 구분 (Android 동일)
        .background(ElsaColor.onSurface, in: RoundedRectangle(cornerRadius: ElsaRadius.md, style: .continuous))
        .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
        .accessibilityElement(children: .combine)
        // UI 테스트가 화면 전체를 훑지 않고 바로 찾는다(2.5초 안에 사라진다)
        .accessibilityIdentifier("elsa.toast")
    }
}

// MARK: - Inline Message

/// 화면 안에 고정 표시하는 알림 박스 (폼 에러 요약, 안내 문구 등). (Android `ElsaInlineMessage`)
struct ElsaInlineMessage: View {
    let message: String
    var type: ElsaMessageType = .info
    var title: String? = nil

    var body: some View {
        HStack(alignment: title == nil ? .center : .top, spacing: ElsaSpacing.sm) {
            ElsaIcon(type.icon)
                .foregroundStyle(type.accent)
            VStack(alignment: .leading, spacing: ElsaSpacing.xxs) {
                if let title {
                    Text(title).elsaFont(ElsaFont.subtitle)
                }
                Text(message).elsaFont(ElsaFont.bodySmall)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundStyle(type.onContainer)
        .padding(.horizontal, ElsaSpacing.lg)
        .padding(.vertical, ElsaSpacing.md)
        .background(type.container, in: RoundedRectangle(cornerRadius: ElsaRadius.md, style: .continuous))
    }
}
