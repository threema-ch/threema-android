package ch.threema.base.concurrent

import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.ThreadFactory

private val logger = getThreemaLogger("TrulySingleThreadExecutorThreadFactory")

class TrulySingleThreadExecutorThreadFactory(
    val name: String,
    val created: (Thread) -> Unit,
) : ThreadFactory {
    var thread: Thread? = null

    override fun newThread(runnable: Runnable): Thread {
        thread?.also {
            logger.error("Thread '{}' was already created", it.name)
        }
        return Thread(runnable, name).also {
            thread = it
            created(it)
        }
    }
}
