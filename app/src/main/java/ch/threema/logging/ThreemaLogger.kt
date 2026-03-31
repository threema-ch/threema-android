package ch.threema.logging

import android.util.Log
import ch.threema.logging.backend.LogBackend
import org.slf4j.helpers.MarkerIgnoringBase

class ThreemaLogger(
    private val tag: String,
    private val backends: List<LogBackend>,
) : MarkerIgnoringBase() {

    var prefix: String? = null

    override fun isTraceEnabled() = true

    override fun isDebugEnabled() = true

    override fun isInfoEnabled() = true

    override fun isWarnEnabled() = true

    override fun isErrorEnabled() = true

    override fun trace(msg: String?) {
        print(Log.VERBOSE, null, msg)
    }

    override fun trace(format: String, arg: Any?) {
        print(Log.VERBOSE, format, arrayOf(arg))
    }

    override fun trace(format: String, arg1: Any?, arg2: Any?) {
        print(Log.VERBOSE, format, arrayOf(arg1, arg2))
    }

    override fun trace(format: String, vararg arguments: Any?) {
        print(Log.VERBOSE, format, arrayOf(*arguments))
    }

    override fun trace(msg: String?, t: Throwable?) {
        print(Log.VERBOSE, t, msg)
    }

    override fun debug(msg: String?) {
        print(Log.DEBUG, null, msg)
    }

    override fun debug(format: String, arg: Any?) {
        print(Log.DEBUG, format, arrayOf(arg))
    }

    override fun debug(format: String, arg1: Any?, arg2: Any?) {
        print(Log.DEBUG, format, arrayOf(arg1, arg2))
    }

    override fun debug(format: String, vararg arguments: Any?) {
        print(Log.DEBUG, format, arrayOf(*arguments))
    }

    override fun debug(msg: String?, t: Throwable?) {
        print(Log.DEBUG, t, msg)
    }

    override fun info(msg: String?) {
        print(Log.INFO, null, msg)
    }

    override fun info(format: String, arg: Any?) {
        print(Log.INFO, format, arrayOf(arg))
    }

    override fun info(format: String, arg1: Any?, arg2: Any?) {
        print(Log.INFO, format, arrayOf(arg1, arg2))
    }

    override fun info(format: String, vararg arguments: Any?) {
        print(Log.INFO, format, arrayOf(*arguments))
    }

    override fun info(msg: String?, t: Throwable?) {
        print(Log.INFO, t, msg)
    }

    override fun warn(msg: String?) {
        print(Log.WARN, null, msg)
    }

    override fun warn(format: String, arg: Any?) {
        print(Log.WARN, format, arrayOf(arg))
    }

    override fun warn(format: String, arg1: Any?, arg2: Any?) {
        print(Log.WARN, format, arrayOf(arg1, arg2))
    }

    override fun warn(format: String, vararg arguments: Any?) {
        print(Log.WARN, format, arrayOf(*arguments))
    }

    override fun warn(msg: String?, t: Throwable?) {
        print(Log.WARN, t, msg)
    }

    override fun error(msg: String?) {
        print(Log.ERROR, null, msg)
    }

    override fun error(format: String, arg: Any?) {
        print(Log.ERROR, format, arrayOf(arg))
    }

    override fun error(format: String, arg1: Any?, arg2: Any?) {
        print(Log.ERROR, format, arrayOf(arg1, arg2))
    }

    override fun error(format: String, vararg arguments: Any?) {
        print(Log.ERROR, format, arrayOf(*arguments))
    }

    override fun error(msg: String?, t: Throwable?) {
        print(Log.ERROR, t, msg)
    }

    private fun print(@LogLevel level: Int, throwable: Throwable?, message: String?) {
        var message = message
        if (prefix != null) {
            message = "$prefix: $message"
        }
        backends.forEach { backend ->
            backend.print(level, tag, throwable, message)
        }
    }

    private fun print(@LogLevel level: Int, messageFormat: String, args: Array<Any?>) {
        var messageFormat = messageFormat
        if (prefix != null) {
            messageFormat = "$prefix: $messageFormat"
        }
        val extractedThrowable = args.lastOrNull() as? Throwable
        backends.forEach { backend ->
            backend.print(level, tag, extractedThrowable, messageFormat, args)
        }
    }
}
