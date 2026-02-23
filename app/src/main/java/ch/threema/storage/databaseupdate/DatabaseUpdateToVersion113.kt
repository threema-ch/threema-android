package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion113(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            "CREATE TABLE `app_tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`appTaskData` TEXT NOT NULL)",
        )
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "create table for persisting app tasks"

    companion object {
        const val VERSION = 113
    }
}
