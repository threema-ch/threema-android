package ch.threema.logging

import android.annotation.SuppressLint
import ch.threema.base.utils.getThreemaLogger
import ch.threema.libthreema.LogDispatcher
import ch.threema.libthreema.LogLevel

@SuppressLint("LoggerName")
private val logger = getThreemaLogger("libthreema")

class LibthreemaLogger : LogDispatcher {
    override fun log(level: LogLevel, record: String) {
        when (level) {
            LogLevel.TRACE -> logger.trace(record)
            LogLevel.DEBUG -> logger.debug(record)
            LogLevel.INFO -> logger.info(record)
            LogLevel.WARN -> logger.warn(record)
            LogLevel.ERROR -> logger.error(record)
        }
    }
}
