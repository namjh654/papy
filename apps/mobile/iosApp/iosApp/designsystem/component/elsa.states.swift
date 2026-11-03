import SwiftUI
import Core

// MARK: - Empty

/// 빈 화면: 가운데 정렬 문구(+ 선택 액션). 목록이 비었을 때 ElsaPagedList 가 쓴다. (Android `ElsaEmptyState`)
struct ElsaEmptyState: View {
    let title: String
    var message: String? = nil
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: ElsaSpacing.sm) {
            Text(title)
                .elsaFont(ElsaFont.subtitle)
                .foregroundStyle(ElsaColor.onSurface)
            if let message {
                Text(message)
                    .elsaFont(ElsaFont.body)
                    .foregroundStyle(ElsaColor.onSurfaceVariant)
            }
            if let actionTitle, let action {
                ElsaButton(actionTitle, style: .secondary, size: .small, fullWidth: false, action: action)
            }
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.vertical, ElsaSpacing.section)
    }
}

// MARK: - Error

/// 전체 오류 화면: 오류 문구 + 다시 시도. 보여줄 내용이 없을 때만 쓴다(내용이 있으면 ElsaInlineMessage). (Android `ElsaErrorState`)
struct ElsaErrorState: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: ElsaSpacing.md) {
            ElsaIcon("elsa_ic_error", size: ElsaSize.icon * 2)
                .foregroundStyle(ElsaColor.onSurfaceTertiary)
            Text(message)
                .elsaFont(ElsaFont.body)
                .foregroundStyle(ElsaColor.onSurfaceVariant)
                .multilineTextAlignment(.center)
            ElsaButton(String(localized: "common_retry"), style: .secondary, size: .small, fullWidth: false, action: onRetry)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, ElsaSpacing.section)
    }
}

// MARK: - Offline

/// 오프라인 안내 띠. 루트(AppNavigation)가 NetworkMonitor.isOnline 을 보고 화면 위에 한 번만 둔다. (Android `ElsaOfflineBanner`)
struct ElsaOfflineBanner: View {
    var body: some View {
        HStack(spacing: ElsaSpacing.sm) {
            ElsaIcon("elsa_ic_offline")
            Text("offline_banner")
                .elsaFont(ElsaFont.bodySmall)
            Spacer(minLength: 0)
        }
        .foregroundStyle(ElsaColor.surface)
        .padding(.horizontal, ElsaSpacing.lg)
        .padding(.vertical, ElsaSpacing.sm)
        .background(ElsaColor.onSurface)
        .transition(.move(edge: .top).combined(with: .opacity))
    }
}

// MARK: - Content State

/// 한 건을 불러와 보여주는 화면(상세·수정)의 공통 전환: 값이 있으면 내용, 없으면 전체 로딩 또는 전체 오류(다시 시도).
/// 값이 있는 동안의 로딩·오류(수정 중, 수정 실패)는 화면이 버튼 로딩·토스트로 따로 보여준다. (Android `ElsaContentState`)
///
/// ```swift
/// ElsaContentState(value: state.item, isLoading: state.isLoading, error: state.error, onRetry: { viewModel.load() }) { item in … }
/// ```
struct ElsaContentState<Value, Content: View>: View {
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
                    ElsaErrorState(message: error.localizedMessage, onRetry: onRetry)
                        .padding(ElsaSize.screenPadding)
                } else if isLoading {
                    ElsaLoadingIndicator()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

// MARK: - Badge

/// 짧은 상태 표시(새 글·즐겨찾기 등). 색은 `ElsaMessageType` 을 따른다. (Android `ElsaBadge`)
struct ElsaBadge: View {
    let text: String
    var type: ElsaMessageType = .info

    var body: some View {
        Text(text)
            .elsaFont(ElsaFont.caption)
            .lineLimit(1)
            .foregroundStyle(type.onContainer)
            .padding(.horizontal, ElsaSpacing.sm)
            .padding(.vertical, ElsaSpacing.xxs)
            .background(type.container, in: RoundedRectangle(cornerRadius: ElsaRadius.xs, style: .continuous))
    }
}
