package ch.threema.common

import java.util.UUID
import kotlin.test.Test

class UUIDGeneratorTest {
    @Test
    fun `generated uuid is valid`() {
        UUID.fromString(UUIDGenerator.default.generate())
    }
}
