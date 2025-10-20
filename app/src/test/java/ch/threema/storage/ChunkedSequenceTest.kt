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

package ch.threema.storage

import android.database.Cursor
import ch.threema.testhelpers.willThrow
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ChunkedSequenceTest {
    @Test
    fun `test all used cursors are closed`() {
        val createdCursors = mutableListOf<Cursor>()

        val chunkedSequence = ChunkedSequence(
            chunks = LongProgression.fromClosedRange(0L, 100L, 10L),
            cursorCreator = { _, _ ->
                createClosableEmptyCursorMock().also { cursor -> createdCursors.add(cursor) }
            },
            extractor = { },
        )

        // Ensure that there are no elements because the cursors are empty
        val numElements = (chunkedSequence as Sequence<Unit>).count()
        assertEquals(0, numElements)

        createdCursors.forEach { cursor ->
            verify(exactly = 1) { cursor.close() }
        }
    }

    @Test
    fun `test several chunks`() {
        val rangeCalls = mutableListOf<Pair<Long, Long>>()

        val chunkedSequence = ChunkedSequence(
            chunks = LongProgression.fromClosedRange(0L, 22L, 10L),
            cursorCreator = { from, size ->
                rangeCalls.add(from to size)
                createClosableEmptyCursorMock()
            },
            extractor = { },
        )

        // Ensure that there are no elements because the cursors are empty
        val numElements = (chunkedSequence as Sequence<Unit>).count()
        assertEquals(0, numElements)

        val expectedCalls = listOf(
            0L to 10L,
            10L to 10L,
            20L to 10L,
        )
        assertEquals(expectedCalls, rangeCalls)
    }

    @Test
    fun `test one chunk`() {
        val rangeCalls = mutableListOf<Pair<Long, Long>>()

        val chunkedSequence = ChunkedSequence(
            chunks = LongProgression.fromClosedRange(0L, 9L, 10L),
            cursorCreator = { from, size ->
                rangeCalls.add(from to size)
                createClosableEmptyCursorMock()
            },
            extractor = { },
        )

        // Ensure that there are no elements because the cursors are empty
        val numElements = (chunkedSequence as Sequence<Unit>).count()
        assertEquals(0, numElements)

        val expectedCalls = listOf(
            0L to 10L,
        )
        assertEquals(expectedCalls, rangeCalls)
    }

    @Test
    fun `sequence can be consumed only once`() {
        val chunkedSequence = ChunkedSequence(
            chunks = LongProgression.fromClosedRange(0L, 9L, 10L),
            cursorCreator = { _, _ -> createClosableEmptyCursorMock() },
            extractor = { },
        )

        // Iterate through it once
        for (unit in chunkedSequence) {
            fail("Expected to be empty, but got $unit")
        }

        // Assert that it can't be iterated once more
        {
            for (unit in chunkedSequence) {
                fail("Expected to be empty, but got $unit")
            }
        } willThrow IllegalStateException::class
    }

    @Test
    fun `all elements are produced`() {
        val chunkedSequenceIterator = ChunkedSequence(
            chunks = LongProgression.fromClosedRange(0L, 99L, 12L),
            cursorCreator = { from, size -> createdRangeEmittingCursorMock(from, from + size) },
            extractor = { cursor -> cursor.getLong(0) },
        ).iterator()

        for (expected in 0L..99L) {
            assertEquals(expected, chunkedSequenceIterator.next())
        }
    }

    private fun createClosableEmptyCursorMock(): Cursor {
        val cursor = mockk<Cursor>()
        every { cursor.close() } just Runs
        every { cursor.isClosed } returns false
        every { cursor.moveToNext() } returns false
        every { cursor.moveToFirst() } returns false
        return cursor
    }

    /**
     * A mocked cursor that returns the values from [from] until [to] when calling [Cursor.getLong] at index 0.
     */
    private fun createdRangeEmittingCursorMock(from: Long, to: Long): Cursor {
        val cursor = mockk<Cursor>()

        val size = to - from

        var isClosed = false
        var cursorPosition = -1L

        every { cursor.close() } answers {
            isClosed = true
        }
        every { cursor.moveToNext() } answers {
            if (isClosed) {
                false
            } else {
                cursorPosition++
                cursorPosition < size
            }
        }
        every { cursor.moveToFirst() } answers {
            if (isClosed) {
                false
            } else {
                cursorPosition = 0
                0 < size
            }
        }
        every { cursor.isClosed } returns isClosed
        every { cursor.getLong(0) } answers {
            if (isClosed) {
                throw IllegalStateException("Cursor is already closed")
            }
            if (cursorPosition < 0) {
                throw IllegalStateException("Cursor position is negative")
            }
            if (cursorPosition >= to) {
                throw IllegalStateException("Cursor position is past last entry")
            }
            from + cursorPosition
        }

        return cursor
    }
}
