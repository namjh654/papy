package cloud.elsa.app.feature.auth.reset

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.feature.auth.ViewModelTest
import cloud.elsa.app.feature.auth.login.LoginNotice
import cloud.elsa.app.feature.auth.login.LoginNoticeStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordResetViewModelTest : ViewModelTest() {

    private val api = FakePasswordResetApi()
    private val notices = LoginNoticeStore()
    private val viewModel = PasswordResetViewModel(PasswordResetRepository(api), notices)

    private fun toConfirmStep() {
        viewModel.usernameChanged("elsa")
        viewModel.requestCode()
    }

    @Test
    fun requestCodeMovesToConfirmStep() = runTest {
        assertFalse(viewModel.uiState.value.canRequest)
        toConfirmStep()

        assertEquals(PasswordResetStep.Confirm, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.canGoToPreviousStep)
        assertEquals("elsa", api.codeRequests.single().username)
    }

    @Test
    fun confirmSendsDoneAndLeavesNoticeForLogin() = runTest {
        toConfirmStep()
        viewModel.codeChanged("123456")
        viewModel.passwordChanged("password1")
        viewModel.passwordConfirmationChanged("password1")
        assertTrue(viewModel.uiState.value.canConfirm)

        viewModel.events.test {
            viewModel.confirm()
            assertEquals(PasswordResetEvent.Done, awaitItem())
        }
        assertEquals("password1", api.confirms.single().password)
        assertEquals(LoginNotice.PasswordChanged, notices.pending.value)
    }

    @Test
    fun serverErrorStaysOnStepAndShowsError() = runTest {
        api.error = AppError.Http(404, "")
        viewModel.usernameChanged("nobody")
        viewModel.requestCode()

        assertEquals(PasswordResetStep.Request, viewModel.uiState.value.step)
        assertEquals(ErrorCode.RequestRejected, viewModel.uiState.value.error?.code)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun previousStepReturnsToRequestAndKeepsUsername() = runTest {
        toConfirmStep()
        viewModel.codeChanged("123456")

        viewModel.previousStep()

        assertEquals(PasswordResetStep.Request, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.code)
        assertEquals("elsa", viewModel.uiState.value.username)
        assertFalse(viewModel.uiState.value.canGoToPreviousStep)
    }

    @Test
    fun resendCodeSendsEvent() = runTest {
        toConfirmStep()

        viewModel.events.test {
            viewModel.resendCode()
            assertEquals(PasswordResetEvent.CodeResent, awaitItem())
        }
        assertEquals(2, api.codeRequests.size)
    }
}
