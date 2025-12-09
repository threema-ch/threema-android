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

package ch.threema.common.files

import ch.threema.common.emptyByteArray
import ch.threema.testhelpers.createTempDirectory
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimpleFileHandleTest {

    private lateinit var directory: File

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory()
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `file does not exist`() {
        val file = File(directory, "foo")
        val fileHandle = SimpleFileHandle(file)
        assertFalse(fileHandle.exists())
        assertFalse(fileHandle.isEmpty())
        assertNull(fileHandle.read())
    }

    @Test
    fun `file is empty`() {
        val file = File(directory, "foo")
        file.createNewFile()
        val fileHandle = SimpleFileHandle(file)
        assertTrue(fileHandle.exists())
        assertTrue(fileHandle.isEmpty())
        assertContentEquals(emptyByteArray(), fileHandle.read()!!.readAllBytes())
    }

    @Test
    fun `file is non-empty`() {
        val file = File(directory, "foo")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val fileHandle = SimpleFileHandle(file)
        assertTrue(fileHandle.exists())
        assertFalse(fileHandle.isEmpty())
        assertContentEquals(byteArrayOf(1, 2, 3), fileHandle.read()!!.readAllBytes())
    }

    @Test
    fun `create file in existing directory`() {
        val file = File(directory, "foo")
        val fileHandle = SimpleFileHandle(file)

        fileHandle.create()

        assertTrue(file.exists())
        assertEquals(0, file.length())
    }

    @Test
    fun `create file in non-existing directory`() {
        val subdirectory = File(directory, "subdir")
        val file = File(subdirectory, "foo")
        val fileHandle = SimpleFileHandle(file)

        fileHandle.create()

        assertTrue(file.exists())
        assertEquals(0, file.length())
    }

    @Test
    fun `write file in existing directory`() {
        val file = File(directory, "foo")
        val fileHandle = SimpleFileHandle(file)

        fileHandle.write().use { outputStream ->
            outputStream.write(byteArrayOf(1, 2, 3))
        }

        assertTrue(file.exists())
        assertContentEquals(byteArrayOf(1, 2, 3), fileHandle.read()!!.readAllBytes())
    }

    @Test
    fun `write file in non-existing directory`() {
        val subdirectory = File(directory, "subdir")
        val file = File(subdirectory, "foo")
        val fileHandle = SimpleFileHandle(file)

        fileHandle.write().use { outputStream ->
            outputStream.write(byteArrayOf(1, 2, 3))
        }

        assertTrue(file.exists())
        assertContentEquals(byteArrayOf(1, 2, 3), fileHandle.read()!!.readAllBytes())
    }

    @Test
    fun `replace existing file`() {
        val file = File(directory, "foo")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val fileHandle = SimpleFileHandle(file)

        fileHandle.write().use { outputStream ->
            outputStream.write(byteArrayOf(4, 5))
        }

        assertTrue(file.exists())
        assertContentEquals(byteArrayOf(4, 5), fileHandle.read()!!.readAllBytes())
    }

    @Test
    fun `delete non-existing file`() {
        val file = File(directory, "foo")
        val fileHandle = SimpleFileHandle(file)

        fileHandle.delete()

        assertFalse(file.exists())
    }

    @Test
    fun `delete existing file`() {
        val file = File(directory, "foo")
        file.createNewFile()
        val fileHandle = SimpleFileHandle(file)

        fileHandle.delete()

        assertFalse(file.exists())
    }

    @Test
    fun `delete fails`() {
        val subdirectory = File(directory, "dir")
        subdirectory.mkdir()
        val file = File(subdirectory, "foo")
        file.createNewFile()
        val fileHandle = SimpleFileHandle(subdirectory)

        assertFailsWith<IOException> {
            fileHandle.delete()
        }
    }
}
