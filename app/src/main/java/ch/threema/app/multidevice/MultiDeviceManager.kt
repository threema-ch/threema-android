/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.multidevice


import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.multidevice.linking.DeviceJoinDataCollector
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseReason
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.flow.Flow

interface MultiDeviceManager {
    // TODO(ANDR-2519): Remove when md allows fs
    val isMdDisabledOrSupportsFs: Boolean

    val isMultiDeviceActive: Boolean

    val linkedDevices: List<String>

    val propertiesProvider: MultiDevicePropertyProvider

    val socketCloseListener: D2mSocketCloseListener

    // TODO(ANDR-2604): Remove when a dialog is shown in ui and this is not needed anymore
    val latestSocketCloseReason: Flow<D2mSocketCloseReason?>

    @WorkerThread
    suspend fun activate(
        deviceLabel: String,
        taskManager: TaskManager, // TODO(ANDR-2519): Remove
        contactService: ContactService, // TODO(ANDR-2519): remove
        userService: UserService, // TODO(ANDR-2519): remove
        fsMessageProcessor: ForwardSecurityMessageProcessor, // TODO(ANDR-2519): remove
    )

    @WorkerThread
    suspend fun deactivate(
        taskManager: TaskManager,
        userService: UserService, // TODO(ANDR-2519): remove
        fsMessageProcessor: ForwardSecurityMessageProcessor // TODO(ANDR-2519): remove
    )

    @WorkerThread
    suspend fun setDeviceLabel(deviceLabel: String)

    @AnyThread
    suspend fun linkDevice(
        deviceJoinOfferUri: String,
        deviceJoinDataCollector: DeviceJoinDataCollector,
    )
}
