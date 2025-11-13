/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.di

import android.content.Context
import ch.threema.app.GlobalListeners
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ServiceManagerProviderImpl
import ch.threema.app.startup.AppStartupMonitorImpl
import ch.threema.app.systemupdates.SystemUpdateState
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.utils.WidgetUtil
import ch.threema.app.workers.ShareTargetUpdateWorker
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.DatabaseState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = LoggingUtil.getThreemaLogger("MasterKeyLockStateChangeHandler")

// TODO(ANDR-4187): Move this logic to a better suited place
class MasterKeyLockStateChangeHandler(
    private val appContext: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val appStartupMonitor: AppStartupMonitorImpl,
    private val serviceManagerProvider: ServiceManagerProviderImpl,
) {

    @Volatile
    private var globalListeners: GlobalListeners? = null

    fun onMasterKeyUnlocked(
        serviceManager: ServiceManager,
        databaseState: StateFlow<DatabaseState>,
        systemUpdateState: StateFlow<SystemUpdateState>,
    ) {
        serviceManagerProvider.setServiceManager(serviceManager)

        appStartupMonitor.onServiceManagerReady(databaseState, systemUpdateState)
        globalListeners = GlobalListeners(appContext, serviceManager).apply {
            setUp()
        }

        WidgetUtil.updateWidgets(appContext)
    }

    suspend fun onMasterKeyLocked() = coroutineScope {
        appStartupMonitor.onServiceManagerDestroyed()

        val serviceManager = serviceManagerProvider.getServiceManagerOrNull()
        serviceManagerProvider.setServiceManager(null)
        serviceManager?.let {
            serviceManager.notificationService.cancelConversationNotificationsOnLockApp()
            serviceManager.connection.takeIf { it.isRunning }?.let { connection ->
                launch(dispatcherProvider.io) {
                    try {
                        connection.stop()
                    } catch (e: InterruptedException) {
                        logger.error("Interrupted while stopping connection", e)
                    }
                }
            }

            if (serviceManager.preferenceService.isDirectShare) {
                ShareTargetUpdateWorker.cancelScheduledShareTargetShortcutUpdate(appContext)
                ShortcutUtil.deleteAllShareTargetShortcuts()
            }

            launch(dispatcherProvider.io) {
                serviceManager.databaseService.close()
                serviceManager.dhSessionStore.close()
            }
        }

        globalListeners?.tearDown()
        globalListeners = null

        ConfigUtils.scheduleAppRestart(appContext)

        WidgetUtil.updateWidgets(appContext)
    }
}
