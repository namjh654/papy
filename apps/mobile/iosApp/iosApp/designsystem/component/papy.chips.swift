import SwiftUI

// 공통 UI 2차(21-1). Android `PapyChips.kt` 와 같다. 수치는 design/components.md

/// 칩: 둥근 알약. `selected` 면 `primary` 채움, 아니면 `fill`. `onRemove` 가 있으면 오른쪽에 지우기(최근 검색어 등).
struct PapyChip: View {
    let label: String
    var selected = false
    var action: (() -> Void)? = nil
    var onRemove: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: PapySpacing.xxs) {
            Text(label)
                .papyFont(PapyFont.label)
                .foregroundStyle(selected ? PapyColor.onPrimary : PapyColor.onSurface)
                .lineLimit(1)
            if let onRemove {
                Button(action: onRemove) {
                    PapyIcon("papy_ic_close", size: PapySize.iconSmall)
                        .foregroundStyle(PapyColor.onSurfaceTertiary)
                        .frame(width: 28, height: 28)
                }
                .accessibilityLabel(String(localized: "search_recent_remove"))
            }
        }
        .padding(.leading, PapySpacing.md)
        .padding(.trailing, onRemove == nil ? PapySpacing.md : PapySpacing.xs)
        .frame(minHeight: 36)
        .background(selected ? PapyColor.primary : PapyColor.fill, in: Capsule())
        .contentShape(Capsule())
        .onTapGesture { action?() }
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

/// 칩 여러 개: 넘치면 다음 줄로
struct PapyChipGroup<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        PapyFlowLayout(spacing: PapySpacing.sm, lineSpacing: PapySpacing.sm) { content() }
    }
}

/// 세그먼트 탭: `fill` 바탕 위에 고른 칸만 `surface` 로 떠 보인다. 칸 너비는 같다. (Android `PapySegmentedTabs`)
struct PapySegmentedTabs: View {
    let options: [String]
    let selectedIndex: Int
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(options.enumerated()), id: \.offset) { index, label in
                let selected = index == selectedIndex
                Button { onSelect(index) } label: {
                    Text(label)
                        .papyFont(selected ? PapyFont.bodyStrong : PapyFont.body)
                        .foregroundStyle(selected ? PapyColor.onSurface : PapyColor.onSurfaceVariant)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: PapySize.controlHeightSmall - PapySpacing.xxs * 2)
                        .background {
                            if selected {
                                RoundedRectangle(cornerRadius: PapyRadius.sm, style: .continuous)
                                    .fill(PapyColor.surface)
                                    .overlay(RoundedRectangle(cornerRadius: PapyRadius.sm, style: .continuous).stroke(PapyColor.divider, lineWidth: PapySize.border))
                            }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(PapySpacing.xxs)
        .background(PapyColor.fill, in: RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous))
        .animation(.easeInOut(duration: 0.15), value: selectedIndex)
    }
}

/// 스켈레톤: 내용 자리에 `fill` 색 상자가 천천히 깜박인다(1초 주기)
struct PapySkeleton: View {
    var radius: CGFloat = PapyRadius.sm
    @State private var dim = false

    var body: some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(PapyColor.fill)
            .opacity(dim ? 0.45 : 1)
            .onAppear {
                withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) { dim = true }
            }
            .accessibilityHidden(true)
    }
}

/// 목록 행 자리(아바타 + 두 줄) — 목록 첫 로딩
struct PapySkeletonRow: View {
    var avatar: CGFloat = 48

    var body: some View {
        HStack(spacing: PapySpacing.md) {
            PapySkeleton(radius: PapyRadius.full).frame(width: avatar, height: avatar)
            VStack(alignment: .leading, spacing: PapySpacing.sm) {
                GeometryReader { geo in
                    VStack(alignment: .leading, spacing: PapySpacing.sm) {
                        PapySkeleton().frame(width: geo.size.width * 0.4, height: 14)
                        PapySkeleton().frame(width: geo.size.width * 0.8, height: 12)
                    }
                }
                .frame(height: 34)
            }
        }
        .padding(.horizontal, PapySpacing.xl)
        .padding(.vertical, PapySpacing.md)
    }
}

/// 글 카드 자리 — 커뮤니티 첫 로딩
struct PapySkeletonCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: PapySpacing.sm) {
            HStack(spacing: PapySpacing.sm) {
                PapySkeleton(radius: PapyRadius.full).frame(width: 40, height: 40)
                PapySkeleton().frame(width: 100, height: 14)
            }
            PapySkeleton().frame(height: 12)
            PapySkeleton().frame(width: 220, height: 12)
        }
        .padding(PapySpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PapyColor.surface, in: RoundedRectangle(cornerRadius: PapyRadius.xl, style: .continuous))
    }
}

/// 검색창: 둥근 채움 칸, 앞 돋보기, 글자가 있으면 뒤에 지우기, 키보드 검색 키 → `onSearch`. (Android `PapySearchBar`)
struct PapySearchBar: View {
    @Binding var text: String
    var placeholder: String = String(localized: "search_placeholder")
    /// 열자마자 입력하게 할 때(대화 검색 등)
    var focus: FocusState<Bool>.Binding? = nil
    let onSearch: () -> Void

    var body: some View {
        HStack(spacing: PapySpacing.sm) {
            PapyIcon("papy_ic_search").foregroundStyle(PapyColor.onSurfaceTertiary)
            field
                .papyFont(PapyFont.body)
                .submitLabel(.search)
                .onSubmit(onSearch)
                .autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: {
                    PapyIcon("papy_ic_close", size: PapySize.iconSmall)
                        .foregroundStyle(PapyColor.onSurfaceTertiary)
                        .frame(width: 36, height: 36)
                }
                .accessibilityLabel(String(localized: "search_clear"))
            }
        }
        .padding(.leading, PapySpacing.md)
        .padding(.trailing, PapySpacing.xs)
        .frame(minHeight: 44)
        .background(PapyColor.fill, in: Capsule())
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

private struct PapyListSkeletonKey: EnvironmentKey {
    static let defaultValue: AnyView? = nil
}

extension EnvironmentValues {
    /// `PapyPagedList` 첫 로딩 때 가운데 로딩 대신 보일 자리(스켈레톤 등)
    var papyListSkeleton: AnyView? {
        get { self[PapyListSkeletonKey.self] }
        set { self[PapyListSkeletonKey.self] = newValue }
    }
}

extension View {
    /// 목록 첫 로딩 자리. (Android `PapyPagedList(loadingContent = …)`)
    func papyListSkeleton<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        environment(\.papyListSkeleton, AnyView(content()))
    }
}

#Preview("칩·세그먼트·스켈레톤·검색창") {
    VStack(spacing: PapySpacing.lg) {
        PapySearchBar(text: .constant("엘사"), onSearch: {})
        PapyChipGroup {
            PapyChip(label: "전체", selected: true)
            PapyChip(label: "인기")
            PapyChip(label: "최근 검색어", onRemove: {})
        }
        PapySegmentedTabs(options: ["전체", "내 글"], selectedIndex: 0, onSelect: { _ in })
        PapySkeletonRow()
        PapySkeletonCard()
    }
    .padding(PapySpacing.lg)
    .papyScreenBackground()
}
