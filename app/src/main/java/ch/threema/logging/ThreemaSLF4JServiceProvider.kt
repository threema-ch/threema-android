package ch.threema.logging

import androidx.annotation.Keep
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

@Keep
class ThreemaSLF4JServiceProvider : SLF4JServiceProvider {
    private lateinit var loggerFactory: ThreemaLoggerFactory

    override fun getMarkerFactory() = null

    override fun getMDCAdapter() = NOPMDCAdapter()

    override fun getRequestedApiVersion() = "2.0.17"

    override fun getLoggerFactory() = loggerFactory

    override fun initialize() {
        loggerFactory = ThreemaLoggerFactory(
            logBackendFactory = CachedLogBackendFactory(
                logBackendFactory = LogBackendFactoryImpl(),
            ),
        )
    }
}
