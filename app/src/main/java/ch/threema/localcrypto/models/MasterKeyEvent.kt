package ch.threema.localcrypto.models

sealed interface MasterKeyEvent {
    /**
     * Remote secret protection has been removed from the master key and isn't in use anymore.
     *
     * @param remoteSecretAuthenticationToken the token associated with the now obsolete remote secret.
     * It should be used to delete the remote secret from the server.
     */
    data class RemoteSecretDeactivated(
        val remoteSecretAuthenticationToken: RemoteSecretAuthenticationToken,
    ) : MasterKeyEvent
}
