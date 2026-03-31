package ch.threema.domain.protocol.csp.messages

import ch.threema.common.emptyByteArray
import ch.threema.testhelpers.willThrow
import kotlin.test.Test
import kotlin.test.assertIs

class ContactRequestProfilePictureMessageTest {
    @Test
    fun testValid() {
        assertIs<ContactRequestProfilePictureMessage>(
            ContactRequestProfilePictureMessage.fromByteArray(
                emptyByteArray(),
            ),
        )
    }

    @Test
    fun testValidExplicit() {
        assertIs<ContactRequestProfilePictureMessage>(
            ContactRequestProfilePictureMessage.fromByteArray(
                data = emptyByteArray(),
                offset = 0,
                length = 0,
            ),
        )
    }

    @Test
    fun testNegativeOffset() {
        val testBlockLazy = {
            ContactRequestProfilePictureMessage.fromByteArray(
                data = emptyByteArray(),
                offset = -1,
                length = 0,
            )
        }

        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun testInvalidLength() {
        val testBlockLazy = {
            ContactRequestProfilePictureMessage.fromByteArray(
                data = ByteArray(42),
                offset = 0,
                length = 42,
            )
        }

        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun testValidWithOffset() {
        assertIs<ContactRequestProfilePictureMessage>(
            ContactRequestProfilePictureMessage.fromByteArray(
                data = ByteArray(1),
                offset = 1,
                length = 0,
            ),
        )
    }
}
