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

package ch.threema.base

import ch.threema.testhelpers.createTempDirectory
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class FileExtensionsTest {

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
    fun `write file successfully`() {
        val file = File(directory, "my-file")
        assertFalse(file.exists())

        file.writeAtomically { outputStream ->
            outputStream.write(byteArrayOf(1, 2, 3))
            outputStream.write(byteArrayOf(4, 5, 6))
        }

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), file.readBytes())
    }

    @Test
    fun `write file unsuccessfully`() {
        val file = File(directory, "my-file")
        assertFalse(file.exists())

        assertFails {
            file.writeAtomically { outputStream ->
                outputStream.write(byteArrayOf(1, 2, 3))
                error("something went wrong")
            }
        }

        assertFalse(file.exists())
    }
}
