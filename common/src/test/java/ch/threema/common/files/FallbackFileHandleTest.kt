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

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FallbackFileHandleTest {
    @Test
    fun `primary exists, fallback does not`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns true
            },
            fallbackFile = mockk {
                every { exists() } returns false
            },
        )
        assertTrue(fileHandle.exists())
    }

    @Test
    fun `primary exists, fallback exists`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns true
            },
            fallbackFile = mockk {
                every { exists() } returns true
            },
        )
        assertTrue(fileHandle.exists())
    }

    @Test
    fun `primary does not exist, fallback exists`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns false
            },
            fallbackFile = mockk {
                every { exists() } returns true
            },
        )
        assertTrue(fileHandle.exists())
    }

    @Test
    fun `neither primary nor fallback exist`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns false
            },
            fallbackFile = mockk {
                every { exists() } returns false
            },
        )
        assertFalse(fileHandle.exists())
    }

    @Test
    fun `primary is empty`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns true
                every { isEmpty() } returns true
            },
            fallbackFile = mockk(),
        )
        assertTrue(fileHandle.isEmpty())
    }

    @Test
    fun `primary is not empty`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns true
                every { isEmpty() } returns false
            },
            fallbackFile = mockk(),
        )
        assertFalse(fileHandle.isEmpty())
    }

    @Test
    fun `primary does not exist, fallback is empty`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns false
            },
            fallbackFile = mockk {
                every { exists() } returns true
                every { isEmpty() } returns true
            },
        )
        assertTrue(fileHandle.isEmpty())
    }

    @Test
    fun `primary does not exist, fallback is not empty`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns false
            },
            fallbackFile = mockk {
                every { exists() } returns true
                every { isEmpty() } returns false
            },
        )
        assertFalse(fileHandle.isEmpty())
    }

    @Test
    fun `neither primary nor fallback exist means not empty`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns false
            },
            fallbackFile = mockk {
                every { exists() } returns false
            },
        )
        assertFalse(fileHandle.isEmpty())
    }

    @Test
    fun `read from primary`() {
        val inputStreamMock = mockk<InputStream>()
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns true
                every { read() } returns inputStreamMock
            },
            fallbackFile = mockk(),
        )
        assertEquals(inputStreamMock, fileHandle.read())
    }

    @Test
    fun `read from fallback`() {
        val inputStreamMock = mockk<InputStream>()
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns false
            },
            fallbackFile = mockk {
                every { exists() } returns true
                every { read() } returns inputStreamMock
            },
        )
        assertEquals(inputStreamMock, fileHandle.read())
    }

    @Test
    fun `read returns null if neither primary nor fallback exists`() {
        val fileHandle = FallbackFileHandle(
            primaryFile = mockk {
                every { exists() } returns false
            },
            fallbackFile = mockk {
                every { exists() } returns false
            },
        )
        assertNull(fileHandle.read())
    }

    @Test
    fun `creating a file, deletes the fallback`() {
        val primaryFileHandleMock = mockk<FileHandle> {
            every { create() } just runs
        }
        val fallbackFileHandleMock = mockk<FileHandle> {
            every { delete() } just runs
        }
        val fileHandle = FallbackFileHandle(
            primaryFile = primaryFileHandleMock,
            fallbackFile = fallbackFileHandleMock,
        )

        fileHandle.create()

        verify(exactly = 1) { primaryFileHandleMock.create() }
        verify(exactly = 1) { fallbackFileHandleMock.delete() }
    }

    @Test
    fun `writing a file, deletes the fallback`() {
        val outputStreamMock = mockk<OutputStream>()
        val primaryFileHandleMock = mockk<FileHandle> {
            every { write() } returns outputStreamMock
        }
        val fallbackFileHandleMock = mockk<FileHandle> {
            every { delete() } just runs
        }
        val fileHandle = FallbackFileHandle(
            primaryFile = primaryFileHandleMock,
            fallbackFile = fallbackFileHandleMock,
        )

        assertEquals(outputStreamMock, fileHandle.write())

        verify(exactly = 1) { primaryFileHandleMock.write() }
        verify(exactly = 1) { fallbackFileHandleMock.delete() }
    }

    @Test
    fun `deleting a file, deletes primary and fallback`() {
        val primaryFileHandleMock = mockk<FileHandle> {
            every { delete() } just runs
        }
        val fallbackFileHandleMock = mockk<FileHandle> {
            every { delete() } just runs
        }
        val fileHandle = FallbackFileHandle(
            primaryFile = primaryFileHandleMock,
            fallbackFile = fallbackFileHandleMock,
        )

        fileHandle.delete()

        verify(exactly = 1) { primaryFileHandleMock.delete() }
        verify(exactly = 1) { fallbackFileHandleMock.delete() }
    }

    @Test
    fun `creating a file succeeds even when fallback can not be deleted`() {
        val primaryFileHandleMock = mockk<FileHandle> {
            every { create() } just runs
        }
        val fallbackFileHandleMock = mockk<FileHandle> {
            every { delete() } answers { throw IOException() }
        }
        val fileHandle = FallbackFileHandle(
            primaryFile = primaryFileHandleMock,
            fallbackFile = fallbackFileHandleMock,
        )

        fileHandle.create()

        verify(exactly = 1) { primaryFileHandleMock.create() }
        verify(exactly = 1) { fallbackFileHandleMock.delete() }
    }

    @Test
    fun `writing a file succeeds even when fallback can not be deleted`() {
        val outputStreamMock = mockk<OutputStream>()
        val primaryFileHandleMock = mockk<FileHandle> {
            every { write() } returns outputStreamMock
        }
        val fallbackFileHandleMock = mockk<FileHandle> {
            every { delete() } answers { throw IOException() }
        }
        val fileHandle = FallbackFileHandle(
            primaryFile = primaryFileHandleMock,
            fallbackFile = fallbackFileHandleMock,
        )

        assertEquals(outputStreamMock, fileHandle.write())

        verify(exactly = 1) { primaryFileHandleMock.write() }
        verify(exactly = 1) { fallbackFileHandleMock.delete() }
    }

    @Test
    fun `deleting a file succeeds even when fallback can not be deleted`() {
        val primaryFileHandleMock = mockk<FileHandle> {
            every { delete() } just runs
        }
        val fallbackFileHandleMock = mockk<FileHandle> {
            every { delete() } answers { throw IOException() }
        }
        val fileHandle = FallbackFileHandle(
            primaryFile = primaryFileHandleMock,
            fallbackFile = fallbackFileHandleMock,
        )

        fileHandle.delete()

        verify(exactly = 1) { primaryFileHandleMock.delete() }
        verify(exactly = 1) { fallbackFileHandleMock.delete() }
    }
}
