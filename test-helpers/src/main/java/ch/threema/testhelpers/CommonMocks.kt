package ch.threema.testhelpers

import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom

/**
 * Creates a mock of [SecureRandom] which deterministically returns bytes in incrementing order, i.e., every sequence of "random" bytes
 * will just be `[0, 1, 2, 3, 4, ...]`.
 */
fun mockSecureRandom(): SecureRandom =
    mockk<SecureRandom> {
        every { nextBytes(any()) } answers {
            val byteArray = firstArg<ByteArray>()
            byteArray.indices.forEach { i ->
                byteArray[i] = i.toByte()
            }
        }
    }
