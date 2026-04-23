package ch.threema.app.startup

import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("NoOpRemoteSecretProtectionStateMonitorImpl")

class NoOpRemoteSecretProtectionStateMonitorImpl : RemoteSecretProtectionStateMonitor {
    override suspend fun monitorRemoteSecretProtectionState() {
        logger.debug("Remote secret is not monitored")
    }
}
