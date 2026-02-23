package ch.threema.app.logging

import ch.threema.logging.ThreemaLogger
import ch.threema.logging.backend.LogBackend
import java.lang.reflect.Constructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreemaLoggerTest {
    private val expectedThrowable = IllegalStateException()
    private val throwableExpectingBackend = object : LogBackend {
        override fun isEnabled(level: Int) = true

        override fun print(level: Int, tag: String, throwable: Throwable?, message: String?) {
            assertEquals(expectedThrowable, throwable)
        }

        override fun print(
            level: Int,
            tag: String,
            throwable: Throwable?,
            messageFormat: String,
            vararg args: Any?,
        ) {
            assertEquals(expectedThrowable, throwable)
        }
    }
    private val noThrowableExpectingBackend = object : LogBackend {
        override fun isEnabled(level: Int) = true

        override fun print(level: Int, tag: String, throwable: Throwable?, message: String?) {
            assertNull(throwable)
        }

        override fun print(
            level: Int,
            tag: String,
            throwable: Throwable?,
            messageFormat: String,
            vararg args: Any?,
        ) {
            assertNull(throwable)
        }
    }

    private val throwableExpectingLogger: ThreemaLogger by lazy {
        val loggerConstructor: Constructor<ThreemaLogger> =
            ThreemaLogger::class.java.getDeclaredConstructor(
                String::class.java,
                LogBackend::class.java,
            )
        loggerConstructor.isAccessible = true
        loggerConstructor.newInstance("ThreemaLoggerTest", throwableExpectingBackend)
    }

    private val noThrowableExpectingLogger: ThreemaLogger by lazy {
        val loggerConstructor: Constructor<ThreemaLogger> =
            ThreemaLogger::class.java.getDeclaredConstructor(
                String::class.java,
                LogBackend::class.java,
            )
        loggerConstructor.isAccessible = true
        loggerConstructor.newInstance("ThreemaLoggerTest", noThrowableExpectingBackend)
    }

    @Test
    fun testThrowableProvided() {
        throwableExpectingLogger.trace("Trace", expectedThrowable)
        throwableExpectingLogger.debug("Debug", expectedThrowable)
        throwableExpectingLogger.info("Info", expectedThrowable)
        throwableExpectingLogger.warn("Warn", expectedThrowable)
        throwableExpectingLogger.error("Error", expectedThrowable)
    }

    @Test
    fun testThrowableProvidedOneArg() {
        throwableExpectingLogger.trace("Trace {}", "arg", expectedThrowable)
        throwableExpectingLogger.debug("Debug {}", "arg", expectedThrowable)
        throwableExpectingLogger.info("Info {}", "arg", expectedThrowable)
        throwableExpectingLogger.warn("Warn {}", "arg", expectedThrowable)
        throwableExpectingLogger.error("Error {}", "arg", expectedThrowable)
    }

    @Test
    fun testThrowableProvidedTwoArg() {
        throwableExpectingLogger.trace("Trace {} {}", "arg", "arg", expectedThrowable)
        throwableExpectingLogger.debug("Debug {} {}", "arg", "arg", expectedThrowable)
        throwableExpectingLogger.info("Info {} {}", "arg", "arg", expectedThrowable)
        throwableExpectingLogger.warn("Warn {} {}", "arg", "arg", expectedThrowable)
        throwableExpectingLogger.error("Error {} {}", "arg", "arg", expectedThrowable)
    }

    @Test
    fun testNoThrowableProvided() {
        noThrowableExpectingLogger.trace("Trace")
        noThrowableExpectingLogger.debug("Debug")
        noThrowableExpectingLogger.info("Info")
        noThrowableExpectingLogger.warn("Warn")
        noThrowableExpectingLogger.error("Error")
    }

    @Test
    fun testNoThrowableProvidedOneArg() {
        noThrowableExpectingLogger.trace("Trace {}", "arg")
        noThrowableExpectingLogger.debug("Debug {}", "arg")
        noThrowableExpectingLogger.info("Info {}", "arg")
        noThrowableExpectingLogger.warn("Warn {}", "arg")
        noThrowableExpectingLogger.error("Error {}", "arg")
    }

    @Test
    fun testNoThrowableProvidedTwoArg() {
        noThrowableExpectingLogger.trace("Trace {} {}", "arg", "arg")
        noThrowableExpectingLogger.debug("Debug {} {}", "arg", "arg")
        noThrowableExpectingLogger.info("Info {} {}", "arg", "arg")
        noThrowableExpectingLogger.warn("Warn {} {}", "arg", "arg")
        noThrowableExpectingLogger.error("Error {} {}", "arg", "arg")
    }

    @Test
    fun testTwoThrowablesProvided() {
        val unexpectedThrowable = IllegalArgumentException()
        throwableExpectingLogger.trace("Trace {}", unexpectedThrowable, expectedThrowable)
        throwableExpectingLogger.debug("Debug {}", unexpectedThrowable, expectedThrowable)
        throwableExpectingLogger.info("Info {}", unexpectedThrowable, expectedThrowable)
        throwableExpectingLogger.warn("Warn {}", unexpectedThrowable, expectedThrowable)
        throwableExpectingLogger.error("Error {}", unexpectedThrowable, expectedThrowable)
    }
}
