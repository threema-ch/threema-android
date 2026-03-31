package ch.threema.app.di

import android.content.Context
import ch.threema.app.GlobalListeners
import ch.threema.app.managers.ServiceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ServiceManagerProviderImpl
import ch.threema.app.startup.AppStartupMonitorImpl
import ch.threema.app.systemupdates.SystemUpdateState
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.voip.util.VoipUtil
import ch.threema.app.webclient.services.instance.DisconnectContext
import ch.threema.app.widget.WidgetUpdater
import ch.threema.app.workers.ShareTargetUpdateWorker
import ch.threema.base.utils.getThreemaLogger
import ch.threema.storage.DatabaseProviderImpl
import ch.threema.storage.DatabaseState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val logger = getThreemaLogger("MasterKeyLockStateChangeHandler")

// TODO(ANDR-4187): Move this logic to a better suited place
class MasterKeyLockStateChangeHandler(
    private val appContext: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val appStartupMonitor: AppStartupMonitorImpl,
    private val serviceManagerProvider: ServiceManagerProviderImpl,
    private val preferenceService: PreferenceService,
    private val databaseProvider: DatabaseProviderImpl,
    private val widgetUpdater: WidgetUpdater,
) : KoinComponent {

    @Volatile
    private var globalListeners: GlobalListeners? = null

    fun onMasterKeyUnlocked(
        serviceManager: ServiceManager,
        databaseState: StateFlow<DatabaseState>,
        systemUpdateState: StateFlow<SystemUpdateState>,
    ) {
        serviceManagerProvider.setServiceManager(serviceManager)

        appStartupMonitor.onMasterKeyUnlocked(databaseState, systemUpdateState)
        globalListeners = GlobalListeners(appContext, serviceManager).apply {
            setUp()
        }

        widgetUpdater.updateWidgets()
    }

    suspend fun onMasterKeyLocked() = coroutineScope {
        appStartupMonitor.onMasterKeyLocked()

        serviceManagerProvider.getServiceManagerOrNull()
            ?.let { serviceManager ->
                cleanUpSession(serviceManager)
            }
        serviceManagerProvider.setServiceManager(null)

        globalListeners?.tearDown()
        globalListeners = null

        ConfigUtils.scheduleAppRestart(appContext)

        widgetUpdater.updateWidgets()
    }

    private suspend fun cleanUpSession(serviceManager: ServiceManager) {
        withTimeout(SERVICE_MANAGER_CLEANUP_TIMEOUT) {
            stopOngoingCalls(serviceManager)
            dismissNotifications(serviceManager)
            stopConnection(serviceManager)
            deleteShareTargetShortcuts()
            stopWebClientSession(serviceManager)
            clearAvatarCache(serviceManager)
            closeDatabases(serviceManager)
        }

        delay(SERVICE_MANAGER_CLEANUP_GRACE_PERIOD)
        serviceManager.close()
    }

    private fun stopOngoingCalls(serviceManager: ServiceManager) {
        if (serviceManager.voipStateService.callState?.isIdle != true) {
            VoipUtil.sendOneToOneCallHangupCommand(appContext)
        }
        serviceManager.groupCallManager.abortCurrentCall()
    }

    private fun dismissNotifications(serviceManager: ServiceManager) {
        serviceManager.notificationService.cancelConversationNotificationsOnLockApp()
    }

    private suspend fun stopConnection(serviceManager: ServiceManager) = withContext(dispatcherProvider.io) {
        serviceManager.connection.takeIf { it.isRunning }?.let { connection ->
            try {
                connection.stop()
            } catch (e: InterruptedException) {
                logger.error("Interrupted while stopping connection", e)
            }
        }
    }

    private fun deleteShareTargetShortcuts() {
        if (preferenceService.isDirectShare()) {
            ShareTargetUpdateWorker.cancelScheduledShareTargetShortcutUpdate(appContext)
            ShortcutUtil.deleteAllShareTargetShortcuts(preferenceService)
        }
    }

    private fun stopWebClientSession(serviceManager: ServiceManager) {
        serviceManager.webClientServiceManager.sessionService.stopAll(
            DisconnectContext.byUs(DisconnectContext.REASON_SESSION_STOPPED),
        )
    }

    private fun clearAvatarCache(serviceManager: ServiceManager) {
        serviceManager.avatarCacheService.clear()
    }

    private suspend fun closeDatabases(serviceManager: ServiceManager) = withContext(dispatcherProvider.io) {
        databaseProvider.close()
        serviceManager.dhSessionStore.close()
    }

    companion object {
        /**
         * Locking the master key must complete quickly. If it doesn't, we'd rather crash the app to ensure
         * that the master key is cleared from memory than to linger here and wait for the full cleanup.
         */
        private val SERVICE_MANAGER_CLEANUP_TIMEOUT = 3.seconds

        /**
         * we grant a short grace period for services to shutdown properly before the service manager is closed,
         * to reduce the risk of crashes
         */
        private val SERVICE_MANAGER_CLEANUP_GRACE_PERIOD = 400.milliseconds
    }
}
