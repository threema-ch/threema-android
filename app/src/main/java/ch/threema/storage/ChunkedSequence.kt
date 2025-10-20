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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A chunked sequence is used to read data from the database using smaller chunks to prevent working
 * with huge cursors. This may speed up accessing elements from the database. Note that this
 * sequence can only be iterated once.
 *
 * @param chunks the number of chunks that will be used
 * @param cursorCreator provide the cursor from the given range
 * @param extractor extract the value from the cursor. Note that it must not modify the cursor's position.
 */
class ChunkedSequence<T>(
    private val chunks: LongProgression,
    private val cursorCreator: (from: Long, size: Long) -> Cursor,
    private val extractor: (Cursor) -> T,
) : AutoCloseable, Sequence<T>, Iterable<T> {
    private val iterationStarted = AtomicBoolean(false)

    private var chunkIterator = chunks.iterator()
    private var currentCursor: Cursor? = null
    private val sequence = sequence {
        while (chunkIterator.hasNext()) {
            val cursor = cursorCreator(chunkIterator.next(), chunks.step)
            currentCursor = cursor
            while (!cursor.isClosed && cursor.moveToNext()) {
                yield(extractor(cursor))
            }
            cursor.close()
        }
    }

    override fun iterator(): Iterator<T> {
        if (iterationStarted.getAndSet(true)) {
            error("Cannot iterate several times over this chunked sequence")
        }
        return sequence.iterator()
    }

    override fun close() {
        currentCursor?.close()
    }
}
