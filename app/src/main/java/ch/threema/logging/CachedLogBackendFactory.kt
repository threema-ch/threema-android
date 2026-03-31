package ch.threema.logging

import ch.threema.logging.backend.LogBackend

class CachedLogBackendFactory(
    private val logBackendFactory: LogBackendFactory,
) : LogBackendFactory {
    private val cache = mutableMapOf<Int, List<LogBackend>>()

    override fun getBackends(@LogLevel minLogLevel: Int): List<LogBackend> = synchronized(cache) {
        cache.getOrPut(minLogLevel) {
            logBackendFactory.getBackends(minLogLevel)
        }
    }
}
