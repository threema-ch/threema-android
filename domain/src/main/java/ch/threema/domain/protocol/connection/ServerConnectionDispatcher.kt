package ch.threema.domain.protocol.connection

import ch.threema.base.concurrent.TrulySingleThreadExecutorThreadFactory
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

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

internal class SingleThreadedServerConnectionDispatcher(private val assertContext: Boolean) :
    ServerConnectionDispatcher {
    private companion object {
        var threadsCreated = 0
    }

    private lateinit var thread: Thread

    private var exceptionHandlerReference: WeakReference<ServerConnectionDispatcher.ExceptionHandler>? =
        null
    override var exceptionHandler: ServerConnectionDispatcher.ExceptionHandler?
        get() = exceptionHandlerReference?.get()
        set(value) {
            exceptionHandlerReference = WeakReference(value)
        }

    private val dispatcher: ExecutorCoroutineDispatcher
    override val coroutineContext: CoroutineContext

    init {
        val factory =
            TrulySingleThreadExecutorThreadFactory("ServerConnectionWorker-${threadsCreated++}") {
                thread = it
            }
        dispatcher = Executors.newSingleThreadExecutor(factory).asCoroutineDispatcher()

        val handler =
            CoroutineExceptionHandler { _, throwable -> exceptionHandler?.handleException(throwable) }
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
