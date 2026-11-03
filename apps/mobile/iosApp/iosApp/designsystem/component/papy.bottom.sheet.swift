import SwiftUI

// MARK: - Bottom Sheet

extension View {
    /// 공통 바텀시트: 내용 높이만큼 열리고, 끌어내리거나 바깥을 누르면 닫힌다. (Android `PapyBottomSheet`)
    func papyBottomSheet<Content: View>(
        isPresented: Binding<Bool>,
        title: String? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        sheet(isPresented: isPresented) {
            PapyBottomSheetContainer(title: title, content: content)
        }
    }

    /// 액션 메뉴(길게 눌러 메뉴·첨부 메뉴 등): 바텀시트 위의 선택지. 누르면 시트가 닫힌 뒤 그 항목의 동작을 한다
    /// — 카메라·파일 선택처럼 새 화면을 여는 동작이 시트와 겹치지 않게. (Android `PapyActionSheet`)
    func papyActionSheet(isPresented: Binding<Bool>, title: String? = nil, actions: [PapyAction]) -> some View {
        modifier(PapyActionSheetModifier(isPresented: isPresented, title: title, actions: actions))
    }
}

/// 시트 내용 상자: 높이를 재서 그만큼만 연다(반쯤 열린 단계 없음)
struct PapyBottomSheetContainer<Content: View>: View {
    let title: String?
    @ViewBuilder let content: () -> Content

    @State private var height: CGFloat = 240

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title)
                    .papyFont(PapyFont.title)
                    .foregroundStyle(PapyColor.onSurface)
                    .padding(.horizontal, PapySpacing.xl)
                    .padding(.vertical, PapySpacing.md)
            }
            content()
        }
        .padding(.top, PapySpacing.xl)
        .padding(.bottom, PapySpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
        .presentationDetents([.height(height)])
        .presentationDragIndicator(.visible)
        .presentationBackground(PapyColor.surface)
    }
}

// MARK: - Action Sheet

/// 액션 메뉴의 한 항목. `isDestructive` 는 삭제처럼 되돌릴 수 없는 동작(빨간 글자). (Android `PapyAction`)
struct PapyAction: Identifiable {
    let id = UUID()
    let label: String
    var icon: String? = nil
    var isDestructive = false
    let action: () -> Void
}

private struct PapyActionSheetModifier: ViewModifier {
    @Binding var isPresented: Bool
    let title: String?
    let actions: [PapyAction]

    /// 시트가 다 닫힌 뒤(onDismiss) 실행할 항목
    @State private var pending: PapyAction?

    func body(content: Content) -> some View {
        content.sheet(isPresented: $isPresented, onDismiss: {
            pending?.action()
            pending = nil
        }) {
            PapyBottomSheetContainer(title: nil) {
                PapyActionList(title: title, actions: actions) { action in
                    pending = action
                    isPresented = false
                }
            }
        }
    }
}

/// 액션 메뉴 본문(미리보기 대상, DEC-122)
struct PapyActionList: View {
    let title: String?
    let actions: [PapyAction]
    let onTap: (PapyAction) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title)
                    .papyFont(PapyFont.bodySmall)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
                    .padding(.horizontal, PapySpacing.xl)
                    .padding(.vertical, PapySpacing.md)
            }
            ForEach(actions) { action in
                let color = action.isDestructive ? PapyColor.error : PapyColor.onSurface
                Button { onTap(action) } label: {
                    HStack(spacing: PapySpacing.lg) {
                        if let icon = action.icon {
                            PapyIcon(icon).foregroundStyle(color)
                        }
                        Text(action.label)
                            .papyFont(PapyFont.body)
                            .foregroundStyle(color)
                        Spacer(minLength: 0)
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

#Preview("액션 메뉴") {
    PapyActionList(
        title: "첨부",
        actions: [
            PapyAction(label: "사진", icon: "papy_ic_image") {},
            PapyAction(label: "카메라", icon: "papy_ic_camera") {},
            PapyAction(label: "파일", icon: "papy_ic_file") {},
            PapyAction(label: "삭제", icon: "papy_ic_delete", isDestructive: true) {},
        ],
        onTap: { _ in }
    )
    .background(PapyColor.surface)
}
