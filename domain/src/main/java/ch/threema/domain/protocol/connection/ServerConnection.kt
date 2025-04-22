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

/**
 * The [ServerConnection] connects to the server used for the exchange of messages. Different types
 * of servers (e.g. CSP-Server, Mediator-Server) will have different implementations.
 *
 * This interface only defines, how the connection is started, stopped and monitored. It does not
 * define how actual messages are exchanged with the server. This can be handled differently depending
 * on the implementation.
 */
interface ServerConnection {
    val isRunning: Boolean

    val connectionState: ConnectionState

    val isNewConnectionSession: Boolean

    /**
     * Disable the connection to attempt a reconnect in this session.
     * If a new connection session is started (e.g. [start] or the app is restarted) the
     * flag is reset.
     */
    fun disableReconnect()

    /**
     * Start the connection. The connection must handle sending and receiving of messages in an own thread.
     */
    fun start()

    /**
     * Stop the connection and wait for processing to terminate.
     *
     * There won't be an attempt to reconnect after the connection has been stopped by this method.
     *
     * This is a blocking call and should only be called
     * from a worker thread, not from the main thread.
     */
    @WorkerThread
    @Throws(InterruptedException::class)
    fun stop()

    fun addConnectionStateListener(listener: ConnectionStateListener)
    fun removeConnectionStateListener(listener: ConnectionStateListener)
}
