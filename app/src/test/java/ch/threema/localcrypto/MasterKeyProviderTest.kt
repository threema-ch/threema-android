package ch.threema.localcrypto

import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class MasterKeyProviderTest {
    @Test
    fun `master key can only be accessed when unlocked`() {
        val masterKeyFlow = MutableStateFlow<MasterKey?>(null)
        val masterKeyProvider = MasterKeyProvider(masterKeyFlow)

        assertNull(masterKeyProvider.getMasterKeyOrNull())
        assertFailsWith<MasterKeyLockedException> {
            masterKeyProvider.getMasterKey()
        }
        assertTrue(masterKeyProvider.isLocked())

        val masterKeyMock = mockk<MasterKey>()
        masterKeyFlow.value = masterKeyMock

        assertSame(masterKeyMock, masterKeyProvider.getMasterKeyOrNull())
        assertSame(masterKeyMock, masterKeyProvider.getMasterKey())
        assertFalse(masterKeyProvider.isLocked())

        masterKeyFlow.value = null

        assertNull(masterKeyProvider.getMasterKeyOrNull())
        assertFailsWith<MasterKeyLockedException> {
            masterKeyProvider.getMasterKey()
        }
        assertTrue(masterKeyProvider.isLocked())
    }

    @Test
    fun `awaiting master key unlocked and locked`() = runTest {
        val masterKeyFlow = MutableStateFlow<MasterKey?>(null)
        val masterKeyProvider = MasterKeyProvider(masterKeyFlow)
        val masterKeyMock = mockk<MasterKey>()

        launch {
            delay(1.seconds)
            masterKeyFlow.value = masterKeyMock
        }
        val masterKey = masterKeyProvider.awaitUnlocked()
        assertSame(masterKeyMock, masterKey)

        launch {
            delay(1.seconds)
            masterKeyFlow.value = null
        }
        masterKeyProvider.awaitLocked()
    }
}
