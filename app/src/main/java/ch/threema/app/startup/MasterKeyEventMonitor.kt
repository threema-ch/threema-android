package ch.threema.app.startup

import ch.threema.app.apptaskexecutor.AppTaskExecutor
import ch.threema.app.apptaskexecutor.tasks.RemoteSecretDeleteStepsTask
import ch.threema.app.widget.WidgetUpdater
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.MasterKeyEvent
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent

class MasterKeyEventMonitor(
    private val masterKeyManager: MasterKeyManager,
    private val appTaskExecutor: AppTaskExecutor,
    private val widgetUpdater: WidgetUpdater,
) : KoinComponent {
    suspend fun monitorMasterKeyEvents() = coroutineScope {
        masterKeyManager.events.collect { masterKeyEvent ->
            when (masterKeyEvent) {
                is MasterKeyEvent.RemoteSecretActivated -> {
                    widgetUpdater.updateWidgets()
                }
                is MasterKeyEvent.RemoteSecretDeactivated -> {
                    appTaskExecutor.persistAndScheduleTask(
                        appTask = RemoteSecretDeleteStepsTask(
                            authenticationToken = masterKeyEvent.remoteSecretAuthenticationToken,
                        ),
                    )
                    widgetUpdater.updateWidgets()
                }
            }
        }
    }
}
