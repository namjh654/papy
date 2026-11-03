import SwiftUI
import Core

// 메시지(채팅) UI — 화면 구성·동작은 텔레그램처럼, 코드·이름·로고는 쓰지 않는다(DEC-111·125). Android `ElsaChat.kt` 와 같다.

private enum BubbleMetrics {
    /// 말풍선 최대 너비(두 플랫폼이 같은 줄바꿈)
    static let maxWidth: CGFloat = 300
    static let corner: CGFloat = 18
    static let joined: CGFloat = 6
}

/// 말풍선. 내 것은 오른쪽·`primary`, 상대 것은 왼쪽·`surface`. 묶음(`position`)의 이어지는 쪽 모서리는 작게, 마지막은 꼬리 쪽 모서리를 작게.
/// `time` 이 있으면(묶음의 마지막) 오른쪽 아래에 시간·전송 상태를 붙인다. (Android `ElsaMessageBubble`)
struct ElsaMessageBubble<Content: View>: View {
    let isMine: Bool
    let position: BubblePosition
    var time: String? = nil
    var sendState: SendState = .sent
    var isRead = false
    /// 사진처럼 말풍선 여백 없이 꽉 채우는 내용
    var fillContent = false
    @ViewBuilder let content: () -> Content

    var body: some View {
        HStack(spacing: ElsaSpacing.xs) {
            if isMine { Spacer(minLength: ElsaSpacing.xxxl) }
            if sendState == .failed {
                ElsaIcon("elsa_ic_error")
                    .foregroundStyle(ElsaColor.error)
                    .accessibilityLabel(String(localized: "chat_failed"))
            }
            BubbleStack(spacing: ElsaSpacing.xxs) {
                content()
                if let time {
                    meta(time)
                        .padding(fillContent ? ElsaSpacing.xs : 0)
                }
            }
            .padding(.horizontal, fillContent ? 0 : ElsaSpacing.md)
            .padding(.vertical, fillContent ? 0 : ElsaSpacing.sm)
            .foregroundStyle(onContainer)
            .background(container)
            .clipShape(shape)
            .frame(maxWidth: BubbleMetrics.maxWidth, alignment: isMine ? .trailing : .leading)
            .fixedSize(horizontal: false, vertical: true)
            if !isMine { Spacer(minLength: ElsaSpacing.xxxl) }
        }
    }

    private var container: Color { isMine ? ElsaColor.primary : ElsaColor.surface }
    private var onContainer: Color { isMine ? ElsaColor.onPrimary : ElsaColor.onSurface }

    private var shape: UnevenRoundedRectangle {
        let joinsPrevious = position == .middle || position == .last
        let joinsNext = position == .first || position == .middle
        let near = joinsPrevious ? BubbleMetrics.joined : BubbleMetrics.corner
        let far = joinsNext ? BubbleMetrics.joined : BubbleMetrics.joined / 2
        let big = BubbleMetrics.corner
        return isMine
            ? UnevenRoundedRectangle(topLeadingRadius: big, bottomLeadingRadius: big, bottomTrailingRadius: far, topTrailingRadius: near, style: .continuous)
            : UnevenRoundedRectangle(topLeadingRadius: near, bottomLeadingRadius: far, bottomTrailingRadius: big, topTrailingRadius: big, style: .continuous)
    }

    private func meta(_ time: String) -> some View {
        let color = fillContent ? ElsaColor.onSurfaceVariant : onContainer.opacity(0.7)
        return HStack(spacing: ElsaSpacing.xxs) {
            Text(time).elsaFont(ElsaFont.caption)
            if isMine, let icon = statusIcon {
                ElsaIcon(icon, size: 14)
            }
        }
        .foregroundStyle(color)
    }

    private var statusIcon: String? {
        switch sendState {
        case .sending: "elsa_ic_clock"
        case .failed: nil
        case .sent: isRead ? "elsa_ic_check_double" : "elsa_ic_check"
        }
    }
}

/// 말풍선 안쪽 배치: 너비는 내용에 맞추고(최대는 부르는 쪽이 정한다), 시간 줄(두 번째)은 오른쪽 아래.
/// VStack 에 maxWidth: .infinity 를 주면 짧은 말풍선도 최대 너비로 늘어나서 직접 잰다. Android 는 Column 이 같은 일을 한다
private struct BubbleStack: Layout {
    let spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let sizes = subviews.map { $0.sizeThatFits(ProposedViewSize(width: proposal.width, height: nil)) }
        let width = min(proposal.width ?? .infinity, sizes.map(\.width).max() ?? 0)
        let height = sizes.map(\.height).reduce(0, +) + spacing * CGFloat(max(0, sizes.count - 1))
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for (index, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
            let x = index == 0 ? bounds.minX : bounds.maxX - size.width
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(width: index == 0 ? bounds.width : size.width, height: size.height))
            y += size.height + spacing
        }
    }
}

/// 말풍선 글자
struct ElsaMessageText: View {
    let text: String

    var body: some View {
        Text(text).elsaFont(ElsaFont.body)
    }
}

/// 사진 말풍선 내용: 기기 미리보기(`preview`, 보내는 중) 또는 서버 주소(`url`). 둘 다 없으면 사진 아이콘 자리.
struct ElsaMessageImage: View {
    let url: String?
    let preview: UIImage?

    var body: some View {
        ZStack {
            ElsaColor.fill
            if let preview {
                Image(uiImage: preview).resizable().scaledToFill()
            } else if let url, !url.isEmpty {
                ElsaRemoteImage(url: url) { ProgressView() }
            } else {
                ElsaIcon("elsa_ic_image").foregroundStyle(ElsaColor.onSurfaceTertiary)
            }
        }
        .frame(width: BubbleMetrics.maxWidth, height: BubbleMetrics.maxWidth * 3 / 4)
        .clipped()
        .accessibilityLabel(String(localized: "chat_attachment_photo"))
    }
}

/// 파일 말풍선 내용: 아이콘 + 이름 + 크기·"저장됨". `downloading` 이면 아이콘 자리에 받는 정도(`progress`, 모르면 돌아가는 표시) — 30-1
struct ElsaMessageFile: View {
    let name: String
    let size: String
    var downloading = false
    var progress: Float? = nil

    var body: some View {
        HStack(spacing: ElsaSpacing.sm) {
            Group {
                if downloading, let progress {
                    ProgressView(value: Double(progress)).progressViewStyle(.circular)
                } else if downloading {
                    ProgressView()
                } else {
                    ElsaIcon("elsa_ic_file")
                }
            }
            .frame(width: 40, height: 40)
            .background(.foreground.opacity(0.15), in: Circle())
            VStack(alignment: .leading, spacing: 0) {
                Text(name).elsaFont(ElsaFont.bodyStrong).lineLimit(1)
                Text(size).elsaFont(ElsaFont.caption).opacity(0.7)
            }
        }
    }
}

/// 날짜 구분선: 가운데 작은 알약
struct ElsaDateSeparator: View {
    let text: String

    var body: some View {
        Text(text)
            .elsaFont(ElsaFont.caption)
            .foregroundStyle(ElsaColor.onSurfaceVariant)
            .padding(.horizontal, ElsaSpacing.md)
            .padding(.vertical, ElsaSpacing.xs)
            .background(ElsaColor.surface.opacity(0.9), in: Capsule())
            .frame(maxWidth: .infinity)
            .padding(.vertical, ElsaSpacing.sm)
    }
}

/// 입력창: 첨부 버튼 + 여러 줄 입력(최대 5줄) + 보내기 버튼(글자가 있을 때만 켜짐). 화면이 `safeAreaInset(edge: .bottom)` 으로 붙이면 키보드 위에 붙는다.
/// `onAttach` 가 nil 이면 첨부 버튼이 없다(댓글 입력 등).
struct ElsaChatInputBar: View {
    @Binding var text: String
    let canSend: Bool
    let onSend: () -> Void
    let onAttach: (() -> Void)?
    var placeholder: String = String(localized: "chat_input_placeholder")

    var body: some View {
        HStack(alignment: .bottom, spacing: ElsaSpacing.xs) {
            if let onAttach {
                Button(action: onAttach) {
                    ElsaIcon("elsa_ic_attach", size: ElsaSize.iconLarge)
                        .foregroundStyle(ElsaColor.onSurfaceVariant)
                        .frame(width: ElsaSize.controlHeightSmall, height: ElsaSize.controlHeightSmall)
                }
                .accessibilityLabel(String(localized: "attach_title"))
            } else {
                Spacer().frame(width: ElsaSpacing.sm)
            }
            TextField(placeholder, text: $text, axis: .vertical)
                .elsaFont(ElsaFont.body)
                .lineLimit(1...5)
                .padding(.horizontal, ElsaSpacing.md)
                .padding(.vertical, ElsaSpacing.sm)
                .frame(minHeight: ElsaSize.controlHeightSmall)
                .background(ElsaColor.fill, in: RoundedRectangle(cornerRadius: ElsaRadius.lg, style: .continuous))
            Button(action: onSend) {
                ElsaIcon("elsa_ic_send", size: ElsaSize.iconLarge)
                    .foregroundStyle(canSend ? ElsaColor.primary : ElsaColor.onSurfaceTertiary)
                    .frame(width: ElsaSize.controlHeightSmall, height: ElsaSize.controlHeightSmall)
            }
            .disabled(!canSend)
            .accessibilityLabel(String(localized: "chat_send"))
        }
        .padding(.horizontal, ElsaSpacing.xs)
        .padding(.vertical, ElsaSpacing.xs)
        .background(ElsaColor.surface)
    }
}

/// 맨 아래로 이동 버튼(위로 스크롤했을 때)
struct ElsaScrollToBottomButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ElsaIcon("elsa_ic_chevron_down")
                .foregroundStyle(ElsaColor.onSurfaceVariant)
                .frame(width: ElsaSize.controlHeightSmall, height: ElsaSize.controlHeightSmall)
                .background(ElsaColor.surface, in: Circle())
                .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
        }
        .accessibilityLabel(String(localized: "chat_scroll_bottom"))
        .transition(.scale.combined(with: .opacity))
    }
}

#Preview("메시지") {
    VStack(spacing: ElsaSpacing.xxs) {
        ElsaDateSeparator(text: "2026년 10월 26일 월요일")
        ElsaMessageBubble(isMine: false, position: .first) { ElsaMessageText(text: "안녕하세요!") }
        ElsaMessageBubble(isMine: false, position: .last, time: "오후 3:20") { ElsaMessageText(text: "내일 회의 자료 공유드려요.") }
        ElsaMessageBubble(isMine: true, position: .first) { ElsaMessageText(text: "좋아요") }
        ElsaMessageBubble(isMine: true, position: .middle) { ElsaMessageFile(name: "회의록.pdf", size: "1.2 MB") }
        ElsaMessageBubble(isMine: true, position: .last, time: "오후 3:21", isRead: true) { ElsaMessageText(text: "확인했어요 👍") }
        ElsaMessageBubble(isMine: true, position: .single, time: "오후 3:22", sendState: .failed) { ElsaMessageText(text: "보내지 못한 메시지") }
        ElsaChatInputBar(text: .constant(""), canSend: false, onSend: {}, onAttach: {})
    }
    .padding(ElsaSpacing.lg)
    .elsaScreenBackground(grouped: true)
}
