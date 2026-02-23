package ch.threema.logging.backend

import android.util.Log
import ch.threema.app.BuildConfig
import ch.threema.logging.LogLevel
import org.slf4j.helpers.MessageFormatter

/**
 * A logging backend that logs to the ADB logcat.
 */
class LogcatBackend(@field:LogLevel @param:LogLevel private val minLogLevel: Int) : LogBackend {
    override fun isEnabled(level: Int): Boolean {
        return level >= this.minLogLevel
    }

    override fun print(
        @LogLevel level: Int,
        tag: String,
        throwable: Throwable?,
        message: String?,
    ) {
        if (!isEnabled(level)) {
            return
        }
        // Prepend tag to message body to avoid the Android log tag length limit
        var messageBody = cleanTag(tag, STRIP_PREFIXES) + ": "
        if (message == null) {
            if (throwable != null) {
                messageBody += throwable.stackTraceToString()
            }
        } else {
            messageBody += message
            if (throwable != null) {
                messageBody += '\n' + throwable.stackTraceToString()
            }
        }
        Log.println(level, TAG, messageBody)
    }

    override fun print(
        @LogLevel level: Int,
        tag: String,
        throwable: Throwable?,
        messageFormat: String,
        vararg args: Any?,
    ) {
        if (!isEnabled(level)) {
            return
        }
        try {
            print(level, tag, throwable, message = MessageFormatter.arrayFormat(messageFormat, args).message)
        } catch (_: Exception) {
            print(level, tag, throwable, message = messageFormat)
        }
    }

    companion object {
        private const val TAG = BuildConfig.LOG_TAG

        /**
         * For tags starting with these prefixes, the package path is stripped
         */
        private val STRIP_PREFIXES = arrayOf(
            "ch.threema.app.",
            "ch.threema.domain.",
            "ch.threema.storage.",
        )
    }
}
