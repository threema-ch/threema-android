package ch.threema.localcrypto

import ch.threema.common.toCryptographicByteArray
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Version1MasterKeyCryptoTest {
    @Test
    fun `check verification, is equal`() {
        assertTrue(
            Version1MasterKeyCrypto().checkVerification(
                masterKeyData = MasterKeyData(MASTER_KEY),
                verification = MasterKeyTestData.Version1.VERIFICATION.toCryptographicByteArray(),
            ),
        )
    }

    @Test
    fun `check verification, is not equal`() {
        val invalid = MasterKeyTestData.Version1.VERIFICATION.copyOf()
        invalid[3] = 123
        assertFalse(
            Version1MasterKeyCrypto().checkVerification(
                masterKeyData = MasterKeyData(MASTER_KEY),
                verification = invalid.toCryptographicByteArray(),
            ),
        )
    }

    @Test
    fun `decrypt passphrase`() {
        val masterKeyData = Version1MasterKeyCrypto().decryptPassphraseProtectedMasterKey(
            protection = MasterKeyState.WithPassphrase.PassphraseProtection.Version1(
                protectedKey = MasterKeyTestData.Version1.PROTECTED_KEY.toCryptographicByteArray(),
                salt = MasterKeyTestData.Version1.SALT.toCryptographicByteArray(),
                verification = MasterKeyTestData.Version1.VERIFICATION.toCryptographicByteArray(),
            ),
            passphrase = "superSecretPassword".toCharArray(),
        )

        assertEquals(MasterKeyData(MASTER_KEY), masterKeyData)
    }
}
