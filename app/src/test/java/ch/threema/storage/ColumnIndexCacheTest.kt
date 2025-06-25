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

import android.database.Cursor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class ColumnIndexCacheTest {
    @Test
    fun `column indices are taken from cursor`() {
        val columnIndexCache = ColumnIndexCache()

        val cursor = mockCursor(columnName = "foo", columnIndex = 2)
        val index = columnIndexCache.getColumnIndex(cursor, "foo")

        assertEquals(2, index)
    }

    @Test
    fun `column indices are cached`() {
        val columnIndexCache = ColumnIndexCache()

        val cursor = mockCursor(columnName = "foo", columnIndex = 2)
        val index1 = columnIndexCache.getColumnIndex(cursor, "foo")
        val index2 = columnIndexCache.getColumnIndex(cursor, "foo")

        assertEquals(index1, index2)
        verify(exactly = 1) { cursor.getColumnIndex("foo") }
    }

    @Test
    fun `index taken from cursor again after clearing`() {
        val columnIndexCache = ColumnIndexCache()

        val cursor = mockCursor(columnName = "foo", columnIndex = 2)
        val index1 = columnIndexCache.getColumnIndex(cursor, "foo")
        columnIndexCache.clear()
        val index2 = columnIndexCache.getColumnIndex(cursor, "foo")

        assertEquals(index1, index2)
        verify(exactly = 2) { cursor.getColumnIndex("foo") }
    }

    private fun mockCursor(columnName: String, columnIndex: Int) =
        mockk<Cursor> {
            every { getColumnIndex(columnName) } returns columnIndex
        }
}
