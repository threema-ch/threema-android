package ch.threema.logging.backend

import ch.threema.logging.LogLevel

interface LogBackend {
    /**
     * Return whether this backend is enabled for the specified log level.
     */
    fun isEnabled(@LogLevel level: Int): Boolean

    /**
     * Log a message to the backend.
     *
     * This method should automatically check using [isEnabled] if the message is allowed to be logged or not.
     *
     * @param level     The log level
     * @param tag       The log tag
     * @param throwable A throwable
     * @param message   A message
     */
    fun print(@LogLevel level: Int, tag: String, throwable: Throwable?, message: String?)

    /**
     * Log a message to the backend.
     *
     * This method should automatically check using [isEnabled] if the message is allowed to be logged or not.
     *
     * @param level         The log level
     * @param tag           The log tag
     * @param throwable     A throwable
     * @param messageFormat A message format string
     * @param args          The message arguments
     */
    fun print(@LogLevel level: Int, tag: String, throwable: Throwable?, messageFormat: String, args: Array<Any?>)
}
