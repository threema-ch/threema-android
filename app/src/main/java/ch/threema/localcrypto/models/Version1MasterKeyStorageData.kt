package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray

/**
 * Represents the data stored in the master key file version 1,
 * i.e., the version used prior to introduction of the Remote Secrets feature in app version 6.2.0.
 */
sealed class Version1MasterKeyStorageData {
    /**
     * @param protectedKey The master key encrypted with the passphrase
     */
    data class PassphraseProtected(
        val protectedKey: CryptographicByteArray,
        val salt: CryptographicByteArray,
        val verification: CryptographicByteArray,
    ) : Version1MasterKeyStorageData()

    data class Unprotected(
        val masterKeyData: MasterKeyData,
        val verification: CryptographicByteArray,
    ) : Version1MasterKeyStorageData()
}
