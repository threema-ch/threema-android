/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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
