package io.github.namjh654.papy.feature.home

import app.cash.turbine.test
import io.github.namjh654.papy.core.data.session.UserModel
import io.github.namjh654.papy.core.data.session.UserStore
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
        userStore.setUser(UserModel(id = "papy", name = "Papy"))
        val vm = HomeViewModel(userStore)

        vm.uiState.test {
            assertEquals("Papy", awaitItem().displayName)
            userStore.clear()
            assertEquals("", awaitItem().displayName)
        }
    }
}
