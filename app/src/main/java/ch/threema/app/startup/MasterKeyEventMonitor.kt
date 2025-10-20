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

package ch.threema.app.startup

import ch.threema.app.apptaskexecutor.AppTaskExecutor
import ch.threema.app.apptaskexecutor.tasks.RemoteSecretDeleteStepsTask
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.base.utils.LoggingUtil
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.MasterKeyEvent
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val logger = LoggingUtil.getThreemaLogger("MasterKeyEventMonitor")

class MasterKeyEventMonitor(
    private val serviceManagerProvider: ServiceManagerProvider,
    private val masterKeyManager: MasterKeyManager,
    private val appTaskExecutor: AppTaskExecutor,
) : KoinComponent {
    suspend fun monitorMasterKeyEvents() = coroutineScope {
        masterKeyManager.events.collect { masterKeyEvent ->
            when (masterKeyEvent) {
                MasterKeyEvent.RemoteSecretActivated -> {
                    serviceManagerProvider.getServiceManagerOrNull()?.notificationService?.showRemoteSecretActivatedNotification()
                        ?: logger.error("Could not show RS activation notification because the service manager was not ready")
                }

                is MasterKeyEvent.RemoteSecretDeactivated -> {
                    serviceManagerProvider.getServiceManagerOrNull()?.notificationService?.showRemoteSecretDeactivatedNotification()
                        ?: logger.error("Could not show RS deactivation notification because the service manager was not ready")

                    appTaskExecutor.persistAndScheduleTask(
                        appTask = RemoteSecretDeleteStepsTask(
                            serviceManagerProvider = get(),
                            appStartupMonitor = get(),
                            masterKeyManager = get(),
                            authenticationToken = masterKeyEvent.remoteSecretAuthenticationToken,
                        ),
                    )
                }
            }
        }
    }
}
