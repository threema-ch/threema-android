/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app

import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.multidevice.MultiDeviceManagerImpl
import ch.threema.app.stores.PreferenceStoreInterface
import ch.threema.app.tasks.TaskArchiverImpl
import ch.threema.app.utils.DeviceCookieManagerImpl
import ch.threema.domain.models.AppVersion
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.DatabaseServiceNew

class TestCoreServiceManager(
    override val version: AppVersion,
    override val databaseService: DatabaseServiceNew,
    override val preferenceStore: PreferenceStoreInterface,
    override val taskArchiver: TaskArchiverImpl,
    override val deviceCookieManager: DeviceCookieManagerImpl,
    override val taskManager: TaskManager,
    override val multiDeviceManager: MultiDeviceManagerImpl,
): CoreServiceManager
