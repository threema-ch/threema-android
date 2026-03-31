package ch.threema.android

import android.database.Cursor

/**
 * Iterates through the cursor starting at its next position and returns a list of the result of [transform] for each of the cursor's entries.
 *
 * Note that the cursor is closed afterwards.
 */
fun <R, S> Cursor.map(readerCreator: (Cursor) -> S, transform: S.() -> R): List<R> = use {
    val reader = readerCreator(this)
    val results = mutableListOf<R>()
    while (moveToNext()) {
        results.add(transform(reader))
    }
    return results
}
