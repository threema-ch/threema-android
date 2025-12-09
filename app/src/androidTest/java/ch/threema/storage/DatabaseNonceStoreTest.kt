/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.storage

import androidx.test.core.app.ApplicationProvider
import ch.threema.app.ThreemaApplication
import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceScope
import ch.threema.base.crypto.NonceStore
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseNonceStoreTest {
    private lateinit var tempDbFileName: String

    private lateinit var _store: DatabaseNonceStore
    private val store: NonceStore
        get() = _store

    @BeforeTest
    fun setup() {
        tempDbFileName = "threema-nonce-test-${System.currentTimeMillis()}.db"
        val identityStore = TestIdentityStore()
        _store = DatabaseNonceStore(
            ApplicationProvider.getApplicationContext(),
            identityStore,
            tempDbFileName,
        )
    }

    @AfterTest
    fun teardown() {
        _store.close()
        ApplicationProvider
            .getApplicationContext<ThreemaApplication>()
            .deleteDatabase(tempDbFileName)
    }

    @Test
    fun testSameNonceWithDifferentScope() {
        assertStoreEmpty()

        val nonces = createNonces()

        // Assert the nonces do not exist in the store
        nonces.forEach {
            // Assert that nonce does not exist in store
            assertFalse(store.exists(NonceScope.CSP, it))
            assertFalse(store.exists(NonceScope.D2D, it))
        }

        // Assert that storing the nonces succeeds
        nonces.forEach {
            assertTrue(store.store(NonceScope.CSP, it))
            assertTrue(store.store(NonceScope.D2D, it))
        }

        // Assert the nonces exist after the insert
        nonces.forEach {
            assertTrue(store.exists(NonceScope.CSP, it))
            assertTrue(store.exists(NonceScope.D2D, it))
        }
    }

    @Test
    fun testExistsWithExistingNonce() {
        assertStoreEmpty()

        val nonces = createNonces()

        // Assert that storing the nonces succeeds
        nonces.forEach {
            // Assert that storing a nonce succeeds
            assertTrue(store.store(NonceScope.CSP, it))
            assertTrue(store.store(NonceScope.D2D, it))
        }

        // Assert the nonces exist after the insert
        nonces.forEach {
            assertTrue(store.exists(NonceScope.CSP, it))
            assertTrue(store.exists(NonceScope.D2D, it))
        }
    }

    @Test
    fun testStoreWithExistingNonce() {
        assertStoreEmpty()

        val nonces = createNonces()

        // Assert the nonces do not exist in the store
        nonces.forEach {
            // Assert that nonce does not exist in store
            assertFalse(store.exists(NonceScope.CSP, it))
            assertFalse(store.exists(NonceScope.D2D, it))
        }

        // Assert that storing the nonces succeeds
        nonces.forEach {
            assertTrue(store.store(NonceScope.CSP, it))
            assertTrue(store.store(NonceScope.D2D, it))
        }

        // Assert that storing the nonces again fails
        nonces.forEach {
            assertFalse(store.store(NonceScope.CSP, it))
            assertFalse(store.store(NonceScope.D2D, it))
        }
    }

    @Test
    fun testBulkExport() {
        assertStoreEmpty()

        val nonces = createNonces()
        val expectedHashedNonces = nonces.map { hashNonce(it) }

        // Assert that storing the nonces succeeds
        nonces.forEach {
            assertTrue(store.store(NonceScope.CSP, it))
            assertTrue(store.store(NonceScope.D2D, it))
        }

        assertSameHashedNonces(expectedHashedNonces, store.getAllHashedNonces(NonceScope.CSP))
        assertSameHashedNonces(expectedHashedNonces, store.getAllHashedNonces(NonceScope.D2D))
    }

    @Test
    fun testBulkImportHashed() {
        assertStoreEmpty()

        val nonces = createNonces()
        val hashedNonces = nonces.map { hashNonce(it) }

        // Insert the hashed nonces. As they are inserted as if they were already hashed,
        // the store must not hash them again.
        assertTrue(store.insertHashedNonces(NonceScope.CSP, hashedNonces))
        assertTrue(store.insertHashedNonces(NonceScope.D2D, hashedNonces))

        // Assert that all unhashed nonces are detected as existing in the store
        nonces.forEach {
            // Assert that the nonce as inserted should exist
            assertTrue(store.exists(NonceScope.CSP, it))
            assertTrue(store.exists(NonceScope.D2D, it))
        }
    }

    @Test
    fun testGettingRawNoncesAsHashedNonces() {
        assertStoreEmpty()
        val nonces = createNonces()

        // Insert raw nonces without hashing them. This test is important as this may still be the case on some devices as nonces used to be stored
        // without being hashed first.
        nonces.forEach { nonce ->
            val statement = _store.writableDatabase.compileStatement("INSERT INTO nonce_csp VALUES (?)")
            statement.bindBlob(1, nonce.bytes)
            statement.executeInsert()
        }

        // Check that insertion worked
        nonces.forEach { nonce ->
            assertTrue(store.exists(NonceScope.CSP, nonce))
        }
        assertEquals(256, store.getCount(NonceScope.CSP))

        // Act
        val hashedNonces = store.getAllHashedNonces(NonceScope.CSP)

        // Assert
        assertEquals(256, hashedNonces.size)
        assertEquals(256, store.getCount(NonceScope.CSP))
        nonces.forEach { nonce ->
            assertTrue(store.exists(NonceScope.CSP, nonce))
        }
    }

    private fun assertSameHashedNonces(
        expected: Collection<HashedNonce>,
        actual: Collection<HashedNonce>,
    ) {
        assertEquals(expected.size, actual.size)
        expected.forEach { expectedNonce ->
            // If `actual.contains(expectedNonce)` is used only referential equality is checked
            // which will fail.
            assertTrue(actual.find { it.bytes.contentEquals(expectedNonce.bytes) } != null)
        }
    }

    private fun assertStoreEmpty() {
        assertEquals(0, store.getCount(NonceScope.CSP))
        assertEquals(0, store.getCount(NonceScope.D2D))
    }

    /**
     * Create 256 sequential nonces, where the LSB acts as a counter.
     */
    private fun createNonces(): List<Nonce> {
        return (0..255)
            .map { ByteArray(23) + byteArrayOf(it.toByte()) }
            .map { Nonce(it) }
    }
}

fun hashNonce(nonce: Nonce): HashedNonce {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(USER_IDENTITY.encodeToByteArray(), "HmacSHA256"))
    return HashedNonce(mac.doFinal(nonce.bytes))
}

const val USER_IDENTITY = "01234567"

private class TestIdentityStore : IdentityStore {
    override fun getIdentity(): Identity = USER_IDENTITY

    override fun encryptData(
        plaintext: ByteArray,
        nonce: ByteArray,
        receiverPublicKey: ByteArray,
    ): ByteArray = throw UnsupportedOperationException()

    override fun decryptData(
        ciphertext: ByteArray,
        nonce: ByteArray,
        senderPublicKey: ByteArray,
    ): ByteArray = throw UnsupportedOperationException()

    override fun calcSharedSecret(publicKey: ByteArray): ByteArray =
        throw UnsupportedOperationException()

    override fun getServerGroup(): String = throw UnsupportedOperationException()

    override fun getPublicKey(): ByteArray = throw UnsupportedOperationException()

    override fun getPrivateKey(): ByteArray = throw UnsupportedOperationException()

    override fun getPublicNickname(): String = throw UnsupportedOperationException()

    override fun storeIdentity(
        identity: Identity,
        serverGroup: String,
        privateKey: ByteArray,
    ) = throw UnsupportedOperationException()

    override fun setPublicNickname(publicNickname: String) {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        throw UnsupportedOperationException()
    }
}
