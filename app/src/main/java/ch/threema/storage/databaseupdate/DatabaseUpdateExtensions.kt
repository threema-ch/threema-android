package ch.threema.storage.databaseupdate

import android.content.Context
import androidx.preference.PreferenceManager
import ch.threema.app.stores.PreferenceStore
import ch.threema.domain.types.Identity
import ch.threema.storage.runQuery
import net.zetetic.database.sqlcipher.SQLiteDatabase

fun SQLiteDatabase.fieldExists(
    table: String,
    fieldName: String,
): Boolean =
    // The SQLite table_info pragma returns one row for each normal column in the named table.
    runQuery(
        table = "pragma_table_info('$table')",
        columns = arrayOf("name"),
        selection = "name = ?",
        selectionArgs = arrayOf(fieldName),
    )
        .use { cursor -> cursor.count > 0 }

fun SQLiteDatabase.tableExists(
    table: String,
): Boolean =
    rawQuery(
        "SELECT 1 FROM `sqlite_master` WHERE type = 'table' AND name = ?",
        arrayOf(table),
    )
        .use { cursor -> cursor.count > 0 }

fun getMyIdentity(context: Context): Identity? =
    PreferenceManager.getDefaultSharedPreferences(context).getString(PreferenceStore.PREFS_IDENTITY, null)
