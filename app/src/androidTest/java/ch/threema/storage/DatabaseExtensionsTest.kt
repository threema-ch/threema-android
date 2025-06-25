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

package ch.threema.storage

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseExtensionsTest {
    private var inMemoryDatabase: SQLiteDatabase = SQLiteDatabase.create(null)

    @BeforeTest
    fun setUp() {
        inMemoryDatabase.execSQL("CREATE TABLE IF NOT EXISTS testtable (hello TEXT, world INTEGER)")
    }

    @Test
    fun testTableExistsForNonExistingTable() {
        assertFalse(inMemoryDatabase.tableExists("non_existing_table"))
    }

    @Test
    fun testTableExistsForExistingTable() {
        assertTrue(inMemoryDatabase.tableExists("testtable"))
    }

    @Test
    fun testFieldExistNonExistingTable() {
        assertFalse(inMemoryDatabase.fieldExists("non_existing_table", "non_existing_field"))
    }

    @Test
    fun testFieldExistExistingTable() {
        assertFalse(inMemoryDatabase.fieldExists("testtable", "non_existing_field"))
        assertTrue(inMemoryDatabase.fieldExists("testtable", "hello"))
        assertTrue(inMemoryDatabase.fieldExists("testtable", "world"))
    }
}
