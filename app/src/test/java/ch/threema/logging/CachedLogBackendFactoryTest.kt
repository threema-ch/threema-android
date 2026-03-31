package ch.threema.logging

import android.util.Log
import ch.threema.logging.backend.LogBackend
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedLogBackendFactoryTest {

    @Test
    fun `backends are taken from delegate`() {
        val logBackendFactoryMock = mockk<LogBackendFactory> {
            every { getBackends(Log.INFO) } returns listOf(
                logBackendMock1,
                logBackendMock2,
            )
            every { getBackends(Log.VERBOSE) } returns listOf(
                logBackendMock3,
            )
        }
        val logBackendFactory = CachedLogBackendFactory(
            logBackendFactory = logBackendFactoryMock,
        )

        val infoLogBackends = logBackendFactory.getBackends(Log.INFO)
        assertEquals(listOf(logBackendMock1, logBackendMock2), infoLogBackends)
        val verboseLogBackends = logBackendFactory.getBackends(Log.VERBOSE)
        assertEquals(listOf(logBackendMock3), verboseLogBackends)
    }

    @Test
    fun `backends are cached`() {
        val logBackendFactoryMock = mockk<LogBackendFactory> {
            every { getBackends(Log.INFO) } returns listOf(logBackendMock1)
        }
        val logBackendFactory = CachedLogBackendFactory(logBackendFactory = logBackendFactoryMock)

        val infoLogBackends1 = logBackendFactory.getBackends(Log.INFO)
        val infoLogBackends2 = logBackendFactory.getBackends(Log.INFO)
        assertEquals(infoLogBackends1, infoLogBackends2)
        verify(exactly = 1) { logBackendFactoryMock.getBackends(Log.INFO) }
    }

    companion object {
        val logBackendMock1 = mockk<LogBackend>()
        val logBackendMock2 = mockk<LogBackend>()
        val logBackendMock3 = mockk<LogBackend>()
    }
}
