package io.github.namjh654.papy.feature.auth.social

import app.cash.turbine.test
import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.data.session.AuthSession
import io.github.namjh654.papy.core.data.session.AuthStatus
import io.github.namjh654.papy.core.data.session.UserModel
import io.github.namjh654.papy.feature.auth.TestSession
import io.github.namjh654.papy.feature.auth.ViewModelTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FakeConsentApi : SocialConsentApi {
    val requests = mutableListOf<SocialConsentRequest>()
    var error: Throwable? = null
    override suspend fun agree(request: SocialConsentRequest) {
        error?.let { throw it }
        requests += request
    }
}

class SocialConsentViewModelTest : ViewModelTest() {
    private val session = TestSession()
    private val api = FakeConsentApi()
    private fun viewModel() = SocialConsentViewModel(SocialConsentRepository(api, session.repository))

    @Test
    fun agreeCompletes() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.agree(marketing = false)
            assertEquals(SocialConsentEvent.Completed, awaitItem())
        }
        assertEquals(listOf(SocialConsentRequest(marketingAgreed = false)), api.requests)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun failureShowsErrorAndStays() = runTest {
        api.error = AppError.Http(500, "boom")
        val vm = viewModel()
        vm.agree(marketing = true)
        assertEquals(ErrorCode.ServerError, vm.uiState.value.error?.code)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun cancelLogsOut() = runTest {
        session.repository.start(AuthSession("a", "r", UserModel(id = "s1", name = "n")))
        val vm = viewModel()
        vm.events.test {
            vm.cancel()
            assertEquals(SocialConsentEvent.Cancelled, awaitItem())
        }
        assertTrue(session.authStore.status.value == AuthStatus.Unauthenticated)
    }
}
