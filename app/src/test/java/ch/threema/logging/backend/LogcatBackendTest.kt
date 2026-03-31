package ch.threema.logging.backend

import android.util.Log
import ch.threema.app.BuildConfig
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.Test

class LogcatBackendTest {
    @Test
    fun testTagCleaning() {
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 1

        val backend = LogcatBackend(Log.INFO)

        backend.print(Log.WARN, "ch.threema.app.Hello", null, "hello")
        verify(exactly = 1) { Log.println(Log.WARN, BuildConfig.LOG_TAG, "Hello: hello") }

        backend.print(Log.INFO, "ch.threema.domain.Bye", null, "goodbye")
        verify(exactly = 1) { Log.println(Log.INFO, BuildConfig.LOG_TAG, "Bye: goodbye") }

        backend.print(Log.INFO, "ch.threema.app.subpackage.Abcd", null, "msg")
        verify(exactly = 1) { Log.println(Log.INFO, BuildConfig.LOG_TAG, "subpackage.Abcd: msg") }

        backend.print(Log.ERROR, "any.other.package", null, "hmmmm")
        verify(exactly = 1) { Log.println(Log.ERROR, BuildConfig.LOG_TAG, "any.other.package: hmmmm") }

        unmockkStatic(Log::class)
    }
}
