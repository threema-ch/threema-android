package ch.threema.app.utils.executor

import ch.threema.app.utils.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * This can be used as a drop-in replacement for [BackgroundExecutor] to make use of coroutines.
 */
class CoroutineBackgroundExecutor(
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun <R> execute(backgroundTask: BackgroundTask<R>) = withContext(dispatcherProvider.main) {
        backgroundTask.runBefore()
        val result = withContext(dispatcherProvider.worker) {
            backgroundTask.runInBackground()
        }
        backgroundTask.runAfter(result)
    }
}
