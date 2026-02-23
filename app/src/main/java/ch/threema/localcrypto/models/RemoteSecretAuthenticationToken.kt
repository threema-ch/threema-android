package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray
import ch.threema.localcrypto.MasterKeyConfig.REMOTE_SECRET_AUTH_TOKEN_LENGTH

class RemoteSecretAuthenticationToken(value: ByteArray) : CryptographicByteArray(value) {
    init {
        require(value.size == REMOTE_SECRET_AUTH_TOKEN_LENGTH)
    }
}
