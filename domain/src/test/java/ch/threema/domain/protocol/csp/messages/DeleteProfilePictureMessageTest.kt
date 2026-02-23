package ch.threema.domain.protocol.csp.messages

import ch.threema.testhelpers.willThrow
import kotlin.test.Test
import kotlin.test.assertIs

class DeleteProfilePictureMessageTest {
    @Test
    fun testValid() {
        assertIs<DeleteProfilePictureMessage>(
            DeleteProfilePictureMessage.fromByteArray(
                ByteArray(0),
            ),
        )
    }

    @Test
    fun testValidExplicit() {
        assertIs<DeleteProfilePictureMessage>(
            DeleteProfilePictureMessage.fromByteArray(
                data = ByteArray(0),
                offset = 0,
                length = 0,
            ),
        )
    }

    @Test
    fun testNegativeOffset() {
        val testBlockLazy = {
            DeleteProfilePictureMessage.fromByteArray(
                data = ByteArray(0),
                offset = -1,
                length = 0,
            )
        }

        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun testInvalidLength() {
        val testBlockLazy = {
            DeleteProfilePictureMessage.fromByteArray(
                data = ByteArray(42),
                offset = 0,
                length = 42,
            )
        }

        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun testValidWithOffset() {
        assertIs<DeleteProfilePictureMessage>(
            DeleteProfilePictureMessage.fromByteArray(
                data = ByteArray(1),
                offset = 1,
                length = 0,
            ),
        )
    }
}
