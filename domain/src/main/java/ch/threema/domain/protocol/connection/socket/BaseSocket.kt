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

package ch.threema.domain.protocol.connection.socket

import ch.threema.domain.protocol.connection.InputPipe
import ch.threema.domain.protocol.connection.Pipe
import ch.threema.domain.protocol.connection.PipeHandler
import ch.threema.domain.protocol.connection.QueuedPipeHandler
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException
import kotlin.coroutines.CoroutineContext

private val logger = ConnectionLoggingUtil.getConnectionLogger("BaseSocket")

internal abstract class BaseSocket(
    protected val ioProcessingStoppedSignal: CompletableDeferred<Unit>,
    private val inputDispatcher: CoroutineContext
    ) : ServerSocket {

    private val inbound = InputPipe<ByteArray>()
    protected val outbound = QueuedPipeHandler<ByteArray>()

    override val closedSignal = CompletableDeferred<ServerSocketCloseReason>()

    final override val source: Pipe<ByteArray> = inbound
    final override val sink: PipeHandler<ByteArray> = outbound

    protected var ioJob: Job? = null

    /**
     * False, while the socket is connected and io should be processed. Set to true when no further data
     * received should be processed.
     */
    protected var ioProcessingStopped = true

    final override suspend fun processIo() {
        synchronized(this) {
            if (closedSignal.isCompleted) {
                throw ServerSocketException("The socket is already closed")
            }
            launchIoJob()
        }
        ioProcessingStoppedSignal.await()
    }

    private fun launchIoJob() {
        val errorHandler = CoroutineExceptionHandler { _, e ->
            if (e is CancellationException || e is InterruptedIOException) {
                logger.info("IO Processing cancelled")
                ioProcessingStoppedSignal.complete(Unit)
            } else {
                // do not log the exception as it will be logged elsewhere
                logger.warn("IO processing stopped exceptionally")
                ioProcessingStoppedSignal.completeExceptionally(e)
            }
        }
        ioJob = CoroutineScope(Dispatchers.Default + errorHandler).launch {
            launch { setupReading() }
            launch { setupWriting() }
        }.also { it.invokeOnCompletion { throwable ->
            val alreadyCompleted = !ioProcessingStoppedSignal.complete(Unit)
            val exceptionally = throwable != null
            logger.info(
                "IO job completed (exceptionally={}, alreadyCompleted={})",
                exceptionally,
                alreadyCompleted
            )
            closeSocketAndCompleteClosedSignal(ServerSocketCloseReason("IO processing has stopped (exceptionally=$exceptionally)"))
        } }
    }

    final override fun close(reason: ServerSocketCloseReason) {
        synchronized(this) {
            logger.info("Close ServerSocket (reason={})", reason)
            if (closedSignal.isCompleted) {
                logger.debug("Socket is already closed")
                return
            }
            // when the socket is closed, io processing is stopped
            ioProcessingStopped = true
            runBlocking { ioJob?.cancelAndJoin() }
            closeSocketAndCompleteClosedSignal(reason)
        }
    }

    /**
     * Perform the actual closing of the underlying socket and complete the socket closed signal.
     *
     * This should be called _after_ [ioJob] has completed.
     */
    private fun closeSocketAndCompleteClosedSignal(reason: ServerSocketCloseReason) {
        logger.info("Close actual socket")
        // set stopped, as depending on the code path it might not be set to stopped yet
        ioProcessingStopped = true
        closeSocket(reason)
        if (!closedSignal.complete(reason)) {
            logger.info("Close signal already completed")
        }
    }

    protected abstract suspend fun setupReading()
    protected abstract suspend fun setupWriting()
    protected abstract fun closeSocket(reason: ServerSocketCloseReason)

    /**
     * Send an inbound message. This method only returns after the sending inbound has been completed.
     * This does not necessarily mean that the message has already been completed, as it might be processed
     * by an asynchronous task manager.
     * In this case it only means that the parsed message has been passed over to the task manager.
     */
    protected suspend fun sendInbound(data: ByteArray) {
        withContext(inputDispatcher) {
            inbound.send(data)
        }
    }
}
