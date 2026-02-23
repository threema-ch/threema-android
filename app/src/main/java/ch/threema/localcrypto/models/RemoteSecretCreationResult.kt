package ch.threema.localcrypto.models

data class RemoteSecretCreationResult(
    val remoteSecret: RemoteSecret,
    val parameters: RemoteSecretParameters,
)
