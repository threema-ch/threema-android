package ch.threema.localcrypto

import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.localcrypto.models.RemoteSecret
import kotlinx.coroutines.Deferred

/**
 * Wrapper around libthreema's RemoteSecretMonitorProtocol, which allows to fetch and monitor the remote secret.
 */
interface RemoteSecretLoop : AutoCloseable {
    val remoteSecret: Deferred<RemoteSecret>

    /**
     * Fetches and monitors the remote secret. Will run forever or until an error occurs, in which case an exception is thrown.
     * If an error occurs, [close] must be called and [run] must not be called again.
     */
    @Throws(RemoteSecretMonitorException::class, BlockedByAdminException::class)
    suspend fun run()
}
