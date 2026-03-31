package ch.threema.logging.backend

import android.content.Context
import android.util.Log
import ch.threema.android.showToast
import ch.threema.logging.LogLevel
import org.slf4j.helpers.MessageFormatter

class DebugToasterBackend(
    private val appContext: Context,
) : LogBackend {
    override fun isEnabled(@LogLevel level: Int): Boolean =
        level >= Log.ERROR

    override fun print(@LogLevel level: Int, tag: String, throwable: Throwable?, message: String?) {
        printIfNeeded(level, tag, throwable) {
            message
        }
    }

    override fun print(@LogLevel level: Int, tag: String, throwable: Throwable?, messageFormat: String, args: Array<Any?>) {
        printIfNeeded(level, tag, throwable) {
            MessageFormatter.arrayFormat(messageFormat, args).message
        }
    }

    private fun printIfNeeded(@LogLevel level: Int, tag: String, throwable: Throwable?, message: () -> String?) {
        if (!isEnabled(level) || tag in IGNORED_TAGS) {
            return
        }
        try {
            appContext.showToast(
                message = createMessage(
                    tag = tag,
                    throwable = throwable,
                    message = message(),
                ),
            )
        } catch (_: IllegalAccessException) {
            // appContext might not be a visual context, i.e., it might not have access to a WindowManager and thus can't show toasts.
            // In this case we just catch and ignore the exception, as there's nothing we can do.
        }
    }

    private fun createMessage(tag: String, throwable: Throwable?, message: String?): String {
        val detail = when {
            message != null -> message
            throwable != null -> throwable.stackTraceToString()
            else -> tag
        }
        return "❗ $detail"
    }

    companion object {
        private val IGNORED_TAGS = arrayOf(
            "ch.threema.libwebrtc",
        )
    }
}
