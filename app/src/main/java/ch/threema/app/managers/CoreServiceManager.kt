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

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.IdentityStore
import ch.threema.app.stores.PreferenceStoreInterface
import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.models.AppVersion
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.taskmanager.TaskArchiver
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.DatabaseServiceNew

/**
 * The core service manager contains some core services that are used before the other services are
 * instantiated. Note that some of the provided services must be further initialized before they can
 * be used.
 */
interface CoreServiceManager {
    /**
     * The app version.
     */
    val version: AppVersion

    /**
     * The database service.
     */
    val databaseService: DatabaseServiceNew

    /**
     * The preference store
     */
    val preferenceStore: PreferenceStoreInterface

    /**
     * The task archiver. Note that this must only be used to load the persisted tasks when the
     * service manager has been set.
     */
    val taskArchiver: TaskArchiver

    /**
     * The device cookie manager. Note that this must only be used when the notification service is
     * passed to it.
     */
    val deviceCookieManager: DeviceCookieManager

    /**
     * The task manager. Note that this must only be used to schedule tasks when the task archiver
     * has access to the service manager.
     */
    val taskManager: TaskManager

    /**
     * The multi device manager.
     */
    val multiDeviceManager: MultiDeviceManager

    /**
     * The identity store.
     */
    val identityStore: IdentityStore

    /**
     * The nonce factory.
     */
    val nonceFactory: NonceFactory
}
