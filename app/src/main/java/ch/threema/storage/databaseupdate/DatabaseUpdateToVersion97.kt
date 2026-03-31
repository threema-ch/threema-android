package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion97(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        for (table in arrayOf(
            "message",
            "m_group_message",
            "distribution_list_message",
        )) {
            if (!sqLiteDatabase.fieldExists(table, "deletedAtUtc")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE `$table` ADD COLUMN `deletedAtUtc` DATETIME")
            }
        }
    }

    override fun getDescription() = "add deletedAtUtc field for messages"

    override val version = 97
}
