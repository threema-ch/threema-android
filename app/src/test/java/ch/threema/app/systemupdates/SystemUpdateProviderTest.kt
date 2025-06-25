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
