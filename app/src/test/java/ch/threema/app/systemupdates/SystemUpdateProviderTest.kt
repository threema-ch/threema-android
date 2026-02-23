package ch.threema.app.systemupdates

import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemUpdateProviderTest {
    private val systemUpdateProvider = SystemUpdateProvider(
        context = mockk(),
        serviceManager = mockk(),
    )

    @Test
    fun `no system updates when on latest version`() {
        val updates = systemUpdateProvider.getUpdates(oldVersion = systemUpdateProvider.getVersion())
        assertEquals(emptyList(), updates)
    }

    @Test
    fun `some system updates`() {
        val updates = systemUpdateProvider.getUpdates(oldVersion = 60)

        assertTrue(updates.isNotEmpty())
        assertTrue(updates.all { it.getVersion() >= 60 })
    }

    @Test
    fun `system updates are in correct order`() {
        val updates = systemUpdateProvider.getUpdates(oldVersion = 0)

        var previous = 0
        updates.forEach { update ->
            assertTrue(update.getVersion() > previous, "Update ${update.getVersion()} or $previous is out of order")
            previous = update.getVersion()
        }
    }

    @Test
    fun `version number is valid`() {
        val updates = systemUpdateProvider.getUpdates(oldVersion = 0)

        assertTrue(systemUpdateProvider.getVersion() >= updates.last().getVersion())
    }
}
