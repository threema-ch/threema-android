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
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.TaskCreator
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.InboundD2mMessage.DevicesInfo
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.protobuf.d2d.MdD2D
import kotlinx.coroutines.flow.Flow

interface MultiDeviceManager {
    // TODO(ANDR-2519): Remove when md allows fs
    val isMdDisabledOrSupportsFs: Boolean

    val isMultiDeviceActive: Boolean

    val propertiesProvider: MultiDevicePropertyProvider

    val socketCloseListener: D2mSocketCloseListener

    /**
     * Disable multi device locally. This requires that all devices of this device group including this device have been dropped. FS must be enabled
     * afterwards. Note that most likely callers of this method also want to restart the connection afterwards.
     */
    @WorkerThread
    fun removeMultiDeviceLocally(serviceManager: ServiceManager)

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
     *  @return A list of all **other** devices in the current device group.
     */
    @AnyThread
    suspend fun loadLinkedDevices(taskCreator: TaskCreator): Result<Map<DeviceId, DevicesInfo.AugmentedDeviceInfo>>

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

    // TODO(ANDR-2519): Remove when md allows fs
    @WorkerThread
    fun enableForwardSecurity(serviceManager: ServiceManager)

    companion object {
        /**
         * This defines the oldest version of the d2d protocol the android client would link to.
         */
        val minimumSupportedD2dProtocolVersion = MdD2D.ProtocolVersion.V0_2

        const val DEVICE_JOIN_OFFER_URI_PREFIX: String = "threema://device-group/join"
    }
}
