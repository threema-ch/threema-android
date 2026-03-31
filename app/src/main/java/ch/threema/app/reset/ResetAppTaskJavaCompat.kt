package ch.threema.app.reset

import android.content.Context
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("ResetAppTaskJavaCompat")

/**
 * This class only exists to make it possible to use [ResetAppTask] from Java.
 */
class ResetAppTaskJavaCompat(
    private val appContext: Context,
    private val resetAppTask: ResetAppTask,
    private val dispatcherProvider: DispatcherProvider,
) {
    fun executeAsync() {
        CoroutineScope(dispatcherProvider.worker).launch {
            execute()
        }
    }

    private suspend fun execute() {
        try {
            resetAppTask.execute()
        } catch (e: Exception) {
            logger.error("Failed to reset app", e)
            appContext.showToast(R.string.an_error_occurred, ToastDuration.LONG)

            // Deletion may have partially succeeded, so the app may be in an inconsistent state now.
            // Therefore, it is safer to close the app entirely.
            exitProcess(0)
        }
    }
}
