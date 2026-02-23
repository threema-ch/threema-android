package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.types.Identity

data class RemoteSecretClientParameters(
    val workServerBaseUrl: String,
    val userIdentity: Identity,
    val clientKey: CryptographicByteArray,
    val credentials: UserCredentials,
)
