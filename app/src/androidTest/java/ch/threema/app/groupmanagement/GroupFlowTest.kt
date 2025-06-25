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

package ch.threema.app.groupmanagement

import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ConnectionStateListener
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.taskmanager.TaskManager

enum class SetupConfig {
    MULTI_DEVICE_ENABLED,
    MULTI_DEVICE_DISABLED,
}

enum class ReflectionExpectation(val setupConfig: SetupConfig) {
    /**
     * In case multi device is enabled and the reflection is expected to succeed.
     */
    REFLECTION_SUCCESS(SetupConfig.MULTI_DEVICE_ENABLED),

    /**
     * In case multi device is enabled and the reflection is expected to fail due to an unfulfilled
     * precondition.
     */
    REFLECTION_FAIL(SetupConfig.MULTI_DEVICE_ENABLED),

    /**
     * In case multi device is disabled and the reflection is expected to be skipped.
     */
    REFLECTION_SKIPPED(SetupConfig.MULTI_DEVICE_DISABLED),
}

abstract class GroupFlowTest {
    protected val serviceManager by lazy { ThreemaApplication.requireServiceManager() }

    protected val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    protected val groupModelRepository by lazy { serviceManager.modelRepositories.groups }

    private val testMultiDeviceManagerEnabled by lazy {
        TestMultiDeviceManager(
            isMdDisabledOrSupportsFs = false,
            isMultiDeviceActive = true,
        )
    }

    private val testMultiDeviceManagerDisabled by lazy {
        TestMultiDeviceManager(
            isMdDisabledOrSupportsFs = true,
            isMultiDeviceActive = false,
        )
    }

    protected fun getGroupFlowDispatcher(
        setupConfig: SetupConfig,
        taskManager: TaskManager,
        connection: ServerConnection = ConnectionLoggedIn,
    ) = GroupFlowDispatcher(
        serviceManager.modelRepositories.contacts,
        serviceManager.modelRepositories.groups,
        serviceManager.contactService,
        serviceManager.groupService,
        serviceManager.groupCallManager,
        serviceManager.userService,
        serviceManager.contactStore,
        serviceManager.identityStore,
        serviceManager.forwardSecurityMessageProcessor,
        serviceManager.nonceFactory,
        serviceManager.blockedIdentitiesService,
        serviceManager.preferenceService,
        when (setupConfig) {
            SetupConfig.MULTI_DEVICE_ENABLED -> testMultiDeviceManagerEnabled
            SetupConfig.MULTI_DEVICE_DISABLED -> testMultiDeviceManagerDisabled
        },
        serviceManager.apiService,
        serviceManager.apiConnector,
        serviceManager.fileService,
        serviceManager.databaseService,
        taskManager,
        connection,
    )

    data object ConnectionDisconnected : ServerConnection {

        override val isRunning: Boolean = false

        override val connectionState: ConnectionState = ConnectionState.DISCONNECTED

        override val isNewConnectionSession: Boolean = false

        override fun disableReconnect() {}

        override fun start() {}

        override fun stop() {}

        override fun addConnectionStateListener(listener: ConnectionStateListener) {}

        override fun removeConnectionStateListener(listener: ConnectionStateListener) {}
    }

    data object ConnectionLoggedIn : ServerConnection {

        override val isRunning: Boolean = true

        override val connectionState: ConnectionState = ConnectionState.LOGGEDIN

        override val isNewConnectionSession: Boolean = true

        override fun disableReconnect() {}

        override fun start() {}

        override fun stop() {}

        override fun addConnectionStateListener(listener: ConnectionStateListener) {}

        override fun removeConnectionStateListener(listener: ConnectionStateListener) {}
    }
}
