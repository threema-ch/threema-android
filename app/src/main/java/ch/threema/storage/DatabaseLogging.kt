package ch.threema.storage

import android.util.Log
import ch.threema.base.utils.getThreemaLogger
import net.zetetic.database.LogTarget
import net.zetetic.database.Logger

private val logger = getThreemaLogger("DatabaseLogging")

fun setupDatabaseLogging() {
    Logger.setTarget(object : LogTarget {
        override fun isLoggable(tag: String?, priority: Int) = Log.isLoggable(tag, priority)

        override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
            val fullMessage = "$tag: $message"
            when (priority) {
                Logger.VERBOSE -> logger.trace(fullMessage, throwable)
                Logger.DEBUG -> logger.debug(fullMessage, throwable)
                Logger.INFO -> logger.info(fullMessage, throwable)
                Logger.WARN -> logger.warn(fullMessage, throwable)
                Logger.ERROR -> logger.error(fullMessage, throwable)
                Logger.ASSERT -> logger.error(fullMessage, throwable)
            }
        }
    })
}
