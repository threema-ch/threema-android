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

package ch.threema.domain.protocol.connection.socket

import androidx.annotation.WorkerThread
import ch.threema.domain.protocol.connection.PipeSink
import ch.threema.domain.protocol.connection.PipeSource
import ch.threema.domain.protocol.connection.ServerConnectionException
import kotlinx.coroutines.Deferred
import java.net.Socket

internal interface ServerSocket : PipeSource<ByteArray>, PipeSink<ByteArray> {

    val address: String?

    val closedSignal: Deferred<ServerSocketCloseReason>

    /**
     * Connect the underlying [Socket].
     * If the underlying [Socket] is already connected, it will be closed and a new [Socket] will be
     * created.
     */
    @WorkerThread
    @Throws(Exception::class)
    fun connect()

    /**
     * Process io of the underlying socket.
     * This will read data received by the underlying socket and make it available via the output pipe.
     * [ByteArray]s written to the input pipe will be sent using the underlying socket.
     *
     * This Method will only return if the processing has either been cancelled exceptionally or by
     * calling [close]
     */
    @WorkerThread
    @Throws(Exception::class)
    suspend fun processIo()

    /**
     * Close the underlying socket. If the [ServerSocket] is reconnected, a new underlying socket will be created.
     */
    @WorkerThread
    @Throws(Exception::class)
    fun close(reason: ServerSocketCloseReason)
}

open class ServerSocketException(msg: String?) : ServerConnectionException(msg)
