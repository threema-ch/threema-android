package ch.threema.app.startup

import ch.threema.app.services.RemoteSecretMonitorService
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.RemoteSecretProtectionState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map

class RemoteSecretProtectionStateMonitorImpl(
    private val remoteSecretMonitorServiceScheduler: RemoteSecretMonitorService.Scheduler,
    private val masterKeyManager: MasterKeyManager,
) : RemoteSecretProtectionStateMonitor {
    override suspend fun monitorRemoteSecretProtectionState() {
        masterKeyManager.remoteSecretProtectionState
            .map { protectionState ->
                when (protectionState) {
                    RemoteSecretProtectionState.ACTIVE -> true
                    RemoteSecretProtectionState.INACTIVE -> false
                }
            }
            .distinctUntilChanged()
            .dropWhile { remoteSecretActive -> !remoteSecretActive }
            .collect { serviceNeeded ->
                if (serviceNeeded) {
                    remoteSecretMonitorServiceScheduler.start()
                } else {
                    remoteSecretMonitorServiceScheduler.stop()
                }
            }
    }
}
