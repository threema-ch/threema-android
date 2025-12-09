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

package ch.threema.domain.protocol.rendezvous

import ch.threema.base.utils.getThreemaLogger
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

private val logger = getThreemaLogger("WebSocketRendezvousPath")

internal class WebSocketRendezvousPath(
    override val pid: UInt,
    private val okHttpClient: OkHttpClient,
    private val url: String,
) : RendezvousPath {
    private val _closedSignal = CompletableDeferred<Unit>()
    override val closedSignal: Deferred<Unit> = _closedSignal

    private val inboundQueue = Channel<ByteArray>(Channel.UNLIMITED)

    private val connectedSignal = CompletableDeferred<Unit>()

    private var webSocket: WebSocket? = null
    private val webSocketListener = object : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger.debug("WebSocket closed: code={}, reason={}", code, reason)
            _closedSignal.complete(Unit)
            inboundQueue.close()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            logger.debug("WebSocket closing: code={}, reason={}", code, reason)
            close()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger.warn("WebSocket failure", t)
            connectedSignal.completeExceptionally(t)
            inboundQueue.close(IOException("WebSocket failure", t))
            _closedSignal.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            logger.trace("Text message received: {}", text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            logger.trace("Byte message received (length={})", bytes.size)
            val result = inboundQueue.trySend(bytes.toByteArray())
            result.exceptionOrNull()?.let {
                logger.error("Error when receiving a message", it)
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            logger.info("Connected to {}", response.request.url)
            connectedSignal.complete(Unit)
        }
    }

    override fun close() {
        webSocket.let {
            if (it != null) {
                val initiated = it.close(1000, "Closed")
                logger.debug("Close rendezvous websocket (initiatedByThisCall={})", initiated)
            } else {
                logger.debug("Websocket is `null`, cannot close")
            }
        }
    }

    override suspend fun connect() {
        val request = Request.Builder()
            .url(url)
            .build()
        webSocket = okHttpClient.newWebSocket(request, webSocketListener)
        connectedSignal.await()
    }

    override suspend fun write(bytes: ByteArray) {
        val socket = webSocket ?: throw RendezvousException("Web socket is not initialized")
        if (!socket.send(bytes.toByteString())) {
            throw IOException("Could not write bytes")
        }
        while (socket.queueSize() > 0) {
            if (closedSignal.isCompleted) {
                throw IOException("Socket has been closed while writing")
            }
            // Delay before next check to make the method suspending
            delay(1)
        }
    }

    override suspend fun read(): ByteArray {
        logger.trace("Read from inbound queue")
        return try {
            inboundQueue.receive()
        } catch (e: ClosedReceiveChannelException) {
            throw IOException(e)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebSocketRendezvousPath) return false

        return url == other.url
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }
}
