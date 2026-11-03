package cloud.elsa.app.feature.auth.login

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.data.session.AuthSession
import cloud.elsa.app.core.data.session.SessionRepository
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.core.data.storage.login.LoginStorage

data class LoginPreference(
    val savedId: String,
    val rememberId: Boolean,
    val autoLogin: Boolean,
)

class LoginRepository(
    private val loginApi: LoginApi,
    private val loginStorage: LoginStorage,
    private val sessionRepository: SessionRepository,
) {

    /**
     * 로그인
     *
     * 1. API 로그인
     * 2. 로그인 결과를 AuthSession으로 변환
     * 3. 로그인 옵션 저장 + 세션 시작(Token/User 영속화, Store 갱신)
     */
    suspend fun login(
        username: String,
        password: String,
        rememberId: Boolean,
        autoLogin: Boolean,
    ): UserModel {

        if (username.isBlank() || password.isBlank()) {
            throw AppError.Validation(ErrorCode.CredentialsRequired)
        }

        val session = authenticate(
            username = username,
            password = password,
        )

        loginStorage.setRememberId(rememberId)
        loginStorage.setAutoLogin(autoLogin)
        loginStorage.setSavedId(if (rememberId) username else null)

        sessionRepository.start(session)

        return session.user
    }

    /** 서버 인증. 서버 없이 개발할 때는 Koin 에서 [LoginApi] 구현을 교체한다. */
    private suspend fun authenticate(
        username: String,
        password: String,
    ): AuthSession {

        val response = try {
            loginApi.login(
                LoginRequest(
                    id = username,
                    password = password,
                )
            )
        } catch (e: AppError) {
            // 로그인 요청의 401 은 세션 만료가 아니라 아이디·비밀번호 불일치다
            throw if (e.isUnauthorized) AppError.Validation(ErrorCode.InvalidCredentials) else e
        }

        val accessToken =
            response.accessToken
                ?: response.token
                ?: throw AppError.Validation(ErrorCode.InvalidResponse)

        return AuthSession(
            accessToken = accessToken,
            refreshToken = response.refreshToken,
            user = UserModel(
                id = response.user?.id ?: username,
                name = response.displayName
                    ?: response.user?.displayName
                    ?: response.user?.username
                    ?: username,
                email = response.user?.email,
            ),
        )
    }

    /**
     * 로그인 화면 진입 시 저장된 로그인 설정 복원
     */
    suspend fun getLoginPreference(): LoginPreference {

        val rememberId = loginStorage.isRememberId()

        return LoginPreference(
            savedId = if (rememberId) loginStorage.getSavedId().orEmpty() else "",
            rememberId = rememberId,
            autoLogin = loginStorage.isAutoLogin(),
        )
    }
}
