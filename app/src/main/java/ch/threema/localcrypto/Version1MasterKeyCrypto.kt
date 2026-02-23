package ch.threema.localcrypto

import ch.threema.common.models.CryptographicByteArray
import ch.threema.common.secureContentEquals
import ch.threema.common.xor
import ch.threema.domain.libthreema.LibthreemaJavaBridge.createScryptParameters
import ch.threema.libthreema.scrypt
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import java.security.MessageDigest

class Version1MasterKeyCrypto {
    fun checkVerification(masterKeyData: MasterKeyData, verification: CryptographicByteArray): Boolean =
        calculateVerification(masterKeyData).secureContentEquals(verification.value)

    private fun calculateVerification(masterKeyData: MasterKeyData): ByteArray {
        val messageDigest = MessageDigest.getInstance("SHA-1")
        messageDigest.update(masterKeyData.value)
        return messageDigest.digest().copyOfRange(0, MasterKeyConfig.VERSION1_VERIFICATION_LENGTH)
    }

    fun decryptPassphraseProtectedMasterKey(
        protection: MasterKeyState.WithPassphrase.PassphraseProtection.Version1,
        passphrase: CharArray,
    ): MasterKeyData {
        val passphraseKey = deriveVersion1PassphraseKey(passphrase, protection.salt)
        return MasterKeyData(protection.protectedKey.value xor passphraseKey)
    }

    private fun deriveVersion1PassphraseKey(
        passphrase: CharArray,
        salt: CryptographicByteArray,
    ): ByteArray =
        scrypt(
            password = String(passphrase).toByteArray(),
            salt = salt.value,
            parameters = createScryptParameters(outputLength = MasterKeyConfig.KEY_LENGTH.toByte()),
        )
}
