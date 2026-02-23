package ch.threema.domain.taskmanager

import ch.threema.base.concurrent.TrulySingleThreadExecutorThreadFactory
import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

private val logger = getThreemaLogger("TaskManagerDispatcher")

internal interface TaskManagerDispatcherAsserter {
    fun assertDispatcherContext()
}

internal interface TaskManagerDispatcher : TaskManagerDispatcherAsserter {
    val coroutineContext: CoroutineContext
}

internal class SingleThreadedTaskManagerDispatcher(
    private val assertContext: Boolean,
    threadName: String,
) : TaskManagerDispatcher {
    private lateinit var thread: Thread

    private val dispatcher: ExecutorCoroutineDispatcher
    override val coroutineContext: CoroutineContext

    init {
        val factory = TrulySingleThreadExecutorThreadFactory(threadName) {
            thread = it
        }

        dispatcher = Executors.newSingleThreadExecutor(factory).asCoroutineDispatcher()

        coroutineContext = dispatcher
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
}
