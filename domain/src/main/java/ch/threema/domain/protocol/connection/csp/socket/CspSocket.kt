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

package ch.threema.domain.protocol.connection.csp.socket

import ch.threema.domain.protocol.connection.socket.BaseSocket
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.socket.ServerSocketException
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.io.DataInputStream
import java.io.OutputStream
import java.net.Inet6Address
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

private val logger = ConnectionLoggingUtil.getConnectionLogger("CspSocket")

internal class CspSocket(
    private val socketFactory: SocketFactory,
    private val addressProvider: ChatServerAddressProvider,
    ioProcessingStoppedSignal: CompletableDeferred<Unit>,
    inputDispatcher: CoroutineContext,
) : BaseSocket(ioProcessingStoppedSignal, inputDispatcher) {
    private var _address: String? = null
    override val address: String? get() = _address

    private lateinit var socket: Socket

    override suspend fun connect() {
        if (this::socket.isInitialized && !socket.isClosed) {
            logger.trace("Close socket (Connect)")
            socket.close()
        }

        addressProvider.update()

        val address =
            addressProvider.get() ?: throw ServerSocketException("No server address available")

        ioProcessingStopped = false

        logger.info("Connecting to {} ...", address)
        socket = socketFactory.makeSocket(address)
        socket.tcpNoDelay = true

        val timeout = when (address.address) {
            is Inet6Address -> ProtocolDefines.CONNECT_TIMEOUT_IPV6 * 1000
            else -> ProtocolDefines.CONNECT_TIMEOUT * 1000
        }
        socket.connect(address, timeout)

        _address = address.toString()
    }

    override suspend fun closeSocket(reason: ServerSocketCloseReason) {
        if (this::socket.isInitialized) {
            closeInbound(reason)
            // We await the write and read jobs. Note that if we do not await the write job, the
            // read job won't be cancelable.
            writeJob?.cancelAndJoin()
            readJob?.cancelAndJoin()
            if (!socket.isClosed) {
                logger.trace("Close socket (reason={})", reason)
                withContext(Dispatchers.IO) { socket.close() }
                _address = null
            }
            logger.info("Socket is closed")
        } else {
            logger.info("Socket is not initialized. Ignore closing.")
        }
    }

    /**
     * Set the socket SO_TIMEOUT.
     *
     * This has no effect if the underlying socket has not been created e.g. by a call to [connect]
     *
     * @see Socket.setSoTimeout
     */
    fun setSocketSoTimeout(timeout: Int) {
        if (this::socket.isInitialized) {
            socket.soTimeout = timeout
        }
    }

    /**
     * Move the internal pointer to the next available address.
     * The pointer will wrap around if the last available address is reached.
     */
    fun advanceAddress() {
        addressProvider.advance()
    }

    override suspend fun setupReading() {
        val dis = withContext(Dispatchers.IO) { DataInputStream(socket.getInputStream()) }
        dis.use {
            try {
                readInput(it)
            } finally {
                logger.info("Reading stopped")
            }
        }
    }

    private suspend fun readInput(dis: DataInputStream) {
        // Expect handshake messages first
        // `server-hello`: https://clients.pages.threema.dev/protocols/threema-protocols/structbuf/csp/#m:handshake:server-hello
        sendInbound(readNBytes(dis, ProtocolDefines.SERVER_HELLO_LEN))
        // `login-ack`: https://clients.pages.threema.dev/protocols/threema-protocols/structbuf/csp/#m:handshake:login-ack
        sendInbound(readNBytes(dis, ProtocolDefines.SERVER_LOGIN_ACK_LEN))

        while (!ioProcessingStopped) {
            val length = ByteBuffer.wrap(readNBytes(dis, 2))
                .order(ByteOrder.LITTLE_ENDIAN)
                .short.toUShort().toInt()
            val data = readNBytes(dis, length)

            // Send the received data inbound. Only carry on receiving data again after the
            // sending has completed.
            sendInbound(data)
        }
    }

    override suspend fun setupWriting() {
        val outputStream = runInterruptible(Dispatchers.IO) { socket.getOutputStream() }
        outputStream.use {
            try {
                writeOutput(it)
            } finally {
                logger.info("Writing stopped")
            }
        }
    }

    private suspend fun writeOutput(outputStream: OutputStream) {
        while (!ioProcessingStopped) {
            val data = outbound.take()
            logger.debug("Write {} bytes to output", data.size)
            runInterruptible(Dispatchers.IO) {
                outputStream.write(data)
                outputStream.flush()
            }
        }
    }

    private suspend fun readNBytes(dis: DataInputStream, n: Int): ByteArray {
        logger.debug("Read {} bytes from input", n)
        return if (n == 0) {
            ByteArray(0)
        } else {
            runInterruptible(Dispatchers.IO) {
                val bytes = ByteArray(n)
                dis.readFully(bytes)
                bytes
            }
        }
    }
}
