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

package ch.threema.common

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileExtensionsTest {
    @Test
    fun `clear a directory recursively`() {
        val root = Files.createTempDirectory("root").toFile()
        root.mkdir()
        val fileA = File(root, "A")
        fileA.createNewFile()
        val fileB = File(root, "B")
        fileB.createNewFile()
        val directory1 = File(root, "1")
        directory1.mkdir()
        val directory2 = File(root, "2")
        directory2.mkdir()
        val file1A = File(directory1, "1A")
        file1A.createNewFile()
        val directory11 = File(directory1, "11")
        directory11.mkdir()

        root.clearDirectoryRecursively()

        assertTrue(root.exists())
        assertFalse(fileA.exists())
        assertFalse(fileB.exists())
        assertFalse(fileB.exists())
        assertFalse(directory1.exists())
        assertFalse(directory2.exists())
        assertFalse(file1A.exists())
        assertFalse(directory11.exists())
    }

    @Test
    fun `clear a directory non-recursively`() {
        val root = Files.createTempDirectory("root").toFile()
        root.mkdir()
        val fileA = File(root, "A")
        fileA.createNewFile()
        val fileB = File(root, "B")
        fileB.createNewFile()
        val directory1 = File(root, "1")
        directory1.mkdir()
        val directory2 = File(root, "2")
        directory2.mkdir()
        val file1A = File(directory1, "1A")
        file1A.createNewFile()
        val directory11 = File(directory1, "11")
        directory11.mkdir()

        root.clearDirectoryNonRecursively()

        assertTrue(root.exists())
        assertFalse(fileA.exists())
        assertFalse(fileB.exists())
        assertFalse(fileB.exists())
        assertTrue(directory1.exists())
        assertTrue(directory2.exists())
        assertTrue(file1A.exists())
        assertTrue(directory11.exists())
    }

    @Test
    fun `get total size of directory`() {
        val root = Files.createTempDirectory("root").toFile()
        root.mkdir()
        val fileA = File(root, "A")
        fileA.writeText("Hello") // size 5
        val fileB = File(root, "B")
        fileB.writeText("World") // size 5
        val directory1 = File(root, "1")
        directory1.mkdir()
        val directory2 = File(root, "2")
        directory2.mkdir()
        val file1A = File(directory1, "1A")
        file1A.writeText("\uD83D\uDC08") // size 4
        val file1B = File(directory1, "1A")
        file1B.createNewFile() // size 0
        val directory11 = File(directory1, "11")
        directory11.mkdir()

        val totalSize = root.getTotalSize()

        assertEquals(14, totalSize)
    }
}
