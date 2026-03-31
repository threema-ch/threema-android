package ch.threema.localcrypto

import ch.threema.app.DangerousTest
import ch.threema.localcrypto.protobuf.KeyWrapper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DangerousTest(reason = "Deletes keys from the key store")
class KeyStoreCryptoTest {

    private lateinit var keyStoreSecretKeyManager: KeyStoreSecretKeyManager
    private lateinit var keyStoreCrypto: KeyStoreCrypto

    @BeforeTest
    fun setUp() {
        keyStoreSecretKeyManager = KeyStoreSecretKeyManager()
        keyStoreCrypto = KeyStoreCrypto(keyStoreSecretKeyManager)
        keyStoreSecretKeyManager.deleteAllSecretKeys()
    }

    @AfterTest
    fun tearDown() {
        keyStoreSecretKeyManager.deleteAllSecretKeys()
    }

    @Test
    fun encryptAndDecrypt() {
        val myData = byteArrayOf(1, 2, 3, 4, 5, 6)

        val encryptedData = keyStoreCrypto.encryptWithNewSecretKey(myData, previousKeyAlias = null)
        assertEquals(SecretKeyAlias.PRIMARY, keyStoreCrypto.extractSecretKeyAlias(encryptedData))

        val keyWrapper = KeyWrapper.parseFrom(encryptedData)
        assertEquals("threema_master_key_a", keyWrapper.keyStoreAlias)
        assertEquals(12, keyWrapper.iv.size())

        val myData2 = keyStoreCrypto.decryptWithExistingSecretKey(encryptedData)

        assertContentEquals(myData, myData2)
    }

    @Test
    fun encryptAndDecryptWithSecondaryKeyAlias() {
        val myData = byteArrayOf(1, 2, 3, 4, 5, 6)

        val encryptedData = keyStoreCrypto.encryptWithNewSecretKey(myData, previousKeyAlias = SecretKeyAlias.PRIMARY)
        assertEquals(SecretKeyAlias.SECONDARY, keyStoreCrypto.extractSecretKeyAlias(encryptedData))

        val keyWrapper = KeyWrapper.parseFrom(encryptedData)
        assertEquals("threema_master_key_b", keyWrapper.keyStoreAlias)

        val myData2 = keyStoreCrypto.decryptWithExistingSecretKey(encryptedData)

        assertContentEquals(myData, myData2)
    }

    @Test
    fun deleteSecretKeyAlias() {
        val myData = byteArrayOf(1, 2, 3, 4, 5, 6)

        val encryptedData = keyStoreCrypto.encryptWithNewSecretKey(myData, previousKeyAlias = null)

        keyStoreCrypto.deleteSecretKey(SecretKeyAlias.PRIMARY)

        assertFailsWith<IllegalStateException> {
            keyStoreCrypto.decryptWithExistingSecretKey(encryptedData)
        }
    }
}
