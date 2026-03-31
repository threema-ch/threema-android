package ch.threema.logging

import ch.threema.logging.backend.LogBackend

interface LogBackendFactory {
    fun getBackends(@LogLevel minLogLevel: Int): List<LogBackend>
}
