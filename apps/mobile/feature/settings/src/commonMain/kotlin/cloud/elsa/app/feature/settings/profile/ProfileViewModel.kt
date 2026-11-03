package cloud.elsa.app.feature.settings.profile

import cloud.elsa.app.core.common.error.toAppError
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.core.data.session.UserStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 프로필 편집. 수정 폼의 기준:
 * - 처음 값과 달라지면 [ProfileUiState.isDirty] → 저장 버튼 활성
 * - 저장하지 않고 나가려 하면 화면이 확인한다: `isDirty` 일 때 뒤로가기를 가로채 확인 다이얼로그
 *   (Android `BackHandler(enabled = state.isDirty)`, iOS 는 뒤로 버튼을 바꿔 단다)
 * - 파일(사진)은 화면이 바이트로 넘기고, 저장할 때 업로드한다
 */
class ProfileViewModel(
    userStore: UserStore,
    private val repository: ProfileRepository,
) : StatefulViewModel<ProfileUiState, ProfileEvent>(ProfileUiState()) {

    val uiState: StateFlow<ProfileUiState> = state

    val events: Flow<ProfileEvent> = eventFlow

    /** 비교 기준(마지막으로 저장된 값) */
    private var saved: UserModel = userStore.user.value ?: UserModel(id = "", name = "")

    /** 새로 고른 사진. 크기가 커서 상태에 넣지 않는다 */
    private var avatar: ByteArray? = null

    init {
        update { copy(name = saved.name, email = saved.email.orEmpty(), avatarUrl = saved.avatarUrl) }
    }

    fun nameChanged(value: String) = update { copy(name = value, error = null) }

    fun emailChanged(value: String) = update { copy(email = value, error = null) }

    /** 화면의 이미지 선택기가 고른 사진(바이트) */
    fun avatarPicked(bytes: ByteArray) {
        avatar = bytes
        update { copy(hasNewAvatar = true, error = null) }
    }

    fun save() = launch {
        val state = currentState
        if (!state.canSave) return@launch
        setState { copy(isSaving = true, error = null) }
        saved = repository.save(current = saved, name = state.name, email = state.email, avatar = avatar)
        avatar = null
        update { copy(name = saved.name, email = saved.email.orEmpty(), avatarUrl = saved.avatarUrl, hasNewAvatar = false, isSaving = false) }
        sendEvent(ProfileEvent.Saved)
    }

    /** 상태를 바꾸고 isDirty 를 다시 계산한다 */
    private fun update(reduce: ProfileUiState.() -> ProfileUiState) = setState {
        val next = reduce()
        next.copy(isDirty = next.hasNewAvatar || next.name != saved.name || next.email != saved.email.orEmpty())
    }

    override fun onError(throwable: Throwable) = setState {
        copy(isSaving = false, error = throwable.toAppError().toUiError())
    }
}
