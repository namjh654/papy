import SwiftUI
import Core

// 채팅 고도화(22-2). Android `PapyChatExtras.kt` 와 같다

/// 말풍선 안 인용 블록: 왼쪽 막대 3 + 이름(label)·원문 한 줄(bodySmall). 색은 말풍선 글자색을 따른다. 누르면 원문으로.
struct PapyReplyQuote: View {
    let name: String
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: PapySpacing.sm) {
                Rectangle().fill(.foreground.opacity(0.8)).frame(width: 3)
                VStack(alignment: .leading, spacing: 0) {
                    Text(name).papyFont(PapyFont.label).lineLimit(1)
                    Text(text).papyFont(PapyFont.bodySmall).opacity(0.8).lineLimit(1)
                }
                .padding(.vertical, PapySpacing.xs)
                Spacer(minLength: 0)
            }
            .padding(.trailing, PapySpacing.sm)
            .background(.foreground.opacity(0.12), in: RoundedRectangle(cornerRadius: PapyRadius.xs, style: .continuous))
            .clipShape(RoundedRectangle(cornerRadius: PapyRadius.xs, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// 입력창 위 답장 막대: 왼쪽 `primary` 막대 + "답장 · 이름" + 원문 한 줄 + 취소
struct PapyReplyBar: View {
    let name: String
    let text: String
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: PapySpacing.sm) {
            PapyIcon("papy_ic_reply").foregroundStyle(PapyColor.primary)
            Rectangle().fill(PapyColor.primary).frame(width: 3, height: 36)
            VStack(alignment: .leading, spacing: 0) {
                Text("\(String(localized: "chat_reply")) · \(name)").papyFont(PapyFont.label).foregroundStyle(PapyColor.primary).lineLimit(1)
                Text(text).papyFont(PapyFont.bodySmall).foregroundStyle(PapyColor.onSurfaceVariant).lineLimit(1)
            }
            Spacer(minLength: 0)
            Button(action: onCancel) {
                PapyIcon("papy_ic_close").foregroundStyle(PapyColor.onSurfaceTertiary)
                    .frame(width: PapySize.controlHeightSmall, height: PapySize.controlHeightSmall)
            }
            .accessibilityLabel(String(localized: "chat_cancel_reply"))
        }
        .padding(.leading, PapySpacing.md)
        .padding(.top, PapySpacing.sm)
        .background(PapyColor.surface)
    }
}

/// 상대가 입력 중: 상대 말풍선 모양 안에 점 3개가 차례로 깜박인다
struct PapyTypingIndicator: View {
    @State private var phase = false

    var body: some View {
        PapyMessageBubble(isMine: false, position: .single) {
            HStack(spacing: PapySpacing.xs) {
                ForEach(0..<3, id: \.self) { i in
                    Circle()
                        .fill(PapyColor.onSurfaceVariant)
                        .frame(width: 8, height: 8)
                        .opacity(phase ? 1 : 0.3)
                        .animation(.easeInOut(duration: 0.6).repeatForever().delay(Double(i) * 0.2), value: phase)
                }
            }
            .padding(.vertical, PapySpacing.xs)
        }
        .onAppear { phase = true }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(String(localized: "chat_typing"))
    }
}

/// 사진 전체 화면: 검은 바탕, 두 손가락으로 확대(1~4배)·이동, 두 번 누르면 원래 크기, 위 닫기. `preview`(기기 사진) 또는 `url`.
struct PapyImageViewer: View {
    var preview: UIImage? = nil
    var url: String? = nil
    let onClose: () -> Void

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            Group {
                if let preview {
                    Image(uiImage: preview).resizable().scaledToFit()
                } else if let url {
                    PapyRemoteImage(url: url, contentMode: .fit) { ProgressView().tint(.white) }
                }
            }
            .scaleEffect(scale)
            .offset(offset)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .gesture(
                MagnifyGesture()
                    .onChanged { scale = min(4, max(1, lastScale * $0.magnification)) }
                    .onEnded { _ in lastScale = scale; if scale == 1 { offset = .zero; lastOffset = .zero } }
                    .simultaneously(with: DragGesture()
                        .onChanged { value in
                            guard scale > 1 else { return }
                            offset = CGSize(width: lastOffset.width + value.translation.width, height: lastOffset.height + value.translation.height)
                        }
                        .onEnded { _ in lastOffset = offset })
            )
            .onTapGesture(count: 2) {
                withAnimation { scale = 1; lastScale = 1; offset = .zero; lastOffset = .zero }
            }
            .accessibilityLabel(String(localized: "chat_attachment_photo"))
            Button(action: onClose) {
                PapyIcon("papy_ic_close", size: PapySize.iconLarge)
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
            }
            .padding(PapySpacing.sm)
            .accessibilityLabel(String(localized: "chat_close"))
        }
    }
}

#Preview("채팅 고도화") {
    VStack(spacing: PapySpacing.sm) {
        PapyMessageBubble(isMine: true, position: .single, time: "오후 3:21") {
            VStack(alignment: .leading, spacing: PapySpacing.xs) {
                PapyReplyQuote(name: "엘사", text: "내일 회의 자료 공유드려요.", action: {})
                PapyMessageText(text: "확인했어요 👍")
            }
        }
        PapyTypingIndicator()
        PapyReplyBar(name: "엘사", text: "내일 회의 자료 공유드려요.", onCancel: {})
    }
    .padding(PapySpacing.lg)
    .papyScreenBackground(grouped: true)
}
