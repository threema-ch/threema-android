package ch.threema.app.utils

import ch.threema.app.ThreemaApplication
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileUtilTest {
    @Test
    fun testValidPaths() {
        // Arrange
        val paths = listOf(
            "/data/data/other/app/files/.crs-private_key/",
            "/sdcard/Download/some_file.txt",
        )

        paths.forEach { path ->
            // Act
            val isSanePath = FileUtil.isSanePath(ThreemaApplication.getAppContext(), path)

            // Assert
            assertTrue { isSanePath }
        }
    }

    @Test
    fun testValidPathsWithTraversals() {
        // Arrange
        val paths = listOf(
            "/data/data/other/app/../app/./files/.crs-private_key/",
            "../../../../sdcard/Download/some_file.txt",
            "/data/../sdcard/Download/some_file.txt",
        )

        paths.forEach { path ->
            // Act
            val isSanePath = FileUtil.isSanePath(ThreemaApplication.getAppContext(), path)

            // Assert
            assertTrue { isSanePath }
        }
    }

    @Test
    fun testInvalidInternalPaths() {
        // Arrange
        val paths = listOf(
            "/data/data/ch.threema.app/databases/db.db",
            "/data/data/ch.threema.app/files/file.txt",
            "/data/data/ch.threema.app/file.txt",
        )

        paths.forEach { path ->
            // Act
            val isSanePath = FileUtil.isSanePath(ThreemaApplication.getAppContext(), path)

            // Assert
            assertFalse { isSanePath }
        }
    }

    @Test
    fun testInvalidInternalPathsWithPathTraversals() {
        // Arrange
        val paths = listOf(
            "../.././data/data/ch.threema.app/databases/db.db",
            "/data/data/../data/ch.threema.app/files/file.txt",
            "/data/data/ch.threema.app/../ch.threema.app/../ch.threema.app/file.txt",
            "/data/data/.///./ch.threema.app/file.txt",
            "/data/../../../data/data/ch.threema.app/file.txt",
        )

        paths.forEach { path ->
            // Act
            val isSanePath = FileUtil.isSanePath(ThreemaApplication.getAppContext(), path)

            // Assert
            assertFalse { isSanePath }
        }
    }
}
