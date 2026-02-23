package ch.threema.app.utils

import androidx.lifecycle.Lifecycle
import ch.threema.android.Destroyable
import ch.threema.android.Destroyer
import ch.threema.android.Destroyer.Companion.createDestroyer
import ch.threema.android.ownedBy
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertSame

class DestroyerTest {

    @Test
    fun `registered destroyables are destroyed exactly once`() {
        val destroyer = createDestroyer()
        val destroyable1 = mockk<Destroyable>(relaxed = true).ownedBy(destroyer)
        val destroyable2 = mockk<Destroyable>(relaxed = true).ownedBy(destroyer)

        destroyer.onDestroy(mockk())

        verify(exactly = 1) { destroyable1.destroy() }
        verify(exactly = 1) { destroyable2.destroy() }
    }

    @Test
    fun `registered destroyables are cleared after being destroyed`() {
        val destroyer = createDestroyer()
        val destroyable = mockk<Destroyable>(relaxed = true).ownedBy(destroyer)

        destroyer.onDestroy(mockk())
        destroyer.onDestroy(mockk())

        verify(exactly = 1) { destroyable.destroy() }
    }

    @Test
    fun `register returns created object and registers the destroy lambda`() {
        val destroyable = mockk<Destroyable>(relaxed = true)
        val destroyer = createDestroyer()

        val result = destroyer.register(
            create = { destroyable },
            destroy = { destroyable.destroy() },
        )
        destroyer.onDestroy(mockk())

        assertSame(result, destroyable)
        verify(exactly = 1) { destroyable.destroy() }
    }

    private fun createDestroyer(): Destroyer =
        mockk<Lifecycle>(relaxed = true).createDestroyer()
}
