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

package ch.threema.domain.protocol.connection.d2m

import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.BaseServerConnection
import ch.threema.domain.protocol.connection.BaseServerConnectionConfiguration
import ch.threema.domain.protocol.connection.ServerConnectionDependencyProvider
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.TaskManager
import okhttp3.OkHttpClient

/**
 * Only this interface is exposed to other modules
 */
interface D2mConnection

internal class D2mConnectionImpl(
    dependencyProvider: ServerConnectionDependencyProvider,
    private val closeListener: D2mSocketCloseListener
) : D2mConnection, BaseServerConnection(dependencyProvider) {

    override fun onSocketClosed(reason: ServerSocketCloseReason) {
        closeListener.onSocketClosed(reason)
    }
}

fun interface MultiDevicePropertyProvider {
    fun get(): MultiDeviceProperties
}

data class D2mConnectionConfiguration(
    override val identityStore: IdentityStoreInterface,
    override val serverAddressProvider: ServerAddressProvider,
    override val version: Version,
    override val assertDispatcherContext: Boolean,
    override val deviceCookieManager: DeviceCookieManager,
    override val incomingMessageProcessor: IncomingMessageProcessor,
    override val taskManager: TaskManager,
    val multiDevicePropertyProvider: MultiDevicePropertyProvider,
    val closeListener: D2mSocketCloseListener,
    val okHttpClient: OkHttpClient
) : BaseServerConnectionConfiguration
