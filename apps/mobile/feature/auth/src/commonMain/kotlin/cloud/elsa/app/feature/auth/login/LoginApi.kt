package cloud.elsa.app.feature.auth.login

import cloud.elsa.app.core.data.network.BaseApi

/**
 * 로그인 API 계약. 실제 구현은 [RemoteLoginApi]. 서버 없이 개발·테스트할 때는
 * Koin 에서 다른 구현으로 교체한다(`single<LoginApi> { FakeLoginApi() }`).
 */
interface LoginApi {
    suspend fun login(request: LoginRequest): LoginResponse
}

internal class RemoteLoginApi(
    private val baseApi: BaseApi,
) : LoginApi {
    // {BASE_URL}/api/auth/login
    override suspend fun login(request: LoginRequest): LoginResponse =
        baseApi.post("/api/auth/login", request)
}
