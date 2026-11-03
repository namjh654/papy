package io.github.namjh654.papy.feature.settings.profile

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.data.session.SessionRepository
import io.github.namjh654.papy.core.data.session.UserModel

/**
 * 프로필 저장: 검증 → (새 사진이 있으면) 업로드 → 수정 요청 → 세션의 사용자 갱신.
 * 세션을 갱신하므로 사용자를 보여주는 다른 화면(홈·설정)이 바로 따라온다.
 */
class ProfileRepository(
    private val api: ProfileApi,
    private val sessionRepository: SessionRepository,
) {
    /**
     * @param current 지금 사용자(사진을 바꾸지 않으면 기존 주소를 유지한다)
     * @param avatar 새로 고른 사진. 없으면 null
     */
    suspend fun save(current: UserModel, name: String, email: String, avatar: ByteArray?): UserModel {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        if (trimmedName.isEmpty()) throw AppError.Validation(ErrorCode.NameRequired)
        if (trimmedEmail.isNotEmpty() && !EMAIL.matches(trimmedEmail)) throw AppError.Validation(ErrorCode.InvalidEmail)

        val avatarUrl = avatar?.let { api.uploadAvatar(it).avatarUrl } ?: current.avatarUrl
        val saved = api.update(ProfileUpdateRequest(trimmedName, trimmedEmail.ifEmpty { null }, avatarUrl))
        // 서버가 돌려준 값이 기준. id 와 사진 주소는 응답에 없을 때 기존 값을 쓴다
        val user = saved.copy(id = saved.id.ifBlank { current.id }, avatarUrl = saved.avatarUrl ?: avatarUrl)
        sessionRepository.updateUser(user)
        return user
    }

    private companion object {
        /** 형식만 가볍게 본다(@ 앞뒤와 점). 실제 확인은 서버가 한다 */
        val EMAIL = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")
    }
}
