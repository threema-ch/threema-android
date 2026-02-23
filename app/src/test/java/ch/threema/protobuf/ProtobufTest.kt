package ch.threema.protobuf

import kotlin.test.Test
import kotlin.test.assertContentEquals

class ProtobufTest {
    @Test
    fun `test combineEncryptedDataAndNonce`() {
        val combined = combineEncryptedDataAndNonce(
            data = DATA,
            nonce = NONCE,
        )

        assertContentEquals(COMBINED, combined)
    }

    @Test
    fun `test separateEncryptedDataAndNonce`() {
        val (data, nonce) = separateEncryptedDataAndNonce(COMBINED)

        assertContentEquals(DATA, data)
        assertContentEquals(NONCE, nonce)
    }

    companion object {
        private val DATA = byteArrayOf(1, 2, 3, 4)
        private val NONCE = byteArrayOf(9, 8, 7, 6)
        private val COMBINED = byteArrayOf(10, 4, 9, 8, 7, 6, 18, 4, 1, 2, 3, 4)
    }
}
