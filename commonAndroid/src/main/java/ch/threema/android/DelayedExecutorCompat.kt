package ch.threema.android

import android.os.Build
import ch.threema.common.DispatcherProvider
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class DelayedExecutorCompat(
    val delay: Duration,
    val executor: Executor? = null,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.default,
) : Executor {
    override fun execute(command: Runnable?) {
        command ?: return
        CoroutineScope(dispatcherProvider.worker).launch {
            delay(delay)
            if (executor != null) {
                executor.execute(command)
            } else {
                command.run()
            }
        }
    }
}

fun createDelayedExecutor(delay: Duration, executor: Executor? = null): Executor =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // CompletableFuture.delayedExecutor is only available in Android SDK 31+, as it was added in Java 9
        CompletableFuture.delayedExecutor(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS, executor)
    } else {
        DelayedExecutorCompat(delay, executor)
    }

@JvmOverloads
fun createDelayedExecutor(delayMs: Long, executor: Executor? = null) =
    createDelayedExecutor(delayMs.milliseconds, executor)
