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

package ch.threema.domain.protocol.connection.d2m.socket

import ch.threema.domain.protocol.connection.socket.BaseSocket
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

private val logger = ConnectionLoggingUtil.getConnectionLogger("D2mSocket")

internal class D2mSocket(
    private val okHttpClient: OkHttpClient,
    private val addressProvider: D2mServerAddressProvider,
    ioProcessingStoppedSignal: CompletableDeferred<Unit>,
    inputDispatcher: CoroutineContext,
) : BaseSocket(ioProcessingStoppedSignal, inputDispatcher) {
    private val inboundQueue = Channel<ByteArray>(Channel.UNLIMITED)

    private var _address: String? = null
    override val address: String? = _address

    private val connectedSignal = CompletableDeferred<Unit>()

    private var webSocket: WebSocket? = null
    private val webSocketListener = object : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger.debug("WebSocket closed: code={}, reason={}", code, reason)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            logger.debug("WebSocket closing: code={}, reason={}", code, reason)
            val closeCode = D2mCloseCode(code, reason)
            close(D2mSocketCloseReason(reason, closeCode))
            ioProcessingStoppedSignal.completeExceptionally(
                D2mSocketCloseException(
                    "WebSocket closing",
                    closeCode,
                ),
            )
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger.warn("WebSocket failure", t)
            readJob?.cancel()
            writeJob?.cancel()
            connectedSignal.completeExceptionally(t)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            logger.debug("Text message received: {}", text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val result = inboundQueue.trySend(bytes.toByteArray())
            result.exceptionOrNull()?.let {
                logger.error("Error when receiving a message", it)
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            val url = response.request.url.toString()
            logger.info("Connected to {}", url)
            _address = url
            connectedSignal.complete(Unit)
        }
    }

    override suspend fun connect() {
        ioProcessingStopped = false

        val url = addressProvider.get()

        logger.info("Connecting to {} ...", url)

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, webSocketListener)
        connectedSignal.await()
    }

    override suspend fun closeSocket(reason: ServerSocketCloseReason) {
        // In case the read job is still active, we propagate the information that the connection is
        // stopping via closing the inbound queue. This ensures that the the close event does not
        // overtake some inbound message that is still being processed. In case the read job is not
        // running anymore, we propagate the close reason directly.
        if (readJob?.isActive == true) {
            // Close the inbound queue and await the read job to ensure the close event has been
            // propagated.
            inboundQueue.close(ServerSocketClosed(reason))
            readJob?.join()
        } else {
            closeInbound(reason)
        }

        webSocket?.let {
            if (it.close(D2mCloseCode.NORMAL, reason.msg)) {
                logger.trace("WebSocket shutdown initiated (reason={})", reason)
            }
            logger.debug("WebSocket shutdown underway or already closed")
        } ?: run {
            logger.debug("WebSocket is not initialized. Ignore closing")
        }
    }

    override suspend fun setupWriting() {
        try {
            writeOutput()
        } finally {
            logger.trace("Writing stopped")
        }
    }

    private suspend fun writeOutput() {
        while (!ioProcessingStopped) {
            val data = outbound.take()
            logger.debug("Write {} bytes to output", data.size)
            webSocket!!.send(data.toByteString())
        }
    }

    override suspend fun setupReading() {
        try {
            readInput()
        } finally {
            logger.trace("Reading stopped")
        }
    }

    private suspend fun readInput() {
        try {
            // We read from the input as long as possible. It is important that we continue reading
            // until the inbound queue has been closed - even if io processing is already
            // terminated. This ensures that received messages are still propagated towards the
            // task manager until the connection has been closed.
            while (true) {
                val data = inboundQueue.receive()
                logger.debug("Received {} bytes", data.size)

                // Send the received data inbound. Only carry on receiving data again after the
                // sending has completed.
                sendInbound(data)
            }
        } catch (e: ServerSocketClosed) {
            logger.debug("Inbound queue closed", e)
            closeInbound(e.serverSocketCloseReason)
        }
    }
}

private data class ServerSocketClosed(val serverSocketCloseReason: ServerSocketCloseReason) :
    Throwable()
