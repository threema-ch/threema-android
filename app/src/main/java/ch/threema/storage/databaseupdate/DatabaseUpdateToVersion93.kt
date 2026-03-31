package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion93(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        for (table in arrayOf(
            "message",
            "m_group_message",
        )) {
            if (!sqLiteDatabase.fieldExists(table, "editedAtUtc")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE `$table` ADD COLUMN `editedAtUtc` DATETIME")
            }
        }
    }

    override fun getDescription() = "add editedAt field for messages"

    override val version = 93
}
