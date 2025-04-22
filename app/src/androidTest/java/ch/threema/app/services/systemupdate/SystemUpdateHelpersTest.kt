/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.services.systemupdate

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Before
import org.junit.Test

class SystemUpdateHelpersTest {
    private var inMemoryDatabase: SQLiteDatabase = SQLiteDatabase.create(null)

    @Before
    fun setUp() {
        this.inMemoryDatabase.execSQL("CREATE TABLE IF NOT EXISTS testtable (hello TEXT, world INTEGER)")
    }

    @Test
    fun testFieldExistNonExistingTable() {
        assertFalse {
            fieldExists(this.inMemoryDatabase, "non_existing_table", "non_existing_field")
        }
    }

    @Test
    fun testFieldExistExistingTable() {
        assertFalse {
            fieldExists(this.inMemoryDatabase, "testtable", "non_existing_field")
        }
        assertTrue {
            fieldExists(this.inMemoryDatabase, "testtable", "hello")
        }
        assertTrue {
            fieldExists(this.inMemoryDatabase, "testtable", "world")
        }
    }
}
