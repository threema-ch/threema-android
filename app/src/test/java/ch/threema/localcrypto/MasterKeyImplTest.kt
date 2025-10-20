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

package ch.threema.localcrypto

import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.apache.commons.io.output.ByteArrayOutputStream

class MasterKeyImplTest {
    @Test
    fun `access raw bytes`() {
        val masterKey = MasterKeyImpl(MASTER_KEY)
        assertContentEquals(MASTER_KEY, masterKey.value)
    }

    @Test
    fun `initially valid`() {
        val masterKey = MasterKeyImpl(MASTER_KEY)
        assertTrue(masterKey.isValid())
    }

    @Test
    fun `cipher streams can read and write data`() {
        val masterKey = MasterKeyImpl(MASTER_KEY)
        val testBytesSize = 65521 // prime number to exercise padding
        val testBytes = Random(RANDOM_SEED).nextBytes(testBytesSize)

        val outStream = ByteArrayOutputStream()
        masterKey.getCipherOutputStream(outStream).use { cipherOutStream ->
            cipherOutStream.write(testBytes)
        }

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val readBytes = masterKey.getCipherInputStream(inStream).use { cipherInStream ->
            cipherInStream.readAllBytes()
        }

        assertContentEquals(testBytes, readBytes)
    }

    @Test
    fun `reading invalid data stream fails`() {
        val masterKey = MasterKeyImpl(MASTER_KEY)

        val inStream = ByteArrayInputStream(byteArrayOf(1, 2, 3))

        assertFailsWith<IOException> {
            masterKey.getCipherInputStream(inStream)
        }
    }

    @Test
    fun `invalidate key`() {
        val bytes = MASTER_KEY.copyOf()
        val masterKey = MasterKeyImpl(bytes)

        masterKey.invalidate()

        val emptyKey = ByteArray(MASTER_KEY.size)
        assertFalse(masterKey.isValid())
        assertContentEquals(emptyKey, masterKey.value)
        assertContentEquals(emptyKey, bytes)
    }

    @Test
    fun `cipher streams cannot read data when key is invalidated`() {
        val masterKey = MasterKeyImpl(MASTER_KEY.copyOf())

        masterKey.invalidate()

        assertFailsWith<IOException> {
            masterKey.getCipherInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        }
    }

    @Test
    fun `cipher streams cannot write data when key is invalidated`() {
        val masterKey = MasterKeyImpl(MASTER_KEY.copyOf())

        masterKey.invalidate()

        assertFailsWith<IOException> {
            masterKey.getCipherOutputStream(ByteArrayOutputStream())
        }
    }

    companion object {
        private const val RANDOM_SEED = 123456789
    }
}
