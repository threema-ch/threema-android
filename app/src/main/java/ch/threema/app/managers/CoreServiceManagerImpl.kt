/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.managers

import ch.threema.app.multidevice.MultiDeviceManagerImpl
import ch.threema.app.services.ServerMessageService
import ch.threema.app.services.ServerMessageServiceImpl
import ch.threema.app.stores.IdentityStore
import ch.threema.app.stores.PreferenceStoreInterface
import ch.threema.app.tasks.TaskArchiverImpl
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DeviceCookieManagerImpl
import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.models.AppVersion
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.TaskManagerConfiguration
import ch.threema.domain.taskmanager.TaskManagerProvider
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseServiceNew

/**
 * The core service manager contains some core services that are used before the other services are
 * instantiated. Note that some of the provided services must be further initialized before they can
 * be used.
 */
class CoreServiceManagerImpl(
    override val version: AppVersion,
    override val databaseService: DatabaseServiceNew,
    override val preferenceStore: PreferenceStoreInterface,
    override val identityStore: IdentityStore,
    private val nonceDatabaseStoreProvider: () -> DatabaseNonceStore,
) : CoreServiceManager {

    /**
     * The task archiver. Note that this must only be used to load the persisted tasks when the
     * service manager has been set.
     */
    override val taskArchiver: TaskArchiverImpl by lazy {
        TaskArchiverImpl(databaseService.taskArchiveFactory)
    }

    /**
     * The device cookie manager. Note that this must only be used when the notification service is
     * passed to it.
     */
    override val deviceCookieManager: DeviceCookieManagerImpl by lazy {
        DeviceCookieManagerImpl(preferenceStore, databaseService)
    }

    /**
     * The task manager. Note that this must only be used to schedule tasks when the task archiver
     * has access to the service manager.
     */
    override val taskManager: TaskManager by lazy {
        TaskManagerProvider.getTaskManager(
            TaskManagerConfiguration(
                { taskArchiver },
                deviceCookieManager,
                ConfigUtils.isDevBuild()
            )
        )
    }

    /**
     * The server message service.
     * TODO(ANDR-2604): Use this wherever server messages are used
     */
    private val serverMessageService: ServerMessageService by lazy {
        ServerMessageServiceImpl(databaseService)
    }

    /**
     * The multi device manager.
     */
    override val multiDeviceManager: MultiDeviceManagerImpl by lazy {
        MultiDeviceManagerImpl(
            preferenceStore,
            serverMessageService,
            version,
        )
    }

    /**
     * The nonce factory.
     */
    override val nonceFactory: NonceFactory by lazy { NonceFactory(nonceDatabaseStoreProvider()) }
}
