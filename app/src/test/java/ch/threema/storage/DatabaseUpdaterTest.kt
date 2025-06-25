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

package ch.threema.storage

import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseUpdaterTest {
    private val updater = DatabaseUpdater(
        context = mockk(),
        database = mockk(),
        databaseService = mockk(),
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
    fun `database updates are in correct order`() {
        val updates = updater.getUpdates(oldVersion = 0)

        var previous = 0
        updates.forEach { update ->
            assertTrue(update.getVersion() > previous)
            previous = update.getVersion()
        }
    }
}
