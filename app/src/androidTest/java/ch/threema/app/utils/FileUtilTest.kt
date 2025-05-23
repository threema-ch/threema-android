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
