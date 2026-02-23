package ch.threema.app.voip.groupcall

import ch.threema.app.BuildConfig
import ch.threema.base.concurrent.TrulySingleThreadExecutorThreadFactory
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*

class GroupCallThreadUtil {
    interface ExceptionHandler {
        fun handle(t: Throwable)
    }

    companion object {
        var exceptionHandler: ExceptionHandler? = null
        val dispatcher: CoroutineContext
        lateinit var thread: Thread

        init {
            val factory = TrulySingleThreadExecutorThreadFactory("GroupCallWorker") {
                thread = it
            }
            val handler = CoroutineExceptionHandler { _, exception ->
                exceptionHandler?.handle(exception) ?: throw exception
            }
            dispatcher = Executors.newSingleThreadExecutor(factory).asCoroutineDispatcher().plus(handler)
        }

        fun assertDispatcherThread() {
            if (BuildConfig.DEBUG) {
                val actual = Thread.currentThread()
                if (actual !== thread) {
                    throw Error("Thread mismatch, expected '${thread.name}', got '${actual.name}'")
                }
            }
        }
    }
}
