package ch.threema.common

import ch.threema.testhelpers.createTempDirectory
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileExtensionsTest {
    @Test
    fun `clear a directory recursively`() {
        val root = createTempDirectory("root")
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
        val root = createTempDirectory("root")
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
        val root = createTempDirectory("root")
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

    @Test
    fun `delete a file securely`() {
        val trashDirectory = createTempDirectory()
        val file = File.createTempFile("test", "test")
        file.writeText("Hello World")
        assertTrue(file.exists())

        file.deleteSecurely(trashDirectory)

        assertFalse(file.exists())
        assertContentEquals(emptyArray<File>(), trashDirectory.listFiles())
    }

    @Test
    fun `delete a file securely that does not exist`() {
        val file = File.createTempFile("test", "test")
        file.delete()
        assertFalse(file.exists())

        file.deleteSecurely(createTempDirectory())
        assertFalse(file.exists())
    }

    @Test
    fun `cannot delete a directory securely`() {
        val directory = createTempDirectory("root")

        assertFailsWith<IOException> {
            directory.deleteSecurely(createTempDirectory())
        }
    }
}
