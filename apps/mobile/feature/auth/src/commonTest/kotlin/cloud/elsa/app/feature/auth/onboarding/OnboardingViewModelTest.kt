package cloud.elsa.app.feature.auth.onboarding

import app.cash.turbine.test
import cloud.elsa.app.feature.auth.TestSession
import cloud.elsa.app.feature.auth.ViewModelTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingViewModelTest : ViewModelTest() {

    private val session = TestSession()
    private val viewModel = OnboardingViewModel(session.settingStorage)

    @Test
    fun nextMovesThroughPagesThenFinishes() = runTest {
        repeat(OnboardingUiState.PAGE_COUNT - 1) { viewModel.next() }
        assertTrue(viewModel.uiState.value.isLastPage)
        assertFalse(session.settingStorage.isOnboardingDone())

        viewModel.events.test {
            viewModel.next()
            assertEquals(OnboardingEvent.Done, awaitItem())
        }
        assertTrue(session.settingStorage.isOnboardingDone()) // 다음 실행부터는 보이지 않는다
    }

    @Test
    fun skipFinishesFromAnyPage() = runTest {
        viewModel.events.test {
            viewModel.skip()
            assertEquals(OnboardingEvent.Done, awaitItem())
        }
        assertTrue(session.settingStorage.isOnboardingDone())
    }

    @Test
    fun pageStaysWithinBounds() {
        viewModel.pageChanged(99)
        assertEquals(OnboardingUiState.PAGE_COUNT - 1, viewModel.uiState.value.page)

        viewModel.previous()
        assertEquals(OnboardingUiState.PAGE_COUNT - 2, viewModel.uiState.value.page)

        viewModel.pageChanged(-1)
        assertEquals(0, viewModel.uiState.value.page)
    }
}
