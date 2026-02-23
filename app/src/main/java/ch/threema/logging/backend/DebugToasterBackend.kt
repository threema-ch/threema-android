package ch.threema.logging.backend

import android.content.Context
import android.util.Log
import android.widget.Toast
import ch.threema.app.utils.RuntimeUtil
import ch.threema.logging.LogLevel
import org.slf4j.helpers.MessageFormatter

class DebugToasterBackend(
    private val getContext: () -> Context?,
    @LogLevel
    private val minLogLevel: Int = Log.ERROR,
) : LogBackend {
    override fun isEnabled(@LogLevel level: Int): Boolean =
        level >= minLogLevel && !isRunningInUITest

    override fun print(@LogLevel level: Int, tag: String, throwable: Throwable?, message: String?) {
        if (isEnabled(level)) {
            showToast(
                message = createMessage(
                    tag = tag,
                    throwable = throwable,
                    message = message,
                ),
            )
        }
    }

    override fun print(@LogLevel level: Int, tag: String, throwable: Throwable?, messageFormat: String, vararg args: Any?) {
        if (isEnabled(level)) {
            showToast(
                message = createMessage(
                    tag = tag,
                    throwable = throwable,
                    message = MessageFormatter.arrayFormat(messageFormat, args).message,
                ),
            )
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

    private fun showToast(message: String) {
        getContext()?.let { context ->
            RuntimeUtil.runOnUiThread {
                try {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    // do nothing here, we tried our best
                }
            }
        }
    }

    private val isRunningInUITest: Boolean by lazy {
        try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
