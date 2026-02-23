package org.slf4j.impl

import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

class ThreemaSLF4JServiceProvider : SLF4JServiceProvider {
    private lateinit var loggerFactory: ThreemaLoggerFactory

    override fun getLoggerFactory() = loggerFactory

    override fun getMarkerFactory() = null

    override fun getMDCAdapter() = NOPMDCAdapter()

    override fun getRequestedApiVersion() = "2.0.17"

    override fun initialize() {
        loggerFactory = ThreemaLoggerFactory()
    }
}
