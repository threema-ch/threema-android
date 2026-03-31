package ch.threema.app.utils.executor

import android.os.Handler
import android.os.Looper
import ch.threema.android.Destroyable
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * A background executor that can be used to perform background work and afterwards update the UI.
 * Regarding usage, it similar to AsyncTasks.
 *
 * Note that if any exceptions are thrown in the [BackgroundTask], the completable deferred will be
 * completed exceptionally.
 *
 * See also [CoroutineBackgroundExecutor].
 */
class BackgroundExecutor : Destroyable {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Execute a task in this executor. The result of [BackgroundTask.runInBackground] is passed to
     * [BackgroundTask.runAfter]. Use [executeDeferred] if a deferred is needed to get the result of
     * the background task.
     */
    fun <R> execute(backgroundTask: BackgroundTask<R>) {
        executeDeferred(backgroundTask)
    }

    /**
     * Execute a task in this executor. The result of [BackgroundTask.runInBackground] is passed to
     * [BackgroundTask.runAfter] and it is also returned as a deferred.
     * If the deferred result is not used, use [execute] instead.
     *
     * @return a deferred that is completed normally after [BackgroundTask.runAfter] has been
     * executed and exceptionally if an exception has been thrown during execution
     */
    fun <R> executeDeferred(backgroundTask: BackgroundTask<R>): Deferred<R> {
        val completableDeferred = CompletableDeferred<R>()

        // Run background task initialization on the same thread as the caller
        try {
            backgroundTask.runBefore()
        } catch (e: Exception) {
            completableDeferred.completeExceptionally(e)
            return completableDeferred
        }

        executor.execute Executor@{
            // Execute background task in executor
            val result = try {
                backgroundTask.runInBackground()
            } catch (e: Exception) {
                completableDeferred.completeExceptionally(e)
                return@Executor
            }

            if (executor.isShutdown) {
                return@Executor
            }

            // Run final method on main thread and complete the deferred afterwards
            handler.post {
                try {
                    backgroundTask.runAfter(result)
                    completableDeferred.complete(result)
                } catch (e: Exception) {
                    completableDeferred.completeExceptionally(e)
                }
            }
        }

        return completableDeferred
    }

    override fun destroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdown()
    }
}

/**
 * A task that can be run with a [BackgroundExecutor] on a background thread.
 */
interface BackgroundTask<R> {
    /**
     * This method is run before [runInBackground] on the same thread as the callee of
     * [BackgroundExecutor.execute] is running.
     */
    fun runBefore() {
        // The default implementation does not need to do anything
    }

    /**
     * This method is run on the background thread. The result of it will be passed to [runAfter].
     */
    fun runInBackground(): R

    /**
     * This method is run on the UI thread after [runInBackground] has completed.
     * @param result the result from [runInBackground]
     */
    fun runAfter(result: R) {
        // The default implementation does not need to do anything
    }
}
