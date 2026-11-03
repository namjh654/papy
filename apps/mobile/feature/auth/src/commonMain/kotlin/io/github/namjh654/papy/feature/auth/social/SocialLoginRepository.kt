package io.github.namjh654.papy.feature.auth.social

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.data.session.AuthSession
import io.github.namjh654.papy.core.data.session.SessionRepository
import io.github.namjh654.papy.core.data.session.UserModel
import io.github.namjh654.papy.core.data.social.SocialCredential
import io.github.namjh654.papy.core.data.storage.login.LoginStorage

/** 소셜 로그인 결과. 처음 가입한 사용자면 화면이 약관 동의로 보낸다 */
data class SocialLoginResult(
    val user: UserModel,
    val isNewUser: Boolean,
)

/**
 * 플랫폼 SDK 가 넘긴 [SocialCredential] 을 서버와 교환해 세션을 시작한다 — 아이디 로그인과 같은 [SessionRepository.start].
 */
class SocialLoginRepository(
    private val api: SocialLoginApi,
    private val loginStorage: LoginStorage,
    private val sessionRepository: SessionRepository,
) {

    /** @param autoLogin 로그인 화면의 "자동 로그인" 선택(아이디 로그인과 같다). 소셜은 아이디를 저장하지 않는다 */
    suspend fun login(credential: SocialCredential, autoLogin: Boolean): SocialLoginResult {
        if (credential.token.isBlank()) throw AppError.Validation(ErrorCode.SocialLoginFailed)

        val response = try {
            api.login(
                SocialLoginRequest(
                    provider = credential.provider.id,
                    token = credential.token,
                    authorizationCode = credential.authorizationCode,
                    nonce = credential.nonce,
                )
            )
        } catch (e: AppError) {
            // 서버가 소셜 토큰을 거절(401·4xx) → 세션 만료가 아니라 소셜 로그인 실패
            throw if (e is AppError.Http && e.statusCode in 400..499) AppError.Validation(ErrorCode.SocialLoginFailed) else e
        }

        val accessToken = response.accessToken ?: throw AppError.Validation(ErrorCode.InvalidResponse)
        val user = UserModel(
            id = response.user?.id ?: throw AppError.Validation(ErrorCode.InvalidResponse),
            name = response.user.displayName.orEmpty(),
            email = response.user.email,
            provider = credential.provider,
        )

        loginStorage.setAutoLogin(autoLogin)
        sessionRepository.start(AuthSession(accessToken = accessToken, refreshToken = response.refreshToken, user = user))
        return SocialLoginResult(user = user, isNewUser = response.isNewUser)
    }
}
