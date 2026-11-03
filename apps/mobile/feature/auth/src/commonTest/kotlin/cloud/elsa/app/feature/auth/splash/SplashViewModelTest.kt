package cloud.elsa.app.feature.auth.splash

import app.cash.turbine.test
import cloud.elsa.app.core.data.session.AuthSession
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.feature.auth.TestSession
import cloud.elsa.app.feature.auth.ViewModelTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SplashViewModelTest : ViewModelTest() {

    private val session = TestSession()
    private val user = UserModel(id = "elsa", name = "Elsa")

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
