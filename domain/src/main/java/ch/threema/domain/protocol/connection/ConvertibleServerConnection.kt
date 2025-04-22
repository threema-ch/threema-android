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

package ch.threema.domain.protocol.connection

import androidx.annotation.WorkerThread
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import java8.util.function.Supplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val logger = ConnectionLoggingUtil.getConnectionLogger("ConvertibleServerConnection")

/**
 * A wrapper that can handle changing connections. Every time the connection is started the used
 * connection is retrieved by calling [Supplier.get] of the [connectionSupplier].
 *
 * The [connectionSupplier] is responsible to provide a [ServerConnection] that can be used without
 * interfering with the previous connection.
 */
open class ConvertibleServerConnection(
    private val connectionSupplier: Supplier<ServerConnection>,
) : ServerConnection, ConnectionStateListener, ReconnectableServerConnection {
    private val connectionStateListeners = mutableSetOf<ConnectionStateListener>()

    private var connection: ServerConnection? = null

    override val isRunning: Boolean
        get() = connection?.isRunning ?: false

    override val connectionState: ConnectionState
        get() = connection?.connectionState ?: ConnectionState.DISCONNECTED

    override val isNewConnectionSession: Boolean
        get() = connection?.isNewConnectionSession ?: true

    override fun disableReconnect() {
        connection?.disableReconnect()
    }

    override fun start() {
        logger.debug("Start")

        if (connection?.isRunning == true) {
            logger.warn("Connection is already running")
            return
        }

        if (!connection.let { it == null || it.connectionState == ConnectionState.DISCONNECTED }) {
            logger.warn("Connection is neither new nor disconnected. Abort connecting.")
            return
        }

        connectionSupplier.get().also { newConnection ->
            if (newConnection != connection) {
                logger.debug("Connection has changed")

                // Drop and stop old connection
                connection?.let { oldConnection ->
                    oldConnection.removeConnectionStateListener(this)

                    logger.debug("Stopping old connection asynchronously")
                    CoroutineScope(Dispatchers.IO).launch {
                        oldConnection.stop()
                        logger.debug("Old connection stopped")
                    }
                }

                // Register new connection
                newConnection.addConnectionStateListener(this)
                connection = newConnection
            }
        }.start()
    }

    @WorkerThread
    @Throws(InterruptedException::class)
    override fun stop() {
        logger.debug("Stop")

        synchronized(this) {
            connection?.stop()
        }
    }

    @WorkerThread
    @Throws(InterruptedException::class)
    override fun reconnect() {
        synchronized(this) {
            logger.info("Reconnect")
            stop()
            start()
        }
    }

    override fun addConnectionStateListener(listener: ConnectionStateListener) {
        synchronized(connectionStateListeners) {
            connectionStateListeners.add(listener)
        }
    }

    override fun removeConnectionStateListener(listener: ConnectionStateListener) {
        synchronized(connectionStateListeners) {
            connectionStateListeners.remove(listener)
        }
    }

    override fun updateConnectionState(connectionState: ConnectionState?) {
        synchronized(connectionStateListeners) {
            connectionStateListeners.forEach { listener ->
                try {
                    listener.updateConnectionState(connectionState)
                } catch (e: Exception) {
                    logger.warn("Exception while invoking connection state listener", e)
                }
            }
        }
    }
}
