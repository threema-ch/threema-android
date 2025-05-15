/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.base.crypto

import ch.threema.base.utils.SecureRandomUtil.generateRandomBytes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.LinkedList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonceFactoryTest {
    @Test
    fun testNext() {
        val nonceStoreMock = mockk<NonceStore>(relaxed = true)

        val nonce = Nonce(
            byteArrayOf(
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01,
            ),
        )

        val nonceProvider = TestNonceProvider()
        nonceProvider.addNextNonces(listOf(nonce.bytes))

        // Store always return true
        every { nonceStoreMock.store(any<NonceScope>(), nonce) } returns true
        val factory = NonceFactory(nonceStoreMock, nonceProvider)
        val result: Nonce = factory.next(NonceScope.CSP)

        // Check if uniqueness is verified
        verify(exactly = 1) { nonceStoreMock.exists(any<NonceScope>(), nonce) }

        // Verify the result
        assertEquals(24, result.bytes.size)
    }

    @Test
    fun testNext2Times() {
        val nonceProvider = TestNonceProvider()
        val existingNonce = byteArrayOf(
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x01,
        )
        val newNonce = byteArrayOf(
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x02,
        )

        nonceProvider.addNextNonces(listOf(existingNonce, newNonce))

        val nonceStoreMock = mockk<NonceStore>()
        every { nonceStoreMock.exists(any<NonceScope>(), Nonce(existingNonce)) } returns true
        every { nonceStoreMock.exists(any<NonceScope>(), Nonce(newNonce)) } returns false

        val factory = NonceFactory(nonceStoreMock, nonceProvider)
        val result: Nonce = factory.next(NonceScope.CSP)

        // Check if uniqueness is verified twice (because the first nonce already existed)
        verify(exactly = 1) { nonceStoreMock.exists(any<NonceScope>(), Nonce(existingNonce)) }
        verify(exactly = 1) { nonceStoreMock.exists(any<NonceScope>(), Nonce(newNonce)) }

        // Verify the result
        assertEquals(24, result.bytes.size)
    }

    @Test
    fun testNextDoesNotStoreNonce() {
        val nonceStoreMock = mockk<NonceStore>(relaxed = true)

        val factory = NonceFactory(nonceStoreMock)
        val createdNonce = factory.next(NonceScope.CSP)

        verify(exactly = 0) { nonceStoreMock.store(any<NonceScope>(), createdNonce) }
    }

    @Test
    fun testExistsReturnsValueReturnedByNonceStore() {
        val existingNonce = Nonce(
            byteArrayOf(
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01,
            ),
        )

        val nonceStoreMock = mockk<NonceStore>()

        every { nonceStoreMock.exists(any<NonceScope>(), existingNonce) } answers {
            val nonce = secondArg<ByteArray>()
            nonce.contentEquals(existingNonce.bytes)
        }

        val factory = NonceFactory(nonceStoreMock)
        assertTrue(factory.exists(NonceScope.CSP, existingNonce))

        every { nonceStoreMock.exists(any<NonceScope>(), existingNonce) } answers {
            val nonce = secondArg<ByteArray>()
            !nonce.contentEquals(existingNonce.bytes)
        }

        assertFalse(factory.exists(NonceScope.CSP, existingNonce))
    }
}

private class TestNonceProvider : NonceFactoryNonceBytesProvider {
    private val nextNonces = LinkedList<ByteArray>()
    fun addNextNonces(nonces: List<ByteArray>) {
        nextNonces.addAll(nonces)
    }

    override fun next(length: Int): ByteArray {
        // Check length
        assertEquals(24, length.toLong())
        val nonce: ByteArray? = nextNonces.pollFirst()
        if (nonce != null) {
            assertEquals(24, nonce.size.toLong())
        }
        return nonce ?: generateRandomBytes(length)
    }
}
