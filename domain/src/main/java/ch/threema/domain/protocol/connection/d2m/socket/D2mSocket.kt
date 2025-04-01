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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.CoroutineContext

private val logger = ConnectionLoggingUtil.getConnectionLogger("D2mSocket")

internal class D2mSocket(
    private val okHttpClient: OkHttpClient,
    private val addressProvider: D2mServerAddressProvider,
    ioProcessingStoppedSignal: CompletableDeferred<Unit>,
    inputDispatcher: CoroutineContext
) : BaseSocket(ioProcessingStoppedSignal, inputDispatcher) {
    private val inboundQueue = Channel<ByteArray>(Channel.UNLIMITED)

    private var _address: String? = null
    override val address: String? = _address

    private val connectedSignal = CompletableDeferred<Unit>()

    private var webSocket: WebSocket? = null
    private val webSocketListener = object : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger.debug("WebSocket closed: code={}, reason={}", code, reason)
            ioJob?.cancel()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            logger.debug("WebSocket closing: code={}, reason={}", code, reason)
            val closeCode = D2mCloseCode(code, reason)
            close(D2mSocketCloseReason(reason, closeCode))
            ioProcessingStoppedSignal.completeExceptionally(
                D2mSocketCloseException(
                    "WebSocket closing",
                    closeCode
                )
            )
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger.warn("WebSocket failure", t)
            ioJob?.cancel()
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

    override fun connect() {
        ioProcessingStopped = false

        val url = addressProvider.get()

        logger.info("Connecting to {} ...", url)

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, webSocketListener)
        runBlocking { connectedSignal.await() }
    }

    override fun closeSocket(reason: ServerSocketCloseReason) {
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
        while (!ioProcessingStopped) {
            val data = inboundQueue.receive()
            logger.debug("Received {} bytes", data.size)

            // Send the received data inbound. Only carry on receiving data again after the
            // sending has completed.
            sendInbound(data)
        }
    }
}
