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
        return getScopedHashedNonces(scope).any { it.bytes.contentEquals(nonce.hashNonce(HASH_KEY).bytes) }
    }

    override fun store(scope: NonceScope, nonce: Nonce): Boolean {
        return getScopedHashedNonces(scope).add(nonce.hashNonce(HASH_KEY))
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
        hashedNonces: MutableList<HashedNonce>,
    ) {
        val scopedNonces: List<HashedNonce> = getScopedHashedNonces(scope)
        val from = Math.max(0, offset)
        val to = Math.min(scopedNonces.size, from + chunkSize)
        hashedNonces.addAll(scopedNonces.subList(from, to))
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

    companion object {
        // We always hash nonces with 01234567 in tests.
        private const val HASH_KEY = "01234567"
    }
}
