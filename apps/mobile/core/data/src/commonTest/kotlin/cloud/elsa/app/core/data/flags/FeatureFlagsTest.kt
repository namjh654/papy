package cloud.elsa.app.core.data.flags

import cloud.elsa.app.core.data.storage.FakeLocalStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagsTest {

    private val storage = FakeLocalStorage()

    @Test
    fun defaultUntilServerSaysOtherwise() = runTest(UnconfinedTestDispatcher()) {
        val flags = FeatureFlags(storage, backgroundScope)
        assertTrue(flags.isEnabled(FeatureKey.ChatAttachments))

        flags.update(mapOf("chat_attachments" to false))
        assertFalse(flags.isEnabled(FeatureKey.ChatAttachments))
        assertFalse(flags.observe(FeatureKey.ChatAttachments).first())
    }

    @Test
    fun lastValuesSurviveRestart() = runTest(UnconfinedTestDispatcher()) {
        FeatureFlags(storage, backgroundScope).update(mapOf("chat_attachments" to false))

        val restarted = FeatureFlags(storage, backgroundScope)
        assertFalse(restarted.isEnabled(FeatureKey.ChatAttachments))
    }

    @Test
    fun emptyResponseKeepsValues() = runTest(UnconfinedTestDispatcher()) {
        val flags = FeatureFlags(storage, backgroundScope)
        flags.update(mapOf("chat_attachments" to false))
        flags.update(emptyMap())
        assertFalse(flags.isEnabled(FeatureKey.ChatAttachments))
    }
}
