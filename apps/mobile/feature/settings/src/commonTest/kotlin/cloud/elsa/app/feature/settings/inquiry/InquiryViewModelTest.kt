package cloud.elsa.app.feature.settings.inquiry

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.core.data.session.UserStore
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
class InquiryViewModelTest {

    private val api = FakeInquiryApi()
    private val userStore = UserStore().apply { setUser(UserModel(id = "u", name = "U", email = "u@elsa.cloud")) }
    private val vm by lazy { InquiryViewModel(InquiryRepository(api, "1.0.0", "ios"), userStore) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun emailStartsWithAccountEmail() = runTest {
        assertEquals("u@elsa.cloud", vm.uiState.value.email)
    }

    @Test
    fun needsCategoryAndContent() = runTest {
        vm.contentChanged("hello")
        assertFalse(vm.uiState.value.canSubmit)
        vm.submit()
        assertTrue(api.requests.isEmpty())

        vm.categorySelected(InquiryCategory.Suggestion)
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun submitSendsAndFinishes() = runTest {
        vm.categorySelected(InquiryCategory.Account)
        vm.contentChanged("help")
        vm.events.test {
            vm.submit()
            assertEquals(InquiryEvent.Submitted, awaitItem())
        }
        assertEquals("help", api.requests.single().content)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun failureShowsErrorAndKeepsInput() = runTest {
        api.error = AppError.Http(500, "")
        vm.categorySelected(InquiryCategory.Bug)
        vm.contentChanged("crash")
        vm.submit()

        assertEquals(ErrorCode.ServerError, vm.uiState.value.error?.code)
        assertEquals("crash", vm.uiState.value.content)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun invalidEmailIsReported() = runTest {
        vm.categorySelected(InquiryCategory.Bug)
        vm.contentChanged("crash")
        vm.emailChanged("bad")
        vm.submit()
        assertEquals(ErrorCode.InvalidEmail, vm.uiState.value.error?.code)
    }
}
