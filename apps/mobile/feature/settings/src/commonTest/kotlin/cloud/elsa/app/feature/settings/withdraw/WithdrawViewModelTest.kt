package cloud.elsa.app.feature.settings.withdraw

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.data.session.AuthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WithdrawViewModelTest {

    private val fixture = WithdrawFixture()
    private val viewModel by lazy { WithdrawViewModel(fixture.repository, fixture.userStore) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun needsPasswordAndConfirmation() = runTest {
        fixture.login()
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.passwordChanged("password1")
        viewModel.withdraw() // 체크 전에는 요청하지 않는다
        assertTrue(fixture.requests.isEmpty())

        viewModel.confirmedChanged(true)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun successSendsEventAndEndsSession() = runTest {
        fixture.login()
        viewModel.passwordChanged("password1")
        viewModel.confirmedChanged(true)

        viewModel.events.test {
            viewModel.withdraw()
            assertEquals(WithdrawEvent.Withdrawn, awaitItem())
        }
        assertEquals(AuthStatus.Unauthenticated, fixture.authStore.status.value)
    }

    @Test
    fun failureShowsErrorAndStays() = runTest {
        fixture.login()
        fixture.error = AppError.Http(403, "")
        viewModel.passwordChanged("wrong")
        viewModel.confirmedChanged(true)

        viewModel.withdraw()

        assertEquals(ErrorCode.RequestRejected, viewModel.uiState.value.error?.code)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AuthStatus.Authenticated, fixture.authStore.status.value)
    }

    @Test
    fun socialUserNeedsOnlyConfirmation() = runTest {
        fixture.login(provider = cloud.elsa.app.core.data.social.SocialProvider.Naver)
        assertFalse(viewModel.uiState.value.requiresPassword)
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.confirmedChanged(true)
        assertTrue(viewModel.uiState.value.canSubmit) // 비밀번호 없이
        viewModel.withdraw()

        assertEquals(listOf(WithdrawRequest(password = null)), fixture.requests)
    }
}
