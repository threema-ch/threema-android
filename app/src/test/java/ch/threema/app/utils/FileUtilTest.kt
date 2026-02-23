package ch.threema.app.utils

import io.mockk.mockk
import io.mockk.verify
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.slf4j.Logger

class FileUtilTest {
    @Test
    fun testDeleteFileOrWarn() {
        // Create temporary file
        val tempFile = Files.createTempFile("FileUtilTest", "tmp").toFile()
        assertTrue(tempFile.exists(), "Temporary file was not created")

        // Mock logger
        val logger = mockk<Logger>(relaxed = true)

        // Remove it
        FileUtil.deleteFileOrWarn(tempFile, "testfile", logger)
        assertFalse(tempFile.exists(), "Temporary file was not deleted")
        verify(exactly = 0) { logger.warn(any(), any<Any>()) }

        // Deleting fails the second time
        FileUtil.deleteFileOrWarn(tempFile, "testfile", logger)
        verify(exactly = 1) { logger.warn("Could not delete {}", "testfile") }
    }

    @Test
    fun testSanitizeFileName() {
        val emptyFileName = ""
        val badFileName = "test:file@/*123-\"asd?|<>.png"
        val badZipName = "/zip..file.zip"
        val validName = "testfile.zip"

        assertNull(FileUtil.sanitizeFileName(emptyFileName))
        assertEquals("test_file@__123-_asd____.png", FileUtil.sanitizeFileName(badFileName))
        assertEquals("_zip_file.zip", FileUtil.sanitizeFileName(badZipName))
        assertEquals("testfile.zip", FileUtil.sanitizeFileName(validName))
    }
}
