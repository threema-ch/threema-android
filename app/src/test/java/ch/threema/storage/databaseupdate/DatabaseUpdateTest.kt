package ch.threema.storage.databaseupdate

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseUpdateTest {
    @Test
    fun `full description without description`() {
        val databaseUpdate = mockk<DatabaseUpdate> {
            every { getVersion() } returns 42
            every { getDescription() } returns null
        }

        assertEquals("version 42", databaseUpdate.fullDescription)
    }

    @Test
    fun `full description with description`() {
        val databaseUpdate = mockk<DatabaseUpdate> {
            every { getVersion() } returns 42
            every { getDescription() } returns "test stuff"
        }

        assertEquals("version 42 (test stuff)", databaseUpdate.fullDescription)
    }
}
