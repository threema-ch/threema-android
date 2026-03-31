package ch.threema.app.systemupdates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemUpdateProviderTest {
    private val systemUpdateProvider = SystemUpdateProvider()

    @Test
    fun `no system updates when on latest version`() {
        val updates = systemUpdateProvider.getUpdates(oldVersion = systemUpdateProvider.version)
        assertEquals(emptyList(), updates)
    }

    @Test
    fun `some system updates when old version is older than current version`() {
        val updates = systemUpdateProvider.getUpdates(oldVersion = 60)

        assertTrue(updates.isNotEmpty())
    }

    @Test
    fun `updates are never older than old version`() {
        for (oldVersion in 12 until systemUpdateProvider.version) {
            val updates = systemUpdateProvider.getUpdates(oldVersion = oldVersion)
            assertTrue(updates.all { it.version > oldVersion })
        }
    }

    @Test
    fun `system updates are in correct order`() {
        val updates = systemUpdateProvider.getUpdates(oldVersion = 0)

        var previous = 0
        updates.forEach { update ->
            assertTrue(update.version > previous, "Update ${update.version} or $previous is out of order")
            previous = update.version
        }
    }

    @Test
    fun `system updates have correct name and version number`() {
        val updates = systemUpdateProvider.getUpdates(oldVersion = 0)

        updates.forEach { update ->
            assertEquals(
                "SystemUpdateToVersion${update.version}",
                update::class.simpleName,
            )
        }
    }

    @Test
    fun `version number is valid`() {
        val updates = systemUpdateProvider.getUpdates(oldVersion = 0)

        assertTrue(systemUpdateProvider.version >= updates.last().version)
    }
}
