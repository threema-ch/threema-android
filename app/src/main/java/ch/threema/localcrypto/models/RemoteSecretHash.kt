package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray
import ch.threema.localcrypto.MasterKeyConfig.REMOTE_SECRET_HASH_LENGTH

class RemoteSecretHash(value: ByteArray) : CryptographicByteArray(value) {
    init {
        require(value.size == REMOTE_SECRET_HASH_LENGTH)
    }
}
