package ch.threema.storage

import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseUpdaterTest {
    private val updater = DatabaseUpdater(
        appContext = mockk(),
        database = mockk(),
    )

    @Test
    fun `no database updates if already on latest version`() {
        val updates = updater.getUpdates(oldVersion = DatabaseUpdater.VERSION)
        assertEquals(emptyList(), updates)
    }

    @Test
    fun `some database updates when old version is older than latest version`() {
        val updates = updater.getUpdates(oldVersion = 103)

        assertTrue(updates.isNotEmpty())
        assertTrue(updates.all { it.version >= 103 })
    }

    @Test
    fun `updates are never older than old version and never newer than latest version`() {
        for (oldVersion in 0 until DatabaseUpdater.VERSION) {
            val updates = updater.getUpdates(oldVersion = oldVersion)
            assertTrue(updates.none { it.version < oldVersion })
            assertTrue(updates.none { it.version > DatabaseUpdater.VERSION })
        }
    }

    @Test
    fun `database updates are in correct order`() {
        val updates = updater.getUpdates(oldVersion = 0)

        var previous = 0
        updates.forEach { update ->
            assertTrue(update.version > previous)
            previous = update.version
        }
    }

    @Test
    fun `database updates have correct name and version number`() {
        val updates = updater.getUpdates(oldVersion = 0)

        updates.forEach { update ->
            assertEquals(
                "DatabaseUpdateToVersion${update.version}",
                update::class.simpleName,
            )
        }
    }
}
