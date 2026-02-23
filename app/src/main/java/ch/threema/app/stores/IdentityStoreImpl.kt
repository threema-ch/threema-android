package ch.threema.app.stores

import ch.threema.app.listeners.ProfileListener
import ch.threema.app.managers.ListenerManager
import ch.threema.base.ThreemaException
import ch.threema.base.crypto.KeyPair
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import ch.threema.libthreema.CryptoException
import java.util.Collections

private val logger = getThreemaLogger("IdentityStoreImpl")

class IdentityStoreImpl
@JvmOverloads
constructor(
    private val identityProvider: MutableIdentityProvider,
    private val preferenceStore: PreferenceStore,
    private val encryptedPreferenceStore: EncryptedPreferenceStore,
    private val derivePublicKey: (privateKey: ByteArray) -> ByteArray = NaCl.Companion::derivePublicKey,
    private val onNicknameChanged: (nickname: String) -> Unit = { nickName ->
        ListenerManager.profileListeners.handle { listener: ProfileListener -> listener.onNicknameChanged(nickName) }
    },
) : IdentityStore {

    private var identityData: IdentityData? = null
    private var publicNickname: String? = null

    private val naClCache: MutableMap<KeyPair, NaCl> = Collections.synchronizedMap(HashMap())

    init {
        val identity = identityProvider.getIdentity()
        if (identity != null) {
            val serverGroup = preferenceStore.getString(PreferenceStore.PREFS_SERVER_GROUP)
                ?: error("No server group found")
            val publicKey = preferenceStore.getBytes(PreferenceStore.PREFS_PUBLIC_KEY)
            val privateKey = encryptedPreferenceStore.getBytes(EncryptedPreferenceStore.PREFS_PRIVATE_KEY)
                .takeUnless { it.isEmpty() }
            val publicNickname = preferenceStore.getString(PreferenceStore.PREFS_PUBLIC_NICKNAME)

            if (privateKey == null) {
                logger.warn("Private key missing")
            }

            identityData = IdentityData(
                identity = identity,
                serverGroup = serverGroup,
                publicKey = publicKey,
                privateKey = privateKey,
            )
            this.publicNickname = publicNickname
        }
    }

    override fun encryptData(plaintext: ByteArray, nonce: ByteArray, receiverPublicKey: ByteArray) =
        identityData?.privateKey?.let { privateKey ->
            val nacl = getCachedNaCl(privateKey, receiverPublicKey)
            try {
                nacl.encrypt(plaintext, nonce)
            } catch (cryptoException: CryptoException) {
                logger.error("Failed to encrypt data", cryptoException)
                null
            }
        }

    override fun decryptData(ciphertext: ByteArray, nonce: ByteArray, senderPublicKey: ByteArray) =
        identityData?.privateKey?.let { privateKey ->
            val nacl = getCachedNaCl(privateKey, senderPublicKey)
            try {
                nacl.decrypt(ciphertext, nonce)
            } catch (cryptoException: CryptoException) {
                logger.error("Failed to decrypt data", cryptoException)
                null
            }
        }

    override fun calcSharedSecret(publicKey: ByteArray) =
        getCachedNaCl(identityData!!.privateKey!!, publicKey).sharedSecret

    override fun getIdentity() = identityData?.identity

    override fun getServerGroup() = identityData?.serverGroup

    override fun getPublicKey() = identityData?.publicKey

    override fun getPrivateKey() = identityData?.privateKey

    override fun getPublicNickname() = publicNickname.orEmpty()

    override fun setPublicNickname(publicNickname: String) {
        this.publicNickname = publicNickname
        preferenceStore.save(PreferenceStore.PREFS_PUBLIC_NICKNAME, publicNickname)
        onNicknameChanged(publicNickname)
    }

    override fun storeIdentity(
        identity: String,
        serverGroup: String,
        privateKey: ByteArray,
    ) {
        val publicKey = try {
            derivePublicKey(privateKey)
        } catch (e: CryptoException) {
            throw ThreemaException("Could not derive public key", e)
        }
        identityData = IdentityData(
            identity = identity,
            serverGroup = serverGroup,
            publicKey = publicKey,
            privateKey = privateKey.takeUnless { it.isEmpty() },
        )

        identityProvider.setIdentity(identity)
        preferenceStore.save(PreferenceStore.PREFS_SERVER_GROUP, serverGroup)
        preferenceStore.save(PreferenceStore.PREFS_PUBLIC_KEY, publicKey)
        encryptedPreferenceStore.save(EncryptedPreferenceStore.PREFS_PRIVATE_KEY, privateKey)

        // default identity
        setPublicNickname(identity)
    }

    override fun clear() {
        identityData = null
        publicNickname = null

        identityProvider.setIdentity(null)
        preferenceStore.remove(
            setOf(
                PreferenceStore.PREFS_SERVER_GROUP,
                PreferenceStore.PREFS_PUBLIC_KEY,
            ),
        )
        encryptedPreferenceStore.remove(EncryptedPreferenceStore.PREFS_PRIVATE_KEY)
    }

    private fun getCachedNaCl(privateKey: ByteArray, publicKey: ByteArray): NaCl {
        // Check for cached NaCl instance to save heavy Curve25519 computation
        val hashKey = KeyPair(privateKey = privateKey, publicKey = publicKey)
        return naClCache.computeIfAbsent(hashKey) {
            NaCl(privateKey, publicKey)
        }
    }

    private data class IdentityData(
        val identity: Identity,
        val serverGroup: String,
        val publicKey: ByteArray,
        val privateKey: ByteArray?,
    ) {
        init {
            require(identity.length == ProtocolDefines.IDENTITY_LEN)
            require(publicKey.size == NaCl.PUBLIC_KEY_BYTES)
            require(privateKey == null || privateKey.size == NaCl.SECRET_KEY_BYTES)
        }
    }
}
