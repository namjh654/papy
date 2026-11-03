package cloud.elsa.app.feature.settings.profile

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
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
class ProfileViewModelTest {

    private val fixture = ProfileFixture()
    private val viewModel by lazy { ProfileViewModel(fixture.userStore, fixture.repository) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsWithCurrentUserAndNothingToSave() {
        val state = viewModel.uiState.value

        assertEquals("Elsa", state.name)
        assertEquals("elsa@example.com", state.email)
        assertFalse(state.isDirty)
        assertFalse(state.canSave)
    }

    @Test
    fun changeMakesDirtyAndRevertingClearsIt() {
        viewModel.nameChanged("Anna")
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.nameChanged("Elsa")
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun saveUploadsPickedAvatarAndSendsSaved() = runTest {
        viewModel.avatarPicked(byteArrayOf(9, 9))
        viewModel.nameChanged("Anna")

        viewModel.events.test {
            viewModel.save()
            assertEquals(ProfileEvent.Saved, awaitItem())
        }

        val state = viewModel.uiState.value
        assertEquals("https://cdn/new.jpg", state.avatarUrl)
        assertFalse(state.isDirty) // 저장한 값이 새 기준
        assertFalse(state.hasNewAvatar)
        assertEquals("Anna", fixture.userStore.user.value?.name)
    }

    @Test
    fun saveFailureShowsErrorAndKeepsInput() = runTest {
        fixture.error = AppError.Http(500, "")
        viewModel.nameChanged("Anna")

        viewModel.save()

        assertEquals(ErrorCode.ServerError, viewModel.uiState.value.error?.code)
        assertEquals("Anna", viewModel.uiState.value.name)
        assertTrue(viewModel.uiState.value.isDirty)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
