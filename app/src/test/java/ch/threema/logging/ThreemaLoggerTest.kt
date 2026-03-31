package ch.threema.logging

import android.util.Log
import ch.threema.logging.backend.LogBackend
import io.mockk.mockk
import io.mockk.verifySequence
import kotlin.test.Test

class ThreemaLoggerTest {
    @Test
    fun `logging at different levels`() {
        val throwableMock = mockk<Throwable>()
        val backend = mockk<LogBackend>(relaxed = true)
        val logger = ThreemaLogger(
            tag = "Test",
            backends = listOf(backend),
        )

        logger.debug(msg = "Hello")
        logger.debug(format = "Hello {}", "World")
        logger.debug(msg = "Hello", t = throwableMock)
        logger.debug(format = "Hello {}", "World", throwableMock)
        logger.info(msg = "Hello")
        logger.info(format = "Hello {}", "World")
        logger.info(msg = "Hello", t = throwableMock)
        logger.info(format = "Hello {}", "World", throwableMock)
        logger.warn(msg = "Hello")
        logger.warn(format = "Hello {}", "World")
        logger.warn(msg = "Hello", t = throwableMock)
        logger.warn(format = "Hello {}", "World", throwableMock)
        logger.error(msg = "Hello")
        logger.error(format = "Hello {}", "World")
        logger.error(msg = "Hello", t = throwableMock)
        logger.error(format = "Hello {}", "World", throwableMock)

        verifySequence {
            backend.print(Log.DEBUG, "Test", null, "Hello")
            backend.print(Log.DEBUG, "Test", null, "Hello {}", args = arrayOf("World"))
            backend.print(Log.DEBUG, "Test", throwableMock, "Hello")
            backend.print(Log.DEBUG, "Test", throwableMock, "Hello {}", args = arrayOf("World", throwableMock))
            backend.print(Log.INFO, "Test", null, "Hello")
            backend.print(Log.INFO, "Test", null, "Hello {}", args = arrayOf("World"))
            backend.print(Log.INFO, "Test", throwableMock, "Hello")
            backend.print(Log.INFO, "Test", throwableMock, "Hello {}", args = arrayOf("World", throwableMock))
            backend.print(Log.WARN, "Test", null, "Hello")
            backend.print(Log.WARN, "Test", null, "Hello {}", args = arrayOf("World"))
            backend.print(Log.WARN, "Test", throwableMock, "Hello")
            backend.print(Log.WARN, "Test", throwableMock, "Hello {}", args = arrayOf("World", throwableMock))
            backend.print(Log.ERROR, "Test", null, "Hello")
            backend.print(Log.ERROR, "Test", null, "Hello {}", args = arrayOf("World"))
            backend.print(Log.ERROR, "Test", throwableMock, "Hello")
            backend.print(Log.ERROR, "Test", throwableMock, "Hello {}", args = arrayOf("World", throwableMock))
        }
    }

    @Test
    fun `logging with multiple backends`() {
        val throwableMock = mockk<Throwable>()
        val backend1 = mockk<LogBackend>(relaxed = true)
        val backend2 = mockk<LogBackend>(relaxed = true)
        val logger = ThreemaLogger(
            tag = "Test",
            backends = listOf(backend1, backend2),
        )

        logger.debug(msg = "Hello")
        logger.debug(format = "Hello {}", "World")
        logger.debug(msg = "Hello", t = throwableMock)
        logger.debug(format = "Hello {}", "World", throwableMock)

        verifySequence {
            backend1.print(Log.DEBUG, "Test", null, "Hello")
            backend2.print(Log.DEBUG, "Test", null, "Hello")
            backend1.print(Log.DEBUG, "Test", null, "Hello {}", args = arrayOf("World"))
            backend2.print(Log.DEBUG, "Test", null, "Hello {}", args = arrayOf("World"))
            backend1.print(Log.DEBUG, "Test", throwableMock, "Hello")
            backend2.print(Log.DEBUG, "Test", throwableMock, "Hello")
            backend1.print(Log.DEBUG, "Test", throwableMock, "Hello {}", args = arrayOf("World", throwableMock))
            backend2.print(Log.DEBUG, "Test", throwableMock, "Hello {}", args = arrayOf("World", throwableMock))
        }
    }

    @Test
    fun `logging with prefix`() {
        val throwableMock = mockk<Throwable>()
        val backend = mockk<LogBackend>(relaxed = true)
        val logger = ThreemaLogger(
            tag = "Test",
            backends = listOf(backend),
        )
        logger.prefix = "Prefix"

        logger.debug(msg = "Hello")
        logger.debug(format = "Hello {}", "World")
        logger.debug(msg = "Hello", t = throwableMock)
        logger.debug(format = "Hello {}", "World", throwableMock)

        verifySequence {
            backend.print(Log.DEBUG, "Test", null, "Prefix: Hello")
            backend.print(Log.DEBUG, "Test", null, "Prefix: Hello {}", args = arrayOf("World"))
            backend.print(Log.DEBUG, "Test", throwableMock, "Prefix: Hello")
            backend.print(Log.DEBUG, "Test", throwableMock, "Prefix: Hello {}", args = arrayOf("World", throwableMock))
        }
    }
}
