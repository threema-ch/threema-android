package ch.threema.storage

import android.database.Cursor
import androidx.collection.SimpleArrayMap

class ColumnIndexCache {
    private val indexMap = SimpleArrayMap<String, Int>()

    fun getColumnIndex(cursor: Cursor, columnName: String): Int {
        synchronized(indexMap) {
            val cachedIndex = indexMap[columnName]
            if (cachedIndex != null) {
                return cachedIndex
            }
            val index = cursor.getColumnIndex(columnName)
            indexMap.put(columnName, index)
            return index
        }
    }

    fun clear() {
        synchronized(indexMap) {
            indexMap.clear()
        }
    }
}
