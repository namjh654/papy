package cloud.elsa.app.feature.home

import app.cash.turbine.test
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val userStore = UserStore()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun followsCurrentUser() = runTest {
        userStore.setUser(UserModel(id = "elsa", name = "Elsa"))
        val vm = HomeViewModel(userStore)

        vm.uiState.test {
            assertEquals("Elsa", awaitItem().displayName)
            userStore.clear()
            assertEquals("", awaitItem().displayName)
        }
    }
}
