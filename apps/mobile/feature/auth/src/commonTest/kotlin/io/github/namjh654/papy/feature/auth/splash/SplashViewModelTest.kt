package io.github.namjh654.papy.feature.auth.splash

import app.cash.turbine.test
import io.github.namjh654.papy.core.data.session.AuthSession
import io.github.namjh654.papy.core.data.session.UserModel
import io.github.namjh654.papy.feature.auth.TestSession
import io.github.namjh654.papy.feature.auth.ViewModelTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SplashViewModelTest : ViewModelTest() {

    private val session = TestSession()
    private val user = UserModel(id = "papy", name = "Papy")

    @Test
    fun restoresSessionWhenAutoLoginEnabled() = runTest {
        session.loginStorage.setAutoLogin(true)
        session.repository.start(AuthSession("token", null, user))

        SplashViewModel(session.repository, session.settingStorage).events.test {
            assertEquals(SplashEvent.SessionRestored(user), awaitItem())
        }
    }

    @Test
    fun firstLaunchGoesToOnboarding() = runTest {
        SplashViewModel(session.repository, session.settingStorage).events.test {
            assertEquals(SplashEvent.FirstLaunch, awaitItem())
        }
    }

    @Test
    fun reportsNotFoundWithoutSessionAfterOnboarding() = runTest {
        session.settingStorage.setOnboardingDone()
        SplashViewModel(session.repository, session.settingStorage).events.test {
            assertEquals(SplashEvent.SessionNotFound, awaitItem())
        }
    }
}
