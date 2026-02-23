package ch.threema.common

import java.time.Instant
import java.time.LocalDateTime

interface TimeProvider {
    fun get(): Instant

    fun getLocal(): LocalDateTime

    companion object {
        @JvmField
        val default = object : TimeProvider {
            override fun get() = Instant.now()

            override fun getLocal() = LocalDateTime.now()
        }
    }
}
