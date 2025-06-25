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
