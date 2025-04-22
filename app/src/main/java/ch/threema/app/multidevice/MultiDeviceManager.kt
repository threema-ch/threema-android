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
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.multidevice.unlinking.DropDeviceResult
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.DeactivateMultiDeviceTask
import ch.threema.app.tasks.TaskCreator
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.protobuf.d2d.MdD2D
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow

interface MultiDeviceManager {
    // TODO(ANDR-2519): Remove when md allows fs
    val isMdDisabledOrSupportsFs: Boolean

    val isMultiDeviceActive: Boolean

    val propertiesProvider: MultiDevicePropertyProvider

    val socketCloseListener: D2mSocketCloseListener

    /**
     * Deactivate multi device:
     * - drop all (including own) devices from device group
     * - delete dgk
     * - reconnect to chat server
     * - reactivate fs TODO(ANDR-2519): Remove fs part
     *
     * NOTE: This method must be run from within a task, e.g. [DeactivateMultiDeviceTask].
     */
    @WorkerThread
    suspend fun deactivate(serviceManager: ServiceManager, handle: ActiveTaskCodec)

    @WorkerThread
    suspend fun setDeviceLabel(deviceLabel: String)

    /**
     * Start linking of a new device with a device join offer uri.
     * The returned flow emits the current status of the linking process.
     * To abort the linking process, the coroutine performing the linking
     * should be cancelled.
     *
     * @return A **cold** flow emitting the linking state changes.
     */
    @WorkerThread
    suspend fun linkDevice(
        serviceManager: ServiceManager,
        deviceJoinOfferUri: String,
        taskCreator: TaskCreator,
    ): Flow<DeviceLinkingStatus>

    /**
     * Drop a single device by their [deviceId] from the current device group.
     *
     * @param deviceId It is save to call this with a [DeviceId] that might not actually
     * be part of the current device group anymore.
     *
     * @param timeout Determines how long the task for dropping the devices can
     * take before it is cancelled. The follow up task of re-loading the remaining
     * devices can run indefinitely. Use [Duration.INFINITE] to disable any timeout.
     *
     * @return [DropDeviceResult.Success] containing all remaining  linked devices
     * **excluding** our device, [DropDeviceResult.Failure] otherwise.
     */
    @WorkerThread
    suspend fun dropDevice(
        deviceId: DeviceId,
        taskCreator: TaskCreator,
        timeout: Duration = 10.seconds,
    ): DropDeviceResult

    /**
     *  @return A list of all **other** devices in the current device group.
     */
    @AnyThread
    suspend fun loadLinkedDevices(taskCreator: TaskCreator): Result<List<LinkedDevice>>

    @AnyThread
    suspend fun setProperties(persistedProperties: PersistedMultiDeviceProperties?)

    fun reconnect()

    // TODO(ANDR-2519): Remove when md allows fs
    @WorkerThread
    suspend fun disableForwardSecurity(
        handle: ActiveTaskCodec,
        contactService: ContactService,
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor,
        taskCreator: TaskCreator,
    )

    companion object {
        /**
         * This defines the oldest version of the d2d protocol the android client would link to.
         */
        val minimumSupportedD2dProtocolVersion = MdD2D.ProtocolVersion.V0_2

        const val DEVICE_JOIN_OFFER_URI_PREFIX: String = "threema://device-group/join"
    }
}
