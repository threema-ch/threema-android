package ch.threema.testhelpers

import ch.threema.common.TimeProvider
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration

class TestTimeProvider(
    initialTimestamp: Long = 0L,
    var timeZoneOffset: ZoneOffset = ZoneOffset.UTC,
) : TimeProvider {

    constructor(initialTimestamp: Instant) : this(initialTimestamp.toEpochMilli())

    private var timestamp = initialTimestamp

    override fun get(): Instant = Instant.ofEpochMilli(timestamp)

    fun set(timestamp: Long) {
        this.timestamp = timestamp.coerceAtLeast(0L)
    }

    fun set(time: Instant) {
        timestamp = time.toEpochMilli()
    }

    override fun getLocal(): LocalDateTime = LocalDateTime.ofInstant(get(), timeZoneOffset)

    fun advanceBy(duration: Duration) {
        timestamp += duration.inWholeMilliseconds
    }
}
