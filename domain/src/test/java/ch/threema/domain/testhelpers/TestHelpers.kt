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

package ch.threema.domain.testhelpers

import ch.threema.base.ThreemaException
import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.crypto.NonceStore
import ch.threema.domain.models.Contact
import ch.threema.domain.models.BasicContact
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStoreInterface

object TestHelpers {
    const val myIdentity = "TESTTEST"

    @JvmStatic
    val noopContactStore: ContactStore
        get() = object : ContactStore {
            override fun getContactForIdentity(identity: String): Contact {
                return Contact(identity, ByteArray(256))
            }

            override fun addCachedContact(contact: BasicContact) {}
            override fun getCachedContact(identity: String) = null
            override fun getContactForIdentityIncludingCache(identity: String): Contact {
                return getContactForIdentity(identity)
            }

            override fun addContact(contact: Contact) {}
            override fun isSpecialContact(identity: String) = false
        }

    @JvmStatic
    val noopIdentityStore: IdentityStoreInterface
        get() = object : IdentityStoreInterface {
            override fun encryptData(
                plaintext: ByteArray,
                nonce: ByteArray,
                receiverPublicKey: ByteArray
            ): ByteArray {
                return plaintext
            }

            override fun decryptData(
                ciphertext: ByteArray,
                nonce: ByteArray,
                senderPublicKey: ByteArray
            ): ByteArray {
                return ciphertext
            }

            override fun calcSharedSecret(publicKey: ByteArray): ByteArray {
                return ByteArray(32)
            }

            override fun getIdentity(): String {
                return myIdentity
            }

            override fun getServerGroup(): String {
                return ""
            }

            override fun getPublicKey(): ByteArray {
                return ByteArray(256)
            }

            override fun getPrivateKey(): ByteArray {
                return ByteArray(32)
            }

            override fun getPublicNickname(): String {
                return ""
            }

            override fun storeIdentity(
                identity: String,
                serverGroup: String,
                publicKey: ByteArray,
                privateKey: ByteArray
            ) {
            }
        }

    @JvmStatic
    val noopNonceFactory: NonceFactory
        get() = NonceFactory(object : NonceStore {
            override fun exists(scope: NonceScope, nonce: Nonce): Boolean {
                return false
            }

            override fun store(scope: NonceScope, nonce: Nonce): Boolean {
                return true
            }

            override fun getCount(scope: NonceScope): Long {
                return 0
            }

            override fun getAllHashedNonces(scope: NonceScope): List<HashedNonce> {
                return emptyList()
            }

            override fun addHashedNoncesChunk(
                scope: NonceScope,
                chunkSize: Int,
                offset: Int,
                nonces: MutableList<HashedNonce>
            ) {
                // noop
            }

            override fun insertHashedNonces(scope: NonceScope, nonces: List<HashedNonce>): Boolean {
                return true
            }
        })

    /**
     * Adds a default sender and receiver to a message
     */
    @JvmStatic
    fun setMessageDefaultSenderAndReceiver(msg: AbstractMessage): AbstractMessage {
        val toIdentity = "ABCDEFGH"
        msg.fromIdentity = toIdentity
        msg.toIdentity = myIdentity
        return msg
    }

    @Throws(ThreemaException::class)
    @JvmStatic
    fun boxMessage(msg: AbstractMessage?): MessageBox {
        val messageCoder = MessageCoder(noopContactStore, noopIdentityStore)
        val nonceFactory = noopNonceFactory
        val nonce: Nonce = nonceFactory.next(NonceScope.CSP)
        return messageCoder.encode(msg!!, nonce.bytes)
    }

    @Throws(MissingPublicKeyException::class, BadMessageException::class)
    @JvmStatic
    fun decodeMessageFromBox(boxedMessage: MessageBox): AbstractMessage {
        val messageCoder = MessageCoder(noopContactStore, noopIdentityStore)
        return messageCoder.decode(boxedMessage)
    }
}
