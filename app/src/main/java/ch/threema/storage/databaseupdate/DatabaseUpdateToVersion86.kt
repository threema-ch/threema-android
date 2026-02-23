package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion86(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            "CREATE TABLE `tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`task` STRING NOT NULL)",
        )
    }

    override fun getDescription() = "create task archive table"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 86
    }
}
