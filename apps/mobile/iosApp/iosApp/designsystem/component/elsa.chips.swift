import SwiftUI

// 공통 UI 2차(21-1). Android `ElsaChips.kt` 와 같다. 수치는 design/components.md

/// 칩: 둥근 알약. `selected` 면 `primary` 채움, 아니면 `fill`. `onRemove` 가 있으면 오른쪽에 지우기(최근 검색어 등).
struct ElsaChip: View {
    let label: String
    var selected = false
    var action: (() -> Void)? = nil
    var onRemove: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: ElsaSpacing.xxs) {
            Text(label)
                .elsaFont(ElsaFont.label)
                .foregroundStyle(selected ? ElsaColor.onPrimary : ElsaColor.onSurface)
                .lineLimit(1)
            if let onRemove {
                Button(action: onRemove) {
                    ElsaIcon("elsa_ic_close", size: ElsaSize.iconSmall)
                        .foregroundStyle(ElsaColor.onSurfaceTertiary)
                        .frame(width: 28, height: 28)
                }
                .accessibilityLabel(String(localized: "search_recent_remove"))
            }
        }
        .padding(.leading, ElsaSpacing.md)
        .padding(.trailing, onRemove == nil ? ElsaSpacing.md : ElsaSpacing.xs)
        .frame(minHeight: 36)
        .background(selected ? ElsaColor.primary : ElsaColor.fill, in: Capsule())
        .contentShape(Capsule())
        .onTapGesture { action?() }
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

/// 칩 여러 개: 넘치면 다음 줄로
struct ElsaChipGroup<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        ElsaFlowLayout(spacing: ElsaSpacing.sm, lineSpacing: ElsaSpacing.sm) { content() }
    }
}

/// 세그먼트 탭: `fill` 바탕 위에 고른 칸만 `surface` 로 떠 보인다. 칸 너비는 같다. (Android `ElsaSegmentedTabs`)
struct ElsaSegmentedTabs: View {
    let options: [String]
    let selectedIndex: Int
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(options.enumerated()), id: \.offset) { index, label in
                let selected = index == selectedIndex
                Button { onSelect(index) } label: {
                    Text(label)
                        .elsaFont(selected ? ElsaFont.bodyStrong : ElsaFont.body)
                        .foregroundStyle(selected ? ElsaColor.onSurface : ElsaColor.onSurfaceVariant)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: ElsaSize.controlHeightSmall - ElsaSpacing.xxs * 2)
                        .background {
                            if selected {
                                RoundedRectangle(cornerRadius: ElsaRadius.sm, style: .continuous)
                                    .fill(ElsaColor.surface)
                                    .overlay(RoundedRectangle(cornerRadius: ElsaRadius.sm, style: .continuous).stroke(ElsaColor.divider, lineWidth: ElsaSize.border))
                            }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(ElsaSpacing.xxs)
        .background(ElsaColor.fill, in: RoundedRectangle(cornerRadius: ElsaRadius.md, style: .continuous))
        .animation(.easeInOut(duration: 0.15), value: selectedIndex)
    }
}

/// 스켈레톤: 내용 자리에 `fill` 색 상자가 천천히 깜박인다(1초 주기)
struct ElsaSkeleton: View {
    var radius: CGFloat = ElsaRadius.sm
    @State private var dim = false

    var body: some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(ElsaColor.fill)
            .opacity(dim ? 0.45 : 1)
            .onAppear {
                withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) { dim = true }
            }
            .accessibilityHidden(true)
    }
}

/// 목록 행 자리(아바타 + 두 줄) — 목록 첫 로딩
struct ElsaSkeletonRow: View {
    var avatar: CGFloat = 48

    var body: some View {
        HStack(spacing: ElsaSpacing.md) {
            ElsaSkeleton(radius: ElsaRadius.full).frame(width: avatar, height: avatar)
            VStack(alignment: .leading, spacing: ElsaSpacing.sm) {
                GeometryReader { geo in
                    VStack(alignment: .leading, spacing: ElsaSpacing.sm) {
                        ElsaSkeleton().frame(width: geo.size.width * 0.4, height: 14)
                        ElsaSkeleton().frame(width: geo.size.width * 0.8, height: 12)
                    }
                }
                .frame(height: 34)
            }
        }
        .padding(.horizontal, ElsaSpacing.xl)
        .padding(.vertical, ElsaSpacing.md)
    }
}

/// 글 카드 자리 — 커뮤니티 첫 로딩
struct ElsaSkeletonCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: ElsaSpacing.sm) {
            HStack(spacing: ElsaSpacing.sm) {
                ElsaSkeleton(radius: ElsaRadius.full).frame(width: 40, height: 40)
                ElsaSkeleton().frame(width: 100, height: 14)
            }
            ElsaSkeleton().frame(height: 12)
            ElsaSkeleton().frame(width: 220, height: 12)
        }
        .padding(ElsaSpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ElsaColor.surface, in: RoundedRectangle(cornerRadius: ElsaRadius.xl, style: .continuous))
    }
}

/// 검색창: 둥근 채움 칸, 앞 돋보기, 글자가 있으면 뒤에 지우기, 키보드 검색 키 → `onSearch`. (Android `ElsaSearchBar`)
struct ElsaSearchBar: View {
    @Binding var text: String
    var placeholder: String = String(localized: "search_placeholder")
    /// 열자마자 입력하게 할 때(대화 검색 등)
    var focus: FocusState<Bool>.Binding? = nil
    let onSearch: () -> Void

    var body: some View {
        HStack(spacing: ElsaSpacing.sm) {
            ElsaIcon("elsa_ic_search").foregroundStyle(ElsaColor.onSurfaceTertiary)
            field
                .elsaFont(ElsaFont.body)
                .submitLabel(.search)
                .onSubmit(onSearch)
                .autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: {
                    ElsaIcon("elsa_ic_close", size: ElsaSize.iconSmall)
                        .foregroundStyle(ElsaColor.onSurfaceTertiary)
                        .frame(width: 36, height: 36)
                }
                .accessibilityLabel(String(localized: "search_clear"))
            }
        }
        .padding(.leading, ElsaSpacing.md)
        .padding(.trailing, ElsaSpacing.xs)
        .frame(minHeight: 44)
        .background(ElsaColor.fill, in: Capsule())
    }

    @ViewBuilder
    private var field: some View {
        if let focus {
            TextField(placeholder, text: $text).focused(focus)
        } else {
            TextField(placeholder, text: $text)
        }
    }
}

// MARK: - 목록 첫 로딩 자리

private struct ElsaListSkeletonKey: EnvironmentKey {
    static let defaultValue: AnyView? = nil
}

extension EnvironmentValues {
    /// `ElsaPagedList` 첫 로딩 때 가운데 로딩 대신 보일 자리(스켈레톤 등)
    var elsaListSkeleton: AnyView? {
        get { self[ElsaListSkeletonKey.self] }
        set { self[ElsaListSkeletonKey.self] = newValue }
    }
}

extension View {
    /// 목록 첫 로딩 자리. (Android `ElsaPagedList(loadingContent = …)`)
    func elsaListSkeleton<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        environment(\.elsaListSkeleton, AnyView(content()))
    }
}

#Preview("칩·세그먼트·스켈레톤·검색창") {
    VStack(spacing: ElsaSpacing.lg) {
        ElsaSearchBar(text: .constant("엘사"), onSearch: {})
        ElsaChipGroup {
            ElsaChip(label: "전체", selected: true)
            ElsaChip(label: "인기")
            ElsaChip(label: "최근 검색어", onRemove: {})
        }
        ElsaSegmentedTabs(options: ["전체", "내 글"], selectedIndex: 0, onSelect: { _ in })
        ElsaSkeletonRow()
        ElsaSkeletonCard()
    }
    .padding(ElsaSpacing.lg)
    .elsaScreenBackground()
}
