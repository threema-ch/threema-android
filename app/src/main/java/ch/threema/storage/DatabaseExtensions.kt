package ch.threema.storage

import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.transaction
import ch.threema.localcrypto.MasterKey
import java.nio.ByteBuffer
import net.zetetic.database.sqlcipher.SQLiteDatabase

inline fun <T, DB : SupportSQLiteDatabase> DB.runTransaction(
    exclusive: Boolean = true,
    body: DB.() -> T,
): T =
    transaction(exclusive) { body() }

fun SQLiteDatabase.runQuery(
    table: String,
    columns: Array<String>? = null,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    groupBy: String? = null,
    having: String? = null,
    orderBy: String? = null,
    limit: String? = null,
): Cursor =
    query(
        table,
        columns,
        selection,
        selectionArgs,
        groupBy,
        having,
        orderBy,
        limit,
    )

fun SQLiteDatabase.runInsert(
    table: String,
    values: ContentValues,
): Long =
    insertOrThrow(table, null, values)

fun SQLiteDatabase.runUpdate(
    table: String,
    values: ContentValues,
    whereClause: String?,
    whereArgs: Array<String>? = null,
): Int =
    update(table, values, whereClause, whereArgs)

fun SQLiteDatabase.runDelete(
    table: String,
    whereClause: String?,
    whereArgs: Array<out Any?>? = null,
): Int =
    delete(
        table,
        whereClause,
        whereArgs,
    )

fun buildContentValues(block: ContentValues.() -> Unit): ContentValues =
    ContentValues().apply(block)

/**
 * Derives the database password from the master key in such a way that it is never kept in memory as a string,
 * to allow explicitly clearing it from memory when closing the database by filling it with zeroes.
 *
 * For historic reasons, this derived password is the byte representation of the string `x"[lowercase hex encoded master key]"`
 */
fun MasterKey.deriveDatabasePassword(): ByteArray {
    val digits = "0123456789abcdef"
    val masterKeyData = value
    return ByteBuffer.allocate(masterKeyData.size * 2 + 3)
        .apply {
            put('x'.code.toByte())
            put('"'.code.toByte())
            value.forEach { byte ->
                val value = byte.toInt()
                put(digits[(value shr 4) and 0xF].code.toByte())
                put(digits[value and 0xF].code.toByte())
            }
            put('"'.code.toByte())
        }
        .array()
}
