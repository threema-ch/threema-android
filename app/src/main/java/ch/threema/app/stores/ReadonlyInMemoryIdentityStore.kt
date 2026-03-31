package ch.threema.app.stores

import ch.threema.base.crypto.NaCl
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.IdentityString

// TODO(ANDR-4067): This class should be removed
@Deprecated("Do not use this class, it only exists as a workaround")
class ReadonlyInMemoryIdentityStore(
    private val identity: IdentityString,
    private val serverGroup: String,
    private val privateKey: ByteArray,
) : IdentityStore {
    override fun getIdentityString() = identity

    override fun getServerGroup() = serverGroup

    override fun getPublicKey(): ByteArray? {
        throw NotImplementedError()
    }

    override fun getPrivateKey() = privateKey

    override fun getPublicNickname() = identity

    override fun calcSharedSecret(publicKey: ByteArray): ByteArray =
        NaCl(privateKey, publicKey).sharedSecret

    override fun encryptData(plaintext: ByteArray, nonce: ByteArray, receiverPublicKey: ByteArray): ByteArray? {
        throw NotImplementedError()
    }

    override fun decryptData(ciphertext: ByteArray, nonce: ByteArray, senderPublicKey: ByteArray): ByteArray? {
        throw NotImplementedError()
    }

    override fun setPublicNickname(publicNickname: String) {
        throw NotImplementedError()
    }

    override fun storeIdentity(identity: String, serverGroup: String, privateKey: ByteArray) {
        throw NotImplementedError()
    }

    override fun clear() {
        throw NotImplementedError()
    }
}
