package ch.threema.common

import ch.threema.testhelpers.mockSecureRandom
import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SecureRandomExtensionsTest {
    @Test
    fun `generate random bytes`() {
        val secureRandomMock = mockSecureRandom()

        val bytes1 = secureRandomMock.generateRandomBytes(4)
        assertContentEquals(byteArrayOf(0, 1, 2, 3), bytes1)

        val bytes2 = secureRandomMock.generateRandomBytes(300)
        assertContentEquals(ByteArray(300) { it.toByte() }, bytes2)
    }

    @Test
    fun `generate unsigned long`() {
        val secureRandomMock = mockk<SecureRandom> {
            every { nextLong() } returns 1_234_567_890_123_456_789L
        }

        assertEquals(
            1_234_567_890_123_456_789UL,
            secureRandomMock.nextULong(),
        )
    }
}
