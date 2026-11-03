import SwiftUI

// 커뮤니티 UI(20-2). Android `PapyPost.kt` 와 같다. 수치는 design/components.md "커뮤니티"

/// 글쓴이 줄: 아바타 40 + 이름(bodyStrong)·시간(caption) + 더보기. `onAuthorTap` 이 있으면 아바타·이름을 눌러 프로필로(25-1)
struct PapyAuthorRow: View {
    let name: String
    let avatarUrl: String?
    let time: String
    var onMore: (() -> Void)? = nil
    var onAuthorTap: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: PapySpacing.sm) {
            Button { onAuthorTap?() } label: {
                HStack(spacing: PapySpacing.sm) {
                    PapyAvatar(name: name, url: avatarUrl, size: 40)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(name)
                            .papyFont(PapyFont.bodyStrong)
                            .foregroundStyle(PapyColor.onSurface)
                            .lineLimit(1)
                        Text(time)
                            .papyFont(PapyFont.caption)
                            .foregroundStyle(PapyColor.onSurfaceTertiary)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(onAuthorTap == nil)
            Spacer(minLength: 0)
            if let onMore {
                Button(action: onMore) {
                    PapyIcon("papy_ic_more")
                        .foregroundStyle(PapyColor.onSurfaceVariant)
                        .frame(width: PapySize.controlHeightSmall, height: PapySize.controlHeightSmall)
                }
                .accessibilityLabel(String(localized: "community_more"))
            }
        }
    }
}

/// 사진 묶음 1~4장: 1장 16:9, 2장 나란히 정사각, 3장 왼쪽 큰 것 + 오른쪽 둘, 4장 2×2. 사이 간격 `spacing.xxs`, 모서리 `radius.md`.
struct PapyImageGrid<Cell: View>: View {
    let count: Int
    @ViewBuilder let image: (Int) -> Cell

    private let gap = PapySpacing.xxs

    var body: some View {
        if count > 0 {
            grid
                .clipShape(RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous))
        }
    }

    @ViewBuilder
    private var grid: some View {
        switch count {
        case 1:
            cell(0).aspectRatio(16 / 9, contentMode: .fit)
        case 2:
            HStack(spacing: gap) { cell(0); cell(1) }.aspectRatio(2, contentMode: .fit)
        case 3:
            GeometryReader { geo in
                HStack(spacing: gap) {
                    cell(0).frame(width: (geo.size.width - gap) * 2 / 3)
                    VStack(spacing: gap) { cell(1); cell(2) }
                }
            }
            .aspectRatio(3 / 2, contentMode: .fit)
        default:
            VStack(spacing: gap) {
                HStack(spacing: gap) { cell(0); cell(1) }
                HStack(spacing: gap) { cell(2); cell(3) }
            }
            .aspectRatio(1, contentMode: .fit)
        }
    }

    private func cell(_ index: Int) -> some View {
        Color.clear
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay { image(index) }
            .clipped()
    }
}

/// 원격 사진 한 칸(주소가 비어 있으면 회색 자리)
struct PapyPostImage: View {
    let url: String

    var body: some View {
        ZStack {
            PapyColor.fill
            if !url.isEmpty, let remote = URL(string: url) {
                PapyRemoteImage(url: remote.absoluteString)
            } else {
                PapyIcon("papy_ic_image").foregroundStyle(PapyColor.onSurfaceTertiary)
            }
        }
        .accessibilityLabel(String(localized: "community_photo"))
    }
}

/// 좋아요·댓글 수 버튼. 좋아요를 누른 상태는 채운 하트 + `error` 색
struct PapyReactionButton: View {
    let icon: String
    let count: Int32
    let label: String
    var active = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: PapySpacing.xs) {
                PapyIcon(icon)
                Text("\(count)").papyFont(PapyFont.label)
            }
            .foregroundStyle(active ? PapyColor.error : PapyColor.onSurfaceVariant)
            .padding(.horizontal, PapySpacing.sm)
            .frame(minHeight: PapySize.controlHeightSmall)
            .contentShape(Capsule())
        }
        .buttonStyle(PapyPressableButtonStyle())
        .accessibilityLabel(label)
    }
}

/// 좋아요 버튼
struct PapyLikeButton: View {
    let liked: Bool
    let count: Int32
    let action: () -> Void

    var body: some View {
        PapyReactionButton(icon: liked ? "papy_ic_heart_filled" : "papy_ic_heart", count: count, label: String(localized: "community_like"), active: liked, action: action)
    }
}

/// 글 카드(목록): 무테 카드 위에 글쓴이 줄 → 본문(최대 3줄) → 사진 묶음 → 좋아요·댓글. 목록은 `backgroundGrouped` 위, 카드는 `surface`.
struct PapyPostCard<Cell: View>: View {
    let authorName: String
    let avatarUrl: String?
    let time: String
    let content: String
    let imageCount: Int
    let liked: Bool
    let likeCount: Int32
    let commentCount: Int32
    let onTap: () -> Void
    let onLike: () -> Void
    var onMore: (() -> Void)? = nil
    var maxLines = 3
    var onAuthorTap: (() -> Void)? = nil
    @ViewBuilder let image: (Int) -> Cell

    var body: some View {
        VStack(alignment: .leading, spacing: PapySpacing.sm) {
            PapyAuthorRow(name: authorName, avatarUrl: avatarUrl, time: time, onMore: onMore, onAuthorTap: onAuthorTap)
            Text(content)
                .papyFont(PapyFont.body)
                .foregroundStyle(PapyColor.onSurface)
                .lineLimit(maxLines)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.trailing, PapySpacing.md)
            PapyImageGrid(count: imageCount, image: image)
                .padding(.trailing, PapySpacing.md)
            HStack(spacing: PapySpacing.xs) {
                PapyLikeButton(liked: liked, count: likeCount, action: onLike)
                PapyReactionButton(icon: "papy_ic_comment", count: commentCount, label: String(localized: "community_comments"), action: onTap)
            }
            .offset(x: -PapySpacing.sm)
        }
        .padding(.leading, PapySpacing.lg)
        .padding(.trailing, PapySpacing.xs)
        .padding(.top, PapySpacing.md)
        .padding(.bottom, PapySpacing.xs)
        .background(PapyColor.surface, in: RoundedRectangle(cornerRadius: PapyRadius.xl, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: PapyRadius.xl, style: .continuous))
        .onTapGesture(perform: onTap)
    }
}

/// 댓글 한 줄: 아바타 32 + 이름·시간 + 내용, 더보기로 메뉴. `onAuthorTap`: 아바타를 눌러 프로필로
struct PapyCommentRow: View {
    let authorName: String
    let avatarUrl: String?
    let time: String
    let content: String
    var onMore: (() -> Void)? = nil
    var onAuthorTap: (() -> Void)? = nil

    var body: some View {
        HStack(alignment: .top, spacing: PapySpacing.sm) {
            Button { onAuthorTap?() } label: {
                PapyAvatar(name: authorName, url: avatarUrl, size: 32)
            }
            .buttonStyle(.plain)
            .disabled(onAuthorTap == nil)
            VStack(alignment: .leading, spacing: PapySpacing.xxs) {
                HStack(spacing: PapySpacing.xs) {
                    Text(authorName).papyFont(PapyFont.label).foregroundStyle(PapyColor.onSurface)
                    Text(time).papyFont(PapyFont.caption).foregroundStyle(PapyColor.onSurfaceTertiary)
                }
                Text(content).papyFont(PapyFont.body).foregroundStyle(PapyColor.onSurface)
            }
            Spacer(minLength: 0)
            if let onMore {
                Button(action: onMore) {
                    PapyIcon("papy_ic_more")
                        .foregroundStyle(PapyColor.onSurfaceTertiary)
                        .frame(width: PapySize.controlHeightSmall, height: PapySize.controlHeightSmall)
                }
                .accessibilityLabel(String(localized: "community_more"))
            }
        }
        .padding(.vertical, PapySpacing.sm)
    }
}

/// 화면 오른쪽 아래에 뜨는 둥근 동작 버튼(글쓰기 등). 지름 `controlHeight`, `primary`. (Android `PapyFloatingButton`)
struct PapyFloatingButton: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            PapyIcon(icon, size: PapySize.iconLarge)
                .foregroundStyle(PapyColor.onPrimary)
                .frame(width: PapySize.controlHeight, height: PapySize.controlHeight)
                .background(PapyColor.primary, in: Circle())
                .shadow(color: .black.opacity(0.2), radius: 6, y: 3)
        }
        .accessibilityLabel(label)
    }
}

#Preview("커뮤니티") {
    VStack(spacing: PapySpacing.md) {
        PapyPostCard(
            authorName: "엘사", avatarUrl: nil, time: "5분 전",
            content: "오늘 날씨가 너무 좋아요. 산책 다녀왔어요! 다들 주말 잘 보내세요.",
            imageCount: 3, liked: true, likeCount: 12, commentCount: 3,
            onTap: {}, onLike: {}, onMore: {}
        ) { _ in PapyPostImage(url: "") }
        PapyCommentRow(authorName: "안나", avatarUrl: nil, time: "방금 전", content: "좋아요!", onMore: {})
    }
    .padding(PapySpacing.lg)
    .papyScreenBackground(grouped: true)
}
