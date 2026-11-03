package io.github.namjh654.papy.feature.auth.terms

import app.cash.turbine.test
import io.github.namjh654.papy.feature.auth.ViewModelTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermsViewModelTest : ViewModelTest() {

    private val viewModel = TermsViewModel()

    @Test
    fun requiredAgreementsEnableContinue() = runTest {
        assertFalse(viewModel.uiState.value.canContinue)

        viewModel.toggle(Agreement.Service)
        assertFalse(viewModel.uiState.value.canContinue)

        viewModel.toggle(Agreement.Privacy)
        assertTrue(viewModel.uiState.value.canContinue)

        viewModel.events.test {
            viewModel.continueClicked()
            assertEquals(TermsEvent.Agreed(marketing = false), awaitItem())
        }
    }

    @Test
    fun continueWithoutRequiredDoesNothing() = runTest {
        viewModel.toggle(Agreement.Marketing)

        viewModel.events.test {
            viewModel.continueClicked()
            expectNoEvents()
        }
    }

    @Test
    fun toggleAllChecksEverythingThenClears() = runTest {
        viewModel.toggle(Agreement.Marketing)

        viewModel.toggleAll()
        assertTrue(viewModel.uiState.value.allChecked)
        assertTrue(viewModel.uiState.value.isChecked(Agreement.Service))
        viewModel.events.test {
            viewModel.continueClicked()
            assertEquals(TermsEvent.Agreed(marketing = true), awaitItem())
        }

        viewModel.toggleAll()
        assertTrue(viewModel.uiState.value.checked.isEmpty())
    }
}
