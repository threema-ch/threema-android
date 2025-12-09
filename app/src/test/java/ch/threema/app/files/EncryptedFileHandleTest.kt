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

package ch.threema.app.files

import ch.threema.common.files.FileHandle
import ch.threema.localcrypto.MasterKey
import ch.threema.localcrypto.MasterKeyProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedFileHandleTest {

    @Test
    fun `file existence is checked on original`() {
        val fileHandleMock = mockk<FileHandle> {
            every { exists() } returns true
        }
        val fileHandle = EncryptedFileHandle(
            masterKeyProvider = mockk(),
            file = fileHandleMock,
        )

        assertTrue(fileHandle.exists())
        verify(exactly = 1) { fileHandleMock.exists() }
    }

    @Test
    fun `file emptiness is checked on original`() {
        val fileHandleMock = mockk<FileHandle> {
            every { isEmpty() } returns true
        }
        val fileHandle = EncryptedFileHandle(
            masterKeyProvider = mockk(),
            file = fileHandleMock,
        )

        assertTrue(fileHandle.isEmpty())
        verify(exactly = 1) { fileHandleMock.isEmpty() }
    }

    @Test
    fun `file creation is passed to original`() {
        val fileHandleMock = mockk<FileHandle> {
            every { create() } just runs
        }
        val fileHandle = EncryptedFileHandle(
            masterKeyProvider = mockk(),
            file = fileHandleMock,
        )

        fileHandle.create()

        verify(exactly = 1) { fileHandleMock.create() }
    }

    @Test
    fun `file deletion is passed to original`() {
        val fileHandleMock = mockk<FileHandle> {
            every { delete() } just runs
        }
        val fileHandle = EncryptedFileHandle(
            masterKeyProvider = mockk(),
            file = fileHandleMock,
        )

        fileHandle.delete()

        verify(exactly = 1) { fileHandleMock.delete() }
    }

    @Test
    fun `read non-existing file`() {
        val fileHandle = EncryptedFileHandle(
            masterKeyProvider = mockk(),
            file = mockk {
                every { read() } returns null
            },
        )

        assertNull(fileHandle.read())
    }

    @Test
    fun `read encrypted file`() {
        val encryptedInputStreamMock = mockk<InputStream>()
        val plaintextInputStreamMock = mockk<InputStream>()
        val masterKeyMock = mockk<MasterKey> {
            every { decrypt(encryptedInputStreamMock) } returns plaintextInputStreamMock
        }
        val masterKeyProviderMock = mockk<MasterKeyProvider> {
            every { getMasterKey() } returns masterKeyMock
        }
        val fileHandle = EncryptedFileHandle(
            masterKeyProvider = masterKeyProviderMock,
            file = mockk {
                every { read() } returns encryptedInputStreamMock
            },
        )

        assertEquals(plaintextInputStreamMock, fileHandle.read())
    }

    @Test
    fun `write encrypted file`() {
        val encryptedOutputStreamMock = mockk<OutputStream>()
        val plaintextOutputStreamMock = mockk<OutputStream>()
        val masterKeyMock = mockk<MasterKey> {
            every { encrypt(plaintextOutputStreamMock) } returns encryptedOutputStreamMock
        }
        val masterKeyProviderMock = mockk<MasterKeyProvider> {
            every { getMasterKey() } returns masterKeyMock
        }
        val fileHandle = EncryptedFileHandle(
            masterKeyProvider = masterKeyProviderMock,
            file = mockk {
                every { write() } returns plaintextOutputStreamMock
            },
        )

        assertEquals(encryptedOutputStreamMock, fileHandle.write())
    }
}
