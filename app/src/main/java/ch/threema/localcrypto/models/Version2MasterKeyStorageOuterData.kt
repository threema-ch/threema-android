package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray

sealed class Version2MasterKeyStorageOuterData {
    data class PassphraseProtected(
        val argonVersion: Argon2Version,
        val encryptedData: CryptographicByteArray,
        val nonce: CryptographicByteArray,
        val memoryBytes: Int,
        val salt: CryptographicByteArray,
        val iterations: Int,
        val parallelism: Int,
    ) : Version2MasterKeyStorageOuterData()

    data class NotPassphraseProtected(
        val innerData: Version2MasterKeyStorageInnerData,
    ) : Version2MasterKeyStorageOuterData()
}
