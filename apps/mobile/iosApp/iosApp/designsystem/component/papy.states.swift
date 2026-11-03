import SwiftUI
import Core

// MARK: - Empty

/// 빈 화면: 가운데 정렬 문구(+ 선택 액션). 목록이 비었을 때 PapyPagedList 가 쓴다. (Android `PapyEmptyState`)
struct PapyEmptyState: View {
    let title: String
    var message: String? = nil
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: PapySpacing.sm) {
            Text(title)
                .papyFont(PapyFont.subtitle)
                .foregroundStyle(PapyColor.onSurface)
            if let message {
                Text(message)
                    .papyFont(PapyFont.body)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
            }
            if let actionTitle, let action {
                PapyButton(actionTitle, style: .secondary, size: .small, fullWidth: false, action: action)
            }
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.vertical, PapySpacing.section)
    }
}

// MARK: - Error

/// 전체 오류 화면: 오류 문구 + 다시 시도. 보여줄 내용이 없을 때만 쓴다(내용이 있으면 PapyInlineMessage). (Android `PapyErrorState`)
struct PapyErrorState: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: PapySpacing.md) {
            PapyIcon("papy_ic_error", size: PapySize.icon * 2)
                .foregroundStyle(PapyColor.onSurfaceTertiary)
            Text(message)
                .papyFont(PapyFont.body)
                .foregroundStyle(PapyColor.onSurfaceVariant)
                .multilineTextAlignment(.center)
            PapyButton(String(localized: "common_retry"), style: .secondary, size: .small, fullWidth: false, action: onRetry)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, PapySpacing.section)
    }
}

// MARK: - Offline

/// 오프라인 안내 띠. 루트(AppNavigation)가 NetworkMonitor.isOnline 을 보고 화면 위에 한 번만 둔다. (Android `PapyOfflineBanner`)
struct PapyOfflineBanner: View {
    var body: some View {
        HStack(spacing: PapySpacing.sm) {
            PapyIcon("papy_ic_offline")
            Text("offline_banner")
                .papyFont(PapyFont.bodySmall)
            Spacer(minLength: 0)
        }
        .foregroundStyle(PapyColor.surface)
        .padding(.horizontal, PapySpacing.lg)
        .padding(.vertical, PapySpacing.sm)
        .background(PapyColor.onSurface)
        .transition(.move(edge: .top).combined(with: .opacity))
    }
}

// MARK: - Content State

/// 한 건을 불러와 보여주는 화면(상세·수정)의 공통 전환: 값이 있으면 내용, 없으면 전체 로딩 또는 전체 오류(다시 시도).
/// 값이 있는 동안의 로딩·오류(수정 중, 수정 실패)는 화면이 버튼 로딩·토스트로 따로 보여준다. (Android `PapyContentState`)
///
/// ```swift
/// PapyContentState(value: state.item, isLoading: state.isLoading, error: state.error, onRetry: { viewModel.load() }) { item in … }
/// ```
struct PapyContentState<Value, Content: View>: View {
    let value: Value?
    let isLoading: Bool
    let error: UiError?
    let onRetry: () -> Void
    @ViewBuilder let content: (Value) -> Content

    var body: some View {
        if let value {
            content(value)
        } else {
            ZStack {
                if let error {
                    PapyErrorState(message: error.localizedMessage, onRetry: onRetry)
                        .padding(PapySize.screenPadding)
                } else if isLoading {
                    PapyLoadingIndicator()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

// MARK: - Badge

/// 짧은 상태 표시(새 글·즐겨찾기 등). 색은 `PapyMessageType` 을 따른다. (Android `PapyBadge`)
struct PapyBadge: View {
    let text: String
    var type: PapyMessageType = .info

    var body: some View {
        Text(text)
            .papyFont(PapyFont.caption)
            .lineLimit(1)
            .foregroundStyle(type.onContainer)
            .padding(.horizontal, PapySpacing.sm)
            .padding(.vertical, PapySpacing.xxs)
            .background(type.container, in: RoundedRectangle(cornerRadius: PapyRadius.xs, style: .continuous))
    }
}
