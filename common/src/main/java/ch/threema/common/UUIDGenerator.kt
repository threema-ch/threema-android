package ch.threema.common

import java.util.UUID

fun interface UUIDGenerator {
    fun generate(): String

    companion object {
        @JvmField
        val default = UUIDGenerator { UUID.randomUUID().toString() }
    }
}
