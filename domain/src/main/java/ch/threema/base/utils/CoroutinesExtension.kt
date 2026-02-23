package ch.threema.base.utils

import androidx.annotation.AnyThread
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

private val logger = getThreemaLogger("CoroutinesExtension")

/**
 *  Merges all exceptions from the default completed exceptionally state and the ones that could be thrown by [Deferred.getCompleted].
 *
 *  Note: If this [Deferred] was cancelled, [onCompletedExceptionally] will get called with the cancellation exception.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <R> Deferred<R>.onCompleted(
    @AnyThread onCompletedExceptionally: (throwable: Throwable) -> Unit,
    @AnyThread onCompletedNormally: (value: R) -> Unit,
) {
    invokeOnCompletion { throwable: Throwable? ->
        throwable?.let {
            onCompletedExceptionally(throwable)
            return@invokeOnCompletion
        }
        val completedValue = try {
            getCompleted()
        } catch (exception: Exception) {
            logger.error("Failed to complete deferred", exception)
            onCompletedExceptionally(exception)
            return@invokeOnCompletion
        }
        onCompletedNormally(completedValue)
    }
}
