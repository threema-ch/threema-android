package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion85(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        for (table in arrayOf(
            "message",
            "m_group_message",
            "distribution_list_message",
        )) {
            if (!sqLiteDatabase.fieldExists(table, "displayTags")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE `$table` ADD COLUMN `displayTags` TINYINT DEFAULT 0")
            }
        }
    }

    override fun getDescription() = "add display tags field"

    override val version = 85
}
