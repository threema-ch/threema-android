package ch.threema.app.di

import android.annotation.SuppressLint
import ch.threema.base.utils.getThreemaLogger
import org.koin.core.logger.Level
import org.koin.core.logger.Logger as KoinLogger

@SuppressLint("LoggerName")
private val logger = getThreemaLogger("Koin")

object ThreemaKoinLogger : KoinLogger() {
    override fun display(level: Level, msg: String) {
        when (level) {
            Level.DEBUG -> logger.debug(msg)
            Level.INFO -> logger.info(msg)
            Level.WARNING -> logger.warn(msg)
            Level.ERROR -> logger.error(msg)
            Level.NONE -> Unit
        }
    }
}
