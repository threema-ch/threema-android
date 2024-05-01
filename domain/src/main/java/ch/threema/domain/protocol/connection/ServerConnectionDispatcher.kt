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

package ch.threema.domain.protocol.connection

import ch.threema.base.concurrent.TrulySingleThreadExecutorThreadFactory
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

private val logger = ConnectionLoggingUtil.getConnectionLogger("ServerConnectionDispatcher")

internal interface ServerConnectionDispatcher {
    fun interface ExceptionHandler {
        fun handleException(throwable: Throwable)
    }

    var exceptionHandler: ExceptionHandler?

    val coroutineContext: CoroutineContext

    fun assertDispatcherContext()

    /**
     * Close the dispatcher and shutdown the executor. Beware that the [coroutineContext] cannot be used
     * after this call.
     */
    fun close()
}

internal class SingleThreadedServerConnectionDispatcher(private val assertContext: Boolean) : ServerConnectionDispatcher {

    private companion object {
        var THREADS_CREATED = 0
    }

    private lateinit var thread: Thread

    private var exceptionHandlerReference: WeakReference<ServerConnectionDispatcher.ExceptionHandler>? = null
    override var exceptionHandler: ServerConnectionDispatcher.ExceptionHandler?
        get() = exceptionHandlerReference?.get()
        set(value) {
            exceptionHandlerReference = WeakReference(value)
        }

    private val dispatcher: ExecutorCoroutineDispatcher
    override val coroutineContext: CoroutineContext

    init {
        val factory = TrulySingleThreadExecutorThreadFactory("ServerConnectionWorker-${THREADS_CREATED++}") {
            thread = it
        }
        dispatcher = Executors.newSingleThreadExecutor(factory).asCoroutineDispatcher()

        val handler = CoroutineExceptionHandler { _, throwable -> exceptionHandler?.handleException(throwable) }
        coroutineContext = dispatcher.plus(handler)
    }

    override fun assertDispatcherContext() {
        if (assertContext) {
            val actual = Thread.currentThread()
            if (actual !== thread) {
                val msg = "Thread mismatch, expected '${thread.name}', got '${actual.name}'"
                logger.error(msg)
                throw Error(msg)
            }
        }
    }

    override fun close() {
        logger.info("Close connection dispatcher")
        dispatcher.close()
    }
}
