package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion77(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            "CREATE TABLE `group_call` (" +
                "`callId` TEXT PRIMARY KEY NOT NULL, " +
                "`groupId` INTEGER NOT NULL, " +
                "`sfuBaseUrl` TEXT NOT NULL, " +
                "`gck` TEXT NOT NULL)",
        )
    }

    override fun getDescription() = "GroupCalls"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 77
    }
}
