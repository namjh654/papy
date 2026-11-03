package cloud.elsa.app.feature.auth.signup

import cloud.elsa.app.feature.auth.login.LoginNotice
import cloud.elsa.app.feature.auth.login.LoginNoticeStore
import app.cash.turbine.test
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.feature.auth.ViewModelTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignupViewModelTest : ViewModelTest() {

    private var response = SignupResponse(success = true)
    private var lastRequest: SignupRequest? = null
    private val notices = LoginNoticeStore()
    private val viewModel = SignupViewModel(
        marketingAgreed = true,
        SignupRepository(object : SignupApi {
            override suspend fun signup(request: SignupRequest): SignupResponse {
                lastRequest = request
                return response
            }
        }),
        notices,
    )

    private fun fill(password: String = "password1", confirmation: String = "password1") {
        viewModel.usernameChanged("elsa")
        viewModel.passwordChanged(password)
        viewModel.passwordConfirmationChanged(confirmation)
    }

    @Test
    fun enabledOnlyWhenAllFieldsFilled() {
        assertFalse(viewModel.uiState.value.isSignupEnabled)
        fill()
        assertTrue(viewModel.uiState.value.isSignupEnabled)
    }

    @Test
    fun successSendsEvent() = runTest {
        fill()
        viewModel.events.test {
            viewModel.signup()
            assertEquals(SignupEvent.SignupSucceeded("elsa"), awaitItem())
        }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(true, lastRequest?.marketingAgreed) // 약관 화면의 선택 동의가 요청에 실린다
        assertEquals(LoginNotice.SignedUp, notices.pending.value) // 로그인 화면이 안내한다
    }

    @Test
    fun shortPasswordShowsValidationError() = runTest {
        fill(password = "short", confirmation = "short")
        viewModel.signup()

        assertEquals(ErrorCode.PasswordTooShort, viewModel.uiState.value.error?.code)
    }

    @Test
    fun serverRejectionShowsSignupFailed() = runTest {
        response = SignupResponse(success = false)
        fill()
        viewModel.signup()

        assertEquals(ErrorCode.SignupFailed, viewModel.uiState.value.error?.code)
    }

    @Test
    fun mismatchShowsValidationError() = runTest {
        fill(confirmation = "other")
        viewModel.signup()

        assertEquals(ErrorCode.PasswordMismatch, viewModel.uiState.value.error?.code)
    }
}
