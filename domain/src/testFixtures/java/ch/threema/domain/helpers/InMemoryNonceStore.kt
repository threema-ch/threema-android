/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.domain.helpers

import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceScope
import ch.threema.base.crypto.NonceStore
import java.util.LinkedList

/**
 * An in-memory identity store, used for testing.
 */
class InMemoryNonceStore : NonceStore {
    private val nonces = HashMap<NonceScope, LinkedList<HashedNonce>>()

    override fun exists(scope: NonceScope, nonce: Nonce): Boolean {
        return getScopedHashedNonces(scope).any { it.bytes.contentEquals(nonce.bytes) }
    }

    override fun store(scope: NonceScope, nonce: Nonce): Boolean {
        return getScopedHashedNonces(scope).add(HashedNonce(nonce.bytes))
    }

    override fun getAllHashedNonces(scope: NonceScope): List<HashedNonce> {
        return ArrayList(getScopedHashedNonces(scope))
    }

    override fun getCount(scope: NonceScope): Long {
        return getScopedHashedNonces(scope).size.toLong()
    }

    override fun addHashedNoncesChunk(
        scope: NonceScope,
        chunkSize: Int,
        offset: Int,
        nonces: MutableList<HashedNonce>,
    ) {
        val scopedNonces: List<HashedNonce> = getScopedHashedNonces(scope)
        val from = Math.max(0, offset)
        val to = Math.min(scopedNonces.size, from + chunkSize)
        nonces.addAll(scopedNonces.subList(from, to))
    }

    override fun insertHashedNonces(scope: NonceScope, nonces: List<HashedNonce>): Boolean {
        return getScopedHashedNonces(scope).addAll(nonces)
    }

    @Synchronized
    private fun getScopedHashedNonces(scope: NonceScope): MutableList<HashedNonce> {
        if (!nonces.containsKey(scope)) {
            nonces[scope] = LinkedList()
        }
        return nonces[scope]!!
    }
}
