package ch.threema.domain.testhelpers

import ch.threema.base.ThreemaException
import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.crypto.NonceStore
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.IdentityString
import ch.threema.testhelpers.MUST_NOT_BE_CALLED

object TestHelpers {
    const val MY_IDENTITY = "TESTTEST"

    @JvmStatic
    val noopContactStore: ContactStore
        get() = object : ContactStore {
            override fun getContactForIdentity(identity: IdentityString): Contact {
                return Contact(identity, ByteArray(256))
            }

            override fun addCachedContact(contact: BasicContact) {}
            override fun getCachedContact(identity: IdentityString) = null
            override fun getContactForIdentityIncludingCache(identity: IdentityString): Contact {
                return getContactForIdentity(identity)
            }

            override fun addContact(contact: Contact) {}
            override fun isSpecialContact(identity: IdentityString) = false
        }

    @JvmStatic
    val noopIdentityStore: IdentityStore
        get() = object : IdentityStore {
            override fun encryptData(
                plaintext: ByteArray,
                nonce: ByteArray,
                receiverPublicKey: ByteArray,
            ): ByteArray {
                return plaintext
            }

            override fun decryptData(
                ciphertext: ByteArray,
                nonce: ByteArray,
                senderPublicKey: ByteArray,
            ): ByteArray {
                return ciphertext
            }

            override fun calcSharedSecret(publicKey: ByteArray): ByteArray {
                return ByteArray(32)
            }

            override fun getIdentityString() = MY_IDENTITY

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
                identity: IdentityString,
                serverGroup: String,
                privateKey: ByteArray,
            ) {
            }

            override fun setPublicNickname(publicNickname: String) {
                MUST_NOT_BE_CALLED()
            }

            override fun clear() {
                MUST_NOT_BE_CALLED()
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
                hashedNonces: MutableList<HashedNonce>,
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
        msg.toIdentity = MY_IDENTITY
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
