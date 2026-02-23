package ch.threema.localcrypto

import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretCreationResult
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope

class NoOpRemoteSecretManagerImpl : RemoteSecretManager {
    override fun checkRemoteSecretProtection(lockData: MasterKeyState.WithRemoteSecret?) =
        RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED

    override suspend fun createRemoteSecret(clientParameters: RemoteSecretClientParameters): RemoteSecretCreationResult {
        error("must not be called")
    }

    override suspend fun deleteRemoteSecret(
        clientParameters: RemoteSecretClientParameters,
        authenticationToken: RemoteSecretAuthenticationToken,
    ) {
        error("must not be called")
    }

    override suspend fun monitorRemoteSecret(parameters: RemoteSecretParameters) = coroutineScope {
        cancel("nothing to monitor")
    }

    override suspend fun awaitRemoteSecretAndClear(): RemoteSecret {
        awaitCancellation()
    }
}
