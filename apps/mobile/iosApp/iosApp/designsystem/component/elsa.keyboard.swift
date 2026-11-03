import Combine
import SwiftUI
import UIKit

/*
 * 키보드 처리 규칙 (앱 전체, Android `ElsaFormColumn` 과 대응)
 * 1. 입력 화면은 ElsaFormScrollView 를 루트로 쓴다
 * 2. 안의 ElsaTextField 는 자동 등록되어, 포커스되면 키보드 위(화면 중앙)로 스크롤된다
 * 3. 키보드 '다음'(submitLabel .next) 은 다음 필드로 이동, 마지막 필드/'완료' 는 키보드를 닫는다
 * 4. 스크롤을 끌거나 빈 곳을 탭하면 키보드가 닫힌다. 키보드 위 '완료' 버튼 제공(NavigationStack 안에서)
 */

/// 폼 안의 입력 필드 순서와 포커스를 관리한다. ElsaFormScrollView 가 environment 로 주입한다.
@MainActor
final class ElsaFormCoordinator: ObservableObject {
    /// 현재 포커스된 필드 (필드가 보고)
    @Published fileprivate(set) var focusedField: UUID?
    /// 포커스 요청 (필드가 구독)
    @Published fileprivate(set) var requestedFocus: UUID?

    private var order: [UUID] = []

    func register(_ id: UUID) {
        if !order.contains(id) { order.append(id) }
    }

    func unregister(_ id: UUID) {
        order.removeAll { $0 == id }
    }

    func reportFocus(_ id: UUID, isFocused: Bool) {
        if isFocused {
            focusedField = id
        } else if focusedField == id {
            focusedField = nil
        }
    }

    /// 다음 필드로 포커스 이동. 마지막이면 false.
    @discardableResult
    func focusNext(after id: UUID) -> Bool {
        guard let index = order.firstIndex(of: id), index + 1 < order.count else { return false }
        requestedFocus = order[index + 1]
        return true
    }

    func dismissKeyboard() {
        requestedFocus = nil
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

private struct ElsaFormCoordinatorKey: EnvironmentKey {
    static let defaultValue: ElsaFormCoordinator? = nil
}

extension EnvironmentValues {
    var elsaFormCoordinator: ElsaFormCoordinator? {
        get { self[ElsaFormCoordinatorKey.self] }
        set { self[ElsaFormCoordinatorKey.self] = newValue }
    }
}

/// 키보드 대응 스크롤 폼 컨테이너. 입력 화면의 기본 레이아웃.
///
/// `bottomBar`: 화면 하단에 고정되는 주요 동작(전폭 CTA 등). 키보드가 올라오면 키보드 바로 위에 붙는다.
/// (Android `ElsaFormColumn(bottomBar =)`)
struct ElsaFormScrollView<Content: View, BottomBar: View>: View {
    private let spacing: CGFloat
    private let padding: CGFloat
    private let content: () -> Content
    private let bottomBar: () -> BottomBar

    @StateObject private var form = ElsaFormCoordinator()

    init(
        spacing: CGFloat = ElsaSpacing.lg,
        padding: CGFloat = ElsaSize.screenPadding,
        @ViewBuilder content: @escaping () -> Content,
        @ViewBuilder bottomBar: @escaping () -> BottomBar
    ) {
        self.spacing = spacing
        self.padding = padding
        self.content = content
        self.bottomBar = bottomBar
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: spacing) {
                    content()
                }
                .padding(padding)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background {
                    // 빈 곳 탭 → 키보드 닫기 (버튼/필드는 자체 탭을 우선 처리)
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture { form.dismissKeyboard() }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: form.focusedField) { _, id in
                scroll(proxy, to: id)
            }
            // 키보드가 다 올라온 뒤 한 번 더 맞춘다 (첫 포커스 시 safe area 변경 반영)
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidShowNotification)) { _ in
                scroll(proxy, to: form.focusedField)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if BottomBar.self != EmptyView.self {
                VStack(spacing: ElsaSpacing.xs) {
                    bottomBar()
                }
                .padding(.horizontal, ElsaSize.screenPadding)
                .padding(.top, ElsaSpacing.sm)
                .padding(.bottom, ElsaSpacing.lg)
                .frame(maxWidth: .infinity)
                .background(ElsaColor.background)
            }
        }
        .environment(\.elsaFormCoordinator, form)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(String(localized: "common_done")) { form.dismissKeyboard() }
                    .foregroundStyle(ElsaColor.primary)
            }
        }
    }

    private func scroll(_ proxy: ScrollViewProxy, to id: UUID?) {
        guard let id else { return }
        withAnimation(.easeOut(duration: ElsaMotion.normal)) {
            proxy.scrollTo(id, anchor: .center)
        }
    }
}

extension ElsaFormScrollView where BottomBar == EmptyView {
    /// 하단 고정 영역이 없는 폼.
    init(
        spacing: CGFloat = ElsaSpacing.lg,
        padding: CGFloat = ElsaSize.screenPadding,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.init(spacing: spacing, padding: padding, content: content, bottomBar: { EmptyView() })
    }
}

/// ElsaTextField 가 폼 코디네이터에 자신을 등록/보고하도록 하는 내부 modifier.
struct ElsaFormFieldRegistration: ViewModifier {
    let id: UUID
    let focus: FocusState<Bool>.Binding
    let form: ElsaFormCoordinator?

    func body(content: Content) -> some View {
        content
            .id(id)
            .onAppear { form?.register(id) }
            .onDisappear { form?.unregister(id) }
            .onChange(of: focus.wrappedValue) { _, focused in
                form?.reportFocus(id, isFocused: focused)
            }
            .onReceive(form?.$requestedFocus.eraseToAnyPublisher() ?? Empty<UUID?, Never>().eraseToAnyPublisher()) { requested in
                if requested == id { focus.wrappedValue = true }
            }
    }
}
