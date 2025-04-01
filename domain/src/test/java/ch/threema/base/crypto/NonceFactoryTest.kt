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

import android.annotation.SuppressLint
import ch.threema.base.utils.SecureRandomUtil.generateRandomBytes
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import java.util.LinkedList

class NonceFactoryTest {
    @Test
    fun testNext() {
        val nonceStoreMock = Mockito.mock(NonceStore::class.java)

        // Store always return true
        Mockito.`when`(nonceStoreMock.store(anyScope(), anyNonce())).thenReturn(true)
        val factory = NonceFactory(nonceStoreMock)
        val result: Nonce = factory.next(NonceScope.CSP)

        // Check if uniqueness is verified
        Mockito.verify(nonceStoreMock, Mockito.times(1)).exists(anyScope(), anyNonce())

        // Verify the result
        Assert.assertEquals(24, result.bytes.size)
    }

    @Test
    fun testNext2Times() {
        val nonceProvider = TestNonceProvider()
        val existingNonce = byteArrayOf(
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x01
        )
        val newNonce = byteArrayOf(
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x02
        )

        nonceProvider.addNextNonces(listOf(existingNonce, newNonce))

        val nonceStoreMock = Mockito.mock(NonceStore::class.java)
        Mockito.`when`(nonceStoreMock.exists(anyScope(), anyNonce()))
            .thenAnswer { invocation: InvocationOnMock ->
                val nonce = invocation.getArgument(1, ByteArray::class.java)
                nonce.contentEquals(existingNonce)
            }

        val factory = NonceFactory(nonceStoreMock, nonceProvider)
        val result: Nonce = factory.next(NonceScope.CSP)

        // Check if uniqueness is verified twice (because the first nonce already existed)
        Mockito.verify(nonceStoreMock, Mockito.times(2)).exists(anyScope(), anyNonce())

        // Verify the result
        Assert.assertEquals(24, result.bytes.size)
    }

    @Test
    fun testNextDoesNotStoreNonce() {
        val nonceStoreMock = Mockito.mock(NonceStore::class.java)

        val factory = NonceFactory(nonceStoreMock)
        factory.next(NonceScope.CSP)

        Mockito.verify(nonceStoreMock, Mockito.never()).store(anyScope(), anyNonce())
    }

    @Test
    fun testExistsReturnsValueReturnedByNonceStore() {
        val existingNonce = Nonce(
            byteArrayOf(
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01
            )
        )

        val nonceStoreMock = Mockito.mock(NonceStore::class.java)

        Mockito.`when`(nonceStoreMock.exists(anyScope(), anyNonce()))
            .thenAnswer { invocation: InvocationOnMock ->
                val nonce = invocation.getArgument(1, ByteArray::class.java)
                nonce.contentEquals(existingNonce.bytes)
            }

        val factory = NonceFactory(nonceStoreMock)
        Assert.assertTrue(factory.exists(NonceScope.CSP, existingNonce))

        Mockito.`when`(nonceStoreMock.exists(anyScope(), anyNonce()))
            .thenAnswer { invocation: InvocationOnMock ->
                val nonce = invocation.getArgument(1, ByteArray::class.java)
                !nonce.contentEquals(existingNonce.bytes)
            }

        Assert.assertFalse(factory.exists(NonceScope.CSP, existingNonce))
    }
}

@SuppressLint("CheckResult")
private fun anyScope(): NonceScope {
    Mockito.any<NonceScope>()
    return NonceScope.CSP
}

@SuppressLint("CheckResult")
private fun anyNonce(): Nonce {
    Mockito.any<Nonce>()
    return Nonce(byteArrayOf())
}

private class TestNonceProvider : NonceFactoryNonceBytesProvider {
    private val nextNonces = LinkedList<ByteArray>()
    fun addNextNonces(nonces: List<ByteArray>) {
        nextNonces.addAll(nonces)
    }

    override fun next(length: Int): ByteArray {
        // Check length
        Assert.assertEquals(24, length.toLong())
        val nonce: ByteArray? = nextNonces.pollFirst()
        if (nonce != null) {
            Assert.assertEquals(24, nonce.size.toLong())
        }
        return nonce ?: generateRandomBytes(length)
    }
}

