import SwiftUI

// MARK: - Bottom Sheet

extension View {
    /// 공통 바텀시트: 내용 높이만큼 열리고, 끌어내리거나 바깥을 누르면 닫힌다. (Android `ElsaBottomSheet`)
    func elsaBottomSheet<Content: View>(
        isPresented: Binding<Bool>,
        title: String? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        sheet(isPresented: isPresented) {
            ElsaBottomSheetContainer(title: title, content: content)
        }
    }

    /// 액션 메뉴(길게 눌러 메뉴·첨부 메뉴 등): 바텀시트 위의 선택지. 누르면 시트가 닫힌 뒤 그 항목의 동작을 한다
    /// — 카메라·파일 선택처럼 새 화면을 여는 동작이 시트와 겹치지 않게. (Android `ElsaActionSheet`)
    func elsaActionSheet(isPresented: Binding<Bool>, title: String? = nil, actions: [ElsaAction]) -> some View {
        modifier(ElsaActionSheetModifier(isPresented: isPresented, title: title, actions: actions))
    }
}

/// 시트 내용 상자: 높이를 재서 그만큼만 연다(반쯤 열린 단계 없음)
struct ElsaBottomSheetContainer<Content: View>: View {
    let title: String?
    @ViewBuilder let content: () -> Content

    @State private var height: CGFloat = 240

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title)
                    .elsaFont(ElsaFont.title)
                    .foregroundStyle(ElsaColor.onSurface)
                    .padding(.horizontal, ElsaSpacing.xl)
                    .padding(.vertical, ElsaSpacing.md)
            }
            content()
        }
        .padding(.top, ElsaSpacing.xl)
        .padding(.bottom, ElsaSpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
        .presentationDetents([.height(height)])
        .presentationDragIndicator(.visible)
        .presentationBackground(ElsaColor.surface)
    }
}

// MARK: - Action Sheet

/// 액션 메뉴의 한 항목. `isDestructive` 는 삭제처럼 되돌릴 수 없는 동작(빨간 글자). (Android `ElsaAction`)
struct ElsaAction: Identifiable {
    let id = UUID()
    let label: String
    var icon: String? = nil
    var isDestructive = false
    let action: () -> Void
}

private struct ElsaActionSheetModifier: ViewModifier {
    @Binding var isPresented: Bool
    let title: String?
    let actions: [ElsaAction]

    /// 시트가 다 닫힌 뒤(onDismiss) 실행할 항목
    @State private var pending: ElsaAction?

    func body(content: Content) -> some View {
        content.sheet(isPresented: $isPresented, onDismiss: {
            pending?.action()
            pending = nil
        }) {
            ElsaBottomSheetContainer(title: nil) {
                ElsaActionList(title: title, actions: actions) { action in
                    pending = action
                    isPresented = false
                }
            }
        }
    }
}

/// 액션 메뉴 본문(미리보기 대상, DEC-122)
struct ElsaActionList: View {
    let title: String?
    let actions: [ElsaAction]
    let onTap: (ElsaAction) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title)
                    .elsaFont(ElsaFont.bodySmall)
                    .foregroundStyle(ElsaColor.onSurfaceVariant)
                    .padding(.horizontal, ElsaSpacing.xl)
                    .padding(.vertical, ElsaSpacing.md)
            }
            ForEach(actions) { action in
                let color = action.isDestructive ? ElsaColor.error : ElsaColor.onSurface
                Button { onTap(action) } label: {
                    HStack(spacing: ElsaSpacing.lg) {
                        if let icon = action.icon {
                            ElsaIcon(icon).foregroundStyle(color)
                        }
                        Text(action.label)
                            .elsaFont(ElsaFont.body)
                            .foregroundStyle(color)
                        Spacer(minLength: 0)
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

#Preview("액션 메뉴") {
    ElsaActionList(
        title: "첨부",
        actions: [
            ElsaAction(label: "사진", icon: "elsa_ic_image") {},
            ElsaAction(label: "카메라", icon: "elsa_ic_camera") {},
            ElsaAction(label: "파일", icon: "elsa_ic_file") {},
            ElsaAction(label: "삭제", icon: "elsa_ic_delete", isDestructive: true) {},
        ],
        onTap: { _ in }
    )
    .background(ElsaColor.surface)
}
