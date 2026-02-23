package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray

sealed class Version2MasterKeyStorageInnerData {
    data class RemoteSecretProtected(
        val parameters: RemoteSecretParameters,
        val encryptedData: CryptographicByteArray,
    ) : Version2MasterKeyStorageInnerData()

    data class Unprotected(val masterKeyData: MasterKeyData) : Version2MasterKeyStorageInnerData()
}
