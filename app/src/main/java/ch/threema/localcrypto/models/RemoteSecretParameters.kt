package ch.threema.localcrypto.models

data class RemoteSecretParameters(
    val authenticationToken: RemoteSecretAuthenticationToken,
    val remoteSecretHash: RemoteSecretHash,
)
