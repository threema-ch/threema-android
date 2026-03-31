package ch.threema.localcrypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeyStoreSecretKeyManager {
    fun getSecretKey(keyAlias: String): SecretKey? {
        val keyStore = getKeyStore()
        val secretKeyEntry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        return secretKeyEntry?.secretKey
    }

    fun createSecretKey(keyAlias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun getKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE)
            .apply {
                load(null)
            }

    fun deleteSecretKey(keyAlias: String) {
        val keyStore = getKeyStore()
        keyStore.deleteEntry(keyAlias)
    }

    fun deleteAllSecretKeys() {
        val keyStore = getKeyStore()
        keyStore.aliases().toList().forEach { keyAlias ->
            keyStore.deleteEntry(keyAlias)
        }
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}
