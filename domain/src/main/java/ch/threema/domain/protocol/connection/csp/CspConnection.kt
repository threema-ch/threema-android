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

package ch.threema.domain.protocol.connection.csp

import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.BaseServerConnection
import ch.threema.domain.protocol.connection.BaseServerConnectionConfiguration
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnectionDependencyProvider
import ch.threema.domain.protocol.connection.csp.socket.CspSocket
import ch.threema.domain.protocol.connection.csp.socket.HostResolver
import ch.threema.domain.protocol.connection.csp.socket.SocketFactory
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.domain.taskmanager.TaskManager
/**
 * Only this interface is exposed to other modules
 */
interface CspConnection

internal class CspConnectionImpl(
    dependencyProvider: ServerConnectionDependencyProvider
) : CspConnection, BaseServerConnection(dependencyProvider) {

    override fun onConnected() {
        socket.let {
            if (it is CspSocket) {
                it.setSocketSoTimeout(ProtocolDefines.READ_TIMEOUT * 1000)
            }
        }
    }

    override fun onCspAuthenticated() {
        socket.let {
            if (it is CspSocket) {
                it.setSocketSoTimeout(0)
            }
        }
    }

    override fun onException(t: Throwable) {
        if (connectionState != ConnectionState.LOGGEDIN) {
            socket.let {
                if (it is CspSocket) {
                    it.advanceAddress()
                }
            }
        }
    }
}

data class CspConnectionConfiguration(
    override val identityStore: IdentityStoreInterface,
    override val serverAddressProvider: ServerAddressProvider,
    override val version: Version,
    override val assertDispatcherContext: Boolean,
    override val deviceCookieManager: DeviceCookieManager,
    override val taskManager: TaskManager,
    val hostResolver: HostResolver,
    val ipv6: Boolean,
    val socketFactory: SocketFactory
) : BaseServerConnectionConfiguration
