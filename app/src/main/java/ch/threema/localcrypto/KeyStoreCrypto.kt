package ch.threema.localcrypto

import ch.threema.localcrypto.protobuf.KeyWrapper
import ch.threema.localcrypto.protobuf.keyWrapper
import com.google.protobuf.kotlin.toByteString
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides helper functions to encrypt and decrypt the master key with a secret key stored in the Android KeyStore.
 */
class KeyStoreCrypto(
    private val keyStoreSecretKeyManager: KeyStoreSecretKeyManager,
) {
    /**
     * Generates a new secret key in the Android KeyStore and encrypts [data] with it.
     * We generate a new secret key every time to avoid problems stemming from key re-use.
     * After successfully persisting the newly encrypted master key, the previously used secret key should be deleted with [deleteSecretKey].
     *
     * @param data The master key storage data in bytes
     * @param previousKeyAlias The alias of the key that was previously used to encrypt the master key data, if any. This is to ensure
     * that a new secret key is generated with an alias different from the previous one.
     *
     * @return The encrypted data, packaged into a container which also includes the IV and the alias of the newly generated key.
     * The format of this data is an implementation detail not relevant outside of [KeyStoreCrypto].
     */
    fun encryptWithNewSecretKey(data: ByteArray, previousKeyAlias: SecretKeyAlias?): ByteArray {
        val keyAlias = determineKeyAlias(previousKeyAlias)

        // Ensure that no secret key with the newly generated alias exists. This should only happen if previously the master key file was written
        // but then failed to delete the previous key alias, e.g., due to an app crash. In this case, we are certain that the secret key isn't used
        // anymore, so deleting it here is safe.
        deleteSecretKey(keyAlias)

        val secretKey = keyStoreSecretKeyManager.createSecretKey(keyAlias.value)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.getIV()
        val encryptedData = cipher.doFinal(data)

        return keyWrapper {
            this.keyStoreAlias = keyAlias.value
            this.iv = iv.toByteString()
            this.encryptedKeyStorageData = encryptedData.toByteString()
        }
            .toByteArray()
    }

    /**
     * @param encryptedData Encrypted master key storage data as previously returned by [encryptWithNewSecretKey].
     * @return The master key storage data in bytes
     */
    fun decryptWithExistingSecretKey(encryptedData: ByteArray): ByteArray {
        val keyWrapper = KeyWrapper.parseFrom(encryptedData)
        val secretKey = keyStoreSecretKeyManager.getSecretKey(keyAlias = keyWrapper.keyStoreAlias)
            ?: error("No secret key found with alias ${keyWrapper.keyStoreAlias}")
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_AUTHENTICATION_TAG_LENGTH, keyWrapper.iv.toByteArray()))
        return cipher.doFinal(keyWrapper.encryptedKeyStorageData.toByteArray())
    }

    /**
     * @return The key alias of the secret key that was used to encrypt [encryptedData]
     */
    fun extractSecretKeyAlias(encryptedData: ByteArray): SecretKeyAlias {
        val keyWrapper = KeyWrapper.parseFrom(encryptedData)
        return SecretKeyAlias.fromValue(keyWrapper.keyStoreAlias)
            ?: error("Invalid secret key alias (${keyWrapper.keyStoreAlias})")
    }

    private fun determineKeyAlias(previousKeyAlias: SecretKeyAlias?): SecretKeyAlias {
        if (previousKeyAlias == SecretKeyAlias.PRIMARY) {
            return SecretKeyAlias.SECONDARY
        }
        return SecretKeyAlias.PRIMARY
    }

    fun deleteSecretKey(keyAlias: SecretKeyAlias) {
        keyStoreSecretKeyManager.deleteSecretKey(keyAlias.value)
    }

    companion object {
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_AUTHENTICATION_TAG_LENGTH = 128
    }
}
