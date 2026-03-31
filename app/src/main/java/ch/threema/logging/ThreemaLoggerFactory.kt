package ch.threema.logging

import android.util.Log
import org.slf4j.ILoggerFactory
import org.slf4j.Logger

class ThreemaLoggerFactory(
    private val logBackendFactory: LogBackendFactory,
) : ILoggerFactory {
    override fun getLogger(name: String): Logger {
        val minLogLevel = getMinLogLevel(name)
        val backends = logBackendFactory.getBackends(minLogLevel)
        return ThreemaLogger(name, backends)
    }

    @LogLevel
    private fun getMinLogLevel(name: String): Int = when {
        name.startsWith("ch.threema") -> Log.INFO
        name == "Validation" -> Log.INFO
        name.startsWith("SaltyRTC.") || name.startsWith("org.saltyrtc") -> Log.INFO
        name.startsWith("libwebrtc") || name.startsWith("org.webrtc") -> Log.INFO
        else -> Log.WARN
    }
}
