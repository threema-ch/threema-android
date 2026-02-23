package ch.threema.app.threemasafe

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertTrue

class ThreemaSafeMDMConfigTest {
    @Test
    fun isBackupDisabled() {
        val mdmConfigMock = mockk<ThreemaSafeMDMConfig>()
        every { mdmConfigMock.isBackupDisabled } answers { callOriginal() }

        assertTrue(mdmConfigMock.isBackupDisabled)
    }
}
