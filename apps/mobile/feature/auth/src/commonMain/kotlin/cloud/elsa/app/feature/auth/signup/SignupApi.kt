package cloud.elsa.app.feature.auth.signup

import cloud.elsa.app.core.data.network.BaseApi

/** 회원가입 API 계약. 실제 구현은 [RemoteSignupApi], 테스트·목업은 Koin 에서 교체한다. */
interface SignupApi {
    suspend fun signup(request: SignupRequest): SignupResponse
}

internal class RemoteSignupApi(
    private val baseApi: BaseApi,
) : SignupApi {
    // {BASE_URL}/api/signup
    override suspend fun signup(request: SignupRequest): SignupResponse =
        baseApi.post("/api/signup", request)
}
