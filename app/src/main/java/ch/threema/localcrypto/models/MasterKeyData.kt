package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray
import ch.threema.localcrypto.MasterKeyConfig

class MasterKeyData(value: ByteArray) : CryptographicByteArray(value) {
    init {
        require(value.size == MasterKeyConfig.KEY_LENGTH) {
            "Master key has invalid length (${value.size} instead of ${MasterKeyConfig.KEY_LENGTH})"
        }
    }
}
