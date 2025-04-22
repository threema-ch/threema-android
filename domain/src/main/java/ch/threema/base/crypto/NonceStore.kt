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

package ch.threema.base.crypto

import ch.threema.base.utils.SecureRandomUtil
import com.neilalexander.jnacl.NaCl

enum class NonceScope {
    CSP,
    D2D,
}

@JvmInline
value class Nonce(val bytes: ByteArray)

@JvmInline
value class HashedNonce(val bytes: ByteArray)

interface NonceStore {
    fun exists(scope: NonceScope, nonce: Nonce): Boolean
    fun store(scope: NonceScope, nonce: Nonce): Boolean
    fun getCount(scope: NonceScope): Long
    fun getAllHashedNonces(scope: NonceScope): List<HashedNonce>

    /**
     * Add a chunk of hashed nonces in their byte array representation to a list.
     *
     * @param scope The scope of which nonces should be used
     * @param chunkSize The number of nonces to add
     * @param offset the offset where reading the nonces starts
     * @param nonces The list to which the nonces should be added
     */
    fun addHashedNoncesChunk(
        scope: NonceScope,
        chunkSize: Int,
        offset: Int,
        nonces: MutableList<HashedNonce>,
    )

    fun insertHashedNonces(scope: NonceScope, nonces: List<HashedNonce>): Boolean
}

fun interface NonceFactoryNonceBytesProvider {
    fun next(length: Int): ByteArray
}

class NonceFactory(
    private val nonceStore: NonceStore,
    // Nonce Provider is injectable for testing purposes
    private val nonceProvider: NonceFactoryNonceBytesProvider,
) {
    constructor(nonceStore: NonceStore) :
        this(nonceStore, { length -> SecureRandomUtil.generateRandomBytes(length) })

    @JvmName("nextNonce")
    fun next(scope: NonceScope): Nonce {
        return sequence {
            while (true) {
                val nonce = Nonce(nonceProvider.next(NaCl.NONCEBYTES))
                yield(nonce)
            }
        }
            .first { !exists(scope, it) }
    }

    /**
     * @return true if the nonce has been stored, false if the nonce could not be stored or already existed.
     */
    @JvmName("storeNonce")
    fun store(scope: NonceScope, nonce: Nonce) = nonceStore.store(scope, nonce)

    @JvmName("existsNonce")
    fun exists(scope: NonceScope, nonce: Nonce) = nonceStore.exists(scope, nonce)

    fun getCount(scope: NonceScope): Long = nonceStore.getCount(scope)

    fun getAllHashedNonces(scope: NonceScope) = nonceStore.getAllHashedNonces(scope)

    /**
     * @see NonceStore.addHashedNoncesChunk
     */
    fun addHashedNoncesChunk(
        scope: NonceScope,
        chunkSize: Int,
        offset: Int,
        nonces: MutableList<HashedNonce>,
    ) {
        nonceStore.addHashedNoncesChunk(scope, chunkSize, offset, nonces)
    }

    fun insertHashedNonces(scope: NonceScope, nonces: List<HashedNonce>) =
        nonceStore.insertHashedNonces(scope, nonces)

    fun insertHashedNoncesJava(scope: NonceScope, nonces: List<ByteArray>): Boolean {
        return insertHashedNonces(scope, nonces.map { HashedNonce(it) })
    }
}
