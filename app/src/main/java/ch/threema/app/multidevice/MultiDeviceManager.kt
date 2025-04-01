/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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
import ch.threema.app.multidevice.linking.DeviceLinkingDataCollector
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.TaskCreator
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseReason
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import kotlinx.coroutines.flow.Flow

interface MultiDeviceManager {
    // TODO(ANDR-2519): Remove when md allows fs
    val isMdDisabledOrSupportsFs: Boolean

    val isMultiDeviceActive: Boolean

    val propertiesProvider: MultiDevicePropertyProvider

    val socketCloseListener: D2mSocketCloseListener

    // TODO(ANDR-2604): Remove when a dialog is shown in ui and this is not needed anymore
    val latestSocketCloseReason: Flow<D2mSocketCloseReason?>

    @WorkerThread
    suspend fun activate(
        deviceLabel: String,
        contactService: ContactService, // TODO(ANDR-2519): remove
        userService: UserService, // TODO(ANDR-2519): remove
        fsMessageProcessor: ForwardSecurityMessageProcessor, // TODO(ANDR-2519): remove
        taskCreator: TaskCreator,
    )

    /**
     * Deactivate multi device:
     * - drop all (including own) devices from device group
     * - delete dgk
     * - reconnect to chat server
     * - reactivate fs TODO(ANDR-2519): Remove fs part
     *
     * NOTE: This method should not be invoked from within a task as the mediator will close the
     * connection when the own device is dropped. This might lead to unexpected behaviour.
     */
    @WorkerThread
    suspend fun deactivate(
        userService: UserService, // TODO(ANDR-2519): remove
        fsMessageProcessor: ForwardSecurityMessageProcessor, // TODO(ANDR-2519): remove
        taskCreator: TaskCreator,
    )

    @WorkerThread
    suspend fun setDeviceLabel(deviceLabel: String)

    /**
     * Start linking of a new device with a device join offer uri.
     * The returned flow emits the current status of the linking process.
     * To abort the linking process, the coroutine performing the linking
     * should be cancelled.
     */
    @WorkerThread
    suspend fun linkDevice(
        deviceJoinOfferUri: String,
        taskCreator: TaskCreator,
    ): Flow<DeviceLinkingStatus>

    /**
     * Remove all _other_ devices from the device group
     * TODO(ANDR-2717): Remove, as it is only used for development
     */
    suspend fun purge(taskCreator: TaskCreator)

    @AnyThread
    suspend fun loadLinkedDevicesInfo(taskCreator: TaskCreator): List<String>
}
