package cloud.elsa.app.feature.settings

import app.cash.turbine.test
import cloud.elsa.app.core.data.monitoring.AnalyticsTracker
import cloud.elsa.app.core.data.review.InAppReview
import cloud.elsa.app.core.data.security.AppLock
import cloud.elsa.app.core.data.session.AuthSession
import cloud.elsa.app.core.data.session.AuthStatus
import cloud.elsa.app.core.data.session.AuthStore
import cloud.elsa.app.core.data.session.SessionRepository
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.core.data.session.UserStore
import cloud.elsa.app.core.data.settings.AppLanguage
import cloud.elsa.app.core.data.settings.AppSettingsRepository
import cloud.elsa.app.core.data.settings.ThemeMode
import cloud.elsa.app.core.data.storage.login.LoginStorage
import cloud.elsa.app.core.data.storage.setting.SettingStorage
import cloud.elsa.app.core.data.storage.token.TokenStorage
import cloud.elsa.app.core.data.storage.user.UserStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val storage = MemoryStorage()
    private val loginStorage = LoginStorage(storage)
    private val authStore = AuthStore()
    private val userStore = UserStore()
    private val session = SessionRepository(
        TokenStorage(MemorySecureStorage(), storage), UserStorage(storage), loginStorage, authStore, userStore,
    )

    private val events = mutableListOf<Pair<String, Map<String, String>>>()
    private var reviewRequests = 0
    private val analytics = object : AnalyticsTracker {
        override val isEnabled = true
        override fun logEvent(name: String, params: Map<String, String>) { events += name to params }
        override fun setUserId(id: String?) = Unit
    }

    private fun TestScope.viewModel() = SettingsViewModel(
        AppSettingsRepository(SettingStorage(storage), backgroundScope),
        session,
        userStore,
        inAppReview = object : InAppReview { override fun requestReview() { reviewRequests++ } },
        analytics = analytics,
        appLock = AppLock(storage, authStore, backgroundScope),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun selectingThemeUpdatesStateAndStorage() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            assertEquals(ThemeMode.System, awaitItem().theme)
            vm.selectTheme(ThemeMode.Dark)
            assertEquals(ThemeMode.Dark, awaitItem().theme)
        }
        assertEquals("Dark", SettingStorage(storage).getTheme())
        assertEquals(listOf("theme_changed" to mapOf("mode" to "dark")), events)
    }

    @Test
    fun rateAppRequestsReview() = runTest {
        viewModel().rateApp()
        assertEquals(1, reviewRequests)
    }

    @Test
    fun changingLanguageSendsEventOnce() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.selectLanguage(AppLanguage.English)
            assertEquals(SettingsEvent.LanguageChanged(AppLanguage.English), awaitItem())
            vm.selectLanguage(AppLanguage.English) // 같은 값은 무시
            expectNoEvents()
        }
    }

    @Test
    fun logoutEndsSession() = runTest {
        session.start(AuthSession("token", null, UserModel(id = "elsa", name = "Elsa")))
        val vm = viewModel()

        vm.events.test {
            vm.logout()
            assertEquals(SettingsEvent.LoggedOut, awaitItem())
        }
        assertFalse(vm.uiState.value.isLoggingOut)
        assertEquals(AuthStatus.Unauthenticated, authStore.status.value)
        assertEquals(false, loginStorage.isAutoLogin())
    }

    @Test
    fun appLockToggleFollowsAppLock() = runTest {
        val vm = viewModel()
        assertFalse(vm.uiState.value.appLockEnabled)
        vm.appLockChanged(true)
        kotlin.test.assertTrue(vm.uiState.value.appLockEnabled)
        vm.appLockChanged(false)
        assertFalse(vm.uiState.value.appLockEnabled)
    }
}
