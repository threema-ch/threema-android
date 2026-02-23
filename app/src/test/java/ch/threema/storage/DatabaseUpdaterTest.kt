package ch.threema.storage

import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseUpdaterTest {
    private val updater = DatabaseUpdater(
        context = mockk(),
        database = mockk(),
    )

    @Test
    fun `no database updates`() {
        // we don't have the actual current version number available here, so we pick an arbitrarily large one instead
        val updates = updater.getUpdates(oldVersion = 9000)
        assertEquals(emptyList(), updates)
    }

    @Test
    fun `some database updates`() {
        val updates = updater.getUpdates(oldVersion = 103)

        assertTrue(updates.isNotEmpty())
        assertTrue(updates.all { it.getVersion() >= 103 })
    }

    @Test
    fun `no database updates newer than database version`() {
        val updates = updater.getUpdates(oldVersion = 0)

        assertTrue(updates.none { it.getVersion() > DatabaseUpdater.VERSION })
    }

    @Test
    fun `database updates are in correct order`() {
        val updates = updater.getUpdates(oldVersion = 0)

        var previous = 0
        updates.forEach { update ->
            assertTrue(update.getVersion() > previous)
            previous = update.getVersion()
        }
    }
}
