package cloud.elsa.app.feature.settings.inquiry

import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.viewmodel.UiState

/**
 * 문의하기 화면: 종류(칩) → 내용 → 답장 받을 이메일(계정 이메일로 채워 둔다) → 하단 고정 "보내기".
 * [error]: 검증·전송 실패(입력칸 아래 안내)
 */
data class InquiryUiState(
    val category: InquiryCategory? = null,
    val content: String = "",
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: UiError? = null,
) : UiState {
    val canSubmit: Boolean get() = category != null && content.isNotBlank() && !isSubmitting

    /** 글자 수 안내(내용 칸 아래 "123/2000") */
    val contentCount: Int get() = content.trim().length
    val maxContent: Int get() = InquiryRepository.MAX_CONTENT
}
