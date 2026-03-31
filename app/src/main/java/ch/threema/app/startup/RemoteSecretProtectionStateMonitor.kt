package ch.threema.app.startup

interface RemoteSecretProtectionStateMonitor {
    suspend fun monitorRemoteSecretProtectionState()
}
