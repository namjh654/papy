package io.github.namjh654.papy.feature.auth

import io.github.namjh654.papy.core.data.session.AuthStore
import io.github.namjh654.papy.core.data.session.SessionRepository
import io.github.namjh654.papy.core.data.session.UserStore
import io.github.namjh654.papy.core.data.storage.LocalStorage
import io.github.namjh654.papy.core.data.storage.SecureStorage
import io.github.namjh654.papy.core.data.storage.login.LoginStorage
import io.github.namjh654.papy.core.data.storage.setting.SettingStorage
import io.github.namjh654.papy.core.data.storage.token.TokenStorage
import io.github.namjh654.papy.core.data.storage.user.UserStorage
import io.github.namjh654.papy.feature.auth.login.LoginApi
import io.github.namjh654.papy.feature.auth.login.LoginRequest
import io.github.namjh654.papy.feature.auth.login.LoginResponse
import io.github.namjh654.papy.feature.auth.social.SocialLoginApi
import io.github.namjh654.papy.feature.auth.social.SocialLoginRequest
import io.github.namjh654.papy.feature.auth.social.SocialLoginResponse
import io.github.namjh654.papy.feature.auth.social.SocialLoginUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

internal open class FakeLocalStorage : LocalStorage {
    val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String) = values[key]
    override suspend fun setString(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
    override suspend fun clear() = values.clear()
}

internal class FakeSecureStorage : FakeLocalStorage(), SecureStorage

/** 실제 SessionRepository 를 in-memory 저장소로 조립한다 */
internal class TestSession {
    val storage = FakeLocalStorage()
    val secure = FakeSecureStorage()
    val loginStorage = LoginStorage(storage)
    val tokenStorage = TokenStorage(secure, storage)
    val settingStorage = SettingStorage(storage)
    val authStore = AuthStore()
    val userStore = UserStore()
    val repository = SessionRepository(tokenStorage, UserStorage(storage), loginStorage, authStore, userStore)
}

/** 응답을 바꿔 끼울 수 있는 LoginApi. [error] 가 있으면 던진다 */
internal class FakeLoginApi(
    var response: LoginResponse = LoginResponse(accessToken = "access"),
    var error: Throwable? = null,
) : LoginApi {
    var lastRequest: LoginRequest? = null
    override suspend fun login(request: LoginRequest): LoginResponse {
        lastRequest = request
        error?.let { throw it }
        return response
    }
}

/**
 * ViewModel 테스트 베이스: viewModelScope(Dispatchers.Main) 를 즉시 실행 디스패처로 바꾼다.
 * 상태·이벤트는 Turbine 으로 받는다: `vm.events.test { vm.login(); assertEquals(…, awaitItem()) }`.
 * Unconfined 라 중간 상태(isLoading 등)는 StateFlow 에서 합쳐진다 — 최종 상태를 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ViewModelTest {
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()
}

/** 응답을 바꿔 끼울 수 있는 SocialLoginApi. [error] 가 있으면 던진다 */
internal class FakeSocialLoginApi(
    var response: SocialLoginResponse = SocialLoginResponse(
        accessToken = "social-access",
        refreshToken = "social-refresh",
        user = SocialLoginUser(id = "s1", displayName = "Kakao User", email = "k@example.com"),
    ),
    var error: Throwable? = null,
) : SocialLoginApi {
    var lastRequest: SocialLoginRequest? = null
    override suspend fun login(request: SocialLoginRequest): SocialLoginResponse {
        lastRequest = request
        error?.let { throw it }
        return response
    }
}
