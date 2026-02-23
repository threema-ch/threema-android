package ch.threema.base.utils

import java.util.Date

class TimeMeasureUtil {
    private var tStart = Date()
    private var tStop: Date? = null

    val elapsedTime
        get() = (tStop ?: Date()).time - tStart.time

    fun start() {
        tStop = null
        tStart = Date()
    }

    fun stop(): Long {
        tStop = Date()
        return elapsedTime
    }
}
