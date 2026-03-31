package ch.threema.localcrypto

import ch.threema.base.ThreemaException
import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.InvalidCredentialsException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretCreationResult
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionInstruction
import java.io.IOException

class RemoteSecretManagerImpl(
    private val remoteSecretClient: RemoteSecretClient,
    private val shouldUseRemoteSecretProtection: () -> Boolean,
    private val remoteSecretMonitor: RemoteSecretMonitor,
    private val getWorkServerBaseUrl: () -> String,
) : RemoteSecretManager {

    override fun checkRemoteSecretProtection(lockData: MasterKeyState.WithRemoteSecret?): RemoteSecretProtectionInstruction {
        val shouldUseRemoteSecretProtection = shouldUseRemoteSecretProtection()
        val usesRemoteSecretProtection = lockData != null
        return when {
            shouldUseRemoteSecretProtection && !usesRemoteSecretProtection -> RemoteSecretProtectionInstruction.SHOULD_ACTIVATE
            !shouldUseRemoteSecretProtection && usesRemoteSecretProtection -> RemoteSecretProtectionInstruction.SHOULD_DEACTIVATE
            else -> RemoteSecretProtectionInstruction.NO_CHANGE_NEEDED
        }
    }

    @Throws(ThreemaException::class, InvalidCredentialsException::class, IOException::class)
    override suspend fun createRemoteSecret(clientParameters: RemoteSecretClientParameters): RemoteSecretCreationResult =
        try {
            remoteSecretClient.createRemoteSecret(
                parameters = clientParameters,
            )
        } catch (e: RemoteSecretClient.RemoteSecretClientException) {
            throw ThreemaException("Failed to create remote secret", e)
        }

    @Throws(ThreemaException::class, InvalidCredentialsException::class, IOException::class)
    override suspend fun deleteRemoteSecret(
        clientParameters: RemoteSecretClientParameters,
        authenticationToken: RemoteSecretAuthenticationToken,
    ) {
        try {
            remoteSecretClient.deleteRemoteSecret(
                parameters = clientParameters,
                authenticationToken = authenticationToken,
            )
        } catch (e: RemoteSecretClient.RemoteSecretClientException) {
            throw ThreemaException("Failed to delete remote secret", e)
        }
    }

    @Throws(RemoteSecretMonitorException::class, BlockedByAdminException::class)
    override suspend fun monitorRemoteSecret(parameters: RemoteSecretParameters) {
        remoteSecretMonitor.monitor(
            baseUrl = getWorkServerBaseUrl(),
            parameters = parameters,
        )
    }

    override suspend fun awaitRemoteSecretAndClear() = remoteSecretMonitor.awaitRemoteSecretAndClear()
}
