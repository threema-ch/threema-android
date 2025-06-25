/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.utils

import androidx.lifecycle.Lifecycle
import ch.threema.app.utils.Destroyer.Companion.createDestroyer
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
