package ch.threema.common

import java.util.UUID

fun interface UUIDGenerator {
    fun generate(): UUID

    companion object {
        @JvmField
        val default = UUIDGenerator { UUID.randomUUID() }
    }
}
