package io.github.namjh654.papy.feature.auth.login

import app.cash.turbine.test
import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.data.social.SocialCredential
import io.github.namjh654.papy.core.data.social.SocialProvider
import io.github.namjh654.papy.feature.auth.FakeLoginApi
import io.github.namjh654.papy.feature.auth.FakeSocialLoginApi
import io.github.namjh654.papy.feature.auth.TestSession
import io.github.namjh654.papy.feature.auth.ViewModelTest
import io.github.namjh654.papy.feature.auth.social.SocialLoginRepository
import io.github.namjh654.papy.feature.auth.social.SocialLoginRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginViewModelTest : ViewModelTest() {

    private val session = TestSession()
    private val api = FakeLoginApi()
    private val notices = LoginNoticeStore()
    private val socialApi = FakeSocialLoginApi()
    private fun viewModel(
        providers: List<SocialProvider> = emptyList(),
        socialMock: Boolean = false,
    ) = LoginViewModel(
        LoginRepository(api, session.loginStorage, session.repository),
        notices,
        SocialLoginRepository(socialApi, session.loginStorage, session.repository),
        socialProviders = providers,
        socialMock = socialMock,
    )

    @Test
    fun restoresSavedLoginOptions() = runTest {
        session.loginStorage.setRememberId(true)
        session.loginStorage.setSavedId("papy")
        session.loginStorage.setAutoLogin(true)

        val state = viewModel().uiState.value

        assertEquals("papy", state.username)
        assertTrue(state.rememberId)
        assertTrue(state.autoLogin)
        assertFalse(state.isLoginEnabled) // 비밀번호가 비어 있음
    }

    @Test
    fun inputEnablesLoginAndClearsError() = runTest {
        val vm = viewModel()
        vm.usernameChanged("papy")
        assertFalse(vm.uiState.value.isLoginEnabled)

        vm.passwordChanged("pw")
        assertTrue(vm.uiState.value.isLoginEnabled)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun successSendsEventAndClearsPassword() = runTest {
        val vm = viewModel()
        vm.usernameChanged("papy")
        vm.passwordChanged("pw")

        vm.events.test {
            vm.login()
            val event = assertIs<LoginEvent.LoginSucceeded>(awaitItem())
            assertEquals("papy", event.user.id)
        }
        assertEquals("", vm.uiState.value.password)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun failureShowsErrorCode() = runTest {
        api.error = AppError.Http(500, "boom")
        val vm = viewModel()
        vm.usernameChanged("papy")
        vm.passwordChanged("pw")

        vm.login()

        assertEquals(UiError(ErrorCode.ServerError, 500), vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("papy", vm.uiState.value.username)
        assertEquals("", vm.uiState.value.password) // 아이디는 두고 비밀번호만 비운다(DEC-116)
        assertFalse(vm.uiState.value.isLoginEnabled)
    }

    @Test
    fun wrongPasswordShowsInvalidCredentials() = runTest {
        api.error = AppError.Http(401, "invalid credentials")
        val vm = viewModel()
        vm.usernameChanged("papy")
        vm.passwordChanged("wrong")

        vm.login()

        assertEquals(ErrorCode.InvalidCredentials, vm.uiState.value.error?.code) // 세션 만료 문구가 아니다
        assertEquals("", vm.uiState.value.password)
    }

    @Test
    fun blankCredentialsShowValidationError() = runTest {
        val vm = viewModel()
        vm.login()

        assertEquals(ErrorCode.CredentialsRequired, vm.uiState.value.error?.code)
        assertNull(api.lastRequest)
    }

    @Test
    fun noticeLeftByAnotherScreenIsDeliveredOnce() = runTest {
        notices.post(LoginNotice.PasswordChanged) // 비밀번호 재설정 화면이 남긴 안내

        viewModel().events.test {
            assertEquals(LoginEvent.Notice(LoginNotice.PasswordChanged), awaitItem())
        }
        assertEquals(null, notices.pending.value) // 다시 전달되지 않는다
    }

    // ============================================================ 소셜 로그인

    @Test
    fun providersComeFromConfig() = runTest {
        assertEquals(listOf(SocialProvider.Kakao, SocialProvider.Naver), viewModel(listOf(SocialProvider.Kakao, SocialProvider.Naver)).uiState.value.socialProviders)
    }

    @Test
    fun socialTapAsksPlatformToSignIn() = runTest {
        val vm = viewModel(listOf(SocialProvider.Kakao))
        vm.events.test {
            vm.socialLoginTapped(SocialProvider.Kakao)
            assertEquals(LoginEvent.StartSocialSignIn(SocialProvider.Kakao), awaitItem())
        }
        assertTrue(vm.uiState.value.isLoading)
        assertNull(socialApi.lastRequest)
    }

    @Test
    fun sdkCredentialIsExchangedAndLogsIn() = runTest {
        val vm = viewModel(listOf(SocialProvider.Google))
        vm.events.test {
            vm.socialCredentialReceived(SocialCredential(SocialProvider.Google, "id-token", nonce = "n"))
            assertIs<LoginEvent.LoginSucceeded>(awaitItem())
        }
        assertEquals(SocialLoginRequest(provider = "google", token = "id-token", nonce = "n"), socialApi.lastRequest)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun newSocialUserGoesToTerms() = runTest {
        socialApi.response = socialApi.response.copy(isNewUser = true)
        val vm = viewModel()
        vm.events.test {
            vm.socialCredentialReceived(SocialCredential(SocialProvider.Naver, "naver-token"))
            assertIs<LoginEvent.SocialSignedUp>(awaitItem())
        }
    }

    @Test
    fun mockExchangesWithoutSdk() = runTest {
        val vm = viewModel(listOf(SocialProvider.Kakao), socialMock = true)
        vm.events.test {
            vm.socialLoginTapped(SocialProvider.Kakao)
            assertIs<LoginEvent.LoginSucceeded>(awaitItem()) // StartSocialSignIn 없이 바로
        }
        assertEquals(SocialLoginRequest(provider = "kakao", token = "mock-kakao"), socialApi.lastRequest)
    }

    @Test
    fun cancelRestoresWithoutError() = runTest {
        val vm = viewModel(listOf(SocialProvider.Kakao))
        vm.socialLoginTapped(SocialProvider.Kakao)
        vm.socialSignInCancelled()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun sdkOrServerFailureShowsSocialError() = runTest {
        val vm = viewModel()
        vm.socialSignInFailed()
        assertEquals(ErrorCode.SocialLoginFailed, vm.uiState.value.error?.code)

        socialApi.error = AppError.Http(401, "invalid")
        vm.socialCredentialReceived(SocialCredential(SocialProvider.Kakao, "bad"))
        assertEquals(ErrorCode.SocialLoginFailed, vm.uiState.value.error?.code)
        assertFalse(vm.uiState.value.isLoading)
    }
}
